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

import java.nio.channels.FileChannel;
import sun.nio.ch.FileChannelImpl;


/**
 * A file output stream is an output stream for writing data to a
 * <code>File</code> or to a <code>FileDescriptor</code>. Whether or not
 * a file is available or may be created depends upon the underlying
 * platform.  Some platforms, in particular, allow a file to be opened
 * for writing by only one <tt>FileOutputStream</tt> (or other
 * file-writing object) at a time.  In such situations the constructors in
 * this class will fail if the file involved is already open.
 *
 * <p><code>FileOutputStream</code> is meant for writing streams of raw bytes
 * such as image data. For writing streams of characters, consider using
 * <code>FileWriter</code>.
 *
 * @author  Arthur van Hoff
 * @see     java.io.File
 * @see     java.io.FileDescriptor
 * @see     java.io.FileInputStream
 * @see     java.nio.file.Files#newOutputStream
 * @since   JDK1.0
 */
// 文件的输出流（写入）。是一个字节流、节点流
// 注：直接|IO|支持，内部的|flush()|为空操作
// 注：字节流，即以|8|位（|1byte=8bit|）作为一个数据单元。数据流中最小的数据单元是字节
// 注：根据是否直接处理数据，|IO|分为节点流和处理流。节点流是真正直接处理数据的；处理流是装饰加工节点流的
public
class FileOutputStream extends OutputStream
{
    /**
     * The system dependent file descriptor.
     */
    // 一个|FileDescriptor|类型的对象，它是|Java|虚拟机用来对文件定位的
    // 注：其内部|FileDescriptor.fd|字段，就是操作系统中的文件描述符
    private final FileDescriptor fd;

    /**
     * True if the file is opened for append.
     */
    // 用于控制字符是否以追加方式写入文件
    private final boolean append;

    /**
     * The associated channel, initialized lazily.
     */
    private FileChannel channel;

    /**
     * The path of the referenced file
     * (null if the stream is created with a file descriptor)
     */
    // 文件的路径
    private final String path;

    private final Object closeLock = new Object();
    private volatile boolean closed = false;

    /**
     * Creates a file output stream to write to the file with the
     * specified name. A new <code>FileDescriptor</code> object is
     * created to represent this file connection.
     * <p>
     * First, if there is a security manager, its <code>checkWrite</code>
     * method is called with <code>name</code> as its argument.
     * <p>
     * If the file exists but is a directory rather than a regular file, does
     * not exist but cannot be created, or cannot be opened for any other
     * reason then a <code>FileNotFoundException</code> is thrown.
     *
     * @param      name   the system-dependent filename
     * @exception  FileNotFoundException  if the file exists but is a directory
     *                   rather than a regular file, does not exist but cannot
     *                   be created, or cannot be opened for any other reason
     * @exception  SecurityException  if a security manager exists and its
     *               <code>checkWrite</code> method denies write access
     *               to the file.
     * @see        java.lang.SecurityManager#checkWrite(java.lang.String)
     */
    // 基于文件路径，创建一个文件的输出（写入）流对象。设置|append|参数为|false|
    // 注：内部会立即打开该文件，并创建一个|FileDescriptor|对象来表示与此文件的连接
    // 注：如果指定路径是一个目录、或不存在但无法创建、或由于某些原因无法打开，则抛出|FileNotFoundException|
    public FileOutputStream(String name) throws FileNotFoundException {
        this(name != null ? new File(name) : null, false);
    }

    /**
     * Creates a file output stream to write to the file with the specified
     * name.  If the second argument is <code>true</code>, then
     * bytes will be written to the end of the file rather than the beginning.
     * A new <code>FileDescriptor</code> object is created to represent this
     * file connection.
     * <p>
     * First, if there is a security manager, its <code>checkWrite</code>
     * method is called with <code>name</code> as its argument.
     * <p>
     * If the file exists but is a directory rather than a regular file, does
     * not exist but cannot be created, or cannot be opened for any other
     * reason then a <code>FileNotFoundException</code> is thrown.
     *
     * @param     name        the system-dependent file name
     * @param     append      if <code>true</code>, then bytes will be written
     *                   to the end of the file rather than the beginning
     * @exception  FileNotFoundException  if the file exists but is a directory
     *                   rather than a regular file, does not exist but cannot
     *                   be created, or cannot be opened for any other reason.
     * @exception  SecurityException  if a security manager exists and its
     *               <code>checkWrite</code> method denies write access
     *               to the file.
     * @see        java.lang.SecurityManager#checkWrite(java.lang.String)
     * @since     JDK1.1
     */
    // 基于文件路径，创建一个文件的输出（写入）流对象。用于控制内容追加|append|参数由外部指定
    // 注：内部会立即打开该文件，并创建一个|FileDescriptor|对象来表示与此文件的连接
    // 注：如果指定路径是一个目录、或不存在但无法创建、或由于某些原因无法打开，则抛出|FileNotFoundException|
    public FileOutputStream(String name, boolean append)
        throws FileNotFoundException
    {
        this(name != null ? new File(name) : null, append);
    }

    /**
     * Creates a file output stream to write to the file represented by
     * the specified <code>File</code> object. A new
     * <code>FileDescriptor</code> object is created to represent this
     * file connection.
     * <p>
     * First, if there is a security manager, its <code>checkWrite</code>
     * method is called with the path represented by the <code>file</code>
     * argument as its argument.
     * <p>
     * If the file exists but is a directory rather than a regular file, does
     * not exist but cannot be created, or cannot be opened for any other
     * reason then a <code>FileNotFoundException</code> is thrown.
     *
     * @param      file               the file to be opened for writing.
     * @exception  FileNotFoundException  if the file exists but is a directory
     *                   rather than a regular file, does not exist but cannot
     *                   be created, or cannot be opened for any other reason
     * @exception  SecurityException  if a security manager exists and its
     *               <code>checkWrite</code> method denies write access
     *               to the file.
     * @see        java.io.File#getPath()
     * @see        java.lang.SecurityException
     * @see        java.lang.SecurityManager#checkWrite(java.lang.String)
     */
    // 基于文件对象，创建一个文件的输出（写入）流对象。设置|append|参数为|false|
    // 注：内部会立即打开该文件，并创建一个|FileDescriptor|对象来表示与此文件的连接
    // 注：如果指定路径是一个目录、或不存在但无法创建、或由于某些原因无法打开，则抛出|FileNotFoundException|
    public FileOutputStream(File file) throws FileNotFoundException {
        this(file, false);
    }

    /**
     * Creates a file output stream to write to the file represented by
     * the specified <code>File</code> object. If the second argument is
     * <code>true</code>, then bytes will be written to the end of the file
     * rather than the beginning. A new <code>FileDescriptor</code> object is
     * created to represent this file connection.
     * <p>
     * First, if there is a security manager, its <code>checkWrite</code>
     * method is called with the path represented by the <code>file</code>
     * argument as its argument.
     * <p>
     * If the file exists but is a directory rather than a regular file, does
     * not exist but cannot be created, or cannot be opened for any other
     * reason then a <code>FileNotFoundException</code> is thrown.
     *
     * @param      file               the file to be opened for writing.
     * @param     append      if <code>true</code>, then bytes will be written
     *                   to the end of the file rather than the beginning
     * @exception  FileNotFoundException  if the file exists but is a directory
     *                   rather than a regular file, does not exist but cannot
     *                   be created, or cannot be opened for any other reason
     * @exception  SecurityException  if a security manager exists and its
     *               <code>checkWrite</code> method denies write access
     *               to the file.
     * @see        java.io.File#getPath()
     * @see        java.lang.SecurityException
     * @see        java.lang.SecurityManager#checkWrite(java.lang.String)
     * @since 1.4
     */
    // 基于文件对象，创建一个文件的输出（写入）流对象。用于控制内容追加|append|参数由外部指定
    // 注：内部会立即打开该文件，并创建一个|FileDescriptor|对象来表示与此文件的连接
    // 注：如果指定路径是一个目录、或不存在但无法创建、或由于某些原因无法打开，则抛出|FileNotFoundException|
    public FileOutputStream(File file, boolean append)
        throws FileNotFoundException
    {
        // 获取文件路径
        String name = (file != null ? file.getPath() : null);
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkWrite(name);
        }
        if (name == null) {
            throw new NullPointerException();
        }
        if (file.isInvalid()) {
            throw new FileNotFoundException("Invalid file path");
        }

        // 创建一个|FileDescriptor|对象来表示与此文件的连接
        this.fd = new FileDescriptor();

        // 将当前流对象绑定到该文件描述符上
        // 注：所有共享同一个|FD|的流对象中有一个被关闭时，会触发所有流对象释放其文件描述符资源
        fd.attach(this);
        this.append = append;
        this.path = name;

        // 只写方式打开|name|路径的文件
        // 注：底层使用|open64(name, O_WRONLY|O_CREAT|(append ?O_APPEND :O_TRUNC), 0666)|打开文
        // 件，并将描述符设置到|FileInputStream.fd.fd|。是否清空原始文件内容取决于|append|参数
        open(name, append);
    }

    /**
     * Creates a file output stream to write to the specified file
     * descriptor, which represents an existing connection to an actual
     * file in the file system.
     * <p>
     * First, if there is a security manager, its <code>checkWrite</code>
     * method is called with the file descriptor <code>fdObj</code>
     * argument as its argument.
     * <p>
     * If <code>fdObj</code> is null then a <code>NullPointerException</code>
     * is thrown.
     * <p>
     * This constructor does not throw an exception if <code>fdObj</code>
     * is {@link java.io.FileDescriptor#valid() invalid}.
     * However, if the methods are invoked on the resulting stream to attempt
     * I/O on the stream, an <code>IOException</code> is thrown.
     *
     * @param      fdObj   the file descriptor to be opened for writing
     * @exception  SecurityException  if a security manager exists and its
     *               <code>checkWrite</code> method denies
     *               write access to the file descriptor
     * @see        java.lang.SecurityManager#checkWrite(java.io.FileDescriptor)
     */
    // 使用文件描述符|fdObj|创建一个文件输出流，该文件描述符必须已经被打开
    // 注：所有共享同一个|FD|的流对象中有一个被关闭时，会触发所有流对象释放其文件描述符资源
    public FileOutputStream(FileDescriptor fdObj) {
        SecurityManager security = System.getSecurityManager();
        if (fdObj == null) {
            throw new NullPointerException();
        }
        if (security != null) {
            security.checkWrite(fdObj);
        }
        this.fd = fdObj;
        this.append = false;
        this.path = null;

        // 将当前流对象绑定到该文件描述符上
        // 注：所有共享同一个|FD|的流对象中有一个被关闭时，会触发所有流对象释放其文件描述符资源
        fd.attach(this);
    }

    /**
     * Opens a file, with the specified name, for overwriting or appending.
     * @param name name of file to be opened
     * @param append whether the file is to be opened in append mode
     */
    // 核心：使用|open64(name, O_WRONLY|O_CREAT|(append ?O_APPEND :O_TRUNC), 0666)|打开文件，将描
    // 述符设置到|FileInputStream.fd.fd|。是否清空原始文件内容取决于|append|参数
    // 注：如果指定路径是一个目录、或不存在但无法创建、或由于某些原因无法打开，则抛出|FileNotFoundException|
    private native void open0(String name, boolean append)
        throws FileNotFoundException;

    // wrap native call to allow instrumentation
    /**
     * Opens a file, with the specified name, for overwriting or appending.
     * @param name name of file to be opened
     * @param append whether the file is to be opened in append mode
     */
    // 只写方式打开|name|路径的文件
    // 注：如果指定路径是一个目录、或不存在但无法创建、或由于某些原因无法打开，则抛出|FileNotFoundException|
    private void open(String name, boolean append)
        throws FileNotFoundException {
        open0(name, append);
    }

    /**
     * Writes the specified byte to this file output stream.
     *
     * @param   b   the byte to be written.
     * @param   append   {@code true} if the write operation first
     *     advances the position to the end of file
     */
    // 将指定的字节写入输出流。要写入的字节是参数|b|的低|8|位，|b|的高|24|位被忽略。如果发
    // 生|I/O|错误，特别是，如果输出流已关闭，则可能会抛出|IOException|
    // 注：类型|byte|的范围是|-128~127|不能覆盖|ASCII|码表
    // 核心：使用|write(fd, (byte)b, 1)|将数据写入|FileOutputStream.fd.fd|的文件
    private native void write(int b, boolean append) throws IOException;

    /**
     * Writes the specified byte to this file output stream. Implements
     * the <code>write</code> method of <code>OutputStream</code>.
     *
     * @param      b   the byte to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    // 将指定的字节写入输出流。要写入的字节是参数|b|的低|8|位，|b|的高|24|位被忽略。如果发
    // 生|I/O|错误，特别是，如果输出流已关闭，则可能会抛出|IOException|
    // 注：不使用类型|byte|，其的范围是|-128~127|不能覆盖|ASCII|码表
    public void write(int b) throws IOException {
        write(b, append);
    }

    /**
     * Writes a sub array as a sequence of bytes.
     * @param b the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes that are written
     * @param append {@code true} to first advance the position to the
     *     end of file
     * @exception IOException If an I/O error has occurred.
     */
    // 将字节数组|b[off:off+len]|写入到输出流中。如果|len|不为零，则该方法将阻塞，直到输出可用；如果|len|为
    // 零，方法将立即返回
    // 注：底层会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    // 核心：使用|write(fd, b+off, len)|将数据写入|FileOutputStream.fd.fd|的文件
    private native void writeBytes(byte b[], int off, int len, boolean append)
        throws IOException;

    /**
     * Writes <code>b.length</code> bytes from the specified byte array
     * to this file output stream.
     *
     * @param      b   the data.
     * @exception  IOException  if an I/O error occurs.
     */
    // 将字节数组|b|写入到输出流。此方法会阻塞，直到输入可用
    public void write(byte b[]) throws IOException {
        writeBytes(b, 0, b.length, append);
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array
     * starting at offset <code>off</code> to this file output stream.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @exception  IOException  if an I/O error occurs.
     */
    // 将字节数组|b[off:off+len]|写入到输出流中。如果|len|不为零，则该方法将阻塞，直到输出可用；如果|len|为
    // 零，方法将立即返回
    // 注：底层会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    public void write(byte b[], int off, int len) throws IOException {
        writeBytes(b, off, len, append);
    }

    /**
     * Closes this file output stream and releases any system resources
     * associated with this stream. This file output stream may no longer
     * be used for writing bytes.
     *
     * <p> If this stream has an associated channel then the channel is closed
     * as well.
     *
     * @exception  IOException  if an I/O error occurs.
     *
     * @revised 1.4
     * @spec JSR-51
     */
    // 关闭此文件输出流并释放与该流关联的所有系统资源。如果此流具有关联的通道，则该通道也将关闭
    public void close() throws IOException {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
        }

        // 如果此流具有关联的通道，则该通道也将关闭
        if (channel != null) {
            channel.close();
        }

        // 关闭此文件输出流，会触发释放与该流关联的所有系统资源
        fd.closeAll(new Closeable() {
            public void close() throws IOException {
               close0();
           }
        });
    }

    /**
     * Returns the file descriptor associated with this stream.
     *
     * @return  the <code>FileDescriptor</code> object that represents
     *          the connection to the file in the file system being used
     *          by this <code>FileOutputStream</code> object.
     *
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FileDescriptor
     */
     public final FileDescriptor getFD()  throws IOException {
        if (fd != null) {
            return fd;
        }
        throw new IOException();
     }

    /**
     * Returns the unique {@link java.nio.channels.FileChannel FileChannel}
     * object associated with this file output stream.
     *
     * <p> The initial {@link java.nio.channels.FileChannel#position()
     * position} of the returned channel will be equal to the
     * number of bytes written to the file so far unless this stream is in
     * append mode, in which case it will be equal to the size of the file.
     * Writing bytes to this stream will increment the channel's position
     * accordingly.  Changing the channel's position, either explicitly or by
     * writing, will change this stream's file position.
     *
     * @return  the file channel associated with this file output stream
     *
     * @since 1.4
     * @spec JSR-51
     */
    public FileChannel getChannel() {
        synchronized (this) {
            if (channel == null) {
                channel = FileChannelImpl.open(fd, path, false, true, append, this);
            }
            return channel;
        }
    }

    /**
     * Cleans up the connection to the file, and ensures that the
     * <code>close</code> method of this file output stream is
     * called when there are no more references to this stream.
     *
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FileInputStream#close()
     */
    // 确保引用在|GC|时，调用此文件输出流的|close|方法以回收资源
    protected void finalize() throws IOException {
        if (fd != null) {
            if (fd == FileDescriptor.out || fd == FileDescriptor.err) {
                flush();
            } else {
                /* if fd is shared, the references in FileDescriptor
                 * will ensure that finalizer is only called when
                 * safe to do so. All references using the fd have
                 * become unreachable. We can call close()
                 */
                close();
            }
        }
    }

    // 关闭文件描述符，释放相应资源。可重入
    // 核心：使用|close(fd)|关闭文件，并设置|FileOutputStream.fd.fd=-1|
    // 注：底层不会关闭|STDIN,STDOUT,STDERR|几个标准描述符。当需要执行关闭他们时，会将其重定位到|/dev/null|中
    // 注：关闭|STDIN,STDOUT,STDERR|几个标准描述符不是一个好习惯。因为它们可能被很多第三方组件当作标准描述符在使用，
    // 若把它们关闭，而后又被复用到非标准设备上，这十分危险
    private native void close0() throws IOException;

    // 用于初始化|FileOutputStream.fd|字段偏移量。之后可根据该偏移量和|FileOutputStream|实例获取|fd|字段的引用
    private static native void initIDs();

    static {
        initIDs();
    }

}
