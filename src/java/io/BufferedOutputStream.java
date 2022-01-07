/*
 * Copyright (c) 1994, 2003, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

/**
 * The class implements a buffered output stream. By setting up such
 * an output stream, an application can write bytes to the underlying
 * output stream without necessarily causing a call to the underlying
 * system for each byte written.
 *
 * @author  Arthur van Hoff
 * @since   JDK1.0
 */
// 带缓冲区的输出流（写入）。是一个字节流、处理流。主要用于装饰底层流来增加缓存特性。  线程安全
// 注：只有缓冲区被写满时，才会触发缓冲区的数据刷新、写入到底层流中；流被关闭时，会触发刷新动作
// 注：字节流，即以|8bit|（|1byte=8bit|）作为一个数据单元。数据流中最小的数据单元是字节
// 注：根据是否直接处理数据，|IO|分为节点流和处理流。节点流是真正直接处理数据的；处理流是装饰加工节点流的
public
class BufferedOutputStream extends FilterOutputStream {
    /**
     * The internal buffer where data is stored.
     */
    // 缓冲区的字节数组。默认大小为|8k|
    protected byte buf[];

    /**
     * The number of valid bytes in the buffer. This value is always
     * in the range <tt>0</tt> through <tt>buf.length</tt>; elements
     * <tt>buf[0]</tt> through <tt>buf[count-1]</tt> contain valid
     * byte data.
     */
    // 缓冲区中的有效字节数。有效字节即为待刷新、写入到底层流的数据
    // 注：|buf[0:count-1]|为缓冲的有效字节数据；|count|的长度范围为|0~buf.length|
    protected int count;

    /**
     * Creates a new buffered output stream to write data to the
     * specified underlying output stream.
     *
     * @param   out   the underlying output stream.
     */
    // 基于一个输出流，创建一个带缓冲区特性的输出流（写入）对象。缓冲区默认大小为|8k|
    public BufferedOutputStream(OutputStream out) {
        this(out, 8192);
    }

    /**
     * Creates a new buffered output stream to write data to the
     * specified underlying output stream with the specified buffer
     * size.
     *
     * @param   out    the underlying output stream.
     * @param   size   the buffer size.
     * @exception IllegalArgumentException if size &lt;= 0.
     */
    // 基于一个输出流，创建一个带缓冲区特性的输出流（写入）对象。缓冲区默认大小为|size|
    public BufferedOutputStream(OutputStream out, int size) {
        super(out);
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        buf = new byte[size];
    }

    /** Flush the internal buffer */
    // 将缓冲区中的数据刷新、写入到底层流中
    private void flushBuffer() throws IOException {
        if (count > 0) {
            out.write(buf, 0, count);
            count = 0;
        }
    }

    /**
     * Writes the specified byte to this buffered output stream.
     *
     * @param      b   the byte to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    // 将指定的字节写入输出流。要写入的字节是参数|b|的低|8|位，|b|的高|24|位被忽略。此方法可能会阻塞
    // 注：缓冲区已满时，会自动将缓冲区数据刷新、写入到底层流中
    // 注：不使用类型|byte|，其的范围是|-128~127|不能覆盖|ASCII|码表
    public synchronized void write(int b) throws IOException {
        if (count >= buf.length) {
            flushBuffer();
        }
        buf[count++] = (byte)b;
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array
     * starting at offset <code>off</code> to this buffered output stream.
     *
     * <p> Ordinarily this method stores bytes from the given array into this
     * stream's buffer, flushing the buffer to the underlying output stream as
     * needed.  If the requested length is at least as large as this stream's
     * buffer, however, then this method will flush the buffer and write the
     * bytes directly to the underlying output stream.  Thus redundant
     * <code>BufferedOutputStream</code>s will not copy data unnecessarily.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @exception  IOException  if an I/O error occurs.
     */
    // 将字节数组|b[off:off+len]|写入到缓冲流中。如果|len|为零，方法将立即返回
    // 注：内部会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    public synchronized void write(byte b[], int off, int len) throws IOException {
        // 大数据写入。即，被写入的数据长度超过了缓冲区总长度
        if (len >= buf.length) {
            /* If the request length exceeds the size of the output buffer,
               flush the output buffer and then write the data directly.
               In this way buffered streams will cascade harmlessly. */
            // 先将缓冲区中的数据刷新、写入到底层流中
            flushBuffer();
            // 再直接调用底层流的写入接口写入数据
            out.write(b, off, len);
            return;
        }

        // 剩余缓冲区小于待写入数据字节长度，先将缓冲区中的数据刷新、写入到底层流中
        if (len > buf.length - count) {
            flushBuffer();
        }

        // 将数据拷贝至缓冲区中
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    /**
     * Flushes this buffered output stream. This forces any buffered
     * output bytes to be written out to the underlying output stream.
     *
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    // 将缓冲区中的数据刷新、写入到底层流中。并且再调用底层流的刷新接口
    public synchronized void flush() throws IOException {
        flushBuffer();
        out.flush();
    }
}
