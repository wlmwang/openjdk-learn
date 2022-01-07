/*
 * Copyright (c) 1996, 2001, Oracle and/or its affiliates. All rights reserved.
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
 * Convenience class for writing character files.  The constructors of this
 * class assume that the default character encoding and the default byte-buffer
 * size are acceptable.  To specify these values yourself, construct an
 * OutputStreamWriter on a FileOutputStream.
 *
 * <p>Whether or not a file is available or may be created depends upon the
 * underlying platform.  Some platforms, in particular, allow a file to be
 * opened for writing by only one <tt>FileWriter</tt> (or other file-writing
 * object) at a time.  In such situations the constructors in this class
 * will fail if the file involved is already open.
 *
 * <p><code>FileWriter</code> is meant for writing streams of characters.
 * For writing streams of raw bytes, consider using a
 * <code>FileOutputStream</code>.
 *
 * @see OutputStreamWriter
 * @see FileOutputStream
 *
 * @author      Mark Reinhold
 * @since       JDK1.1
 */

// 文件的输出流（写入）。是一个字符流、节点流。其超类实现了流的写入及各类编码方案的转换
// 注：字符流，即以|16bit|（|1char=16bit|）作为一个数据单元。数据流中最小的文本单元是字符
// 注：根据是否直接处理数据，|IO|分为节点流和处理流。节点流是真正直接处理数据的；处理流是装饰加工节点流的
// 注：
// 1.在|Java|中（主要包括在|JVM|、内存、代码里声明的每一个|char/String|类型的变量），字符只以一种形式
// 存在，那就是|unicode|。不选择任何特定的编码，直接使用它们在字符集中的编号，这是统一的唯一的方法
// 2.在|JVM|内部，统一使用|unicode|表示字符，当需要从|JVM|内部移动到外部时（比如：保存为文件、输出到终
// 端），就需要使用具体的编码方案。因此也可以说，所有的编码转换只发生在输入/输出边界的地方
public class FileWriter extends OutputStreamWriter {

    /**
     * Constructs a FileWriter object given a file name.
     *
     * @param fileName  String The system-dependent filename.
     * @throws IOException  if the named file exists but is a directory rather
     *                  than a regular file, does not exist but cannot be
     *                  created, or cannot be opened for any other reason
     */
    public FileWriter(String fileName) throws IOException {
        super(new FileOutputStream(fileName));
    }

    /**
     * Constructs a FileWriter object given a file name with a boolean
     * indicating whether or not to append the data written.
     *
     * @param fileName  String The system-dependent filename.
     * @param append    boolean if <code>true</code>, then data will be written
     *                  to the end of the file rather than the beginning.
     * @throws IOException  if the named file exists but is a directory rather
     *                  than a regular file, does not exist but cannot be
     *                  created, or cannot be opened for any other reason
     */
    public FileWriter(String fileName, boolean append) throws IOException {
        super(new FileOutputStream(fileName, append));
    }

    /**
     * Constructs a FileWriter object given a File object.
     *
     * @param file  a File object to write to.
     * @throws IOException  if the file exists but is a directory rather than
     *                  a regular file, does not exist but cannot be created,
     *                  or cannot be opened for any other reason
     */
    public FileWriter(File file) throws IOException {
        super(new FileOutputStream(file));
    }

    /**
     * Constructs a FileWriter object given a File object. If the second
     * argument is <code>true</code>, then bytes will be written to the end
     * of the file rather than the beginning.
     *
     * @param file  a File object to write to
     * @param     append    if <code>true</code>, then bytes will be written
     *                      to the end of the file rather than the beginning
     * @throws IOException  if the file exists but is a directory rather than
     *                  a regular file, does not exist but cannot be created,
     *                  or cannot be opened for any other reason
     * @since 1.4
     */
    public FileWriter(File file, boolean append) throws IOException {
        super(new FileOutputStream(file, append));
    }

    /**
     * Constructs a FileWriter object associated with a file descriptor.
     *
     * @param fd  FileDescriptor object to write to.
     */
    public FileWriter(FileDescriptor fd) {
        super(new FileOutputStream(fd));
    }

}
