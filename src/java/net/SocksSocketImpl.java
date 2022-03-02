/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.net;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import sun.net.SocksProxy;
import sun.net.www.ParseUtil;
/* import org.ietf.jgss.*; */

/**
 * SOCKS (V4 & V5) TCP socket implementation (RFC 1928).
 * This is a subclass of PlainSocketImpl.
 * Note this class should <b>NOT</b> be public.
 */

// |SOCKS|是一种网络传输协议，位于会话层，主要用于客户端与外网服务器之间的中间传输。即，当一个客户
// 端需要穿过防火墙访问服务器时，可能需要先跟|SOCKS|代理服务器进行连接，然后由这个代理服务器控制该
// 客户端的访问
// 注：|SOCKS|使用握手协议来通知代理软件其客户端试图进行的|SOCKS|连接，然后尽可能透明地进行操作，
// 而常规代理可能会解释和重写报头（例如|FTP|；不过|HTTP|代理只是将请求转发到目标|HTTP|服务器）。
// 虽然|HTTP|代理有不同的使用模式，|HTTP CONNECT|方法允许转发|TCP|连接；然而，|SOCKS|代理还可
// 以转发|UDP|流量（仅|SOCKS5|），而|HTTP|代理不能。|HTTP|代理通常更了解|HTTP|协议，执行更高层
// 次的过滤（虽然通常只用于|GET|和|POST|方法，而不用于|CONNECT|方法）
class SocksSocketImpl extends PlainSocketImpl implements SocksConsts {
    // 代理服务器的域名或字符形式地址
    private String server = null;
    // 代理服务器端口
    private int serverPort = DEFAULT_PORT;

    // 使用|SOCKS|代理服务器时，需要连接的外网服务器的套接字地址
    private InetSocketAddress external_address;
    private boolean useV4 = false;
    private Socket cmdsock = null;

    // 本机与|SOCKS|代理服务器连接的套接字输入、输出流
    private InputStream cmdIn = null;
    private OutputStream cmdOut = null;
    /* true if the Proxy has been set programatically */
    private boolean applicationSetProxy;  /* false */


    SocksSocketImpl() {
        // Nothing needed
    }

    SocksSocketImpl(String server, int port) {
        this.server = server;
        this.serverPort = (port == -1 ? DEFAULT_PORT : port);
    }

    SocksSocketImpl(Proxy proxy) {
        SocketAddress a = proxy.address();
        if (a instanceof InetSocketAddress) {
            InetSocketAddress ad = (InetSocketAddress) a;
            // Use getHostString() to avoid reverse lookups
            server = ad.getHostString();
            serverPort = ad.getPort();
        }
    }

    void setV4() {
        useV4 = true;
    }

    // 本地与代理服务器间建立连接
    private synchronized void privilegedConnect(final String host,
                                              final int port,
                                              final int timeout)
         throws IOException
    {
        try {
            AccessController.doPrivileged(
                new java.security.PrivilegedExceptionAction<Void>() {
                    public Void run() throws IOException {
                              superConnectServer(host, port, timeout);
                              cmdIn = getInputStream();
                              cmdOut = getOutputStream();
                              return null;
                          }
                      });
        } catch (java.security.PrivilegedActionException pae) {
            throw (IOException) pae.getException();
        }
    }

    // 本地与代理服务器间建立|TCP|原生连接
    private void superConnectServer(String host, int port,
                                    int timeout) throws IOException {
        super.connect(new InetSocketAddress(host, port), timeout);
    }

    private static int remainingMillis(long deadlineMillis) throws IOException {
        if (deadlineMillis == 0L)
            return 0;

        final long remaining = deadlineMillis - System.currentTimeMillis();
        if (remaining > 0)
            return (int) remaining;

        throw new SocketTimeoutException();
    }

    private int readSocksReply(InputStream in, byte[] data) throws IOException {
        return readSocksReply(in, data, 0L);
    }

    // 从代理服务器中读取返回的|TCP|信息
    private int readSocksReply(InputStream in, byte[] data, long deadlineMillis) throws IOException {
        int len = data.length;
        int received = 0;
        for (int attempts = 0; received < len && attempts < 3; attempts++) {
            int count;
            try {
                count = ((SocketInputStream)in).read(data, received, len - received, remainingMillis(deadlineMillis));
            } catch (SocketTimeoutException e) {
                throw new SocketTimeoutException("Connect timed out");
            }
            if (count < 0)
                throw new SocketException("Malformed reply from SOCKS server");
            received += count;
        }
        return received;
    }

    /**
     * Provides the authentication machanism required by the proxy.
     */
    private boolean authenticate(byte method, InputStream in,
                                 BufferedOutputStream out) throws IOException {
        return authenticate(method, in, out, 0L);
    }

    private boolean authenticate(byte method, InputStream in,
                                 BufferedOutputStream out,
                                 long deadlineMillis) throws IOException {
        // No Authentication required. We're done then!
        if (method == NO_AUTH)
            return true;
        /**
         * User/Password authentication. Try, in that order :
         * - The application provided Authenticator, if any
         * - the user.name & no password (backward compatibility behavior).
         */
        if (method == USER_PASSW) {
            String userName;
            String password = null;
            final InetAddress addr = InetAddress.getByName(server);
            PasswordAuthentication pw =
                java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<PasswordAuthentication>() {
                        public PasswordAuthentication run() {
                                return Authenticator.requestPasswordAuthentication(
                                       server, addr, serverPort, "SOCKS5", "SOCKS authentication", null);
                            }
                        });
            if (pw != null) {
                userName = pw.getUserName();
                password = new String(pw.getPassword());
            } else {
                userName = java.security.AccessController.doPrivileged(
                        new sun.security.action.GetPropertyAction("user.name"));
            }
            if (userName == null)
                return false;
            out.write(1);
            out.write(userName.length());
            try {
                out.write(userName.getBytes("ISO-8859-1"));
            } catch (java.io.UnsupportedEncodingException uee) {
                assert false;
            }
            if (password != null) {
                out.write(password.length());
                try {
                    out.write(password.getBytes("ISO-8859-1"));
                } catch (java.io.UnsupportedEncodingException uee) {
                    assert false;
                }
            } else
                out.write(0);
            out.flush();
            byte[] data = new byte[2];
            int i = readSocksReply(in, data, deadlineMillis);
            if (i != 2 || data[1] != 0) {
                /* RFC 1929 specifies that the connection MUST be closed if
                   authentication fails */
                out.close();
                in.close();
                return false;
            }
            /* Authentication succeeded */
            return true;
        }
        /**
         * GSSAPI authentication mechanism.
         * Unfortunately the RFC seems out of sync with the Reference
         * implementation. I'll leave this in for future completion.
         */
//      if (method == GSSAPI) {
//          try {
//              GSSManager manager = GSSManager.getInstance();
//              GSSName name = manager.createName("SERVICE:socks@"+server,
//                                                   null);
//              GSSContext context = manager.createContext(name, null, null,
//                                                         GSSContext.DEFAULT_LIFETIME);
//              context.requestMutualAuth(true);
//              context.requestReplayDet(true);
//              context.requestSequenceDet(true);
//              context.requestCredDeleg(true);
//              byte []inToken = new byte[0];
//              while (!context.isEstablished()) {
//                  byte[] outToken
//                      = context.initSecContext(inToken, 0, inToken.length);
//                  // send the output token if generated
//                  if (outToken != null) {
//                      out.write(1);
//                      out.write(1);
//                      out.writeShort(outToken.length);
//                      out.write(outToken);
//                      out.flush();
//                      data = new byte[2];
//                      i = readSocksReply(in, data, deadlineMillis);
//                      if (i != 2 || data[1] == 0xff) {
//                          in.close();
//                          out.close();
//                          return false;
//                      }
//                      i = readSocksReply(in, data, deadlineMillis);
//                      int len = 0;
//                      len = ((int)data[0] & 0xff) << 8;
//                      len += data[1];
//                      data = new byte[len];
//                      i = readSocksReply(in, data, deadlineMillis);
//                      if (i == len)
//                          return true;
//                      in.close();
//                      out.close();
//                  }
//              }
//          } catch (GSSException e) {
//              /* RFC 1961 states that if Context initialisation fails the connection
//                 MUST be closed */
//              e.printStackTrace();
//              in.close();
//              out.close();
//          }
//      }
        return false;
    }

    // 按照|SOCKS V4|协议，请求代理服务器和目标服务器间建立连接
    private void connectV4(InputStream in, OutputStream out,
                           InetSocketAddress endpoint,
                           long deadlineMillis) throws IOException {
        if (!(endpoint.getAddress() instanceof Inet4Address)) {
            throw new SocketException("SOCKS V4 requires IPv4 only addresses");
        }
        out.write(PROTO_VERS4);
        out.write(CONNECT);
        out.write((endpoint.getPort() >> 8) & 0xff);
        out.write((endpoint.getPort() >> 0) & 0xff);
        out.write(endpoint.getAddress().getAddress());
        String userName = getUserName();
        try {
            out.write(userName.getBytes("ISO-8859-1"));
        } catch (java.io.UnsupportedEncodingException uee) {
            assert false;
        }
        out.write(0);
        out.flush();
        byte[] data = new byte[8];
        int n = readSocksReply(in, data, deadlineMillis);
        if (n != 8)
            throw new SocketException("Reply from SOCKS server has bad length: " + n);
        if (data[0] != 0 && data[0] != 4)
            throw new SocketException("Reply from SOCKS server has bad version");
        SocketException ex = null;
        switch (data[1]) {
        case 90:
            // Success!
            external_address = endpoint;
            break;
        case 91:
            ex = new SocketException("SOCKS request rejected");
            break;
        case 92:
            ex = new SocketException("SOCKS server couldn't reach destination");
            break;
        case 93:
            ex = new SocketException("SOCKS authentication failed");
            break;
        default:
            ex = new SocketException("Reply from SOCKS server contains bad status");
            break;
        }
        if (ex != null) {
            in.close();
            out.close();
            throw ex;
        }
    }

    /**
     * Connects the Socks Socket to the specified endpoint. It will first
     * connect to the SOCKS proxy and negotiate the access. If the proxy
     * grants the connections, then the connect is successful and all
     * further traffic will go to the "real" endpoint.
     *
     * @param   endpoint        the {@code SocketAddress} to connect to.
     * @param   timeout         the timeout value in milliseconds
     * @throws  IOException     if the connection can't be established.
     * @throws  SecurityException if there is a security manager and it
     *                          doesn't allow the connection
     * @throws  IllegalArgumentException if endpoint is null or a
     *          SocketAddress subclass not supported by this socket
     */
    // 连接到指定的服务端的套接字地址上。该方法会一直阻塞，直到连接被建立、超时或发生异常
    // 注：若配置了|SOCKS|代理服务器，则将首先连接到|SOCKS|代理并协商访问，如果代理授予连接，则连
    // 接成功，所有进一步的流量都将有该代理服务器转到目标服务器；否则，将使用|PlainSocketImpl|类
    // 的原生|TCP|进行连接
    @Override
    protected void connect(SocketAddress endpoint, int timeout) throws IOException {
        final long deadlineMillis;

        if (timeout == 0) {
            deadlineMillis = 0L;
        } else {
            long finish = System.currentTimeMillis() + timeout;
            deadlineMillis = finish < 0 ? Long.MAX_VALUE : finish;
        }

        SecurityManager security = System.getSecurityManager();
        if (endpoint == null || !(endpoint instanceof InetSocketAddress))
            throw new IllegalArgumentException("Unsupported address type");

        // 检查连接权限
        InetSocketAddress epoint = (InetSocketAddress) endpoint;
        if (security != null) {
            if (epoint.isUnresolved())
                security.checkConnect(epoint.getHostName(),
                                      epoint.getPort());
            else
                security.checkConnect(epoint.getAddress().getHostAddress(),
                                      epoint.getPort());
        }

        if (server == null) {   // 未指定|SOCKS|代理服务器
            // This is the general case
            // server is not null only when the socket was created with a
            // specified proxy in which case it does bypass the ProxySelector
            // 再查询|SOCKS|代理服务选择器
            ProxySelector sel = java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<ProxySelector>() {
                    public ProxySelector run() {
                            return ProxySelector.getDefault();
                        }
                    });

            // 若选择器中也没有|SOCKS|代理服务器，则使用|PlainSocketImpl|类的原生|TCP|进行连接
            if (sel == null) {
                /*
                 * No default proxySelector --> direct connection
                 */
                super.connect(epoint, remainingMillis(deadlineMillis));
                return;
            }
            URI uri;
            // Use getHostString() to avoid reverse lookups
            String host = epoint.getHostString();
            // IPv6 litteral?
            if (epoint.getAddress() instanceof Inet6Address &&
                (!host.startsWith("[")) && (host.indexOf(":") >= 0)) {
                host = "[" + host + "]";
            }
            try {
                uri = new URI("socket://" + ParseUtil.encodePath(host) + ":"+ epoint.getPort());
            } catch (URISyntaxException e) {
                // This shouldn't happen
                assert false : e;
                uri = null;
            }
            Proxy p = null;
            IOException savedExc = null;
            java.util.Iterator<Proxy> iProxy = null;
            iProxy = sel.select(uri).iterator();
            if (iProxy == null || !(iProxy.hasNext())) {
                super.connect(epoint, remainingMillis(deadlineMillis));
                return;
            }

            // 迭代选择器中的|SOCKS|代理服务器，逐个进行本地与代理服务器间建立连接
            while (iProxy.hasNext()) {
                p = iProxy.next();
                if (p == null || p == Proxy.NO_PROXY) {
                    super.connect(epoint, remainingMillis(deadlineMillis));
                    return;
                }
                if (p.type() != Proxy.Type.SOCKS)
                    throw new SocketException("Unknown proxy type : " + p.type());
                if (!(p.address() instanceof InetSocketAddress))
                    throw new SocketException("Unknow address type for proxy: " + p);
                // Use getHostString() to avoid reverse lookups
                server = ((InetSocketAddress) p.address()).getHostString();
                serverPort = ((InetSocketAddress) p.address()).getPort();
                if (p instanceof SocksProxy) {
                    if (((SocksProxy)p).protocolVersion() == 4) {
                        useV4 = true;
                    }
                }

                // Connects to the SOCKS server
                try {
                    // 先进行本地与代理服务器间建立连接
                    privilegedConnect(server, serverPort, remainingMillis(deadlineMillis));
                    // Worked, let's get outta here
                    break;
                } catch (IOException e) {
                    // Ooops, let's notify the ProxySelector
                    sel.connectFailed(uri,p.address(),e);
                    server = null;
                    serverPort = -1;
                    savedExc = e;
                    // Will continue the while loop and try the next proxy
                }
            }

            /*
             * If server is still null at this point, none of the proxy
             * worked
             */
            if (server == null) {
                throw new SocketException("Can't connect to SOCKS proxy:"
                                          + savedExc.getMessage());
            }
        } else {
            // Connects to the SOCKS server
            try {
                // 先进行本地与代理服务器间建立连接
                privilegedConnect(server, serverPort, remainingMillis(deadlineMillis));
            } catch (IOException e) {
                throw new SocketException(e.getMessage());
            }
        }

        // cmdIn & cmdOut were initialized during the privilegedConnect() call
        BufferedOutputStream out = new BufferedOutputStream(cmdOut, 512);
        InputStream in = cmdIn;

        // 按照|SOCKS V4|协议，请求代理服务器和目标服务器间建立连接
        if (useV4) {
            // SOCKS Protocol version 4 doesn't know how to deal with
            // DOMAIN type of addresses (unresolved addresses here)
            if (epoint.isUnresolved())
                throw new UnknownHostException(epoint.toString());
            connectV4(in, out, epoint, deadlineMillis);
            return;
        }

        // 按照|SOCKS V5|协议，请求代理服务器和目标服务器间建立连接

        // This is SOCKS V5
        out.write(PROTO_VERS);
        out.write(2);
        out.write(NO_AUTH);
        out.write(USER_PASSW);
        out.flush();
        byte[] data = new byte[2];
        int i = readSocksReply(in, data, deadlineMillis);
        if (i != 2 || ((int)data[0]) != PROTO_VERS) {
            // Maybe it's not a V5 sever after all
            // Let's try V4 before we give up
            // SOCKS Protocol version 4 doesn't know how to deal with
            // DOMAIN type of addresses (unresolved addresses here)
            if (epoint.isUnresolved())
                throw new UnknownHostException(epoint.toString());
            connectV4(in, out, epoint, deadlineMillis);
            return;
        }
        if (((int)data[1]) == NO_METHODS)
            throw new SocketException("SOCKS : No acceptable methods");
        if (!authenticate(data[1], in, out, deadlineMillis)) {
            throw new SocketException("SOCKS : authentication failed");
        }
        out.write(PROTO_VERS);
        out.write(CONNECT);
        out.write(0);
        /* Test for IPV4/IPV6/Unresolved */
        if (epoint.isUnresolved()) {
            out.write(DOMAIN_NAME);
            out.write(epoint.getHostName().length());
            try {
                out.write(epoint.getHostName().getBytes("ISO-8859-1"));
            } catch (java.io.UnsupportedEncodingException uee) {
                assert false;
            }
            out.write((epoint.getPort() >> 8) & 0xff);
            out.write((epoint.getPort() >> 0) & 0xff);
        } else if (epoint.getAddress() instanceof Inet6Address) {
            out.write(IPV6);
            out.write(epoint.getAddress().getAddress());
            out.write((epoint.getPort() >> 8) & 0xff);
            out.write((epoint.getPort() >> 0) & 0xff);
        } else {
            out.write(IPV4);
            out.write(epoint.getAddress().getAddress());
            out.write((epoint.getPort() >> 8) & 0xff);
            out.write((epoint.getPort() >> 0) & 0xff);
        }
        out.flush();
        data = new byte[4];
        i = readSocksReply(in, data, deadlineMillis);
        if (i != 4)
            throw new SocketException("Reply from SOCKS server has bad length");
        SocketException ex = null;
        int len;
        byte[] addr;
        switch (data[1]) {
        case REQUEST_OK:
            // success!
            switch(data[3]) {
            case IPV4:
                addr = new byte[4];
                i = readSocksReply(in, addr, deadlineMillis);
                if (i != 4)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                data = new byte[2];
                i = readSocksReply(in, data, deadlineMillis);
                if (i != 2)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                break;
            case DOMAIN_NAME:
                len = data[1];
                byte[] host = new byte[len];
                i = readSocksReply(in, host, deadlineMillis);
                if (i != len)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                data = new byte[2];
                i = readSocksReply(in, data, deadlineMillis);
                if (i != 2)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                break;
            case IPV6:
                len = data[1];
                addr = new byte[len];
                i = readSocksReply(in, addr, deadlineMillis);
                if (i != len)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                data = new byte[2];
                i = readSocksReply(in, data, deadlineMillis);
                if (i != 2)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                break;
            default:
                ex = new SocketException("Reply from SOCKS server contains wrong code");
                break;
            }
            break;
        case GENERAL_FAILURE:
            ex = new SocketException("SOCKS server general failure");
            break;
        case NOT_ALLOWED:
            ex = new SocketException("SOCKS: Connection not allowed by ruleset");
            break;
        case NET_UNREACHABLE:
            ex = new SocketException("SOCKS: Network unreachable");
            break;
        case HOST_UNREACHABLE:
            ex = new SocketException("SOCKS: Host unreachable");
            break;
        case CONN_REFUSED:
            ex = new SocketException("SOCKS: Connection refused");
            break;
        case TTL_EXPIRED:
            ex =  new SocketException("SOCKS: TTL expired");
            break;
        case CMD_NOT_SUPPORTED:
            ex = new SocketException("SOCKS: Command not supported");
            break;
        case ADDR_TYPE_NOT_SUP:
            ex = new SocketException("SOCKS: address type not supported");
            break;
        }
        if (ex != null) {
            in.close();
            out.close();
            throw ex;
        }

        // 保存目标服务器
        external_address = epoint;
    }

    private void bindV4(InputStream in, OutputStream out,
                        InetAddress baddr,
                        int lport) throws IOException {
        if (!(baddr instanceof Inet4Address)) {
            throw new SocketException("SOCKS V4 requires IPv4 only addresses");
        }
        super.bind(baddr, lport);
        byte[] addr1 = baddr.getAddress();
        /* Test for AnyLocal */
        InetAddress naddr = baddr;
        if (naddr.isAnyLocalAddress()) {
            naddr = AccessController.doPrivileged(
                        new PrivilegedAction<InetAddress>() {
                            public InetAddress run() {
                                return cmdsock.getLocalAddress();

                            }
                        });
            addr1 = naddr.getAddress();
        }
        out.write(PROTO_VERS4);
        out.write(BIND);
        out.write((super.getLocalPort() >> 8) & 0xff);
        out.write((super.getLocalPort() >> 0) & 0xff);
        out.write(addr1);
        String userName = getUserName();
        try {
            out.write(userName.getBytes("ISO-8859-1"));
        } catch (java.io.UnsupportedEncodingException uee) {
            assert false;
        }
        out.write(0);
        out.flush();
        byte[] data = new byte[8];
        int n = readSocksReply(in, data);
        if (n != 8)
            throw new SocketException("Reply from SOCKS server has bad length: " + n);
        if (data[0] != 0 && data[0] != 4)
            throw new SocketException("Reply from SOCKS server has bad version");
        SocketException ex = null;
        switch (data[1]) {
        case 90:
            // Success!
            external_address = new InetSocketAddress(baddr, lport);
            break;
        case 91:
            ex = new SocketException("SOCKS request rejected");
            break;
        case 92:
            ex = new SocketException("SOCKS server couldn't reach destination");
            break;
        case 93:
            ex = new SocketException("SOCKS authentication failed");
            break;
        default:
            ex = new SocketException("Reply from SOCKS server contains bad status");
            break;
        }
        if (ex != null) {
            in.close();
            out.close();
            throw ex;
        }

    }

    /**
     * Sends the Bind request to the SOCKS proxy. In the SOCKS protocol, bind
     * means "accept incoming connection from", so the SocketAddress is the
     * the one of the host we do accept connection from.
     *
     * @param      saddr   the Socket address of the remote host.
     * @exception  IOException  if an I/O error occurs when binding this socket.
     */
    protected synchronized void socksBind(InetSocketAddress saddr) throws IOException {
        if (socket != null) {
            // this is a client socket, not a server socket, don't
            // call the SOCKS proxy for a bind!
            return;
        }

        // Connects to the SOCKS server

        if (server == null) {
            // This is the general case
            // server is not null only when the socket was created with a
            // specified proxy in which case it does bypass the ProxySelector
            ProxySelector sel = java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<ProxySelector>() {
                    public ProxySelector run() {
                            return ProxySelector.getDefault();
                        }
                    });
            if (sel == null) {
                /*
                 * No default proxySelector --> direct connection
                 */
                return;
            }
            URI uri;
            // Use getHostString() to avoid reverse lookups
            String host = saddr.getHostString();
            // IPv6 litteral?
            if (saddr.getAddress() instanceof Inet6Address &&
                (!host.startsWith("[")) && (host.indexOf(":") >= 0)) {
                host = "[" + host + "]";
            }
            try {
                uri = new URI("serversocket://" + ParseUtil.encodePath(host) + ":"+ saddr.getPort());
            } catch (URISyntaxException e) {
                // This shouldn't happen
                assert false : e;
                uri = null;
            }
            Proxy p = null;
            Exception savedExc = null;
            java.util.Iterator<Proxy> iProxy = null;
            iProxy = sel.select(uri).iterator();
            if (iProxy == null || !(iProxy.hasNext())) {
                return;
            }
            while (iProxy.hasNext()) {
                p = iProxy.next();
                if (p == null || p == Proxy.NO_PROXY) {
                    return;
                }
                if (p.type() != Proxy.Type.SOCKS)
                    throw new SocketException("Unknown proxy type : " + p.type());
                if (!(p.address() instanceof InetSocketAddress))
                    throw new SocketException("Unknow address type for proxy: " + p);
                // Use getHostString() to avoid reverse lookups
                server = ((InetSocketAddress) p.address()).getHostString();
                serverPort = ((InetSocketAddress) p.address()).getPort();
                if (p instanceof SocksProxy) {
                    if (((SocksProxy)p).protocolVersion() == 4) {
                        useV4 = true;
                    }
                }

                // Connects to the SOCKS server
                try {
                    AccessController.doPrivileged(
                        new PrivilegedExceptionAction<Void>() {
                            public Void run() throws Exception {
                                cmdsock = new Socket(new PlainSocketImpl());
                                cmdsock.connect(new InetSocketAddress(server, serverPort));
                                cmdIn = cmdsock.getInputStream();
                                cmdOut = cmdsock.getOutputStream();
                                return null;
                            }
                        });
                } catch (Exception e) {
                    // Ooops, let's notify the ProxySelector
                    sel.connectFailed(uri,p.address(),new SocketException(e.getMessage()));
                    server = null;
                    serverPort = -1;
                    cmdsock = null;
                    savedExc = e;
                    // Will continue the while loop and try the next proxy
                }
            }

            /*
             * If server is still null at this point, none of the proxy
             * worked
             */
            if (server == null || cmdsock == null) {
                throw new SocketException("Can't connect to SOCKS proxy:"
                                          + savedExc.getMessage());
            }
        } else {
            try {
                AccessController.doPrivileged(
                    new PrivilegedExceptionAction<Void>() {
                        public Void run() throws Exception {
                            cmdsock = new Socket(new PlainSocketImpl());
                            cmdsock.connect(new InetSocketAddress(server, serverPort));
                            cmdIn = cmdsock.getInputStream();
                            cmdOut = cmdsock.getOutputStream();
                            return null;
                        }
                    });
            } catch (Exception e) {
                throw new SocketException(e.getMessage());
            }
        }
        BufferedOutputStream out = new BufferedOutputStream(cmdOut, 512);
        InputStream in = cmdIn;
        if (useV4) {
            bindV4(in, out, saddr.getAddress(), saddr.getPort());
            return;
        }
        out.write(PROTO_VERS);
        out.write(2);
        out.write(NO_AUTH);
        out.write(USER_PASSW);
        out.flush();
        byte[] data = new byte[2];
        int i = readSocksReply(in, data);
        if (i != 2 || ((int)data[0]) != PROTO_VERS) {
            // Maybe it's not a V5 sever after all
            // Let's try V4 before we give up
            bindV4(in, out, saddr.getAddress(), saddr.getPort());
            return;
        }
        if (((int)data[1]) == NO_METHODS)
            throw new SocketException("SOCKS : No acceptable methods");
        if (!authenticate(data[1], in, out)) {
            throw new SocketException("SOCKS : authentication failed");
        }
        // We're OK. Let's issue the BIND command.
        out.write(PROTO_VERS);
        out.write(BIND);
        out.write(0);
        int lport = saddr.getPort();
        if (saddr.isUnresolved()) {
            out.write(DOMAIN_NAME);
            out.write(saddr.getHostName().length());
            try {
                out.write(saddr.getHostName().getBytes("ISO-8859-1"));
            } catch (java.io.UnsupportedEncodingException uee) {
                assert false;
            }
            out.write((lport >> 8) & 0xff);
            out.write((lport >> 0) & 0xff);
        } else if (saddr.getAddress() instanceof Inet4Address) {
            byte[] addr1 = saddr.getAddress().getAddress();
            out.write(IPV4);
            out.write(addr1);
            out.write((lport >> 8) & 0xff);
            out.write((lport >> 0) & 0xff);
            out.flush();
        } else if (saddr.getAddress() instanceof Inet6Address) {
            byte[] addr1 = saddr.getAddress().getAddress();
            out.write(IPV6);
            out.write(addr1);
            out.write((lport >> 8) & 0xff);
            out.write((lport >> 0) & 0xff);
            out.flush();
        } else {
            cmdsock.close();
            throw new SocketException("unsupported address type : " + saddr);
        }
        data = new byte[4];
        i = readSocksReply(in, data);
        SocketException ex = null;
        int len, nport;
        byte[] addr;
        switch (data[1]) {
        case REQUEST_OK:
            // success!
            switch(data[3]) {
            case IPV4:
                addr = new byte[4];
                i = readSocksReply(in, addr);
                if (i != 4)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                data = new byte[2];
                i = readSocksReply(in, data);
                if (i != 2)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                nport = ((int)data[0] & 0xff) << 8;
                nport += ((int)data[1] & 0xff);
                external_address =
                    new InetSocketAddress(new Inet4Address("", addr) , nport);
                break;
            case DOMAIN_NAME:
                len = data[1];
                byte[] host = new byte[len];
                i = readSocksReply(in, host);
                if (i != len)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                data = new byte[2];
                i = readSocksReply(in, data);
                if (i != 2)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                nport = ((int)data[0] & 0xff) << 8;
                nport += ((int)data[1] & 0xff);
                external_address = new InetSocketAddress(new String(host), nport);
                break;
            case IPV6:
                len = data[1];
                addr = new byte[len];
                i = readSocksReply(in, addr);
                if (i != len)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                data = new byte[2];
                i = readSocksReply(in, data);
                if (i != 2)
                    throw new SocketException("Reply from SOCKS server badly formatted");
                nport = ((int)data[0] & 0xff) << 8;
                nport += ((int)data[1] & 0xff);
                external_address =
                    new InetSocketAddress(new Inet6Address("", addr), nport);
                break;
            }
            break;
        case GENERAL_FAILURE:
            ex = new SocketException("SOCKS server general failure");
            break;
        case NOT_ALLOWED:
            ex = new SocketException("SOCKS: Bind not allowed by ruleset");
            break;
        case NET_UNREACHABLE:
            ex = new SocketException("SOCKS: Network unreachable");
            break;
        case HOST_UNREACHABLE:
            ex = new SocketException("SOCKS: Host unreachable");
            break;
        case CONN_REFUSED:
            ex = new SocketException("SOCKS: Connection refused");
            break;
        case TTL_EXPIRED:
            ex =  new SocketException("SOCKS: TTL expired");
            break;
        case CMD_NOT_SUPPORTED:
            ex = new SocketException("SOCKS: Command not supported");
            break;
        case ADDR_TYPE_NOT_SUP:
            ex = new SocketException("SOCKS: address type not supported");
            break;
        }
        if (ex != null) {
            in.close();
            out.close();
            cmdsock.close();
            cmdsock = null;
            throw ex;
        }
        cmdIn = in;
        cmdOut = out;
    }

    /**
     * Accepts a connection from a specific host.
     *
     * @param      s   the accepted connection.
     * @param      saddr the socket address of the host we do accept
     *               connection from
     * @exception  IOException  if an I/O error occurs when accepting the
     *               connection.
     */
    protected void acceptFrom(SocketImpl s, InetSocketAddress saddr) throws IOException {
        if (cmdsock == null) {
            // Not a Socks ServerSocket.
            return;
        }
        InputStream in = cmdIn;
        // Sends the "SOCKS BIND" request.
        socksBind(saddr);
        in.read();
        int i = in.read();
        in.read();
        SocketException ex = null;
        int nport;
        byte[] addr;
        InetSocketAddress real_end = null;
        switch (i) {
        case REQUEST_OK:
            // success!
            i = in.read();
            switch(i) {
            case IPV4:
                addr = new byte[4];
                readSocksReply(in, addr);
                nport = in.read() << 8;
                nport += in.read();
                real_end =
                    new InetSocketAddress(new Inet4Address("", addr) , nport);
                break;
            case DOMAIN_NAME:
                int len = in.read();
                addr = new byte[len];
                readSocksReply(in, addr);
                nport = in.read() << 8;
                nport += in.read();
                real_end = new InetSocketAddress(new String(addr), nport);
                break;
            case IPV6:
                addr = new byte[16];
                readSocksReply(in, addr);
                nport = in.read() << 8;
                nport += in.read();
                real_end =
                    new InetSocketAddress(new Inet6Address("", addr), nport);
                break;
            }
            break;
        case GENERAL_FAILURE:
            ex = new SocketException("SOCKS server general failure");
            break;
        case NOT_ALLOWED:
            ex = new SocketException("SOCKS: Accept not allowed by ruleset");
            break;
        case NET_UNREACHABLE:
            ex = new SocketException("SOCKS: Network unreachable");
            break;
        case HOST_UNREACHABLE:
            ex = new SocketException("SOCKS: Host unreachable");
            break;
        case CONN_REFUSED:
            ex = new SocketException("SOCKS: Connection refused");
            break;
        case TTL_EXPIRED:
            ex =  new SocketException("SOCKS: TTL expired");
            break;
        case CMD_NOT_SUPPORTED:
            ex = new SocketException("SOCKS: Command not supported");
            break;
        case ADDR_TYPE_NOT_SUP:
            ex = new SocketException("SOCKS: address type not supported");
            break;
        }
        if (ex != null) {
            cmdIn.close();
            cmdOut.close();
            cmdsock.close();
            cmdsock = null;
            throw ex;
        }

        /**
         * This is where we have to do some fancy stuff.
         * The datastream from the socket "accepted" by the proxy will
         * come through the cmdSocket. So we have to swap the socketImpls
         */
        if (s instanceof SocksSocketImpl) {
            ((SocksSocketImpl)s).external_address = real_end;
        }
        if (s instanceof PlainSocketImpl) {
            PlainSocketImpl psi = (PlainSocketImpl) s;
            psi.setInputStream((SocketInputStream) in);
            psi.setFileDescriptor(cmdsock.getImpl().getFileDescriptor());
            psi.setAddress(cmdsock.getImpl().getInetAddress());
            psi.setPort(cmdsock.getImpl().getPort());
            psi.setLocalPort(cmdsock.getImpl().getLocalPort());
        } else {
            s.fd = cmdsock.getImpl().fd;
            s.address = cmdsock.getImpl().address;
            s.port = cmdsock.getImpl().port;
            s.localport = cmdsock.getImpl().localport;
        }

        // Need to do that so that the socket won't be closed
        // when the ServerSocket is closed by the user.
        // It kinds of detaches the Socket because it is now
        // used elsewhere.
        cmdsock = null;
    }


    /**
     * Returns the value of this socket's {@code address} field.
     *
     * @return  the value of this socket's {@code address} field.
     * @see     java.net.SocketImpl#address
     */
    @Override
    protected InetAddress getInetAddress() {
        if (external_address != null)
            return external_address.getAddress();
        else
            return super.getInetAddress();
    }

    /**
     * Returns the value of this socket's {@code port} field.
     *
     * @return  the value of this socket's {@code port} field.
     * @see     java.net.SocketImpl#port
     */
    @Override
    protected int getPort() {
        if (external_address != null)
            return external_address.getPort();
        else
            return super.getPort();
    }

    @Override
    protected int getLocalPort() {
        if (socket != null)
            return super.getLocalPort();
        if (external_address != null)
            return external_address.getPort();
        else
            return super.getLocalPort();
    }

    @Override
    protected void close() throws IOException {
        if (cmdsock != null)
            cmdsock.close();
        cmdsock = null;
        super.close();
    }

    private String getUserName() {
        String userName = "";
        if (applicationSetProxy) {
            try {
                userName = System.getProperty("user.name");
            } catch (SecurityException se) { /* swallow Exception */ }
        } else {
            userName = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction("user.name"));
        }
        return userName;
    }
}
