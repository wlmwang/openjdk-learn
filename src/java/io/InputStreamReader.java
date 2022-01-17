/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import sun.nio.cs.StreamDecoder;


/**
 * An InputStreamReader is a bridge from byte streams to character streams: It
 * reads bytes and decodes them into characters using a specified {@link
 * java.nio.charset.Charset charset}.  The charset that it uses
 * may be specified by name or may be given explicitly, or the platform's
 * default charset may be accepted.
 *
 * <p> Each invocation of one of an InputStreamReader's read() methods may
 * cause one or more bytes to be read from the underlying byte-input stream.
 * To enable the efficient conversion of bytes to characters, more bytes may
 * be read ahead from the underlying stream than are necessary to satisfy the
 * current read operation.
 *
 * <p> For top efficiency, consider wrapping an InputStreamReader within a
 * BufferedReader.  For example:
 *
 * <pre>
 * BufferedReader in
 *   = new BufferedReader(new InputStreamReader(System.in));
 * </pre>
 *
 * @see BufferedReader
 * @see InputStream
 * @see java.nio.charset.Charset
 *
 * @author      Mark Reinhold
 * @since       JDK1.1
 */

// 装饰一个字节输入流，使其对外提供可指定具体编码方案的字符型输入流
//
// 1.在|JVM|中，字符都是以|unicode|形式表示。即，直接使用它们在字符集中的编号。只有当需要从|JVM|内部
// 移动到外部时，比如保存为文件、输出到终端、传送至远程，才需要使用具体的编码方案
// 2.原理：输入时，将文件字节流按指定编码进行解码成字符串读入到|JVM|中；输入时，将|JVM|字符串按指定编码
// 进行编码成字节流写入到文件中。所以|InputStreamReader/OutputStreamReader|都需要指定编码
// 3.注意：在|JVM|中，字符的|unicode|是按字符（双字节）进行|UTF-16 BE|编码的。这就导致它和通常按字节
// 编码|UTF-16 BE|的字符，必然是不同！
public class InputStreamReader extends Reader {

    // 字节流与字符间的编解码器
    private final StreamDecoder sd;

    /**
     * Creates an InputStreamReader that uses the default charset.
     *
     * @param  in   An InputStream
     */
    // 基于字节流，创建一个字符型输入流。使用默认编码规则
    public InputStreamReader(InputStream in) {
        super(in);
        try {
            sd = StreamDecoder.forInputStreamReader(in, this, (String)null); // ## check lock object
        } catch (UnsupportedEncodingException e) {
            // The default encoding should always be available
            throw new Error(e);
        }
    }

    /**
     * Creates an InputStreamReader that uses the named charset.
     *
     * @param  in
     *         An InputStream
     *
     * @param  charsetName
     *         The name of a supported
     *         {@link java.nio.charset.Charset charset}
     *
     * @exception  UnsupportedEncodingException
     *             If the named charset is not supported
     */
    // 基于字节流，创建一个字符型输入流。使用指定编码的规则
    public InputStreamReader(InputStream in, String charsetName)
        throws UnsupportedEncodingException
    {
        super(in);
        if (charsetName == null)
            throw new NullPointerException("charsetName");
        sd = StreamDecoder.forInputStreamReader(in, this, charsetName);
    }

    /**
     * Creates an InputStreamReader that uses the given charset.
     *
     * @param  in       An InputStream
     * @param  cs       A charset
     *
     * @since 1.4
     * @spec JSR-51
     */
    // 基于字节流，创建一个字符型输入流。使用指定编码的规则
    public InputStreamReader(InputStream in, Charset cs) {
        super(in);
        if (cs == null)
            throw new NullPointerException("charset");
        sd = StreamDecoder.forInputStreamReader(in, this, cs);
    }

    /**
     * Creates an InputStreamReader that uses the given charset decoder.
     *
     * @param  in       An InputStream
     * @param  dec      A charset decoder
     *
     * @since 1.4
     * @spec JSR-51
     */
    // 基于字节流，创建一个字符型输入流。使用指定编解码器
    public InputStreamReader(InputStream in, CharsetDecoder dec) {
        super(in);
        if (dec == null)
            throw new NullPointerException("charset decoder");
        sd = StreamDecoder.forInputStreamReader(in, this, dec);
    }

    /**
     * Returns the name of the character encoding being used by this stream.
     *
     * <p> If the encoding has an historical name then that name is returned;
     * otherwise the encoding's canonical name is returned.
     *
     * <p> If this instance was created with the {@link
     * #InputStreamReader(InputStream, String)} constructor then the returned
     * name, being unique for the encoding, may differ from the name passed to
     * the constructor. This method will return <code>null</code> if the
     * stream has been closed.
     * </p>
     * @return The historical name of this encoding, or
     *         <code>null</code> if the stream has been closed
     *
     * @see java.nio.charset.Charset
     *
     * @revised 1.4
     * @spec JSR-51
     */
    public String getEncoding() {
        return sd.getEncoding();
    }

    /**
     * Reads a single character.
     *
     * @return The character read, or -1 if the end of the stream has been
     *         reached
     *
     * @exception  IOException  If an I/O error occurs
     */
    // 从输入流中读取下一个字符的数据，以|0~65535|范围内的|int|值形式返回。如果已到达流末尾而没有
    // 可用字节，则返回值|-1|。此方法会阻塞，直到输入数据可用、检测到流结束（关闭）或抛出异常为止
    public int read() throws IOException {
        return sd.read();
    }

    /**
     * Reads characters into a portion of an array.
     *
     * @param      cbuf     Destination buffer
     * @param      offset   Offset at which to start storing characters
     * @param      length   Maximum number of characters to read
     *
     * @return     The number of characters read, or -1 if the end of the
     *             stream has been reached
     *
     * @exception  IOException  If an I/O error occurs
     */
    public int read(char cbuf[], int offset, int length) throws IOException {
        return sd.read(cbuf, offset, length);
    }

    /**
     * Tells whether this stream is ready to be read.  An InputStreamReader is
     * ready if its input buffer is not empty, or if bytes are available to be
     * read from the underlying byte stream.
     *
     * @exception  IOException  If an I/O error occurs
     */
    public boolean ready() throws IOException {
        return sd.ready();
    }

    public void close() throws IOException {
        sd.close();
    }
}
