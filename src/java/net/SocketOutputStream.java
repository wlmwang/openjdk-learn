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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * This stream extends FileOutputStream to implement a
 * SocketOutputStream. Note that this class should <b>NOT</b> be
 * public.
 *
 * @author      Jonathan Payne
 * @author      Arthur van Hoff
 */
// 套接字的输出流（写入）。是一个字节流、节点流
// 注：不提供"标记/重置"的支持（回退特性）；直接|IO|支持，底层的|flush()|为空操作
// 注：字节流，即以|8bit|（|1byte=8bit|）作为一个数据单元。数据流中最小的数据单元是字节
// 注：根据是否直接处理数据，|IO|分为节点流和处理流。节点流是真正直接处理数据的；处理流是装饰加工节点流的
class SocketOutputStream extends FileOutputStream
{
    static {
        init();
    }

    private AbstractPlainSocketImpl impl = null;
    private byte temp[] = new byte[1];
    private Socket socket = null;

    /**
     * Creates a new SocketOutputStream. Can only be called
     * by a Socket. This method needs to hang on to the owner Socket so
     * that the fd will not be closed.
     * @param impl the socket output stream inplemented
     */
    SocketOutputStream(AbstractPlainSocketImpl impl) throws IOException {
        super(impl.getFileDescriptor());
        this.impl = impl;
        socket = impl.getSocket();
    }

    /**
     * Returns the unique {@link java.nio.channels.FileChannel FileChannel}
     * object associated with this file output stream. </p>
     *
     * The {@code getChannel} method of {@code SocketOutputStream}
     * returns {@code null} since it is a socket based stream.</p>
     *
     * @return  the file channel associated with this file output stream
     *
     * @since 1.4
     * @spec JSR-51
     */
    public final FileChannel getChannel() {
        return null;
    }

    /**
     * Writes to the socket.
     * @param fd the FileDescriptor
     * @param b the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes that are written
     * @exception IOException If an I/O error has occurred.
     */
    // 将字节数组|b[off:off+len]|写入到|fd|输出流中。如果|len|不为零，则该方法将阻塞，直到输出可用或者被关闭；如
    // 果|len|为零，方法将立即返回
    // 注：底层会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    // 注：对一个套接字写入数据过程中收到了|RST|报文，将抛出|ConnectionResetException|异常。接收|RST|报文场景有：
    // 1.若在发送数据过程中对端的套接字中断了，这将会导致发送端的|write|先返回已发送的字节数，再次|write|时立即返回|-1|，
    // 同时错误码为|ECONNRESET|。这通常出现在发送端发送数据时，对方将接收进程退出了。即，写入数据过程中收到|RST|报文
    // 2.向一个对端已经中断的套接字中|write|数据，系统内核会先触发|SIGPIPE|信号处理函数，而后返回|-1|，同时将错误码置
    // 为|EPIPE|。这通常出现在发送端发送数据前，对方将接收进程退出了。即，向一个已经收到|RST|报文的套接字中写入数据
    private native void socketWrite0(FileDescriptor fd, byte[] b, int off,
                                     int len) throws IOException;

    /**
     * Writes to the socket with appropriate locking of the
     * FileDescriptor.
     * @param b the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes that are written
     * @exception IOException If an I/O error has occurred.
     */
    // 将字节数组|b[off:off+len]|写入到输出流中。如果|len|不为零，则该方法将阻塞，直到输出可用或者被关闭；如
    // 果|len|为零，方法将立即返回
    // 注：底层会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    private void socketWrite(byte b[], int off, int len) throws IOException {

        if (len <= 0 || off < 0 || off + len > b.length) {
            if (len == 0) {
                return;
            }
            throw new ArrayIndexOutOfBoundsException();
        }

        FileDescriptor fd = impl.acquireFD();
        try {
            socketWrite0(fd, b, off, len);
        } catch (SocketException se) {
            if (se instanceof sun.net.ConnectionResetException) {
                // 对一个套接字写入数据过程中收到了|RST|报文
                // 注：若在发送数据过程中对端的套接字中断了，这将会导致发送端的|write|先返回已发送的字节数，再
                // 次|write|时立即返回|-1|，同时错误码为|ECONNRESET|
                impl.setConnectionResetPending();
                se = new SocketException("Connection reset");
            }
            if (impl.isClosedOrPending()) {
                throw new SocketException("Socket closed");
            } else {
                throw se;
            }
        } finally {
            impl.releaseFD();
        }
    }

    /**
     * Writes a byte to the socket.
     * @param b the data to be written
     * @exception IOException If an I/O error has occurred.
     */
    // 将指定的字节写入输出流。要写入的字节是参数|b|的低|8|位，|b|的高|24|位被忽略。如果发
    // 生|I/O|错误，特别是，如果输出流已关闭，则可能会抛出|IOException|
    public void write(int b) throws IOException {
        temp[0] = (byte)b;
        socketWrite(temp, 0, 1);
    }

    /**
     * Writes the contents of the buffer <i>b</i> to the socket.
     * @param b the data to be written
     * @exception SocketException If an I/O error has occurred.
     */
    // 将字节数组|b|写入到输出流。此方法会阻塞，直到输入可用或者被关闭
    public void write(byte b[]) throws IOException {
        socketWrite(b, 0, b.length);
    }

    /**
     * Writes <i>length</i> bytes from buffer <i>b</i> starting at
     * offset <i>len</i>.
     * @param b the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes that are written
     * @exception SocketException If an I/O error has occurred.
     */
    // 将字节数组|b[off:off+len]|写入到输出流中。如果|len|不为零，则该方法将阻塞，直到输出可用或者被关闭；如
    // 果|len|为零，方法将立即返回
    // 注：底层会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    public void write(byte b[], int off, int len) throws IOException {
        socketWrite(b, off, len);
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

    /**
     * Overrides finalize, the fd is closed by the Socket.
     */
    protected void finalize() {}

    /**
     * Perform class load-time initializations.
     */
    private native static void init();

}
