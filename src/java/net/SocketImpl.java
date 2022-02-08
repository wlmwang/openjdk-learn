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

/**
 * The abstract class {@code SocketImpl} is a common superclass
 * of all classes that actually implement sockets. It is used to
 * create both client and server sockets.
 * <p>
 * A "plain" socket implements these methods exactly as
 * described, without attempting to go through a firewall or proxy.
 *
 * @author  unascribed
 * @since   JDK1.0
 */
public abstract class SocketImpl implements SocketOptions {
    /**
     * The actual Socket object.
     */
    // 客户端套接字对象
    Socket socket = null;

    // 服务端套接字对象
    ServerSocket serverSocket = null;

    /**
     * The file descriptor object for this socket.
     */
    // 网络套接字在|JVM|层的文件描述符对象
    // 注：关联|OS|层文件描述符句柄（字段|fd.fd|赋值），在|socketCreate()/socketAccept()|中完成
    protected FileDescriptor fd;

    /**
     * The IP address of the remote end of this socket.
     */
    // 服务端套接字的监听网络地址
    // 对端网络地址：客户端套接字连接的服务器网络地址；服务器接受连接时该客户端套接字的网络地址
    protected InetAddress address;

    /**
     * The port number on the remote host to which this socket is connected.
     */
    // 对端端口号：客户端套接字连接的服务器端口号
    protected int port;

    /**
     * The local port number to which this socket is connected.
     */
    // 服务端套接字的监听端口号
    // 客户端套接字的本地端口号
    protected int localport;

    /**
     * Creates either a stream or a datagram socket.
     *
     * @param      stream   if {@code true}, create a stream socket;
     *                      otherwise, create a datagram socket.
     * @exception  IOException  if an I/O error occurs while creating the
     *               socket.
     */
    // 在|OS|层创建一个网络套接字，并将描述符设置到|fd.fd|字段上。参数|stream|指示它是一个
    // 流、还是一个报文套接字
    // 注：创建成功，设置（服务端、客户端）套接字已创建标志位
    // 注：若是一个服务端套接字（|serverSocket|不为空），则将其设置为|O_NONBLOCK|、|SO_REUSEADDR|
    protected abstract void create(boolean stream) throws IOException;

    /**
     * Connects this socket to the specified port on the named host.
     *
     * @param      host   the name of the remote host.
     * @param      port   the port number.
     * @exception  IOException  if an I/O error occurs when connecting to the
     *               remote host.
     */
    // 在|OS|层将套接字连接到指定的远程域名、端口号上。该方法会一直阻塞，直到连接被建立、超时或发生异常
    // 注：当|timeout==0|时，连接无超时限制；当|timeout>0|时，则在超时前仍未建立连接时，抛出异常
    // 注：当连接成功，设置客户端套接字的已连接、已绑定标志位
    // 注：当连接成功，服务端的地址与端口分别被设置到|adress|和|port|字段上；而客户端的描述符将被设
    // 置到|fd.fd|字段上，客户端实际绑定端口被设置到|localPort|字段上
    protected abstract void connect(String host, int port) throws IOException;

    /**
     * Connects this socket to the specified port number on the specified host.
     *
     * @param      address   the IP address of the remote host.
     * @param      port      the port number.
     * @exception  IOException  if an I/O error occurs when attempting a
     *               connection.
     */
    // 在|OS|层将套接字连接到指定的网络地址、端口号上。该方法会一直阻塞，直到连接被建立、超时或发生异常
    // 注：当|timeout==0|时，连接无超时限制；当|timeout>0|时，则在超时前仍未建立连接时，抛出异常
    // 注：当连接成功，设置客户端套接字的已连接、已绑定标志位
    // 注：当连接成功，服务端的地址与端口分别被设置到|adress|和|port|字段上；而客户端的描述符将被设
    // 置到|fd.fd|字段上，客户端实际绑定端口被设置到|localPort|字段上
    protected abstract void connect(InetAddress address, int port) throws IOException;

    /**
     * Connects this socket to the specified port number on the specified host.
     * A timeout of zero is interpreted as an infinite timeout. The connection
     * will then block until established or an error occurs.
     *
     * @param      address   the Socket address of the remote host.
     * @param     timeout  the timeout value, in milliseconds, or zero for no timeout.
     * @exception  IOException  if an I/O error occurs when attempting a
     *               connection.
     * @since 1.4
     */
    // 在|OS|层将套接字连接到指定的服务器的套接字地址上。该方法会一直阻塞，直到连接被建立、超时或发生异常
    // 注：当|timeout==0|时，连接无超时限制；当|timeout>0|时，则在超时前仍未建立连接时，抛出异常
    // 注：当连接成功，设置客户端套接字的已连接、已绑定标志位
    // 注：当连接成功，服务端的地址与端口分别被设置到|adress|和|port|字段上；而客户端的描述符将被设置
    // 到|fd.fd|字段上，客户端实际绑定端口被设置到|localPort|字段上
    protected abstract void connect(SocketAddress address, int timeout) throws IOException;

    /**
     * Binds this socket to the specified local IP address and port number.
     *
     * @param      host   an IP address that belongs to a local interface.
     * @param      port   the port number.
     * @exception  IOException  if an I/O error occurs when binding this socket.
     */
    // 在|OS|层将套接字与网络地址、端口号进行绑定。并将地址设置到|address|、以及将实际端口号设置
    // 到|localPort|字段上
    // 注：绑定成功，设置（服务端、客户端）套接字已绑定标志位
    protected abstract void bind(InetAddress host, int port) throws IOException;

    /**
     * Sets the maximum queue length for incoming connection indications
     * (a request to connect) to the {@code count} argument. If a
     * connection indication arrives when the queue is full, the
     * connection is refused.
     *
     * @param      backlog   the maximum length of the queue.
     * @exception  IOException  if an I/O error occurs when creating the queue.
     */
    // 在|OS|层将套接字设置为监听状态套接字，其最大挂起连接数为|backlog|
    protected abstract void listen(int backlog) throws IOException;

    /**
     * Accepts a connection.
     *
     * @param      s   the accepted connection.
     * @exception  IOException  if an I/O error occurs when accepting the
     *               connection.
     */
    // 监听并接受一个客户端连接。该方法会一直阻塞，直到一个连接被创建并设置到|s|参数中、或发生异常
    // 注：若设置了|timeout|超时限制，在超时前仍未获得连接时，将会抛出|SocketTimeoutException|异常
    protected abstract void accept(SocketImpl s) throws IOException;

    /**
     * Returns an input stream for this socket.
     *
     * @return     a stream for reading from this socket.
     * @exception  IOException  if an I/O error occurs when creating the
     *               input stream.
    */
    // 获取该套接字的输入流
    protected abstract InputStream getInputStream() throws IOException;

    /**
     * Returns an output stream for this socket.
     *
     * @return     an output stream for writing to this socket.
     * @exception  IOException  if an I/O error occurs when creating the
     *               output stream.
     */
    // 获取该套接字的输出流
    protected abstract OutputStream getOutputStream() throws IOException;

    /**
     * Returns the number of bytes that can be read from this socket
     * without blocking.
     *
     * @return     the number of bytes that can be read from this socket
     *             without blocking.
     * @exception  IOException  if an I/O error occurs when determining the
     *               number of bytes available.
     */
    // 返回可以从此输入流读取或跳过的剩余字节数的估计值，而不会使下一次读取或跳过这么多字节时被阻塞
    protected abstract int available() throws IOException;

    /**
     * Closes this socket.
     *
     * @exception  IOException  if an I/O error occurs when closing this socket.
     */
    // 关闭此套接字。当前在|accept()|中阻塞的任何线程都会抛出|SocketException|异常
    // 注：如果此套接字具有关联的通道，则该通道也将关闭
    protected abstract void close() throws IOException;

    /**
     * Places the input stream for this socket at "end of stream".
     * Any data sent to this socket is acknowledged and then
     * silently discarded.
     *
     * If you read from a socket input stream after invoking this method on the
     * socket, the stream's {@code available} method will return 0, and its
     * {@code read} methods will return {@code -1} (end of stream).
     *
     * @exception IOException if an I/O error occurs when shutting down this
     * socket.
     * @see java.net.Socket#shutdownOutput()
     * @see java.net.Socket#close()
     * @see java.net.Socket#setSoLinger(boolean, int)
     * @since 1.3
     */
    protected void shutdownInput() throws IOException {
      throw new IOException("Method not implemented!");
    }

    /**
     * Disables the output stream for this socket.
     * For a TCP socket, any previously written data will be sent
     * followed by TCP's normal connection termination sequence.
     *
     * If you write to a socket output stream after invoking
     * shutdownOutput() on the socket, the stream will throw
     * an IOException.
     *
     * @exception IOException if an I/O error occurs when shutting down this
     * socket.
     * @see java.net.Socket#shutdownInput()
     * @see java.net.Socket#close()
     * @see java.net.Socket#setSoLinger(boolean, int)
     * @since 1.3
     */
    protected void shutdownOutput() throws IOException {
      throw new IOException("Method not implemented!");
    }

    /**
     * Returns the value of this socket's {@code fd} field.
     *
     * @return  the value of this socket's {@code fd} field.
     * @see     java.net.SocketImpl#fd
     */
    protected FileDescriptor getFileDescriptor() {
        return fd;
    }

    /**
     * Returns the value of this socket's {@code address} field.
     *
     * @return  the value of this socket's {@code address} field.
     * @see     java.net.SocketImpl#address
     */
    protected InetAddress getInetAddress() {
        return address;
    }

    /**
     * Returns the value of this socket's {@code port} field.
     *
     * @return  the value of this socket's {@code port} field.
     * @see     java.net.SocketImpl#port
     */
    protected int getPort() {
        return port;
    }

    /**
     * Returns whether or not this SocketImpl supports sending
     * urgent data. By default, false is returned
     * unless the method is overridden in a sub-class
     *
     * @return  true if urgent data supported
     * @see     java.net.SocketImpl#address
     * @since 1.4
     */
    protected boolean supportsUrgentData () {
        return false; // must be overridden in sub-class
    }

    /**
     * Send one byte of urgent data on the socket.
     * The byte to be sent is the low eight bits of the parameter
     * @param data The byte of data to send
     * @exception IOException if there is an error
     *  sending the data.
     * @since 1.4
     */
    protected abstract void sendUrgentData (int data) throws IOException;

    /**
     * Returns the value of this socket's {@code localport} field.
     *
     * @return  the value of this socket's {@code localport} field.
     * @see     java.net.SocketImpl#localport
     */
    protected int getLocalPort() {
        return localport;
    }

    void setSocket(Socket soc) {
        this.socket = soc;
    }

    Socket getSocket() {
        return socket;
    }

    void setServerSocket(ServerSocket soc) {
        this.serverSocket = soc;
    }

    ServerSocket getServerSocket() {
        return serverSocket;
    }

    /**
     * Returns the address and port of this socket as a {@code String}.
     *
     * @return  a string representation of this socket.
     */
    public String toString() {
        return "Socket[addr=" + getInetAddress() +
            ",port=" + getPort() + ",localport=" + getLocalPort()  + "]";
    }

    // 重置套接字的状态
    void reset() throws IOException {
        address = null;
        port = 0;
        localport = 0;
    }

    /**
     * Sets performance preferences for this socket.
     *
     * <p> Sockets use the TCP/IP protocol by default.  Some implementations
     * may offer alternative protocols which have different performance
     * characteristics than TCP/IP.  This method allows the application to
     * express its own preferences as to how these tradeoffs should be made
     * when the implementation chooses from the available protocols.
     *
     * <p> Performance preferences are described by three integers
     * whose values indicate the relative importance of short connection time,
     * low latency, and high bandwidth.  The absolute values of the integers
     * are irrelevant; in order to choose a protocol the values are simply
     * compared, with larger values indicating stronger preferences. Negative
     * values represent a lower priority than positive values. If the
     * application prefers short connection time over both low latency and high
     * bandwidth, for example, then it could invoke this method with the values
     * {@code (1, 0, 0)}.  If the application prefers high bandwidth above low
     * latency, and low latency above short connection time, then it could
     * invoke this method with the values {@code (0, 1, 2)}.
     *
     * By default, this method does nothing, unless it is overridden in a
     * a sub-class.
     *
     * @param  connectionTime
     *         An {@code int} expressing the relative importance of a short
     *         connection time
     *
     * @param  latency
     *         An {@code int} expressing the relative importance of low
     *         latency
     *
     * @param  bandwidth
     *         An {@code int} expressing the relative importance of high
     *         bandwidth
     *
     * @since 1.5
     */
    protected void setPerformancePreferences(int connectionTime,
                                          int latency,
                                          int bandwidth)
    {
        /* Not implemented yet */
    }

    <T> void setOption(SocketOption<T> name, T value) throws IOException {
        if (name == StandardSocketOptions.SO_KEEPALIVE) {
            setOption(SocketOptions.SO_KEEPALIVE, value);
        } else if (name == StandardSocketOptions.SO_SNDBUF) {
            setOption(SocketOptions.SO_SNDBUF, value);
        } else if (name == StandardSocketOptions.SO_RCVBUF) {
            setOption(SocketOptions.SO_RCVBUF, value);
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            setOption(SocketOptions.SO_REUSEADDR, value);
        } else if (name == StandardSocketOptions.SO_LINGER) {
            setOption(SocketOptions.SO_LINGER, value);
        } else if (name == StandardSocketOptions.IP_TOS) {
            setOption(SocketOptions.IP_TOS, value);
        } else if (name == StandardSocketOptions.TCP_NODELAY) {
            setOption(SocketOptions.TCP_NODELAY, value);
        } else {
            throw new UnsupportedOperationException("unsupported option");
        }
    }

    <T> T getOption(SocketOption<T> name) throws IOException {
        if (name == StandardSocketOptions.SO_KEEPALIVE) {
            return (T)getOption(SocketOptions.SO_KEEPALIVE);
        } else if (name == StandardSocketOptions.SO_SNDBUF) {
            return (T)getOption(SocketOptions.SO_SNDBUF);
        } else if (name == StandardSocketOptions.SO_RCVBUF) {
            return (T)getOption(SocketOptions.SO_RCVBUF);
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            return (T)getOption(SocketOptions.SO_REUSEADDR);
        } else if (name == StandardSocketOptions.SO_LINGER) {
            return (T)getOption(SocketOptions.SO_LINGER);
        } else if (name == StandardSocketOptions.IP_TOS) {
            return (T)getOption(SocketOptions.IP_TOS);
        } else if (name == StandardSocketOptions.TCP_NODELAY) {
            return (T)getOption(SocketOptions.TCP_NODELAY);
        } else {
            throw new UnsupportedOperationException("unsupported option");
        }
    }
}
