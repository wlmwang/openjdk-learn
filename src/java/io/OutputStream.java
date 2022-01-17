/*
 * Copyright (c) 1994, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * This abstract class is the superclass of all classes representing
 * an output stream of bytes. An output stream accepts output bytes
 * and sends them to some sink.
 * <p>
 * Applications that need to define a subclass of
 * <code>OutputStream</code> must always provide at least a method
 * that writes one byte of output.
 *
 * @author  Arthur van Hoff
 * @see     java.io.BufferedOutputStream
 * @see     java.io.ByteArrayOutputStream
 * @see     java.io.DataOutputStream
 * @see     java.io.FilterOutputStream
 * @see     java.io.InputStream
 * @see     java.io.OutputStream#write(int)
 * @since   JDK1.0
 */
// 所有字节输出流（写入）的超类
// 注：字节流，即以|8|位（|1byte=8bit|）作为一个数据单元。数据流中最小的数据单元是字节
public abstract class OutputStream implements Closeable, Flushable {
    /**
     * Writes the specified byte to this output stream. The general
     * contract for <code>write</code> is that one byte is written
     * to the output stream. The byte to be written is the eight
     * low-order bits of the argument <code>b</code>. The 24
     * high-order bits of <code>b</code> are ignored.
     * <p>
     * Subclasses of <code>OutputStream</code> must provide an
     * implementation for this method.
     *
     * @param      b   the <code>byte</code>.
     * @exception  IOException  if an I/O error occurs. In particular,
     *             an <code>IOException</code> may be thrown if the
     *             output stream has been closed.
     */
    // 将指定的字节写入输出流。要写入的字节是参数|b|的低|8|位，|b|的高|24|位被忽略。如果发
    // 生|I/O|错误，特别是，如果输出流已关闭，则可能会抛出|IOException|
    // 注：不使用类型|byte|，其范围是|-128~127|不能覆盖|ASCII|码表
    public abstract void write(int b) throws IOException;

    /**
     * Writes <code>b.length</code> bytes from the specified byte array
     * to this output stream. The general contract for <code>write(b)</code>
     * is that it should have exactly the same effect as the call
     * <code>write(b, 0, b.length)</code>.
     *
     * @param      b   the data.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.OutputStream#write(byte[], int, int)
     */
    // 将字节数组|b|写入到输出流。此方法可能会阻塞，直到输入可用
    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array
     * starting at offset <code>off</code> to this output stream.
     * The general contract for <code>write(b, off, len)</code> is that
     * some of the bytes in the array <code>b</code> are written to the
     * output stream in order; element <code>b[off]</code> is the first
     * byte written and <code>b[off+len-1]</code> is the last byte written
     * by this operation.
     * <p>
     * The <code>write</code> method of <code>OutputStream</code> calls
     * the write method of one argument on each of the bytes to be
     * written out. Subclasses are encouraged to override this method and
     * provide a more efficient implementation.
     * <p>
     * If <code>b</code> is <code>null</code>, a
     * <code>NullPointerException</code> is thrown.
     * <p>
     * If <code>off</code> is negative, or <code>len</code> is negative, or
     * <code>off+len</code> is greater than the length of the array
     * <code>b</code>, then an <tt>IndexOutOfBoundsException</tt> is thrown.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @exception  IOException  if an I/O error occurs. In particular,
     *             an <code>IOException</code> is thrown if the output
     *             stream is closed.
     */
    // 将字节数组|b[off:off+len]|写入到输出流中。如果|len|不为零，则该方法可能将阻塞，直到输出可
    // 用；如果|len|为零，方法将立即返回
    // 注：底层会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    public void write(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                   ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        for (int i = 0 ; i < len ; i++) {
            write(b[off + i]);
        }
    }

    /**
     * Flushes this output stream and forces any buffered output bytes
     * to be written out. The general contract of <code>flush</code> is
     * that calling it is an indication that, if any bytes previously
     * written have been buffered by the implementation of the output
     * stream, such bytes should immediately be written to their
     * intended destination.
     * <p>
     * If the intended destination of this stream is an abstraction provided by
     * the underlying operating system, for example a file, then flushing the
     * stream guarantees only that bytes previously written to the stream are
     * passed to the operating system for writing; it does not guarantee that
     * they are actually written to a physical device such as a disk drive.
     * <p>
     * The <code>flush</code> method of <code>OutputStream</code> does nothing.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    public void flush() throws IOException {
    }

    /**
     * Closes this output stream and releases any system resources
     * associated with this stream. The general contract of <code>close</code>
     * is that it closes the output stream. A closed stream cannot perform
     * output operations and cannot be reopened.
     * <p>
     * The <code>close</code> method of <code>OutputStream</code> does nothing.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    public void close() throws IOException {
    }

}
