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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import sun.net.ConnectionResetException;

/**
 * This stream extends FileInputStream to implement a
 * SocketInputStream. Note that this class should <b>NOT</b> be
 * public.
 *
 * @author      Jonathan Payne
 * @author      Arthur van Hoff
 */
// 套接字的输入流（读取）。是一个字节流、节点流
// 注：不提供"标记/重置"的支持（回退特性）；直接|IO|支持，底层的|flush()|为空操作
// 注：字节流，即以|8bit|（|1byte=8bit|）作为一个数据单元。数据流中最小的数据单元是字节
// 注：根据是否直接处理数据，|IO|分为节点流和处理流。节点流是真正直接处理数据的；处理流是装饰加工节点流的
class SocketInputStream extends FileInputStream
{
    static {
        init();
    }

    private boolean eof;
    private AbstractPlainSocketImpl impl = null;
    private byte temp[];
    private Socket socket = null;

    /**
     * Creates a new SocketInputStream. Can only be called
     * by a Socket. This method needs to hang on to the owner Socket so
     * that the fd will not be closed.
     * @param impl the implemented socket input stream
     */
    SocketInputStream(AbstractPlainSocketImpl impl) throws IOException {
        super(impl.getFileDescriptor());
        this.impl = impl;
        socket = impl.getSocket();
    }

    /**
     * Returns the unique {@link java.nio.channels.FileChannel FileChannel}
     * object associated with this file input stream.</p>
     *
     * The {@code getChannel} method of {@code SocketInputStream}
     * returns {@code null} since it is a socket based stream.</p>
     *
     * @return  the file channel associated with this file input stream
     *
     * @since 1.4
     * @spec JSR-51
     */
    public final FileChannel getChannel() {
        return null;
    }

    /**
     * Reads into an array of bytes at the specified offset using
     * the received socket primitive.
     * @param fd the FileDescriptor
     * @param b the buffer into which the data is read
     * @param off the start offset of the data
     * @param len the maximum number of bytes read
     * @param timeout the read timeout in ms
     * @return the actual number of bytes read, -1 is
     *          returned when the end of the stream is reached.
     * @exception IOException If an I/O error has occurred.
     */
    // 在|OS|层从套接字中读取最大长度为|len|的数据。该方法会一直阻塞，直到数据可读、超时、关闭、或异常
    // 注：若设置了|timeout|超时限制，在超时前仍不可读，将会抛出|SocketTimeoutException|异常
    // 注：底层使用|poll(&pfd, 1, timeout)|实现超时并监听套接字可读，其中|pfd.events=POLLIN|POLLERR|
    // 注：底层使用|recv(fd, bufP, len, 0)|接收数据，返回实际读取的字节数；若为|0|，则表示套接字对端已被
    // 关闭；若为|-1|，则表示流发生了错误（会抛出异常）
    // 注：对一个已经接收到|RST|报文的套接字，读取数据，将抛出|ConnectionResetException|异常。接收|RST|报文场景有：
    // 1.若在发送数据过程中对端的套接字中断了，这将会导致发送端的|write|先返回已发送的字节数，再次|write|时立即返回|-1|，
    // 同时错误码为|ECONNRESET|。这通常出现在发送端发送数据时，对方将接收进程退出了。即，写入数据过程中收到|RST|报文
    // 2.向一个对端已经中断的套接字中|write|数据，系统内核会先触发|SIGPIPE|信号处理函数，而后返回|-1|，同时将错误码置
    // 为|EPIPE|。这通常出现在发送端发送数据前，对方将接收进程退出了。即，向一个已经收到|RST|报文的套接字中写入数据
    private native int socketRead0(FileDescriptor fd,
                                   byte b[], int off, int len,
                                   int timeout)
        throws IOException;

    // wrap native call to allow instrumentation
    /**
     * Reads into an array of bytes at the specified offset using
     * the received socket primitive.
     * @param fd the FileDescriptor
     * @param b the buffer into which the data is read
     * @param off the start offset of the data
     * @param len the maximum number of bytes read
     * @param timeout the read timeout in ms
     * @return the actual number of bytes read, -1 is
     *          returned when the end of the stream is reached.
     * @exception IOException If an I/O error has occurred.
     */
    // 在|OS|层从套接字中读取最大长度为|len|的数据。该方法会一直阻塞，直到数据可读、超时、关闭、或异常
    // 注：返回实际读取的字节数；若为|0|，则表示套接字对端已被关闭；若为|-1|，则表示流发生了错误（会抛出异常）
    private int socketRead(FileDescriptor fd,
                           byte b[], int off, int len,
                           int timeout)
        throws IOException {
        return socketRead0(fd, b, off, len, timeout);
    }

    /**
     * Reads into a byte array data from the socket.
     * @param b the buffer into which the data is read
     * @return the actual number of bytes read, -1 is
     *          returned when the end of the stream is reached.
     * @exception IOException If an I/O error has occurred.
     */
    // 从输入流中读取最多|b.length|个字节的数据到一个字节数组|b|中。此方法会阻塞，直到输入可用、被关闭或者
    // 超时（如果设置的话）
    // 注：返回实际读取的字节数；若为|-1|，则表示已经读取到流末尾
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Reads into a byte array <i>b</i> at offset <i>off</i>,
     * <i>length</i> bytes of data.
     * @param b the buffer into which the data is read
     * @param off the start offset of the data
     * @param length the maximum number of bytes read
     * @return the actual number of bytes read, -1 is
     *          returned when the end of the stream is reached.
     * @exception IOException If an I/O error has occurred.
     */
    // 从输入流中读取最多|length|个字节的数据到一个字节数组|b[off:off+length]|中。如果|length|不为零，
    // 则该方法将阻塞，直到输入可用、被关闭或者超时（如果设置的话）；如果|length|为零，返回将立即返回零
    // 注：底层会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    // 注：返回实际读取的字节数；若为|-1|，则表示已经读取到流末尾（套接字对端已被关闭）
    public int read(byte b[], int off, int length) throws IOException {
        return read(b, off, length, impl.getTimeout());
    }

    // 从输入流中读取最多|length|个字节的数据到一个字节数组|b[off:off+length]|中。如果|length|不为零，
    // 则该方法将阻塞，直到输入可用、被关闭或者超时（如果设置的话）；如果|length|为零，返回将立即返回零
    // 注：返回实际读取的字节数；若为|-1|，则表示已经读取到流末尾（套接字对端已被关闭）
    int read(byte b[], int off, int length, int timeout) throws IOException {
        int n;

        // EOF already encountered
        if (eof) {
            return -1;
        }

        // connection reset
        // 连接被重置，立即抛出异常
        if (impl.isConnectionReset()) {
            throw new SocketException("Connection reset");
        }

        // bounds check
        // 数组|b|是否越界校验
        if (length <= 0 || off < 0 || off + length > b.length) {
            if (length == 0) {
                return 0;
            }
            throw new ArrayIndexOutOfBoundsException();
        }

        boolean gotReset = false;

        // acquire file descriptor and do the read
        // 自增"正在使用文件描述符"计数器
        FileDescriptor fd = impl.acquireFD();
        try {
            n = socketRead(fd, b, off, length, timeout);

            // 返回实际读取的字节数；若为|0|，则表示套接字对端已被关闭；若为|-1|，则表示流发生了错误（会抛出异常）
            if (n > 0) {
                return n;
            }
        } catch (ConnectionResetException rstExc) {
            // 对一个已经接收到|RST|报文的套接字，读取数据
            // 注：对一个已经接收到|RST|报文的套接字，第一次执行|recv|将返回|-1|，同时错误码为|ECONNRESET|；若
            // 再次|recv|将返回|-1|，同时错误码为|EPIPE|，并且系统内核会触发|SIGPIPE|信号
            gotReset = true;
        } finally {
            impl.releaseFD();
        }

        /*
         * We receive a "connection reset" but there may be bytes still
         * buffered on the socket
         */
        if (gotReset) {
            impl.setConnectionResetPending();
            impl.acquireFD();
            try {
                // 对一个已经接收到|RST|报文的套接字，读取数据
                // 注：对一个已经接收到|RST|报文的套接字，第一次执行|recv|将返回|-1|，同时错误码为|ECONNRESET|；若
                // 再次|recv|将返回|-1|，同时错误码为|EPIPE|，并且系统内核会触发|SIGPIPE|信号
                n = socketRead(fd, b, off, length, timeout);
                if (n > 0) {
                    return n;
                }
            } catch (ConnectionResetException rstExc) {
            } finally {
                impl.releaseFD();
            }
        }

        /*
         * If we get here we are at EOF, the socket has been closed,
         * or the connection has been reset.
         */
        if (impl.isClosedOrPending()) {
            throw new SocketException("Socket closed");
        }
        if (impl.isConnectionResetPending()) {
            impl.setConnectionReset();
        }
        if (impl.isConnectionReset()) {
            throw new SocketException("Connection reset");
        }
        eof = true;
        return -1;
    }

    /**
     * Reads a single byte from the socket.
     */
    // 从输入流中读取下一个字节的数据，以|0~255|范围内的|int|值形式返回。如果已到达流末尾而没有
    // 可用字节，则返回值|-1|。此方法会阻塞，直到输入数据可用、检测到流结束（关闭）或抛出异常为止
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        temp = new byte[1];
        int n = read(temp, 0, 1);
        if (n <= 0) {
            return -1;
        }
        return temp[0] & 0xff;
    }

    /**
     * Skips n bytes of input.
     * @param numbytes the number of bytes to skip
     * @return  the actual number of bytes skipped.
     * @exception IOException If an I/O error has occurred.
     */
    // 从输入流中跳过并丢弃|n|个字节的数据。当|numbytes<=0|时，无任何副作用，并立即返回|0|
    public long skip(long numbytes) throws IOException {
        if (numbytes <= 0) {
            return 0;
        }
        long n = numbytes;

        // 单次|read()|系统调用最多跳过|1024|字节的数据
        int buflen = (int) Math.min(1024, n);
        byte data[] = new byte[buflen];
        while (n > 0) {
            int r = read(data, 0, (int) Math.min((long) buflen, n));
            if (r < 0) {
                break;
            }
            n -= r;
        }
        return numbytes - n;
    }

    /**
     * Returns the number of bytes that can be read without blocking.
     * @return the number of immediately available bytes
     */
    // 返回可以从此输入流读取或跳过的剩余字节数的估计值，而不会使下一次读取或跳过这么多字节时被阻塞
    public int available() throws IOException {
        return impl.available();
    }

    /**
     * Closes the stream.
     */
    private boolean closing = false;
    public void close() throws IOException {
        // Prevent recursion. See BugId 4484411
        if (closing)
            return;
        closing = true;
        if (socket != null) {
            if (!socket.isClosed())
                socket.close();
        } else
            impl.close();
        closing = false;
    }

    void setEOF(boolean eof) {
        this.eof = eof;
    }

    /**
     * Overrides finalize, the fd is closed by the Socket.
     */
    protected void finalize() {}

    /**
     * Perform class load-time initializations.
     */
    private native static void init();
}
