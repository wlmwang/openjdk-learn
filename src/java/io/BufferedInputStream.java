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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A <code>BufferedInputStream</code> adds
 * functionality to another input stream-namely,
 * the ability to buffer the input and to
 * support the <code>mark</code> and <code>reset</code>
 * methods. When  the <code>BufferedInputStream</code>
 * is created, an internal buffer array is
 * created. As bytes  from the stream are read
 * or skipped, the internal buffer is refilled
 * as necessary  from the contained input stream,
 * many bytes at a time. The <code>mark</code>
 * operation  remembers a point in the input
 * stream and the <code>reset</code> operation
 * causes all the  bytes read since the most
 * recent <code>mark</code> operation to be
 * reread before new bytes are  taken from
 * the contained input stream.
 *
 * @author  Arthur van Hoff
 * @since   JDK1.0
 */
// 带缓冲区的输入流（读取）。是一个字节流、处理流。主要用于装饰底层流来增加缓存特性。  线程安全
// 亮点：通常情况下，缓冲区是不会自动扩容的。但若设置的"标记上限|marklimit|"大于提供的缓冲区内存，则在
// 缓冲区被用完时，会触发自动扩容，扩容的上限为|marklimit|大小。 因此|marklimit|不能设置过大
// 注：提供"标记/重置"的支持（预读特性。即读取后，可回退）；关闭时，自动关闭底层输入流
// 注：字节流，即以|8bit|（|1byte=8bit|）作为一个数据单元。数据流中最小的数据单元是字节
// 注：根据是否直接处理数据，|IO|分为节点流和处理流。节点流是真正直接处理数据的；处理流是装饰加工节点流的
public
class BufferedInputStream extends FilterInputStream {
    // 缓冲区默认大小。也是默认的最大回退（预读）字节长度的上限
    private static int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * The maximum size of array to allocate.
     * Some VMs reserve some header words in an array.
     * Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     */
    // 缓冲区内存的上限
    // 注：数组对象对比普通的类有一个额外的元数据，用于表示数组的大小。数组的最大尺寸为|2^31|，但
    // 是需要|8|字节的空间存储数组的长度等元数据，所以数组最大为|Integer.MAX_VALUE-8|
    private static int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

    /**
     * The internal buffer array where the data is stored. When necessary,
     * it may be replaced by another array of
     * a different size.
     */
    // 缓冲区的字节数组
    // 注：通常情况下，缓冲区是不会自动扩容的。但若设置的"标记上限|marklimit|"大于现有的缓冲区内存，
    // 则在缓冲区被用完时，会触发自动扩容，扩容的上限为|marklimit|大小
    protected volatile byte buf[];

    /**
     * Atomic updater to provide compareAndSet for buf. This is
     * necessary because closes can be asynchronous. We use nullness
     * of buf[] as primary indicator that this stream is closed. (The
     * "in" field is also nulled out on close.)
     */
    // 字段|buf|的包装器，使其对外可以提供原子操作接口
    // 注：一个基于反射的工具类，它能对指定类的、指定|volatile|限定的字段进行原子更新。同时这个字段
    // 不能是|private|的、也不能是非引用类型的
    private static final
        AtomicReferenceFieldUpdater<BufferedInputStream, byte[]> bufUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (BufferedInputStream.class,  byte[].class, "buf");

    /**
     * The index one greater than the index of the last valid byte in
     * the buffer.
     * This value is always
     * in the range <code>0</code> through <code>buf.length</code>;
     * elements <code>buf[0]</code>  through <code>buf[count-1]
     * </code>contain buffered input data obtained
     * from the underlying  input stream.
     */
    // 从底层流读取数据写入到缓冲区的尾部索引
    // 注：缓冲区中|buf[0:count]|数据，是从代理的底层的流获得的输入数据
    protected int count;

    /**
     * The current position in the buffer. This is the index of the next
     * character to be read from the <code>buf</code> array.
     * <p>
     * This value is always in the range <code>0</code>
     * through <code>count</code>. If it is less
     * than <code>count</code>, then  <code>buf[pos]</code>
     * is the next byte to be supplied as input;
     * if it is equal to <code>count</code>, then
     * the  next <code>read</code> or <code>skip</code>
     * operation will require more bytes to be
     * read from the contained  input stream.
     *
     * @see     java.io.BufferedInputStream#buf
     */
    // 缓冲区当前读取索引。即，要从|buf|数组读取的下一个字符的索引。是一个非负值，且不会超过|count|
    // 注：|buf[pos]|是下一个读取的字节
    protected int pos;

    /**
     * The value of the <code>pos</code> field at the time the last
     * <code>mark</code> method was called.
     * <p>
     * This value is always
     * in the range <code>-1</code> through <code>pos</code>.
     * If there is no marked position in  the input
     * stream, this field is <code>-1</code>. If
     * there is a marked position in the input
     * stream,  then <code>buf[markpos]</code>
     * is the first byte to be supplied as input
     * after a <code>reset</code> operation. If
     * <code>markpos</code> is not <code>-1</code>,
     * then all bytes from positions <code>buf[markpos]</code>
     * through  <code>buf[pos-1]</code> must remain
     * in the buffer array (though they may be
     * moved to  another place in the buffer array,
     * with suitable adjustments to the values
     * of <code>count</code>,  <code>pos</code>,
     * and <code>markpos</code>); they may not
     * be discarded unless and until the difference
     * between <code>pos</code> and <code>markpos</code>
     * exceeds <code>marklimit</code>.
     *
     * @see     java.io.BufferedInputStream#mark(int)
     * @see     java.io.BufferedInputStream#pos
     */
    // 最后一个调用标记方法|mark()|时|pos|字段的值。该值始终在|-1~pos|的范围内。如果输入流中没
    // 有标记位置，则该字段为|-1|；如果输入流中有标记位置，则|buf[markpos]|是重置操作后要作为输
    // 入提供的第一个字节
    // 注：如果|markpos|不是|-1|，那么从位置|buf[markpos]|到|buf[pos-1]|的所有字节都必须保
    // 留在缓冲区中。除非，从标记开始后，从底层流中读取了超过|marklimit|数据，而一直又没有使用过
    // 重置功能，此时标记将被丢弃（即，缓冲区中最多存储已读的数据不能超过|marklimit|字节）
    // 注：可以通过|mark()|在缓冲区的一个位置进行标记，再使用|reset()|重置到该偏移位置。支撑预读
    protected int markpos = -1;

    /**
     * The maximum read ahead allowed after a call to the
     * <code>mark</code> method before subsequent calls to the
     * <code>reset</code> method fail.
     * Whenever the difference between <code>pos</code>
     * and <code>markpos</code> exceeds <code>marklimit</code>,
     * then the  mark may be dropped by setting
     * <code>markpos</code> to <code>-1</code>.
     *
     * @see     java.io.BufferedInputStream#mark(int)
     * @see     java.io.BufferedInputStream#reset()
     */
    // 允许的最大回退（预读）字节长度的限制。即，缓冲区中最多存储已读的数据不能超过|marklimit|字节
    // 注：预读，顾名思义，读取数据后，你可以回退再次读取该数据
    protected int marklimit;

    /**
     * Check to make sure that underlying input stream has not been
     * nulled out due to close; if not return it;
     */
    // 检查底层流有没有因关闭而被置空
    private InputStream getInIfOpen() throws IOException {
        InputStream input = in;
        if (input == null)
            throw new IOException("Stream closed");
        return input;
    }

    /**
     * Check to make sure that buffer has not been nulled out due to
     * close; if not return it;
     */
    // 检查确保缓冲区有没有因关闭而被置空
    private byte[] getBufIfOpen() throws IOException {
        byte[] buffer = buf;
        if (buffer == null)
            throw new IOException("Stream closed");
        return buffer;
    }

    /**
     * Creates a <code>BufferedInputStream</code>
     * and saves its  argument, the input stream
     * <code>in</code>, for later use. An internal
     * buffer array is created and  stored in <code>buf</code>.
     *
     * @param   in   the underlying input stream.
     */
    // 基于一个输入流，创建一个带缓冲区特性的输入（读取）流对象。缓冲区默认大小为|8k|
    public BufferedInputStream(InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Creates a <code>BufferedInputStream</code>
     * with the specified buffer size,
     * and saves its  argument, the input stream
     * <code>in</code>, for later use.  An internal
     * buffer array of length  <code>size</code>
     * is created and stored in <code>buf</code>.
     *
     * @param   in     the underlying input stream.
     * @param   size   the buffer size.
     * @exception IllegalArgumentException if {@code size <= 0}.
     */
    // 基于一个输入流，创建一个带缓冲区特性的输入（读取）流对象。缓冲区默认大小为|size|
    public BufferedInputStream(InputStream in, int size) {
        super(in);
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        // 立即分配该缓冲区的内存
        buf = new byte[size];
    }

    /**
     * Fills the buffer with more data, taking into account
     * shuffling and other tricks for dealing with marks.
     * Assumes that it is being called by a synchronized method.
     * This method also assumes that all data has already been read in,
     * hence pos > count.
     */
    // 从底层流中执行读取数据操作（|I/O|读取），将数据放入缓冲区中。读取数据的最大值为缓冲区剩余内存
    // 注：只有在缓冲区中的数据被|read()|读取完毕后，才会触发调用此|fill()|方法
    private void fill() throws IOException {
        // 检查、获取缓冲区字节数组
        byte[] buffer = getBufIfOpen();

        // |buf[0:pos-1]|的所有字节为缓冲区中已被用户读取的数据。|buf[pos]|为下一个读取字节
        // |buf[pos:count-1]|的所有字节为缓冲区中从底层流已读取、但未被用户读取的数据
        // |buf[count:length-1]|的所有字节为缓冲区中可存储底层流读取数据的剩余内存
        // |buf[markpos:pos-1]|的所有字节为已被用户读取、并且被用户标记的数据

        if (markpos < 0)
            // 1.无效的"标记"索引，也就没有了"回退"的要求，直接清空所有已读数据
            pos = 0;            /* no mark: throw away the buffer */
        else if (pos >= buffer.length)  /* no room left in buffer */
            // 2.有效的"标记"索引；且缓冲区的读取索引已推进到尾部，即再没有内存来存放接下来读取的数据

            if (markpos > 0) {  /* can throw away early part of the buffer */
                // a.设置过一个非零的"标记"索引

                // 将"起始"索引到"标记"索引之间的数据清空，即将"标记"索引设置为|0|
                // 注：移动缓冲区，并保留从"标记"索引到"尾部"索引之间的数据，以便之后"重置、回退"的操作
                int sz = pos - markpos;
                System.arraycopy(buffer, markpos, buffer, 0, sz);
                pos = sz;
                markpos = 0;
            } else if (buffer.length >= marklimit) {
                // b."标记"索引之前已被自动重置为零值；且缓冲区长度大于"标记"上限（用于确保当前缓冲区可以
                // 支持该"标记"上限）

                // 将"标记"索引设置为无效值。即，清除此前的标记状态
                // 注：程序能执行到此处分支，说明缓冲区在被设置"标记"索引后、在缓冲区被读满数据前（缓冲区可
                // 以支持该"标记"上限），都未曾使用过"回退"功能，此时将"标记"丢弃。即，缓冲区中最多存储已读
                // 的数据不能超过|marklimit|字节
                markpos = -1;   /* buffer got too big, invalidate mark */
                pos = 0;        /* drop buffer contents */
            } else if (buffer.length >= MAX_BUFFER_SIZE) {
                // 达到了缓冲区的上限，立刻抛出异常
                throw new OutOfMemoryError("Required array size too large");
            } else {            /* grow buffer */
                // c."标记"索引之前被自动重置为零值；且缓冲区长度小于"标记"索引的上限

                // 缓冲区自动扩容为原来容量的|2|倍
                // 注：此处的扩容，主要目的是使当前流可以支持将"标记"索引设置到上限
                int nsz = (pos <= MAX_BUFFER_SIZE - pos) ?
                        pos * 2 : MAX_BUFFER_SIZE;

                // 缓冲区最大的容量无需超过"标记"索引的上限
                if (nsz > marklimit)
                    nsz = marklimit;

                // 将原始缓冲区的数据拷贝至扩容后的新的缓冲区中
                byte nbuf[] = new byte[nsz];
                System.arraycopy(buffer, 0, nbuf, 0, pos);

                // 原子的将原始缓冲区引用替换为扩容后的新的缓冲区引用地址
                if (!bufUpdater.compareAndSet(this, buffer, nbuf)) {
                    // Can't replace buf if there was an async close.
                    // Note: This would need to be changed if fill()
                    // is ever made accessible to multiple threads.
                    // But for now, the only way CAS can fail is via close.
                    // assert buf == null;
                    throw new IOException("Stream closed");
                }
                buffer = nbuf;
            }
        // 3.有效的"标记"索引；且缓冲区的读取索引还未推进到尾部，即还有剩余内存来存放接下来读取的数据
        // 不处理当前读取索引|pos|、不处理"标记"索引|markpos|、缓冲区也不会自动扩容

        // 当前缓冲区中已有的数据
        // 注：只有在缓冲区中的数据被读取完后，才会调用|fill()|方法，所以此时|count==pos|
        count = pos;

        // 从被代理的底层的流中执行读取数据操作，将数据放入缓冲区中
        int n = getInIfOpen().read(buffer, pos, buffer.length - pos);

        // 将当前缓冲区中已读取数据的偏移增加|n|长度
        if (n > 0)
            count = n + pos;
    }

    /**
     * See
     * the general contract of the <code>read</code>
     * method of <code>InputStream</code>.
     *
     * @return     the next byte of data, or <code>-1</code> if the end of the
     *             stream is reached.
     * @exception  IOException  if this input stream has been closed by
     *                          invoking its {@link #close()} method,
     *                          or an I/O error occurs.
     * @see        java.io.FilterInputStream#in
     */
    // 从输入流中读取下一个字节的数据，以|0~255|范围内的|int|值形式返回。如果已到达流末尾而没有
    // 可用字节，则返回值|-1|。此方法可能会阻塞
    public synchronized int read() throws IOException {
        if (pos >= count) {
            fill();
            if (pos >= count)
                return -1;
        }
        return getBufIfOpen()[pos++] & 0xff;
    }

    /**
     * Read characters into a portion of an array, reading from the underlying
     * stream at most once if necessary.
     */
    // 将数据读入到数组|b[off:off+len|中，如有必要，将从底层流中读取一次（可能会被阻塞）
    private int read1(byte[] b, int off, int len) throws IOException {
        // 缓冲区剩余的可读字节
        int avail = count - pos;

        // 无剩余字节
        if (avail <= 0) {
            /* If the requested length is at least as large as the buffer, and
               if there is no mark/reset activity, do not bother to copy the
               bytes into the local buffer.  In this way buffered streams will
               cascade harmlessly. */
            // 大数据读取，且还没有设置过"标记"位，直接从底层流中读取数据
            if (len >= getBufIfOpen().length && markpos < 0) {
                return getInIfOpen().read(b, off, len);
            }

            // 从底层流中执行读取数据操作，将数据放入缓冲区中
            fill();

            // 再次计算缓冲区剩余的可读字节，若还是无剩余字节，说明已读取到流末尾，返回|-1|表示末尾
            avail = count - pos;
            if (avail <= 0) return -1;
        }

        // 从缓冲区当前偏移开始拷贝数据至参数|b[off:off+cnt]|数组中
        int cnt = (avail < len) ? avail : len;
        System.arraycopy(getBufIfOpen(), pos, b, off, cnt);

        // 跳过已被读取的字节，将当前偏移推进|cnt|长度
        pos += cnt;
        return cnt;
    }

    /**
     * Reads bytes from this byte-input stream into the specified byte array,
     * starting at the given offset.
     *
     * <p> This method implements the general contract of the corresponding
     * <code>{@link InputStream#read(byte[], int, int) read}</code> method of
     * the <code>{@link InputStream}</code> class.  As an additional
     * convenience, it attempts to read as many bytes as possible by repeatedly
     * invoking the <code>read</code> method of the underlying stream.  This
     * iterated <code>read</code> continues until one of the following
     * conditions becomes true: <ul>
     *
     *   <li> The specified number of bytes have been read,
     *
     *   <li> The <code>read</code> method of the underlying stream returns
     *   <code>-1</code>, indicating end-of-file, or
     *
     *   <li> The <code>available</code> method of the underlying stream
     *   returns zero, indicating that further input requests would block.
     *
     * </ul> If the first <code>read</code> on the underlying stream returns
     * <code>-1</code> to indicate end-of-file then this method returns
     * <code>-1</code>.  Otherwise this method returns the number of bytes
     * actually read.
     *
     * <p> Subclasses of this class are encouraged, but not required, to
     * attempt to read as many bytes as possible in the same fashion.
     *
     * @param      b     destination buffer.
     * @param      off   offset at which to start storing bytes.
     * @param      len   maximum number of bytes to read.
     * @return     the number of bytes read, or <code>-1</code> if the end of
     *             the stream has been reached.
     * @exception  IOException  if this input stream has been closed by
     *                          invoking its {@link #close()} method,
     *                          or an I/O error occurs.
     */
    // 从输入流中读取最多|len|个字节的数据到一个字节数组|b[off:off+len]|中。如果|len|不为零，
    // 则该方法将可能被阻塞，直到输入可用；如果|len|为零，方法将立即返回零
    // 注：底层会自动进行数组|b|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    // 注：返回实际读取的字节数；若为|-1|，则表示已经读取到流末尾
    public synchronized int read(byte b[], int off, int len)
        throws IOException
    {
        // 检查当前流是否被关闭
        getBufIfOpen(); // Check for closed stream

        // 越界校验
        if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        // 实际读取的字节数
        int n = 0;

        // 尽最大努力的从输入流中读取|len|个字节的数据到一个字节数组|b[off:off+len]|中，除非底层流无
        // 数据可读时返回。即，最多读取了|len|个字节的数据
        for (;;) {
            // 将数据读入到数组|b[off:off+len|中，如有必要，将从被代理的底层的流中读取一次
            int nread = read1(b, off + n, len - n);

            // 底层流无数据可读、或已经读取了|len|长度的数据
            if (nread <= 0)
                return (n == 0) ? nread : n;

            // 增加实际读取的字节数计数器
            n += nread;

            // 已经读取了|len|长度的数据
            if (n >= len)
                return n;

            // if not closed but no bytes available, return
            // 检查底层流中可用字节，若为空，直接返回，而无需尝试下一次的可能会阻塞的读取
            InputStream input = in;
            if (input != null && input.available() <= 0)
                return n;
        }
    }

    /**
     * See the general contract of the <code>skip</code>
     * method of <code>InputStream</code>.
     *
     * @exception  IOException  if the stream does not support seek,
     *                          or if this input stream has been closed by
     *                          invoking its {@link #close()} method, or an
     *                          I/O error occurs.
     */
    // 从输入流中跳过并丢弃|n|个字节的数据。|n|为负时等同于为零，即都不产生偏移；若产生了实际的偏
    // 移，其最大值为字节数组的剩余可用的字节长度。 返回实际跳过的字节数
    // 注：此方法不会出现回退的偏移，也不会偏移出字节数组的可用边界
    // 注：可跳过并丢弃的字节数上限为当前缓冲区中剩余的可读字节。即，只要缓冲区有一个待读数据，可
    // 跳过并丢弃的字节数上限就为|1|；但缓冲区中无剩余的可读数据，会穿透到底层流中执行跳过
    public synchronized long skip(long n) throws IOException {
        // 检查当前流是否被关闭
        getBufIfOpen(); // Check for closed stream
        if (n <= 0) {
            return 0;
        }

        // 缓冲区剩余的可读字节
        long avail = count - pos;

        if (avail <= 0) {
            // If no mark position set then don't keep in buffer
            // 还没有设置过"标记"位，直接从底层流中跳过并丢弃数据
            if (markpos <0)
                return getInIfOpen().skip(n);

            // Fill in buffer to save bytes for reset
            // 从底层流中执行读取数据操作，将数据放入缓冲区中
            fill();

            // 再次计算缓冲区剩余的可读字节，若还是无剩余字节，说明已读取到流末尾。返回|0|表示未跳过数据
            avail = count - pos;
            if (avail <= 0)
                return 0;
        }

        // 重置可跳过并丢弃的字节数上限为当前缓冲区中剩余的可读字节
        long skipped = (avail < n) ? avail : n;
        pos += skipped;
        return skipped;
    }

    /**
     * Returns an estimate of the number of bytes that can be read (or
     * skipped over) from this input stream without blocking by the next
     * invocation of a method for this input stream. The next invocation might be
     * the same thread or another thread.  A single read or skip of this
     * many bytes will not block, but may read or skip fewer bytes.
     * <p>
     * This method returns the sum of the number of bytes remaining to be read in
     * the buffer (<code>count&nbsp;- pos</code>) and the result of calling the
     * {@link java.io.FilterInputStream#in in}.available().
     *
     * @return     an estimate of the number of bytes that can be read (or skipped
     *             over) from this input stream without blocking.
     * @exception  IOException  if this input stream has been closed by
     *                          invoking its {@link #close()} method,
     *                          or an I/O error occurs.
     */
    // 直接获取底层流的可读取或跳过的剩余字节数的估计值，而不会因下一次调用此输入流的方法而阻塞
    public synchronized int available() throws IOException {
        int n = count - pos;
        int avail = getInIfOpen().available();
        return n > (Integer.MAX_VALUE - avail)
                    ? Integer.MAX_VALUE
                    : n + avail;
    }

    /**
     * See the general contract of the <code>mark</code>
     * method of <code>InputStream</code>.
     *
     * @param   readlimit   the maximum limit of bytes that can be read before
     *                      the mark position becomes invalid.
     * @see     java.io.BufferedInputStream#reset()
     */
    // 将当前读取索引设置"标记"索引，已待后续的回退操作。参数|readlimit|是允许的最大回退（预读）字
    // 节长度的限制。即，缓冲区中最多存储已读的数据不能超过|marklimit|字节
    public synchronized void mark(int readlimit) {
        marklimit = readlimit;
        markpos = pos;
    }

    /**
     * See the general contract of the <code>reset</code>
     * method of <code>InputStream</code>.
     * <p>
     * If <code>markpos</code> is <code>-1</code>
     * (no mark has been set or the mark has been
     * invalidated), an <code>IOException</code>
     * is thrown. Otherwise, <code>pos</code> is
     * set equal to <code>markpos</code>.
     *
     * @exception  IOException  if this stream has not been marked or,
     *                  if the mark has been invalidated, or the stream
     *                  has been closed by invoking its {@link #close()}
     *                  method, or an I/O error occurs.
     * @see        java.io.BufferedInputStream#mark(int)
     */
    // 重置操作。即，|buf[markpos]|是重置后要作为输入提供的第一个字节
    public synchronized void reset() throws IOException {
        getBufIfOpen(); // Cause exception if closed
        if (markpos < 0)
            throw new IOException("Resetting to invalid mark");
        pos = markpos;
    }

    /**
     * Tests if this input stream supports the <code>mark</code>
     * and <code>reset</code> methods. The <code>markSupported</code>
     * method of <code>BufferedInputStream</code> returns
     * <code>true</code>.
     *
     * @return  a <code>boolean</code> indicating if this stream type supports
     *          the <code>mark</code> and <code>reset</code> methods.
     * @see     java.io.InputStream#mark(int)
     * @see     java.io.InputStream#reset()
     */
    // 当前流支持"标记/重置"特性
    public boolean markSupported() {
        return true;
    }

    /**
     * Closes this input stream and releases any system resources
     * associated with the stream.
     * Once the stream has been closed, further read(), available(), reset(),
     * or skip() invocations will throw an IOException.
     * Closing a previously closed stream has no effect.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    // 关闭输入流并释放与该流关联的所有系统资源
    public void close() throws IOException {
        byte[] buffer;
        while ( (buffer = buf) != null) {
            // 关闭缓冲流：即，原子的将缓冲区|buf|数组引用置空、底层流引用置空（且会关闭该底层流）
            if (bufUpdater.compareAndSet(this, buffer, null)) {
                InputStream input = in;
                in = null;

                // 自动关闭底层流
                if (input != null)
                    input.close();
                return;
            }
            // Else retry in case a new buf was CASed in fill()
        }
    }
}
