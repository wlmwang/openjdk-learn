/*
 * Copyright (c) 1994, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

/**
 * This class implements an output stream in which the data is
 * written into a byte array. The buffer automatically grows as data
 * is written to it.
 * The data can be retrieved using <code>toByteArray()</code> and
 * <code>toString()</code>.
 * <p>
 * Closing a <tt>ByteArrayOutputStream</tt> has no effect. The methods in
 * this class can be called after the stream has been closed without
 * generating an <tt>IOException</tt>.
 *
 * @author  Arthur van Hoff
 * @since   JDK1.0
 */

// 字节数组的输出流（写入）。是一个字节流、节点流。字节数组的内存会自动扩容。  线程安全
// 注：字节流，即以|8bit|（|1byte=8bit|）作为一个数据单元。数据流中最小的数据单元是字节
// 注：根据是否直接处理数据，|IO|分为节点流和处理流。节点流是真正直接处理数据的；处理流是装饰加工节点流的
// 注：关闭字节数组的输出流没有任何效果。即使在关闭流后调用此类中的方法，也不会发生|IOException|
public class ByteArrayOutputStream extends OutputStream {

    /**
     * The buffer where data is stored.
     */
    // 输出流的字节数组。自动扩容
    protected byte buf[];

    /**
     * The number of valid bytes in the buffer.
     */
    // 向输出流字节数组写入的一个字节时的索引。也是当前字节数组中已写入数据的长度
    // 注：|buf[0:count-1]|为已写入流中的数据；|count|的长度范围为|0~buf.length|
    protected int count;

    /**
     * Creates a new byte array output stream. The buffer capacity is
     * initially 32 bytes, though its size increases if necessary.
     */
    // 创建一个初始容量为|32|长度的字节数组输出流。其大小在必要时会自动扩容
    public ByteArrayOutputStream() {
        this(32);
    }

    /**
     * Creates a new byte array output stream, with a buffer capacity of
     * the specified size, in bytes.
     *
     * @param   size   the initial size.
     * @exception  IllegalArgumentException if size is negative.
     */
    // 创建一个初始容量为|size|长度的字节数组输出流。其大小在必要时会自动扩容
    public ByteArrayOutputStream(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: "
                                               + size);
        }
        // 立即分配该处理容量内存
        buf = new byte[size];
    }

    /**
     * Increases the capacity if necessary to ensure that it can hold
     * at least the number of elements specified by the minimum
     * capacity argument.
     *
     * @param minCapacity the desired minimum capacity
     * @throws OutOfMemoryError if {@code minCapacity < 0}.  This is
     * interpreted as a request for the unsatisfiably large capacity
     * {@code (long) Integer.MAX_VALUE + (minCapacity - Integer.MAX_VALUE)}.
     */
    // 系统在确保能容纳|minCapacity|个元素时，自行判断容量是否需要扩容
    private void ensureCapacity(int minCapacity) {
        // overflow-conscious code
        // 此处有溢出风险。即，除了真正的需要扩容场景中；当|minCapacity|为负数，两数之差变
        // 成了之和，导致溢出使整个表达式大于|0|，从而也将执行|grow()|
        if (minCapacity - buf.length > 0)
            grow(minCapacity);
    }

    /**
     * The maximum size of array to allocate.
     * Some VMs reserve some header words in an array.
     * Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     */
    // 要分配的字节数组的上限
    // 注：数组对象对比普通的类有一个额外的元数据，用于表示数组的大小。数组的最大尺寸为|2^31|，但
    // 是需要|8|字节的空间存储数组的长度等元数据，所以数组最大为|Integer.MAX_VALUE-8|
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * Increases the capacity to ensure that it can hold at least the
     * number of elements specified by the minimum capacity argument.
     *
     * @param minCapacity the desired minimum capacity
     */
    private void grow(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = buf.length;

        // 新的字节数组容量为老容量的|2|倍
        // 注：有溢出风险。即，当扩容到|2|的整数有溢出，它将变成负数
        int newCapacity = oldCapacity << 1;

        // 新字节数组容量为手动设置的最小容量与|2|倍老容量中的较大值
        // 注：有益的溢出风险。即，当|newCapacity|为负数，两个整数之和溢出|4|字节，结果也将小于|0|
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;

        // 新的字节数组容量超过|MAX_ARRAY_SIZE|大小，进入大容量扩容逻辑
        // 注：有溢出风险。即，当|newCapacity|为负数时，结果将会大于|0|，从而也将执行|hugeCapacity()|
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);

        // 重新申请的一块内存，并将原始数据拷贝自该内存中
        // 注：底层使用|System.arraycopy|进行按字节拷贝
        buf = Arrays.copyOf(buf, newCapacity);
    }

    private static int hugeCapacity(int minCapacity) {
        // 如果|minCapacity|小于|0|，抛出溢出异常
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();

        // 数组容量最大不会超过|Integer.MAX_VALUE|
        return (minCapacity > MAX_ARRAY_SIZE) ?
            Integer.MAX_VALUE :
            MAX_ARRAY_SIZE;
    }

    /**
     * Writes the specified byte to this byte array output stream.
     *
     * @param   b   the byte to be written.
     */
    // 将指定的字节写入输出流。要写入的字节是参数|b|的低|8|位，|b|的高|24|位被忽略。此方法不会阻塞
    // 注：不使用类型|byte|，其的范围是|-128~127|不能覆盖|ASCII|码表
    public synchronized void write(int b) {
        // 必要时，扩容内存
        ensureCapacity(count + 1);
        buf[count] = (byte) b;
        count += 1;
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array
     * starting at offset <code>off</code> to this byte array output stream.
     *
     * @param   b     the data.
     * @param   off   the start offset in the data.
     * @param   len   the number of bytes to write.
     */
    // 将字节数组|b[off:off+len]|写入到输出流中。如果|len|不为零，则该方法将阻塞，直到输出可用；如果|len|为
    // 零，方法将立即返回
    // 注：内部会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    public synchronized void write(byte b[], int off, int len) {
        // 越界校验
        if ((off < 0) || (off > b.length) || (len < 0) ||
            ((off + len) - b.length > 0)) {
            throw new IndexOutOfBoundsException();
        }
        // 必要时，扩容内存
        ensureCapacity(count + len);

        // 拷贝参数|b[off:off+len]|数据至从当前偏移开始的字节数组中
        System.arraycopy(b, off, buf, count, len);

        // 将当前偏移增加|len|长度
        count += len;
    }

    /**
     * Writes the complete contents of this byte array output stream to
     * the specified output stream argument, as if by calling the output
     * stream's write method using <code>out.write(buf, 0, count)</code>.
     *
     * @param      out   the output stream to which to write the data.
     * @exception  IOException  if an I/O error occurs.
     */
    // 将当前输出流的全部数据拷贝一份到指定的输出流中
    public synchronized void writeTo(OutputStream out) throws IOException {
        out.write(buf, 0, count);
    }

    /**
     * Resets the <code>count</code> field of this byte array output
     * stream to zero, so that all currently accumulated output in the
     * output stream is discarded. The output stream can be used again,
     * reusing the already allocated buffer space.
     *
     * @see     java.io.ByteArrayInputStream#count
     */
    // 将当前输出流中已写入的数据清空
    // 注：输出流可以再次使用，重用已经分配的字节数组空间
    public synchronized void reset() {
        count = 0;
    }

    /**
     * Creates a newly allocated byte array. Its size is the current
     * size of this output stream and the valid contents of the buffer
     * have been copied into it.
     *
     * @return  the current contents of this output stream, as a byte array.
     * @see     java.io.ByteArrayOutputStream#size()
     */
    // 将当前输出流的全部数据拷贝一份到新建的字节数组中，并返回给调用者
    public synchronized byte toByteArray()[] {
        return Arrays.copyOf(buf, count);
    }

    /**
     * Returns the current size of the buffer.
     *
     * @return  the value of the <code>count</code> field, which is the number
     *          of valid bytes in this output stream.
     * @see     java.io.ByteArrayOutputStream#count
     */
    // 当前字节数组中已写入数据的字节长度
    public synchronized int size() {
        return count;
    }

    /**
     * Converts the buffer's contents into a string decoding bytes using the
     * platform's default character set. The length of the new <tt>String</tt>
     * is a function of the character set, and hence may not be equal to the
     * size of the buffer.
     *
     * <p> This method always replaces malformed-input and unmappable-character
     * sequences with the default replacement string for the platform's
     * default character set. The {@linkplain java.nio.charset.CharsetDecoder}
     * class should be used when more control over the decoding process is
     * required.
     *
     * @return String decoded from the buffer's contents.
     * @since  JDK1.1
     */
    // 将当前输出流的全部数据拷贝一份到新建的平台默认字符集的字符串对象中，并返回给调用者
    // 注：由于字符串的长度与字符集相关，因此它可能并不等于缓冲区的大小
    // 注：此方法始终使用平台默认字符集的"默认替换字符串"替换"格式错误的输入"和"不可映射"的字符序
    // 列。当需要对编码进行更多控制时，应使用|java.nio.charset.CharsetDecoder|类
    public synchronized String toString() {
        return new String(buf, 0, count);
    }

    /**
     * Converts the buffer's contents into a string by decoding the bytes using
     * the named {@link java.nio.charset.Charset charset}. The length of the new
     * <tt>String</tt> is a function of the charset, and hence may not be equal
     * to the length of the byte array.
     *
     * <p> This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string. The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     *
     * @param      charsetName  the name of a supported
     *             {@link java.nio.charset.Charset charset}
     * @return     String decoded from the buffer's contents.
     * @exception  UnsupportedEncodingException
     *             If the named charset is not supported
     * @since      JDK1.1
     */
    // 相比无参的|toString()|方法，此处用户可手动指定字符集
    // 注：如果系统不支持指定的字符集，将抛出|UnsupportedEncodingException|
    public synchronized String toString(String charsetName)
        throws UnsupportedEncodingException
    {
        return new String(buf, 0, count, charsetName);
    }

    /**
     * Creates a newly allocated string. Its size is the current size of
     * the output stream and the valid contents of the buffer have been
     * copied into it. Each character <i>c</i> in the resulting string is
     * constructed from the corresponding element <i>b</i> in the byte
     * array such that:
     * <blockquote><pre>
     *     c == (char)(((hibyte &amp; 0xff) &lt;&lt; 8) | (b &amp; 0xff))
     * </pre></blockquote>
     *
     * @deprecated This method does not properly convert bytes into characters.
     * As of JDK&nbsp;1.1, the preferred way to do this is via the
     * <code>toString(String enc)</code> method, which takes an encoding-name
     * argument, or the <code>toString()</code> method, which uses the
     * platform's default character encoding.
     *
     * @param      hibyte    the high byte of each resulting Unicode character.
     * @return     the current contents of the output stream, as a string.
     * @see        java.io.ByteArrayOutputStream#size()
     * @see        java.io.ByteArrayOutputStream#toString(String)
     * @see        java.io.ByteArrayOutputStream#toString()
     */
    // 已废弃。请使用|toString(String charsetName)|方法替换该方法
    // 注：此方法不能正确地将字节转换为字符
    @Deprecated
    public synchronized String toString(int hibyte) {
        return new String(buf, hibyte, 0, count);
    }

    /**
     * Closing a <tt>ByteArrayOutputStream</tt> has no effect. The methods in
     * this class can be called after the stream has been closed without
     * generating an <tt>IOException</tt>.
     */
    // 关闭字节数组的输出流没有任何效果
    public void close() throws IOException {
    }

}
