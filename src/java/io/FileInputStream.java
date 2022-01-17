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
 * A <code>FileInputStream</code> obtains input bytes
 * from a file in a file system. What files
 * are  available depends on the host environment.
 *
 * <p><code>FileInputStream</code> is meant for reading streams of raw bytes
 * such as image data. For reading streams of characters, consider using
 * <code>FileReader</code>.
 *
 * @author  Arthur van Hoff
 * @see     java.io.File
 * @see     java.io.FileDescriptor
 * @see     java.io.FileOutputStream
 * @see     java.nio.file.Files#newInputStream
 * @since   JDK1.0
 */
// 文件的输入流（读取）。是一个字节流、节点流
// 注：不提供"标记/重置"的支持（回退特性）；直接|IO|支持，底层的|flush()|为空操作
// 注：字节流，即以|8bit|（|1byte=8bit|）作为一个数据单元。数据流中最小的数据单元是字节
// 注：根据是否直接处理数据，|IO|分为节点流和处理流。节点流是真正直接处理数据的；处理流是装饰加工节点流的
public
class FileInputStream extends InputStream
{
    /* File Descriptor - handle to the open file */
    // 一个|FileDescriptor|类型的对象，它是|Java|虚拟机用来对文件定位的
    // 注：其内部|FileDescriptor.fd|字段，就是操作系统中的文件描述符
    private final FileDescriptor fd;

    /**
     * The path of the referenced file
     * (null if the stream is created with a file descriptor)
     */
    // 文件的路径
    private final String path;

    private FileChannel channel = null;

    private final Object closeLock = new Object();
    private volatile boolean closed = false;

    /**
     * Creates a <code>FileInputStream</code> by
     * opening a connection to an actual file,
     * the file named by the path name <code>name</code>
     * in the file system.  A new <code>FileDescriptor</code>
     * object is created to represent this file
     * connection.
     * <p>
     * First, if there is a security
     * manager, its <code>checkRead</code> method
     * is called with the <code>name</code> argument
     * as its argument.
     * <p>
     * If the named file does not exist, is a directory rather than a regular
     * file, or for some other reason cannot be opened for reading then a
     * <code>FileNotFoundException</code> is thrown.
     *
     * @param      name   the system-dependent file name.
     * @exception  FileNotFoundException  if the file does not exist,
     *                   is a directory rather than a regular file,
     *                   or for some other reason cannot be opened for
     *                   reading.
     * @exception  SecurityException      if a security manager exists and its
     *               <code>checkRead</code> method denies read access
     *               to the file.
     * @see        java.lang.SecurityManager#checkRead(java.lang.String)
     */
    // 基于文件路径，创建一个文件的输入（读取）流对象
    // 注：内部会立即打开该文件，并创建一个|FileDescriptor|对象来表示与此文件的连接
    // 注：如果指定的文件不存在、或是一个目录、或由于某些原因无法打开，则抛出|FileNotFoundException|
    public FileInputStream(String name) throws FileNotFoundException {
        this(name != null ? new File(name) : null);
    }

    /**
     * Creates a <code>FileInputStream</code> by
     * opening a connection to an actual file,
     * the file named by the <code>File</code>
     * object <code>file</code> in the file system.
     * A new <code>FileDescriptor</code> object
     * is created to represent this file connection.
     * <p>
     * First, if there is a security manager,
     * its <code>checkRead</code> method  is called
     * with the path represented by the <code>file</code>
     * argument as its argument.
     * <p>
     * If the named file does not exist, is a directory rather than a regular
     * file, or for some other reason cannot be opened for reading then a
     * <code>FileNotFoundException</code> is thrown.
     *
     * @param      file   the file to be opened for reading.
     * @exception  FileNotFoundException  if the file does not exist,
     *                   is a directory rather than a regular file,
     *                   or for some other reason cannot be opened for
     *                   reading.
     * @exception  SecurityException      if a security manager exists and its
     *               <code>checkRead</code> method denies read access to the file.
     * @see        java.io.File#getPath()
     * @see        java.lang.SecurityManager#checkRead(java.lang.String)
     */
    // 基于文件对象，创建一个文件的输入（读取）流对象
    // 注：内部会立即打开该文件，并创建一个|FileDescriptor|对象来表示与此文件的连接
    // 注：如果指定的文件不存在、或是一个目录、或由于某些原因无法打开，则抛出|FileNotFoundException|
    public FileInputStream(File file) throws FileNotFoundException {
        // 获取文件路径
        String name = (file != null ? file.getPath() : null);
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkRead(name);
        }
        if (name == null) {
            throw new NullPointerException();
        }
        if (file.isInvalid()) {
            throw new FileNotFoundException("Invalid file path");
        }

        // 创建一个|FileDescriptor|对象来表示与此文件的连接
        fd = new FileDescriptor();

        // 将当前流对象绑定到该文件描述符上
        // 注：所有共享同一个|FD|的流对象中有一个被关闭时，会触发所有流对象释放其文件描述符资源
        fd.attach(this);
        path = name;

        // 只读方式打开|name|路径的文件
        // 注：底层使用|open64(path, O_RDONLY, 0666)|打开文件，并将描述符设置到|FileInputStream.fd.fd|
        open(name);
    }

    /**
     * Creates a <code>FileInputStream</code> by using the file descriptor
     * <code>fdObj</code>, which represents an existing connection to an
     * actual file in the file system.
     * <p>
     * If there is a security manager, its <code>checkRead</code> method is
     * called with the file descriptor <code>fdObj</code> as its argument to
     * see if it's ok to read the file descriptor. If read access is denied
     * to the file descriptor a <code>SecurityException</code> is thrown.
     * <p>
     * If <code>fdObj</code> is null then a <code>NullPointerException</code>
     * is thrown.
     * <p>
     * This constructor does not throw an exception if <code>fdObj</code>
     * is {@link java.io.FileDescriptor#valid() invalid}.
     * However, if the methods are invoked on the resulting stream to attempt
     * I/O on the stream, an <code>IOException</code> is thrown.
     *
     * @param      fdObj   the file descriptor to be opened for reading.
     * @throws     SecurityException      if a security manager exists and its
     *                 <code>checkRead</code> method denies read access to the
     *                 file descriptor.
     * @see        SecurityManager#checkRead(java.io.FileDescriptor)
     */
    // 使用文件描述符|fdObj|创建一个文件输入流，该文件描述符必须已经被打开
    // 注：所有共享同一个|FD|的流对象中有一个被关闭时，会触发所有流对象释放其文件描述符资源
    public FileInputStream(FileDescriptor fdObj) {
        SecurityManager security = System.getSecurityManager();
        if (fdObj == null) {
            throw new NullPointerException();
        }
        if (security != null) {
            security.checkRead(fdObj);
        }
        fd = fdObj;
        path = null;

        /*
         * FileDescriptor is being shared by streams.
         * Register this stream with FileDescriptor tracker.
         */
        // 将当前流对象绑定到该文件描述符上
        fd.attach(this);
    }

    /**
     * Opens the specified file for reading.
     * @param name the name of the file
     */
    // 核心：使用|open64(path, O_RDONLY, 0666)|打开文件，将描述符设置到|FileInputStream.fd.fd|
    // 注：如果指定的文件不存在、或是一个目录、或由于某些原因无法打开，则抛出|FileNotFoundException|
    private native void open0(String name) throws FileNotFoundException;

    // wrap native call to allow instrumentation
    /**
     * Opens the specified file for reading.
     * @param name the name of the file
     */
    // 只读方式打开|name|路径的文件
    // 注：如果指定的文件不存在、或是一个目录、或由于某些原因无法打开，则抛出|FileNotFoundException|
    private void open(String name) throws FileNotFoundException {
        open0(name);
    }

    /**
     * Reads a byte of data from this input stream. This method blocks
     * if no input is yet available.
     *
     * @return     the next byte of data, or <code>-1</code> if the end of the
     *             file is reached.
     * @exception  IOException  if an I/O error occurs.
     */
    // 从输入流中读取下一个字节的数据，以|0~255|范围内的|int|值形式返回。如果已到达流末尾而没有
    // 可用字节，则返回值|-1|。此方法会阻塞，直到输入数据可用、检测到流结束（关闭）或抛出异常为止
    public int read() throws IOException {
        return read0();
    }

    // 从输入流中读取下一个字节的数据，以|0~255|范围内的|int|值形式返回。如果已到达流末尾而没有
    // 可用字节，则返回值|-1|。此方法会阻塞，直到输入数据可用、检测到流结束（关闭）或抛出异常为止
    // 注：一个有效的单字节数据被转换成整型后不可能为|-1|，除非|read()|方法主动返回|-1|
    // 核心：使用|read(fd, buf, 1)|读取文件|FileInputStream.fd.fd|的数据，返回实际读取的
    // 字节数；若为|-1|，则表示已经读取到流末尾
    private native int read0() throws IOException;

    /**
     * Reads a subarray as a sequence of bytes.
     * @param b the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes that are written
     * @exception IOException If an I/O error has occurred.
     */
    // 从输入流中读取最多|len|个字节的数据到一个字节数组|b[off:off+len]|中。如果|len|不为零，
    // 则该方法将阻塞，直到输入可用；如果|len|为零，方法将立即返回零
    // 注：底层会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    // 核心：使用|read(fd, buf, len)|读取文件|FileInputStream.fd.fd|的数据，读取的数据将
    // 被拷贝到|b[off:off+len]|中。返回实际读取的字节数；若为|-1|，则表示已经读取到流末尾
    private native int readBytes(byte b[], int off, int len) throws IOException;

    /**
     * Reads up to <code>b.length</code> bytes of data from this input
     * stream into an array of bytes. This method blocks until some input
     * is available.
     *
     * @param      b   the buffer into which the data is read.
     * @return     the total number of bytes read into the buffer, or
     *             <code>-1</code> if there is no more data because the end of
     *             the file has been reached.
     * @exception  IOException  if an I/O error occurs.
     */
    // 从输入流中读取最多|b.length|个字节的数据到一个字节数组|b|中。此方法会阻塞，直到输入可用
    // 注：返回实际读取的字节数；若为|-1|，则表示已经读取到流末尾
    // 注：可能会抛出|IOException|的场景有：除文件结尾以外的任何原因而导致第一个字节也无法读取、
    // 或者当前流已关闭、或者有|I/O|错误
    public int read(byte b[]) throws IOException {
        return readBytes(b, 0, b.length);
    }

    /**
     * Reads up to <code>len</code> bytes of data from this input stream
     * into an array of bytes. If <code>len</code> is not zero, the method
     * blocks until some input is available; otherwise, no
     * bytes are read and <code>0</code> is returned.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in the destination array <code>b</code>
     * @param      len   the maximum number of bytes read.
     * @return     the total number of bytes read into the buffer, or
     *             <code>-1</code> if there is no more data because the end of
     *             the file has been reached.
     * @exception  NullPointerException If <code>b</code> is <code>null</code>.
     * @exception  IndexOutOfBoundsException If <code>off</code> is negative,
     * <code>len</code> is negative, or <code>len</code> is greater than
     * <code>b.length - off</code>
     * @exception  IOException  if an I/O error occurs.
     */
    // 从输入流中读取最多|len|个字节的数据到一个字节数组|b[off:off+len]|中。如果|len|不为零，
    // 则该方法将阻塞，直到输入可用；如果|len|为零，方法将立即返回零
    // 注：底层会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    // 注：返回实际读取的字节数；若为|-1|，则表示已经读取到流末尾
    public int read(byte b[], int off, int len) throws IOException {
        return readBytes(b, off, len);
    }

    /**
     * Skips over and discards <code>n</code> bytes of data from the
     * input stream.
     *
     * <p>The <code>skip</code> method may, for a variety of
     * reasons, end up skipping over some smaller number of bytes,
     * possibly <code>0</code>. If <code>n</code> is negative, the method
     * will try to skip backwards. In case the backing file does not support
     * backward skip at its current position, an <code>IOException</code> is
     * thrown. The actual number of bytes skipped is returned. If it skips
     * forwards, it returns a positive value. If it skips backwards, it
     * returns a negative value.
     *
     * <p>This method may skip more bytes than what are remaining in the
     * backing file. This produces no exception and the number of bytes skipped
     * may include some number of bytes that were beyond the EOF of the
     * backing file. Attempting to read from the stream after skipping past
     * the end will result in -1 indicating the end of the file.
     *
     * @param      n   the number of bytes to be skipped.
     * @return     the actual number of bytes skipped.
     * @exception  IOException  if n is negative, if the stream does not
     *             support seek, or if an I/O error occurs.
     */
    // 从输入流中跳过并丢弃|n|个字节的数据。如果|n|为负，则该方法将尝试向后偏移，如果文件在其当
    // 前位置不支持向后偏移，则会抛出|IOException|；|n|可以为零。 返回实际跳过的字节数。如果
    // 向前跳过，则返回正值；如果向后跳过，则返回负值
    // 注：此方法可能跳过比当前文件中剩余的字节更多的字节。这不会产生任何异常，在跳过|EOF|后，尝
    // 试从流中读取将返回|-1|，以指示文件结尾
    // 核心：使用|lseek64()|设置文件|FileInputStream.fd.fd|的偏移量
    public native long skip(long n) throws IOException;

    /**
     * Returns an estimate of the number of remaining bytes that can be read (or
     * skipped over) from this input stream without blocking by the next
     * invocation of a method for this input stream. Returns 0 when the file
     * position is beyond EOF. The next invocation might be the same thread
     * or another thread. A single read or skip of this many bytes will not
     * block, but may read or skip fewer bytes.
     *
     * <p> In some cases, a non-blocking read (or skip) may appear to be
     * blocked when it is merely slow, for example when reading large
     * files over slow networks.
     *
     * @return     an estimate of the number of remaining bytes that can be read
     *             (or skipped over) from this input stream without blocking.
     * @exception  IOException  if this file input stream has been closed by calling
     *             {@code close} or an I/O error occurs.
     */
    // 返回可以从此输入流读取或跳过的剩余字节数的估计值，而不会使下一次读取或跳过这么多字节时被阻塞
    // 核心：区分|FileInputStream.fd.fd|的类型，分别做如下处理：
    // 1.如果是常规的文件，则使用|fstat64()|总字节数，减去|lseek64()|偏移量
    // 2.如果是|socket,pipe|等文件，则使用|ioctl|接受缓冲区总字节数，减去|lseek64()|偏移量
    // 注：系统调用|ioctl(fd, FIONREAD, &n)|可以得到描述符|fd|的缓冲区里有多少字节要被读取
    public native int available() throws IOException;

    /**
     * Closes this file input stream and releases any system resources
     * associated with the stream.
     *
     * <p> If this stream has an associated channel then the channel is closed
     * as well.
     *
     * @exception  IOException  if an I/O error occurs.
     *
     * @revised 1.4
     * @spec JSR-51
     */
    // 关闭此文件输入流并释放与该流关联的所有系统资源。如果此流具有关联的通道，则该通道也将关闭
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

        // 关闭此文件输入流，会触发释放与该流关联的所有系统资源
        fd.closeAll(new Closeable() {
            public void close() throws IOException {
               close0();
           }
        });
    }

    /**
     * Returns the <code>FileDescriptor</code>
     * object  that represents the connection to
     * the actual file in the file system being
     * used by this <code>FileInputStream</code>.
     *
     * @return     the file descriptor object associated with this stream.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FileDescriptor
     */
    public final FileDescriptor getFD() throws IOException {
        if (fd != null) {
            return fd;
        }
        throw new IOException();
    }

    /**
     * Returns the unique {@link java.nio.channels.FileChannel FileChannel}
     * object associated with this file input stream.
     *
     * <p> The initial {@link java.nio.channels.FileChannel#position()
     * position} of the returned channel will be equal to the
     * number of bytes read from the file so far.  Reading bytes from this
     * stream will increment the channel's position.  Changing the channel's
     * position, either explicitly or by reading, will change this stream's
     * file position.
     *
     * @return  the file channel associated with this file input stream
     *
     * @since 1.4
     * @spec JSR-51
     */
    // 获取一个通道，使客户端能够用通道的方式操作当前的文件
    // 注：开发此接口，主要是为了将文件的读写也融入|NIO|体系
    public FileChannel getChannel() {
        synchronized (this) {
            if (channel == null) {
                channel = FileChannelImpl.open(fd, path, true, false, this);
            }
            return channel;
        }
    }

    // 用于初始化|FileInputStream.fd|字段偏移量。之后可根据该偏移量和|FileInputStream|实例获取|fd|字段的引用
    private static native void initIDs();

    // 关闭文件描述符，释放相应资源。可重入
    // 核心：使用|close(fd)|关闭文件，并设置|FileInputStream.fd.fd=-1|
    // 注：底层不会关闭|STDIN,STDOUT,STDERR|几个标准描述符。当需要执行关闭他们时，会将其重定位到|/dev/null|中
    // 注：关闭|STDIN,STDOUT,STDERR|几个标准描述符不是一个好习惯。因为它们可能被很多第三方组件当作标准描述符在使用，
    // 若把它们关闭，而后又被复用到非标准设备上，这十分危险
    private native void close0() throws IOException;

    static {
        initIDs();
    }

    /**
     * Ensures that the <code>close</code> method of this file input stream is
     * called when there are no more references to it.
     *
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FileInputStream#close()
     */
    // 确保引用在|GC|时，调用此文件输入流的|close|方法以回收资源
    protected void finalize() throws IOException {
        if ((fd != null) &&  (fd != FileDescriptor.in)) {
            /* if fd is shared, the references in FileDescriptor
             * will ensure that finalizer is only called when
             * safe to do so. All references using the fd have
             * become unreachable. We can call close()
             */
            close();
        }
    }
}
