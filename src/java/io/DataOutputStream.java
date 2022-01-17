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
 * A data output stream lets an application write primitive Java data
 * types to an output stream in a portable way. An application can
 * then use a data input stream to read the data back in.
 *
 * @author  unascribed
 * @see     java.io.DataInputStream
 * @since   JDK1.0
 */
// 支持编码基本数据类型的输出流。是一个字节/字符流、处理流。 线程安全
// 注：主要用于装饰底层流来增加基础数据类型的写入（大端序）；也可以写出|UTF|编码的字符串
// 注：字节流，即以|8bit|（|1byte=8bit|）作为一个数据单元。数据流中最小的数据单元是字节
// 注：字符流，即以|16bit|（|1char=16bit|）作为一个数据单元。数据流中最小的文本单元是字符
// 注：根据是否直接处理数据，|IO|分为节点流和处理流。节点流是真正直接处理数据的；处理流是装饰加工节点流的
public
class DataOutputStream extends FilterOutputStream implements DataOutput {
    /**
     * The number of bytes written to the data output stream so far.
     * If this counter overflows, it will be wrapped to Integer.MAX_VALUE.
     */
    // 写入底层流字节长度
    protected int written;

    /**
     * bytearr is initialized on demand by writeUTF
     */
    private byte[] bytearr = null;

    /**
     * Creates a new data output stream to write data to the specified
     * underlying output stream. The counter <code>written</code> is
     * set to zero.
     *
     * @param   out   the underlying output stream, to be saved for later
     *                use.
     * @see     java.io.FilterOutputStream#out
     */
    // 基于一个输出流，创建一个支持编码基本数据类型的输出流对象
    public DataOutputStream(OutputStream out) {
        super(out);
    }

    /**
     * Increases the written counter by the specified value
     * until it reaches Integer.MAX_VALUE.
     */
    // 统计写入底层流字节长度
    private void incCount(int value) {
        int temp = written + value;
        if (temp < 0) {
            temp = Integer.MAX_VALUE;
        }
        written = temp;
    }

    /**
     * Writes the specified byte (the low eight bits of the argument
     * <code>b</code>) to the underlying output stream. If no exception
     * is thrown, the counter <code>written</code> is incremented by
     * <code>1</code>.
     * <p>
     * Implements the <code>write</code> method of <code>OutputStream</code>.
     *
     * @param      b   the <code>byte</code> to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    // 写出一个字节。参数|b|的低|8|位，|b|的高|24|位被忽略
    public synchronized void write(int b) throws IOException {
        out.write(b);
        incCount(1);
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array
     * starting at offset <code>off</code> to the underlying output stream.
     * If no exception is thrown, the counter <code>written</code> is
     * incremented by <code>len</code>.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    // 将字节数组|b[off:off+len]|写入到底层流中。如果|len|为零，方法将立即返回
    // 注：内部会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    public synchronized void write(byte b[], int off, int len)
        throws IOException
    {
        out.write(b, off, len);
        incCount(len);
    }

    /**
     * Flushes this data output stream. This forces any buffered output
     * bytes to be written out to the stream.
     * <p>
     * The <code>flush</code> method of <code>DataOutputStream</code>
     * calls the <code>flush</code> method of its underlying output stream.
     *
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     * @see        java.io.OutputStream#flush()
     */
    // 调用底层流的刷新接口
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * Writes a <code>boolean</code> to the underlying output stream as
     * a 1-byte value. The value <code>true</code> is written out as the
     * value <code>(byte)1</code>; the value <code>false</code> is
     * written out as the value <code>(byte)0</code>. If no exception is
     * thrown, the counter <code>written</code> is incremented by
     * <code>1</code>.
     *
     * @param      v   a <code>boolean</code> value to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    // 将|bool|作为一个字节写入到底层输出流
    // 注：|true|被转换成|1|，|false|被转换成|0|
    public final void writeBoolean(boolean v) throws IOException {
        out.write(v ? 1 : 0);
        incCount(1);
    }

    /**
     * Writes out a <code>byte</code> to the underlying output stream as
     * a 1-byte value. If no exception is thrown, the counter
     * <code>written</code> is incremented by <code>1</code>.
     *
     * @param      v   a <code>byte</code> value to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    // 将|byte|作为一个字节写入到底层输出流
    // 注：参数|b|的低|8|位，|b|的高|24|位被忽略
    public final void writeByte(int v) throws IOException {
        out.write(v);
        incCount(1);
    }

    /**
     * Writes a <code>short</code> to the underlying output stream as two
     * bytes, high byte first. If no exception is thrown, the counter
     * <code>written</code> is incremented by <code>2</code>.
     *
     * @param      v   a <code>short</code> to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    // 将|short|作为两个字节写入到底层输出流。大端序
    public final void writeShort(int v) throws IOException {
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 0) & 0xFF);
        incCount(2);
    }

    /**
     * Writes a <code>char</code> to the underlying output stream as a
     * 2-byte value, high byte first. If no exception is thrown, the
     * counter <code>written</code> is incremented by <code>2</code>.
     *
     * @param      v   a <code>char</code> value to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    // 将|char|作为两个字节写入到底层输出流。大端序
    public final void writeChar(int v) throws IOException {
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 0) & 0xFF);
        incCount(2);
    }

    /**
     * Writes an <code>int</code> to the underlying output stream as four
     * bytes, high byte first. If no exception is thrown, the counter
     * <code>written</code> is incremented by <code>4</code>.
     *
     * @param      v   an <code>int</code> to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    // 将|int|作为四个字节写入到底层输出流。大端序
    public final void writeInt(int v) throws IOException {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>>  8) & 0xFF);
        out.write((v >>>  0) & 0xFF);
        incCount(4);
    }

    private byte writeBuffer[] = new byte[8];

    /**
     * Writes a <code>long</code> to the underlying output stream as eight
     * bytes, high byte first. In no exception is thrown, the counter
     * <code>written</code> is incremented by <code>8</code>.
     *
     * @param      v   a <code>long</code> to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    // 将|long|作为八个字节写入到底层输出流。大端序
    public final void writeLong(long v) throws IOException {
        writeBuffer[0] = (byte)(v >>> 56);
        writeBuffer[1] = (byte)(v >>> 48);
        writeBuffer[2] = (byte)(v >>> 40);
        writeBuffer[3] = (byte)(v >>> 32);
        writeBuffer[4] = (byte)(v >>> 24);
        writeBuffer[5] = (byte)(v >>> 16);
        writeBuffer[6] = (byte)(v >>>  8);
        writeBuffer[7] = (byte)(v >>>  0);
        out.write(writeBuffer, 0, 8);
        incCount(8);
    }

    /**
     * Converts the float argument to an <code>int</code> using the
     * <code>floatToIntBits</code> method in class <code>Float</code>,
     * and then writes that <code>int</code> value to the underlying
     * output stream as a 4-byte quantity, high byte first. If no
     * exception is thrown, the counter <code>written</code> is
     * incremented by <code>4</code>.
     *
     * @param      v   a <code>float</code> value to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     * @see        java.lang.Float#floatToIntBits(float)
     */
    // 将|float|作为四个字节写入到底层输出流
    public final void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    /**
     * Converts the double argument to a <code>long</code> using the
     * <code>doubleToLongBits</code> method in class <code>Double</code>,
     * and then writes that <code>long</code> value to the underlying
     * output stream as an 8-byte quantity, high byte first. If no
     * exception is thrown, the counter <code>written</code> is
     * incremented by <code>8</code>.
     *
     * @param      v   a <code>double</code> value to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     * @see        java.lang.Double#doubleToLongBits(double)
     */
    // 将|double|作为八个字节写入到底层输出流
    public final void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    /**
     * Writes out the string to the underlying output stream as a
     * sequence of bytes. Each character in the string is written out, in
     * sequence, by discarding its high eight bits. If no exception is
     * thrown, the counter <code>written</code> is incremented by the
     * length of <code>s</code>.
     *
     * @param      s   a string of bytes to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    // 将|unicode|字符串作为字节序列写入底层输出流。按顺序写出字符串中的每个字符，其高八位被丢弃
    // 注：处理带有汉字的字符串时，该方法会导致数据丢失。请使用|writeChars()|方法代替
    public final void writeBytes(String s) throws IOException {
        int len = s.length();
        for (int i = 0 ; i < len ; i++) {
            out.write((byte)s.charAt(i));
        }
        incCount(len);
    }

    /**
     * Writes a string to the underlying output stream as a sequence of
     * characters. Each character is written to the data output stream as
     * if by the <code>writeChar</code> method. If no exception is
     * thrown, the counter <code>written</code> is incremented by twice
     * the length of <code>s</code>.
     *
     * @param      s   a <code>String</code> value to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.DataOutputStream#writeChar(int)
     * @see        java.io.FilterOutputStream#out
     */
    // 将|unicode|字符串作为字符序列写入底层输出流。按顺序写出字符串中的每个字符
    public final void writeChars(String s) throws IOException {
        int len = s.length();
        for (int i = 0 ; i < len ; i++) {
            int v = s.charAt(i);
            out.write((v >>> 8) & 0xFF);
            out.write((v >>> 0) & 0xFF);
        }
        incCount(len * 2);
    }

    /**
     * Writes a string to the underlying output stream using
     * <a href="DataInput.html#modified-utf-8">modified UTF-8</a>
     * encoding in a machine-independent manner.
     * <p>
     * First, two bytes are written to the output stream as if by the
     * <code>writeShort</code> method giving the number of bytes to
     * follow. This value is the number of bytes actually written out,
     * not the length of the string. Following the length, each character
     * of the string is output, in sequence, using the modified UTF-8 encoding
     * for the character. If no exception is thrown, the counter
     * <code>written</code> is incremented by the total number of
     * bytes written to the output stream. This will be at least two
     * plus the length of <code>str</code>, and at most two plus
     * thrice the length of <code>str</code>.
     *
     * @param      str   a string to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    // 将|unicode|字符串转换成修改版的|utf-8|字符串，写出到指定的输出流中。修改版|utf-8|字符串
    // 是指在|utf-8|字符串头部增加两个字节存储长度的字符串。编码为：|length + utf-8|
    public final void writeUTF(String str) throws IOException {
        writeUTF(str, this);
    }

    /**
     * Writes a string to the specified DataOutput using
     * <a href="DataInput.html#modified-utf-8">modified UTF-8</a>
     * encoding in a machine-independent manner.
     * <p>
     * First, two bytes are written to out as if by the <code>writeShort</code>
     * method giving the number of bytes to follow. This value is the number of
     * bytes actually written out, not the length of the string. Following the
     * length, each character of the string is output, in sequence, using the
     * modified UTF-8 encoding for the character. If no exception is thrown, the
     * counter <code>written</code> is incremented by the total number of
     * bytes written to the output stream. This will be at least two
     * plus the length of <code>str</code>, and at most two plus
     * thrice the length of <code>str</code>.
     *
     * @param      str   a string to be written.
     * @param      out   destination to write to
     * @return     The number of bytes written out.
     * @exception  IOException  if an I/O error occurs.
     */
    // 将|unicode|字符串转化成修改版的|utf-8|字符串，写出到指定的输出流中。修改版|utf-8|字符串
    // 是指在|utf-8|字符串头部增加两个字节存储长度的字符串。编码为：|length + utf-8|
    // 注：将平台无关的|unicode|字符转换成一个具体的|unicode|编码格式（修改版的|utf-8|）
    // 注：修改版|utf-8|可以将多个|utf-8|字符串整合成一个|utf-8|字符串
    static int writeUTF(String str, DataOutput out) throws IOException {
        int strlen = str.length();
        int utflen = 0;
        int c, count = 0;

        /* use charAt instead of copying String to char array */
        // 计算将|unicode|字符串转换成|utf-8|字符串占用内存的大小
        for (int i = 0; i < strlen; i++) {
            c = str.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                utflen++;
            } else if (c > 0x07FF) {
                utflen += 3;
            } else {
                utflen += 2;
            }
        }

        if (utflen > 65535)
            throw new UTFDataFormatException(
                "encoded string too long: " + utflen + " bytes");

        // 获取将|unicode|字符串转换成|utf-8|字符串使用的缓冲区字节数组
        byte[] bytearr = null;
        if (out instanceof DataOutputStream) {
            DataOutputStream dos = (DataOutputStream)out;
            if(dos.bytearr == null || (dos.bytearr.length < (utflen+2)))
                dos.bytearr = new byte[(utflen*2) + 2];
            bytearr = dos.bytearr;
        } else {
            bytearr = new byte[utflen+2];
        }

        // 大端序写出|utf-8|字符串的占用内存大小
        bytearr[count++] = (byte) ((utflen >>> 8) & 0xFF);
        bytearr[count++] = (byte) ((utflen >>> 0) & 0xFF);

        // 将|unicode|字符串中单字节的字符直接写出
        int i=0;
        for (i=0; i<strlen; i++) {
           c = str.charAt(i);
           if (!((c >= 0x0001) && (c <= 0x007F))) break;
           bytearr[count++] = (byte) c;
        }

        // 逐个将|unicode|字符编码成|utf-8|字符
        for (;i < strlen; i++) {
            c = str.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {   // 单字节
                /* 0xxxxxxx*/
                bytearr[count++] = (byte) c;

            } else if (c > 0x07FF) {    // 三字节
                /* 1110 xxxx  10xx xxxx  10xx xxxx */
                bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                bytearr[count++] = (byte) (0x80 | ((c >>  6) & 0x3F));
                bytearr[count++] = (byte) (0x80 | ((c >>  0) & 0x3F));
            } else {    // 双字节
                /* 110x xxxx   10xx xxxx*/
                bytearr[count++] = (byte) (0xC0 | ((c >>  6) & 0x1F));
                bytearr[count++] = (byte) (0x80 | ((c >>  0) & 0x3F));
            }
        }
        out.write(bytearr, 0, utflen+2);
        return utflen + 2;
    }

    /**
     * Returns the current value of the counter <code>written</code>,
     * the number of bytes written to this data output stream so far.
     * If the counter overflows, it will be wrapped to Integer.MAX_VALUE.
     *
     * @return  the value of the <code>written</code> field.
     * @see     java.io.DataOutputStream#written
     */
    public final int size() {
        return written;
    }
}
