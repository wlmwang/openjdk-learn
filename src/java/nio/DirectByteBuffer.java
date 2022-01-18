/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

// -- This file was mechanically generated: Do not edit! -- //

package java.nio;

import java.io.FileDescriptor;
import sun.misc.Cleaner;
import sun.misc.Unsafe;
import sun.misc.VM;
import sun.nio.ch.DirectBuffer;


// 堆外内存的缓冲区。即，直接使用堆外内存读写数据，是|NIO|高性能的核心设计之一
// 注：堆外内存是指用|malloc()/mmap()|分配的内存，它们可以直接作为系统|IO|函数的参数，这减少了内存间的拷
// 贝。若使用|malloc()|分配堆外内存，它可以避免|JVM|与|Native（用户态）|间的内存拷贝；若使用|mmap|分配
// 的堆外内存，甚至还可以避免|Native（用户态）|与|Native（内核态）|间的内存拷贝，也就是零拷贝（不过，磁盘
// 与主存间的数据拷贝依然存在。即，|DMA|是无法优化掉的）
// 亮点：基于虚引用，使用|Cleaner|机制，进行堆外内存的回收工作
//
// |-XX:+PageAlignDirectMemory|指定申请的内存是否需要按页对齐。默认不对其
// |-XX:MaxDirectMemorySize=<size>|指定申请的最大堆外内存大小。默认与|-Xmx|相等
class DirectByteBuffer

    extends MappedByteBuffer



    implements DirectBuffer
{



    // Cached unsafe-access object
    // 缓存|unsafe-access|对象
    protected static final Unsafe unsafe = Bits.unsafe();

    // Cached array base offset
    // 字节数组的基础偏移量
    private static final long arrayBaseOffset = (long)unsafe.arrayBaseOffset(byte[].class);

    // Cached unaligned-access capability
    // 是否需要内存对齐访问
    // 注：以下架构需要对齐：|arch.equals("x86") || arch.equals("amd64") || arch.equals("x86_64")|
    protected static final boolean unaligned = Bits.unaligned();

    // Base address, used in all indexing calculations
    // NOTE: moved up to Buffer.java for speed in JNI GetDirectBufferAddress
    //    protected long address;

    // An object attached to this buffer. If this buffer is a view of another
    // buffer then we use this field to keep a reference to that buffer to
    // ensure that its memory isn't freed before we are done with it.
    private final Object att;

    public Object attachment() {
        return att;
    }


    // 基于虚引用，使用|Cleaner|机制，进行堆外内存的回收工作
    // 注：当堆上的|DirectByteBuffer|对象被回收时，将触发|Deallocator.run()|执行，以释放堆外内存
    // 注：相同机制|finalize()|被官方"嫌弃"；替代方案为虚引用，用于处理对象被回收时的善后工作
    // 原理：当虚引用对象所引用的对象被回收时，其会被加入到相关引用队列中（此处为|Cleaner.dummyQueue|），
    // 后台线程|ReferenceHandler.run|在检查到该对象时，调用|Cleaner.clean -> Deallocator.run|
    private static class Deallocator
        implements Runnable
    {

        private static Unsafe unsafe = Unsafe.getUnsafe();

        private long address;
        private long size;
        private int capacity;

        private Deallocator(long address, long size, int capacity) {
            assert (address != 0);
            this.address = address;
            this.size = size;
            this.capacity = capacity;
        }

        public void run() {
            if (address == 0) {
                // Paranoia
                return;
            }
            // 调用|freeMemory|方法进行堆外内存的释放
            unsafe.freeMemory(address);
            address = 0;
            // 同时"释放"预留的堆外内存统计变量计数器
            Bits.unreserveMemory(size, capacity);
        }

    }

    private final Cleaner cleaner;

    public Cleaner cleaner() { return cleaner; }











    // Primary constructor
    //
    DirectByteBuffer(int cap) {                   // package-private

        // 初始化缓冲区的四个核心属性：预读索引、起始索引、缓冲区的最大可用索引、以及缓冲区的容量
        super(-1, 0, cap, cap);

        // 判断是否需要页面对齐。默认为|false|，不对齐
        // 注：可通过参数|-XX:+PageAlignDirectMemory|控制
        boolean pa = VM.isDirectMemoryPageAligned();

        // 获取操作系统页大小。典型值为|4k|
        int ps = Bits.pageSize();

        // 计算分配堆外内存的容量
        // 注：页面对齐配置下，申请的堆外内存为|cap+ps|。这为了使后续计算对齐后的首地址到末尾内
        // 存不小于|cap|做准备，即，可用内存不小于用户指定的|cap|容量
        long size = Math.max(1L, (long)cap + (pa ? ps : 0));

        // 更新堆外内存使用详情的统计计数器。当内存不足时，会抛出|OOM|
        // 注：堆外不足时，会尝试调用|System.gc()|后再次申请，如果还是不足，会抛出|OOM|异常
        // 注：默认情况下，可以申请的最大|DirectByteBuffer|内存为|Java|堆的最大限制
        Bits.reserveMemory(size, cap);

        long base = 0;
        try {
            // 调用|allocateMemory()|方法进行堆外内存的实际分配，返回|native pointer|
            // 注：底层调用|os::malloc()|，即调用|C/C++|标准库|malloc()|函数
            base = unsafe.allocateMemory(size);
        } catch (OutOfMemoryError x) {
            // 如果内存分配失败，则释放指定大小的堆外内存统计的计数器
            Bits.unreserveMemory(size, cap);
            throw x;
        }
        // 初始化申请的内存空间为|0|
        unsafe.setMemory(base, size, (byte) 0);

        // 设置堆外内存的可用的起始地址
        // 注：主要为了页对齐的特性。即，申请的内存，只取页对齐部分作为缓冲区的可用内存
        if (pa && (base % ps != 0)) {
            // Round up to page boundary
            // 从|base|开始，计算第一个符合页对齐的地址
            // 注：表达式|(base & (ps-1))|的值为|base|在页对齐后，剩余字节。快速取余表达式
            address = base + ps - (base & (ps - 1));
        } else {
            address = base;
        }

        // 使用|Cleaner|机制，注册堆外内存的回收处理函数
        // 注：当堆上的|this|对象被回收时，将触发|Deallocator.run()|执行，用于释放申请的堆外内存
        // 注：相同机制|finalize()|被官方"嫌弃"；替代方案为虚引用，用于处理对象被回收时的善后工作
        cleaner = Cleaner.create(this, new Deallocator(base, size, cap));
        att = null;



    }



    // Invoked to construct a direct ByteBuffer referring to the block of
    // memory. A given arbitrary object may also be attached to the buffer.
    //
    DirectByteBuffer(long addr, int cap, Object ob) {
        super(-1, 0, cap, cap);
        address = addr;
        cleaner = null;
        att = ob;
    }


    // Invoked only by JNI: NewDirectByteBuffer(void*, long)
    //
    private DirectByteBuffer(long addr, int cap) {
        super(-1, 0, cap, cap);
        address = addr;
        cleaner = null;
        att = null;
    }



    // For memory-mapped buffers -- invoked by FileChannelImpl via reflection
    //
    // 基于|mmap()|共享内存地址|addr|、以及自定义清理器|unmapper|，构建一个零拷贝的堆
    // 外缓冲区
    // 注：此处的|fd|与|unmapper|参数，所关联的文件描述符都是|-1|，因为映射一旦建立，就不
    // 再依赖于用于创建它的文件通道，特别是关闭通道对映射的有效性没有影响，反之也是
    protected DirectByteBuffer(int cap, long addr,
                                     FileDescriptor fd,
                                     Runnable unmapper)
    {

        super(-1, 0, cap, cap, fd);
        address = addr;
        cleaner = Cleaner.create(this, unmapper);
        att = null;



    }



    // For duplicates and slices
    //
    DirectByteBuffer(DirectBuffer db,         // package-private
                               int mark, int pos, int lim, int cap,
                               int off)
    {

        super(mark, pos, lim, cap);
        address = db.address() + off;

        cleaner = null;

        att = db;



    }

    // 基于当前缓冲区的当前位置、容量和限制，创建一个新的字节缓冲区
    // 注：对原始缓冲区内容的更改将在新缓冲区中可见，反之亦然。它们共享了底层的缓冲区内存（新的缓冲
    // 区仅使用原始内存的其中一个子序列）；不过，这两个缓冲区的位置，限制和标记值是独立的
    public ByteBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        int off = (pos << 0);
        assert (off >= 0);
        return new DirectByteBuffer(this, -1, 0, rem, rem, off);
    }

    // 基于当前缓冲区的当前位置、限制、标记、容量和限制，创建一个新的字节缓冲区
    // 注：对原始缓冲区内容的更改将在新缓冲区中可见，反之亦然。它们共享了底层的缓冲区内存（新的缓冲
    // 区使用原始内存的全部序列）；不过，这两个缓冲区的位置，限制和标记值是独立的
    public ByteBuffer duplicate() {
        return new DirectByteBuffer(this,
                                              this.markValue(),
                                              this.position(),
                                              this.limit(),
                                              this.capacity(),
                                              0);
    }

    // 基于当前缓冲区的当前位置、限制、标记、容量和限制，创建一个新的、只读的、字节缓冲区
    // 注：对原始缓冲区内容的更改将在新缓冲区中可见，但新的缓冲区只读。它们共享了底层的缓冲区内存（新
    // 的缓冲区使用原始内存的全部序列）；不过，这两个缓冲区的位置，限制和标记值是独立的
    public ByteBuffer asReadOnlyBuffer() {

        return new DirectByteBufferR(this,
                                           this.markValue(),
                                           this.position(),
                                           this.limit(),
                                           this.capacity(),
                                           0);



    }



    public long address() {
        return address;
    }

    private long ix(int i) {
        return address + (i << 0);
    }

    public byte get() {
        return ((unsafe.getByte(ix(nextGetIndex()))));
    }

    public byte get(int i) {
        return ((unsafe.getByte(ix(checkIndex(i)))));
    }







    public ByteBuffer get(byte[] dst, int offset, int length) {

        if ((length << 0) > Bits.JNI_COPY_TO_ARRAY_THRESHOLD) {
            checkBounds(offset, length, dst.length);
            int pos = position();
            int lim = limit();
            assert (pos <= lim);
            int rem = (pos <= lim ? lim - pos : 0);
            if (length > rem)
                throw new BufferUnderflowException();








                Bits.copyToArray(ix(pos), dst, arrayBaseOffset,
                                 offset << 0,
                                 length << 0);
            position(pos + length);
        } else {
            super.get(dst, offset, length);
        }
        return this;



    }



    public ByteBuffer put(byte x) {

        unsafe.putByte(ix(nextPutIndex()), ((x)));
        return this;



    }

    public ByteBuffer put(int i, byte x) {

        unsafe.putByte(ix(checkIndex(i)), ((x)));
        return this;



    }

    public ByteBuffer put(ByteBuffer src) {

        if (src instanceof DirectByteBuffer) {
            if (src == this)
                throw new IllegalArgumentException();
            DirectByteBuffer sb = (DirectByteBuffer)src;

            int spos = sb.position();
            int slim = sb.limit();
            assert (spos <= slim);
            int srem = (spos <= slim ? slim - spos : 0);

            int pos = position();
            int lim = limit();
            assert (pos <= lim);
            int rem = (pos <= lim ? lim - pos : 0);

            if (srem > rem)
                throw new BufferOverflowException();
            unsafe.copyMemory(sb.ix(spos), ix(pos), srem << 0);
            sb.position(spos + srem);
            position(pos + srem);
        } else if (src.hb != null) {

            int spos = src.position();
            int slim = src.limit();
            assert (spos <= slim);
            int srem = (spos <= slim ? slim - spos : 0);

            put(src.hb, src.offset + spos, srem);
            src.position(spos + srem);

        } else {
            super.put(src);
        }
        return this;



    }

    public ByteBuffer put(byte[] src, int offset, int length) {

        if ((length << 0) > Bits.JNI_COPY_FROM_ARRAY_THRESHOLD) {
            checkBounds(offset, length, src.length);
            int pos = position();
            int lim = limit();
            assert (pos <= lim);
            int rem = (pos <= lim ? lim - pos : 0);
            if (length > rem)
                throw new BufferOverflowException();







                Bits.copyFromArray(src, arrayBaseOffset, offset << 0,
                                   ix(pos), length << 0);
            position(pos + length);
        } else {
            super.put(src, offset, length);
        }
        return this;



    }

    // 压缩此缓冲区（可选操作）。即，将缓冲区的当前|position|和它的|limit|之间的字节复制到缓冲区的
    // 开头，并将标记丢弃
    // 注：缓冲区的|position|设置为复制的字节数，而不是零，以便调用此方法后可以立即调用|put()|方法
    // 注：在缓冲区写入数据后调用此方法，以防写入不完整
    public ByteBuffer compact() {

        int pos = position();
        int lim = limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        unsafe.copyMemory(ix(pos), ix(0), rem << 0);
        position(rem);
        limit(capacity());
        discardMark();
        return this;



    }

    public boolean isDirect() {
        return true;
    }

    public boolean isReadOnly() {
        return false;
    }
































































    byte _get(int i) {                          // package-private
        return unsafe.getByte(address + i);
    }

    void _put(int i, byte b) {                  // package-private

        unsafe.putByte(address + i, b);



    }




    private char getChar(long a) {
        if (unaligned) {
            char x = unsafe.getChar(a);
            return (nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getChar(a, bigEndian);
    }

    public char getChar() {
        return getChar(ix(nextGetIndex((1 << 1))));
    }

    public char getChar(int i) {
        return getChar(ix(checkIndex(i, (1 << 1))));
    }



    private ByteBuffer putChar(long a, char x) {

        if (unaligned) {
            char y = (x);
            unsafe.putChar(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putChar(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putChar(char x) {

        putChar(ix(nextPutIndex((1 << 1))), x);
        return this;



    }

    public ByteBuffer putChar(int i, char x) {

        putChar(ix(checkIndex(i, (1 << 1))), x);
        return this;



    }

    public CharBuffer asCharBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 1;
        if (!unaligned && ((address + off) % (1 << 1) != 0)) {
            return (bigEndian
                    ? (CharBuffer)(new ByteBufferAsCharBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (CharBuffer)(new ByteBufferAsCharBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (CharBuffer)(new DirectCharBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (CharBuffer)(new DirectCharBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }




    private short getShort(long a) {
        if (unaligned) {
            short x = unsafe.getShort(a);
            return (nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getShort(a, bigEndian);
    }

    public short getShort() {
        return getShort(ix(nextGetIndex((1 << 1))));
    }

    public short getShort(int i) {
        return getShort(ix(checkIndex(i, (1 << 1))));
    }



    private ByteBuffer putShort(long a, short x) {

        if (unaligned) {
            short y = (x);
            unsafe.putShort(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putShort(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putShort(short x) {

        putShort(ix(nextPutIndex((1 << 1))), x);
        return this;



    }

    public ByteBuffer putShort(int i, short x) {

        putShort(ix(checkIndex(i, (1 << 1))), x);
        return this;



    }

    public ShortBuffer asShortBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 1;
        if (!unaligned && ((address + off) % (1 << 1) != 0)) {
            return (bigEndian
                    ? (ShortBuffer)(new ByteBufferAsShortBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (ShortBuffer)(new ByteBufferAsShortBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (ShortBuffer)(new DirectShortBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (ShortBuffer)(new DirectShortBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }




    private int getInt(long a) {
        if (unaligned) {
            int x = unsafe.getInt(a);
            return (nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getInt(a, bigEndian);
    }

    public int getInt() {
        return getInt(ix(nextGetIndex((1 << 2))));
    }

    public int getInt(int i) {
        return getInt(ix(checkIndex(i, (1 << 2))));
    }



    private ByteBuffer putInt(long a, int x) {

        if (unaligned) {
            int y = (x);
            unsafe.putInt(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putInt(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putInt(int x) {

        putInt(ix(nextPutIndex((1 << 2))), x);
        return this;



    }

    public ByteBuffer putInt(int i, int x) {

        putInt(ix(checkIndex(i, (1 << 2))), x);
        return this;



    }

    public IntBuffer asIntBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 2;
        if (!unaligned && ((address + off) % (1 << 2) != 0)) {
            return (bigEndian
                    ? (IntBuffer)(new ByteBufferAsIntBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (IntBuffer)(new ByteBufferAsIntBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (IntBuffer)(new DirectIntBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (IntBuffer)(new DirectIntBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }




    private long getLong(long a) {
        if (unaligned) {
            long x = unsafe.getLong(a);
            return (nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getLong(a, bigEndian);
    }

    public long getLong() {
        return getLong(ix(nextGetIndex((1 << 3))));
    }

    public long getLong(int i) {
        return getLong(ix(checkIndex(i, (1 << 3))));
    }



    private ByteBuffer putLong(long a, long x) {

        if (unaligned) {
            long y = (x);
            unsafe.putLong(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putLong(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putLong(long x) {

        putLong(ix(nextPutIndex((1 << 3))), x);
        return this;



    }

    public ByteBuffer putLong(int i, long x) {

        putLong(ix(checkIndex(i, (1 << 3))), x);
        return this;



    }

    public LongBuffer asLongBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 3;
        if (!unaligned && ((address + off) % (1 << 3) != 0)) {
            return (bigEndian
                    ? (LongBuffer)(new ByteBufferAsLongBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (LongBuffer)(new ByteBufferAsLongBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (LongBuffer)(new DirectLongBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (LongBuffer)(new DirectLongBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }




    private float getFloat(long a) {
        if (unaligned) {
            int x = unsafe.getInt(a);
            return Float.intBitsToFloat(nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getFloat(a, bigEndian);
    }

    public float getFloat() {
        return getFloat(ix(nextGetIndex((1 << 2))));
    }

    public float getFloat(int i) {
        return getFloat(ix(checkIndex(i, (1 << 2))));
    }



    private ByteBuffer putFloat(long a, float x) {

        if (unaligned) {
            int y = Float.floatToRawIntBits(x);
            unsafe.putInt(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putFloat(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putFloat(float x) {

        putFloat(ix(nextPutIndex((1 << 2))), x);
        return this;



    }

    public ByteBuffer putFloat(int i, float x) {

        putFloat(ix(checkIndex(i, (1 << 2))), x);
        return this;



    }

    public FloatBuffer asFloatBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 2;
        if (!unaligned && ((address + off) % (1 << 2) != 0)) {
            return (bigEndian
                    ? (FloatBuffer)(new ByteBufferAsFloatBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (FloatBuffer)(new ByteBufferAsFloatBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (FloatBuffer)(new DirectFloatBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (FloatBuffer)(new DirectFloatBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }




    private double getDouble(long a) {
        if (unaligned) {
            long x = unsafe.getLong(a);
            return Double.longBitsToDouble(nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getDouble(a, bigEndian);
    }

    public double getDouble() {
        return getDouble(ix(nextGetIndex((1 << 3))));
    }

    public double getDouble(int i) {
        return getDouble(ix(checkIndex(i, (1 << 3))));
    }



    private ByteBuffer putDouble(long a, double x) {

        if (unaligned) {
            long y = Double.doubleToRawLongBits(x);
            unsafe.putLong(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putDouble(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putDouble(double x) {

        putDouble(ix(nextPutIndex((1 << 3))), x);
        return this;



    }

    public ByteBuffer putDouble(int i, double x) {

        putDouble(ix(checkIndex(i, (1 << 3))), x);
        return this;



    }

    public DoubleBuffer asDoubleBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 3;
        if (!unaligned && ((address + off) % (1 << 3) != 0)) {
            return (bigEndian
                    ? (DoubleBuffer)(new ByteBufferAsDoubleBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (DoubleBuffer)(new ByteBufferAsDoubleBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (DoubleBuffer)(new DirectDoubleBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (DoubleBuffer)(new DirectDoubleBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }

}
