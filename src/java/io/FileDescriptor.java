/*
 * Copyright (c) 1995, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

/**
 * Instances of the file descriptor class serve as an opaque handle
 * to the underlying machine-specific structure representing an open
 * file, an open socket, or another source or sink of bytes. The
 * main practical use for a file descriptor is to create a
 * <code>FileInputStream</code> or <code>FileOutputStream</code> to
 * contain it.
 * <p>
 * Applications should not create their own file descriptors.
 *
 * @author  Pavani Diwanji
 * @see     java.io.FileInputStream
 * @see     java.io.FileOutputStream
 * @since   JDK1.0
 */
// 虚拟机中用来对文件定位的实体。其内部|fd|字段，是操作系统的文件描述符（句柄）。它可以是一个打开的
// 普通|file|或者|socket|或者|pipe|字符设备。主要用在|FileInputStream, FileOutputStream|
// 中，你不应该自己创建|FileDescriptor|实例，因为系统得文件描述符由流对象|native|关联
// 注：对|fd|的操作，在|FD|的所有者|native|中进行。比如：|FileInputStream.open0()|
// 注：所有共享同一个|FD|的流对象中有一个被|GC|（或关闭）时，会触发所有流对象释放其文件描述符资源
public final class FileDescriptor {
    // 映射到操作系统中的文件描述符
    // 注：对它的赋值，通常在|native|中进行。比如：|FileInputStream.open0()|
    private int fd;

    // 当前|FD|被绑定的流对象。比如，|FileInputStream, FileOutputStream|
    // 注：只有一个绑定流时，它会被存储到|parent|中；若有多个流对象共享此|FD|，所有流对象会被存储
    // 到|otherParents|列表中
    // 注：可实现，共享此|FD|的所有流对象中有一个被关闭时，会触发所有流对象释放其文件描述符资源
    private Closeable parent;
    private List<Closeable> otherParents;
    private boolean closed;

    /**
     * Constructs an (invalid) FileDescriptor
     * object.
     */
    public /**/ FileDescriptor() {
        fd = -1;
    }

    private /* */ FileDescriptor(int fd) {
        this.fd = fd;
    }

    /**
     * A handle to the standard input stream. Usually, this file
     * descriptor is not used directly, but rather via the input stream
     * known as <code>System.in</code>.
     *
     * @see     java.lang.System#in
     */
    public static final FileDescriptor in = new FileDescriptor(0);

    /**
     * A handle to the standard output stream. Usually, this file
     * descriptor is not used directly, but rather via the output stream
     * known as <code>System.out</code>.
     * @see     java.lang.System#out
     */
    public static final FileDescriptor out = new FileDescriptor(1);

    /**
     * A handle to the standard error stream. Usually, this file
     * descriptor is not used directly, but rather via the output stream
     * known as <code>System.err</code>.
     *
     * @see     java.lang.System#err
     */
    public static final FileDescriptor err = new FileDescriptor(2);

    /**
     * Tests if this file descriptor object is valid.
     *
     * @return  <code>true</code> if the file descriptor object represents a
     *          valid, open file, socket, or other active I/O connection;
     *          <code>false</code> otherwise.
     */
    public boolean valid() {
        return fd != -1;
    }

    /**
     * Force all system buffers to synchronize with the underlying
     * device.  This method returns after all modified data and
     * attributes of this FileDescriptor have been written to the
     * relevant device(s).  In particular, if this FileDescriptor
     * refers to a physical storage medium, such as a file in a file
     * system, sync will not return until all in-memory modified copies
     * of buffers associated with this FileDescriptor have been
     * written to the physical medium.
     *
     * sync is meant to be used by code that requires physical
     * storage (such as a file) to be in a known state  For
     * example, a class that provided a simple transaction facility
     * might use sync to ensure that all changes to a file caused
     * by a given transaction were recorded on a storage medium.
     *
     * sync only affects buffers downstream of this FileDescriptor.  If
     * any in-memory buffering is being done by the application (for
     * example, by a BufferedOutputStream object), those buffers must
     * be flushed into the FileDescriptor (for example, by invoking
     * OutputStream.flush) before that data will be affected by sync.
     *
     * @exception SyncFailedException
     *        Thrown when the buffers cannot be flushed,
     *        or because the system cannot guarantee that all the
     *        buffers have been synchronized with physical media.
     * @since     JDK1.1
     */
    // 强制系统缓冲区的数据与底层设备同步。此方法会等待|FileDescriptor|的所有修改数据和属性写入相
    // 关设备后返回。特别是，如果此|FileDescriptor|指的是物理存储介质，例如文件系统中的文件，则直
    // 到与此|FileDescriptor|关联的缓冲区的所有内存中修改副本都已写入物理介质后，才会返回
    // 注：同步仅影响|FileDescriptor|下游的缓冲区（即，与系统相关缓冲区）。如果应用程序正在执行任
    // 何内存缓存（例如|BufferedOutputStream|），则必须将这些缓冲区刷新到|FileDescriptor|（例
    // 如，通过调用|OutputStream.flush()|），然后才能同步这些数据
    public native void sync() throws SyncFailedException;

    /* This routine initializes JNI field offsets for the class */
    // 用于初始化|FileDescriptor.fd|字段偏移，之后可根据该偏移和|FileDescriptor|实例获取|fd|字段的引用
    private static native void initIDs();

    static {
        initIDs();
    }

    // Set up JavaIOFileDescriptorAccess in SharedSecrets
    static {
        sun.misc.SharedSecrets.setJavaIOFileDescriptorAccess(
            new sun.misc.JavaIOFileDescriptorAccess() {
                public void set(FileDescriptor obj, int fd) {
                    obj.fd = fd;
                }

                public int get(FileDescriptor obj) {
                    return obj.fd;
                }

                public void setHandle(FileDescriptor obj, long handle) {
                    throw new UnsupportedOperationException();
                }

                public long getHandle(FileDescriptor obj) {
                    throw new UnsupportedOperationException();
                }
            }
        );
    }

    /*
     * Package private methods to track referents.
     * If multiple streams point to the same FileDescriptor, we cycle
     * through the list of all referents and call close()
     */

    /**
     * Attach a Closeable to this FD for tracking.
     * parent reference is added to otherParents when
     * needed to make closeAll simpler.
     */
    // 将一个流绑定到此|FD|上，用于跟踪
    // 注：可实现，共享此|FD|的所有流对象中有一个被关闭时，会触发所有流对象释放其文件描述符资源
    synchronized void attach(Closeable c) {
        if (parent == null) {
            // first caller gets to do this
            parent = c;
        } else if (otherParents == null) {
            otherParents = new ArrayList<>();
            otherParents.add(parent);
            otherParents.add(c);
        } else {
            otherParents.add(c);
        }
    }

    /**
     * Cycle through all Closeables sharing this FD and call
     * close() on each one.
     *
     * The caller closeable gets to call close0().
     */
    // 遍历共享此|FD|的所有流对象，并对每个流对象调用|close0()|
    @SuppressWarnings("try")
    synchronized void closeAll(Closeable releaser) throws IOException {
        // 此|closed|状态位，非线程安全。不过流对象的|close0()|方法一般都是可重入的，即第一个关闭
        // 后，|fd|就会被设置成-1，重复调用直接跳过
        if (!closed) {
            closed = true;
            IOException ioe = null;
            try (Closeable c = releaser) {
                if (otherParents != null) {
                    // 遍历共享此|FD|的所有流对象，并对每个流对象调用|close0()|
                    // 注：没有对|parent|单独处理。原因：若|otherParents|有值，说明|parent|已
                    // 经被复制到|otherParents|中；若仅存在|parent|，调用|c.close0()|即可
                    for (Closeable referent : otherParents) {
                        try {
                            referent.close();
                        } catch(IOException x) {
                            if (ioe == null) {
                                ioe = x;
                            } else {
                                ioe.addSuppressed(x);
                            }
                        }
                    }
                }
            } catch(IOException ex) {
                /*
                 * If releaser close() throws IOException
                 * add other exceptions as suppressed.
                 */
                if (ioe != null)
                    ex.addSuppressed(ioe);
                ioe = ex;
            } finally {
                if (ioe != null)
                    throw ioe;
            }
        }
    }
}
