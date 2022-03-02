/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileDescriptor;

import sun.net.ConnectionResetException;
import sun.net.NetHooks;
import sun.net.ResourceManager;

/**
 * Default Socket Implementation. This implementation does
 * not implement any security checks.
 * Note this class should <b>NOT</b> be public.
 *
 * @author  Steven B. Byrne
 */
abstract class AbstractPlainSocketImpl extends SocketImpl
{
    /* instance variable for SO_TIMEOUT */
    // 超时必须大于零；若超时不大于零，则为无限超时
    int timeout;   // timeout in millisec
    // traffic class
    private int trafficClass;

    private boolean shut_rd = false;
    private boolean shut_wr = false;

    private SocketInputStream socketInputStream = null;
    private SocketOutputStream socketOutputStream = null;

    /* number of threads using the FileDescriptor */
    // 是否有正在使用的文件描述符。对于服务端套接字来说，代表有并发线程正在接受新连接
    protected int fdUseCount = 0;

    /* lock when increment/decrementing fdUseCount */
    protected final Object fdLock = new Object();

    /* indicates a close is pending on the file descriptor */
    // 待关闭标志位。调用当前套接字的关闭方法时设置
    protected boolean closePending = false;

    /* indicates connection reset state */
    private int CONNECTION_NOT_RESET = 0;
    private int CONNECTION_RESET_PENDING = 1;
    private int CONNECTION_RESET = 2;
    private int resetState;
    private final Object resetLock = new Object();

   /* whether this Socket is a stream (TCP) socket or not (UDP)
    */
    protected boolean stream;

    /**
     * Load net library into runtime.
     */
    static {
        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
                public Void run() {
                    System.loadLibrary("net");
                    return null;
                }
            });
    }

    /**
     * Creates a socket with a boolean that specifies whether this
     * is a stream socket (true) or an unconnected UDP socket (false).
     */
    // 在|OS|层创建一个网络套接字，并将描述符设置到|fd.fd|字段上。参数|stream|指示它是一个
    // 流、还是一个报文套接字
    // 注：创建成功，设置（服务端、客户端）套接字已创建标志位
    // 注：若是一个服务端套接字（|serverSocket|不为空），则将其设置为|O_NONBLOCK|、|SO_REUSEADDR|
    protected synchronized void create(boolean stream) throws IOException {
        this.stream = stream;
        if (!stream) {
            ResourceManager.beforeUdpCreate();
            // only create the fd after we know we will be able to create the socket
            fd = new FileDescriptor();
            try {
                socketCreate(false);
            } catch (IOException ioe) {
                ResourceManager.afterUdpClose();
                fd = null;
                throw ioe;
            }
        } else {
            // 在|JVM|层创建文件描述符对象
            fd = new FileDescriptor();

            // 在|OS|层创建一个网络套接字，并将描述符设置到|fd.fd|字段上
            // 注：若是一个服务端套接字，则将其设置为|O_NONBLOCK|、|SO_REUSEADDR|
            socketCreate(true);
        }

        // 设置该套接字已创建标志位。即，与|OS|层的网络套接字的描述符已完成了关联动作
        if (socket != null)
            socket.setCreated();    // 客户端套接字
        if (serverSocket != null)
            serverSocket.setCreated();  // 服务端套接字
    }

    /**
     * Creates a socket and connects it to the specified port on
     * the specified host.
     * @param host the specified host
     * @param port the specified port
     */
    // 在|OS|层将套接字连接到指定的远程域名、端口号上。该方法会一直阻塞，直到连接被建立、超时或发生异常
    // 注：当|timeout==0|时，连接无超时限制；当|timeout>0|时，则在超时前仍未建立连接时，抛出异常
    // 注：当连接成功，设置客户端套接字的已连接、已绑定标志位
    // 注：当连接成功，服务端的地址与端口分别被设置到|adress|和|port|字段上；而客户端的描述符将被设
    // 置到|fd.fd|字段上，客户端实际绑定端口被设置到|localPort|字段上
    protected void connect(String host, int port)
        throws UnknownHostException, IOException
    {
        boolean connected = false;
        try {
            InetAddress address = InetAddress.getByName(host);
            this.port = port;
            this.address = address;

            connectToAddress(address, port, timeout);
            connected = true;
        } finally {
            if (!connected) {
                try {
                    close();
                } catch (IOException ioe) {
                    /* Do nothing. If connect threw an exception then
                       it will be passed up the call stack */
                }
            }
        }
    }

    /**
     * Creates a socket and connects it to the specified address on
     * the specified port.
     * @param address the address
     * @param port the specified port
     */
    // 在|OS|层将套接字连接到指定的网络地址、端口号上。该方法会一直阻塞，直到连接被建立、超时或发生异常
    // 注：当|timeout==0|时，连接无超时限制；当|timeout>0|时，则在超时前仍未建立连接时，抛出异常
    // 注：当连接成功，设置客户端套接字的已连接、已绑定标志位
    // 注：当连接成功，服务端的地址与端口分别被设置到|adress|和|port|字段上；而客户端的描述符将被设
    // 置到|fd.fd|字段上，客户端实际绑定端口被设置到|localPort|字段上
    protected void connect(InetAddress address, int port) throws IOException {
        this.port = port;
        this.address = address;

        try {
            connectToAddress(address, port, timeout);
            return;
        } catch (IOException e) {
            // everything failed
            close();
            throw e;
        }
    }

    /**
     * Creates a socket and connects it to the specified address on
     * the specified port.
     * @param address the address
     * @param timeout the timeout value in milliseconds, or zero for no timeout.
     * @throws IOException if connection fails
     * @throws  IllegalArgumentException if address is null or is a
     *          SocketAddress subclass not supported by this socket
     * @since 1.4
     */
    // 在|OS|层将套接字连接到指定的服务端的套接字地址上。该方法会一直阻塞，直到连接被建立、超时或发生异常
    // 注：当|timeout==0|时，连接无超时限制；当|timeout>0|时，则在超时前仍未建立连接时，抛出异常
    protected void connect(SocketAddress address, int timeout)
            throws IOException {
        boolean connected = false;
        try {
            if (address == null || !(address instanceof InetSocketAddress))
                throw new IllegalArgumentException("unsupported address type");

            // 该网络套接字地址（网络地址+端口号）必须已经解析
            InetSocketAddress addr = (InetSocketAddress) address;
            if (addr.isUnresolved())
                throw new UnknownHostException(addr.getHostName());
            this.port = addr.getPort();
            this.address = addr.getAddress();

            connectToAddress(this.address, port, timeout);
            connected = true;
        } finally {
            if (!connected) {
                try {
                    close();
                } catch (IOException ioe) {
                    /* Do nothing. If connect threw an exception then
                       it will be passed up the call stack */
                }
            }
        }
    }

    // 在|OS|层将套接字连接到指定的网络地址、端口号上。该方法会一直阻塞，直到连接被建立、或发现异常
    // 注：当|timeout==0|时，连接无超时限制；当|timeout>0|时，则在超时前仍未建立连接时，抛出异常
    // 注：当连接成功，设置客户端套接字的已连接、已绑定标志位
    // 注：当连接成功，服务端的地址与端口分别被设置到|adress|和|port|字段上；而客户端的描述符将被设
    // 置到|fd.fd|字段上，客户端实际绑定端口被设置到|localPort|字段上
    private void connectToAddress(InetAddress address, int port, int timeout) throws IOException {
        if (address.isAnyLocalAddress()) {
            // 当连接的地址是本地任意地址时，将其转换成本机实际的网络地址
            doConnect(InetAddress.getLocalHost(), port, timeout);
        } else {
            doConnect(address, port, timeout);
        }
    }

    public void setOption(int opt, Object val) throws SocketException {
        if (isClosedOrPending()) {
            throw new SocketException("Socket Closed");
        }
        boolean on = true;
        switch (opt) {
            /* check type safety b4 going native.  These should never
             * fail, since only java.Socket* has access to
             * PlainSocketImpl.setOption().
             */
        case SO_LINGER:
            if (val == null || (!(val instanceof Integer) && !(val instanceof Boolean)))
                throw new SocketException("Bad parameter for option");
            if (val instanceof Boolean) {
                /* true only if disabling - enabling should be Integer */
                on = false;
            }
            break;
        case SO_TIMEOUT:
            if (val == null || (!(val instanceof Integer)))
                throw new SocketException("Bad parameter for SO_TIMEOUT");
            int tmp = ((Integer) val).intValue();
            if (tmp < 0)
                throw new IllegalArgumentException("timeout < 0");
            timeout = tmp;
            break;
        case IP_TOS:
             if (val == null || !(val instanceof Integer)) {
                 throw new SocketException("bad argument for IP_TOS");
             }
             trafficClass = ((Integer)val).intValue();
             break;
        case SO_BINDADDR:
            throw new SocketException("Cannot re-bind socket");
        case TCP_NODELAY:
            if (val == null || !(val instanceof Boolean))
                throw new SocketException("bad parameter for TCP_NODELAY");
            on = ((Boolean)val).booleanValue();
            break;
        case SO_SNDBUF:
        case SO_RCVBUF:
            if (val == null || !(val instanceof Integer) ||
                !(((Integer)val).intValue() > 0)) {
                throw new SocketException("bad parameter for SO_SNDBUF " +
                                          "or SO_RCVBUF");
            }
            break;
        case SO_KEEPALIVE:
            if (val == null || !(val instanceof Boolean))
                throw new SocketException("bad parameter for SO_KEEPALIVE");
            on = ((Boolean)val).booleanValue();
            break;
        case SO_OOBINLINE:
            if (val == null || !(val instanceof Boolean))
                throw new SocketException("bad parameter for SO_OOBINLINE");
            on = ((Boolean)val).booleanValue();
            break;
        case SO_REUSEADDR:
            if (val == null || !(val instanceof Boolean))
                throw new SocketException("bad parameter for SO_REUSEADDR");
            on = ((Boolean)val).booleanValue();
            break;
        default:
            throw new SocketException("unrecognized TCP option: " + opt);
        }
        socketSetOption(opt, on, val);
    }
    public Object getOption(int opt) throws SocketException {
        if (isClosedOrPending()) {
            throw new SocketException("Socket Closed");
        }
        // 获取的选项为|SO_TIMEOUT|时，直接返回字段
        if (opt == SO_TIMEOUT) {
            return new Integer(timeout);
        }
        int ret = 0;
        /*
         * The native socketGetOption() knows about 3 options.
         * The 32 bit value it returns will be interpreted according
         * to what we're asking.  A return of -1 means it understands
         * the option but its turned off.  It will raise a SocketException
         * if "opt" isn't one it understands.
         */

        switch (opt) {
        case TCP_NODELAY:
            ret = socketGetOption(opt, null);
            return Boolean.valueOf(ret != -1);
        case SO_OOBINLINE:
            ret = socketGetOption(opt, null);
            return Boolean.valueOf(ret != -1);
        case SO_LINGER:
            ret = socketGetOption(opt, null);
            return (ret == -1) ? Boolean.FALSE: (Object)(new Integer(ret));
        case SO_REUSEADDR:
            ret = socketGetOption(opt, null);
            return Boolean.valueOf(ret != -1);
        case SO_BINDADDR:
            // 底层使用|getsockname(fd, (struct sockaddr *)&him, &len)|来获取本地地址
            InetAddressContainer in = new InetAddressContainer();
            ret = socketGetOption(opt, in);
            return in.addr;
        case SO_SNDBUF:
        case SO_RCVBUF:
            ret = socketGetOption(opt, null);
            return new Integer(ret);
        case IP_TOS:
            ret = socketGetOption(opt, null);
            if (ret == -1) { // ipv6 tos
                return new Integer(trafficClass);
            } else {
                return new Integer(ret);
            }
        case SO_KEEPALIVE:
            ret = socketGetOption(opt, null);
            return Boolean.valueOf(ret != -1);
        // should never get here
        default:
            return null;
        }
    }

    /**
     * The workhorse of the connection operation.  Tries several times to
     * establish a connection to the given <host, port>.  If unsuccessful,
     * throws an IOException indicating what went wrong.
     */

    // 在|OS|层将套接字连接到指定的网络地址、端口号上。该方法会一直阻塞，直到连接被建立、或发现异常
    // 注：当|timeout==0|时，连接无超时限制；当|timeout>0|时，则在超时前仍未建立连接时，抛出异常
    // 注：该方法是一个同步方法，即，同一个客户端对象，连接服务器操作是串行的
    // 注：当连接成功，设置客户端套接字的已连接、已绑定标志位
    // 注：当连接成功，服务端的地址与端口分别被设置到|adress|和|port|字段上；而客户端的描述符将被设
    // 置到|fd.fd|字段上，客户端实际绑定端口被设置到|localPort|字段上
    synchronized void doConnect(InetAddress address, int port, int timeout) throws IOException {
        synchronized (fdLock) {
            if (!closePending && (socket == null || !socket.isBound())) {
                NetHooks.beforeTcpConnect(fd, address, port);
            }
        }
        try {
            // 自增"正在使用文件描述符"计数器。即，当前线程正在连接到服务器，客户端套接字需要一个|FD|
            acquireFD();
            try {
                // 在|OS|层将套接字连接到指定的服务端的套接字地址上。该方法会一直阻塞，直到连接被建立、或发现异常
                socketConnect(address, port, timeout);

                /* socket may have been closed during poll/select */
                synchronized (fdLock) {
                    if (closePending) {
                        throw new SocketException ("Socket closed");
                    }
                }
                // If we have a ref. to the Socket, then sets the flags
                // created, bound & connected to true.
                // This is normally done in Socket.connect() but some
                // subclasses of Socket may call impl.connect() directly!
                // 连接成功，设置客户端套接字的已连接、已绑定标志位
                if (socket != null) {
                    socket.setBound();
                    socket.setConnected();
                }
            } finally {
                // 递减"正在使用文件描述符"计数器。即，当前线程已经完成了连接动作
                releaseFD();
            }
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    /**
     * Binds the socket to the specified address of the specified local port.
     * @param address the address
     * @param lport the port
     */
    // 在|OS|层将套接字与网络地址、端口号进行绑定。并将地址设置到|address|、以及将实际端口号设
    // 置到|localPort|字段上
    // 注：绑定成功，设置（服务端、客户端）套接字已绑定标志位
    protected synchronized void bind(InetAddress address, int lport)
        throws IOException
    {
       synchronized (fdLock) {
            if (!closePending && (socket == null || !socket.isBound())) {
                NetHooks.beforeTcpBind(fd, address, lport);
            }
        }
        // 在|OS|层将套接字与网络地址、端口号进行绑定。并将地址设置到|address|、以及将实际端口
        // 号设置到|localPort|字段上
        socketBind(address, lport);

        // 设置套接字已绑定标志位。即，关联|OS|层的网络套接字的描述符已完成了绑定动作
        if (socket != null)
            socket.setBound();
        if (serverSocket != null)
            serverSocket.setBound();
    }

    /**
     * Listens, for a specified amount of time, for connections.
     * @param count the amount of time to listen for connections
     */
    // 在|OS|层将套接字设置为监听状态套接字，其最大挂起连接数为|backlog|
    protected synchronized void listen(int count) throws IOException {
        socketListen(count);
    }

    /**
     * Accepts connections.
     * @param s the connection
     */
    // 监听并接受一个客户端连接。该方法会一直阻塞，直到一个连接被创建并设置到|s|参数中、或发生异常
    protected void accept(SocketImpl s) throws IOException {
        // 自增"正在使用文件描述符"计数器。即，当前线程正在接受一个新连接，新连接套接字需要一个|FD|
        acquireFD();
        try {
            // 在|OS|层监听并接受一个客户端连接，该方法会一直阻塞，直到一个连接被创建并设置到|s|参数中
            socketAccept(s);
        } finally {
            // 递减"正在使用文件描述符"计数器。即，当前线程已经完成了接受一个新连接的动作
            releaseFD();
        }
    }

    /**
     * Gets an InputStream for this socket.
     */
    // 获取该套接字的输入流
    protected synchronized InputStream getInputStream() throws IOException {
        synchronized (fdLock) {
            // 该套接字不能已关闭或正在关闭
            if (isClosedOrPending())
                throw new IOException("Socket Closed");
            // 该套接字不能关闭读入通道
            if (shut_rd)
                throw new IOException("Socket input is shutdown");
            if (socketInputStream == null)
                socketInputStream = new SocketInputStream(this);
        }
        return socketInputStream;
    }

    void setInputStream(SocketInputStream in) {
        socketInputStream = in;
    }

    /**
     * Gets an OutputStream for this socket.
     */
    // 获取该套接字的输出流
    protected synchronized OutputStream getOutputStream() throws IOException {
        synchronized (fdLock) {
            // 该套接字不能已关闭或正在关闭
            if (isClosedOrPending())
                throw new IOException("Socket Closed");
            // 该套接字不能关闭写出通道
            if (shut_wr)
                throw new IOException("Socket output is shutdown");
            if (socketOutputStream == null)
                socketOutputStream = new SocketOutputStream(this);
        }
        return socketOutputStream;
    }

    void setFileDescriptor(FileDescriptor fd) {
        this.fd = fd;
    }

    void setAddress(InetAddress address) {
        this.address = address;
    }

    void setPort(int port) {
        this.port = port;
    }

    void setLocalPort(int localport) {
        this.localport = localport;
    }

    /**
     * Returns the number of bytes that can be read without blocking.
     */
    // 返回可以从此输入流读取或跳过的剩余字节数的估计值，而不会使下一次读取或跳过这么多字节时被阻塞
    protected synchronized int available() throws IOException {
        if (isClosedOrPending()) {
            throw new IOException("Stream closed.");
        }

        /*
         * If connection has been reset or shut down for input, then return 0
         * to indicate there are no buffered bytes.
         */
        if (isConnectionReset() || shut_rd) {
            return 0;
        }

        /*
         * If no bytes available and we were previously notified
         * of a connection reset then we move to the reset state.
         *
         * If are notified of a connection reset then check
         * again if there are bytes buffered on the socket.
         */
        int n = 0;
        try {
            n = socketAvailable();
            if (n == 0 && isConnectionResetPending()) {
                setConnectionReset();
            }
        } catch (ConnectionResetException exc1) {
            setConnectionResetPending();
            try {
                n = socketAvailable();
                if (n == 0) {
                    setConnectionReset();
                }
            } catch (ConnectionResetException exc2) {
            }
        }
        return n;
    }

    /**
     * Closes the socket.
     */
    // 关闭此套接字。若当前有线程阻塞在|accept()|的调用上，会立即抛出|SocketException|异常
    // 注：如果此套接字具有关联的通道，则该通道将会被关闭；如果有正在使用的文件描述符，则只能先执行预关闭，
    // 再设置关闭标志位。这表示，对于服务端，有并发线程正在接受连接、对于客户端，有并发线程正在执行连接
    protected void close() throws IOException {
        synchronized(fdLock) {
            if (fd != null) {
                if (!stream) {
                    ResourceManager.afterUdpClose();
                }

                // 是否有正在使用的文件描述符，若存在，则只能先执行预关闭，再设置关闭标志位
                // 注：对于服务端，代表有并发线程正在接受连接；对于客户端，代表有并发线程正在执行连接
                if (fdUseCount == 0) {
                    if (closePending) {
                        return;
                    }
                    closePending = true;
                    /*
                     * We close the FileDescriptor in two-steps - first the
                     * "pre-close" which closes the socket but doesn't
                     * release the underlying file descriptor. This operation
                     * may be lengthy due to untransmitted data and a long
                     * linger interval. Once the pre-close is done we do the
                     * actual socket to release the fd.
                     */
                    // 分步关闭套接字文件描述符
                    // 1.首先是预关闭套接字（不释放|OS|层文件描述符）。由于未传输的数据
                    // 以及较长的传输延迟间隔，此操作可能会很长
                    // 2.预关闭完成后，再执行实际的套接字的关闭来释放|OS|层文件描述符
                    try {
                        socketPreClose();
                    } finally {
                        socketClose();
                    }
                    fd = null;
                    return;
                } else {
                    /*
                     * If a thread has acquired the fd and a close
                     * isn't pending then use a deferred close.
                     * Also decrement fdUseCount to signal the last
                     * thread that releases the fd to close it.
                     */
                    if (!closePending) {
                        // 设置待关闭标志位
                        closePending = true;
                        fdUseCount--;
                        socketPreClose();   // 预关闭该套接字
                    }
                }
            }
        }
    }

    void reset() throws IOException {
        if (fd != null) {
            socketClose();
        }
        fd = null;
        super.reset();
    }


    /**
     * Shutdown read-half of the socket connection;
     */
    protected void shutdownInput() throws IOException {
      if (fd != null) {
          socketShutdown(SHUT_RD);
          if (socketInputStream != null) {
              socketInputStream.setEOF(true);
          }
          shut_rd = true;
      }
    }

    /**
     * Shutdown write-half of the socket connection;
     */
    protected void shutdownOutput() throws IOException {
      if (fd != null) {
          socketShutdown(SHUT_WR);
          shut_wr = true;
      }
    }

    protected boolean supportsUrgentData () {
        return true;
    }

    protected void sendUrgentData (int data) throws IOException {
        if (fd == null) {
            throw new IOException("Socket Closed");
        }
        socketSendUrgentData (data);
    }

    /**
     * Cleans up if the user forgets to close it.
     */
    protected void finalize() throws IOException {
        close();
    }

    /*
     * "Acquires" and returns the FileDescriptor for this impl
     *
     * A corresponding releaseFD is required to "release" the
     * FileDescriptor.
     */
    // 自增"正在使用文件描述符"计数器
    // 注：若当前套接字有正在使用的文件描述符，则关闭该套接字时，只能先执行预关闭，再设置关闭标志
    // 位。对于服务端，代表有并发线程正在接受连接；对于客户端，代表有并发线程正在执行连接、读取、写入
    FileDescriptor acquireFD() {
        synchronized (fdLock) {
            fdUseCount++;
            return fd;
        }
    }

    /*
     * "Release" the FileDescriptor for this impl.
     *
     * If the use count goes to -1 then the socket is closed.
     */
    // 递减"正在使用文件描述符"计数器
    // 注：若当前套接字有正在使用的文件描述符，则关闭该套接字时，只能先执行预关闭，再设置关闭标志
    // 位。对于服务端，代表有并发线程正在接受连接；对于客户端，代表有并发线程正在执行连接、读取、写入
    void releaseFD() {
        synchronized (fdLock) {
            fdUseCount--;

            // 若"正在使用文件描述符"已递减到|-1|，则立即关闭并释放|OS|层的套接字文件描述符
            if (fdUseCount == -1) {
                if (fd != null) {
                    try {
                        socketClose();
                    } catch (IOException e) {
                    } finally {
                        fd = null;
                    }
                }
            }
        }
    }

    public boolean isConnectionReset() {
        synchronized (resetLock) {
            return (resetState == CONNECTION_RESET);
        }
    }

    public boolean isConnectionResetPending() {
        synchronized (resetLock) {
            return (resetState == CONNECTION_RESET_PENDING);
        }
    }

    public void setConnectionReset() {
        synchronized (resetLock) {
            resetState = CONNECTION_RESET;
        }
    }

    public void setConnectionResetPending() {
        synchronized (resetLock) {
            if (resetState == CONNECTION_NOT_RESET) {
                resetState = CONNECTION_RESET_PENDING;
            }
        }

    }

    /*
     * Return true if already closed or close is pending
     */
    public boolean isClosedOrPending() {
        /*
         * Lock on fdLock to ensure that we wait if a
         * close is in progress.
         */
        synchronized (fdLock) {
            if (closePending || (fd == null)) {
                return true;
            } else {
                return false;
            }
        }
    }

    /*
     * Return the current value of SO_TIMEOUT
     */
    public int getTimeout() {
        return timeout;
    }

    /*
     * "Pre-close" a socket by dup'ing the file descriptor - this enables
     * the socket to be closed without releasing the file descriptor.
     */
    // 通过复制文件描述符实现“预关闭”一个套接字 - 这使得套接字可以在不释放|OS|文件描述符的情况下关闭
    // 注：不释放|OS|层文件描述符，主要为了在缓冲区中已发送的数据能顺利发送到对端。由于未传输的数据以
    // 及较长的传输延迟间隔，此操作可能会很长
    // 注：预关闭是指，当前的文件描述符句柄在|OS|层并未释放，但此时它已经被重新定位至|marker_fd|上
    private void socketPreClose() throws IOException {
        socketClose0(true);
    }

    /*
     * Close the socket (and release the file descriptor).
     */
    // 关闭并释放|OS|层的套接字文件描述符
    protected void socketClose() throws IOException {
        socketClose0(false);
    }

    // 在|OS|层创建一个网络套接字，并将描述符设置到|fd.fd|字段上。参数|isServer|指示它是一个
    // 流、还是一个报文套接字
    // 注：若是一个服务端套接字（|serverSocket|不为空），则将其设置为|O_NONBLOCK|、|SO_REUSEADDR|
    abstract void socketCreate(boolean isServer) throws IOException;

    // 在|OS|层将套接字连接到指定的服务端的套接字地址上。该方法会一直阻塞，直到连接被建立、或发现异常
    // 注：当|timeout==0|时，连接无超时限制；当|timeout>0|时，则在超时前仍未建立连接时，抛出异常
    // 注：当连接成功，服务端的地址与端口分别被设置到|adress|和|port|字段上；而客户端的描述符将被设置
    // 到|fd.fd|字段上，客户端实际绑定端口被设置到|localPort|字段上
    abstract void socketConnect(InetAddress address, int port, int timeout)
        throws IOException;

    // 在|OS|层将套接字与网络地址、端口号进行绑定。并将地址设置到|address|、以及将实际端口号设置
    // 到|localPort|字段上
    abstract void socketBind(InetAddress address, int port)
        throws IOException;

    // 在|OS|层将套接字设置为监听状态套接字，其最大挂起连接数为|backlog|
    // 注：此时套接字将被设置为|LISTEN|状态，准备接受客户端的连接。即，服务端套接字
    abstract void socketListen(int count)
        throws IOException;

    // 在|OS|层监听并接受一个客户端连接，该方法会一直阻塞，直到一个连接被创建并设置到|s|参数中、或发生异常
    // 注：若设置了|timeout|超时限制，在超时前仍未获得连接时，将抛出|SocketTimeoutException|异常
    abstract void socketAccept(SocketImpl s)
        throws IOException;

    // 返回可以从此输入流读取或跳过的剩余字节数的估计值，而不会使下一次读取或跳过这么多字节时被阻塞
    // 注：系统调用|ioctl(fd, FIONREAD, &n)|可以得到描述符|fd|的缓冲区里有多少字节可被读取
    abstract int socketAvailable()
        throws IOException;

    // 关闭一个文件描述符。若参数为|true|时，则是预关闭套接字（不释放|OS|层文件描述符）；若为|false|时，
    // 则是实际的套接字的关闭（释放|OS|层文件描述符）
    // 注：不释放|OS|层文件描述符，主要为了在缓冲区中已发送的数据能顺利发送到对端。由于未传输的数据以
    // 及较长的传输延迟间隔，此操作可能会很长
    abstract void socketClose0(boolean useDeferredClose)
        throws IOException;
    abstract void socketShutdown(int howto)
        throws IOException;
    abstract void socketSetOption(int cmd, boolean on, Object value)
        throws SocketException;
    abstract int socketGetOption(int opt, Object iaContainerObj) throws SocketException;
    abstract void socketSendUrgentData(int data)
        throws IOException;

    public final static int SHUT_RD = 0;
    public final static int SHUT_WR = 1;
}
