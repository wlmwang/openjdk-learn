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


/**

 * A read/write HeapByteBuffer.






 */

// 堆上内存的缓冲区。即，使用堆上的字节数组读写数据
// 注：相比于|DirectByteBuffer|缓冲区，堆上内存缓冲区需要|JVM|与|Native（用户态）|间的内存拷贝
class HeapByteBuffer
    extends ByteBuffer
{

    // For speed these fields are actually declared in X-Buffer;
    // these declarations are here as documentation
    /*

    protected final byte[] hb;
    protected final int offset;

    */

    // 在堆上创建一个指定容量的字节数组，并基于此，创建一个缓冲区对象
    HeapByteBuffer(int cap, int lim) {            // package-private

        super(-1, 0, lim, cap, new byte[cap], 0);
        /*
        hb = new byte[cap];
        offset = 0;
        */




    }

    // 基于一个字节数组、偏移、长度，创建一个堆上的缓冲区对象
    // 注：缓冲区将由给定的字节数组支持；也就是说，对缓冲区的修改将导致数组被修改，反之亦然
    HeapByteBuffer(byte[] buf, int off, int len) { // package-private

        super(-1, off, off + len, buf.length, buf, 0);
        /*
        hb = buf;
        offset = 0;
        */




    }

    // 基于一个字节数组、标记、位置、限制、容量、偏移，创建一个堆上的缓冲区对象
    // 注：缓冲区将由给定的字节数组支持；也就是说，对缓冲区的修改将导致数组被修改，反之亦然
    protected HeapByteBuffer(byte[] buf,
                                   int mark, int pos, int lim, int cap,
                                   int off)
    {

        super(mark, pos, lim, cap, buf, off);
        /*
        hb = buf;
        offset = off;
        */




    }

    // 基于当前缓冲区的当前位置、容量和限制，创建一个新的字节缓冲区
    // 注：对原始缓冲区内容的更改将在新缓冲区中可见，反之亦然。它们共享了底层的缓冲区内存（新的缓冲
    // 区仅使用原始内存的其中一个子序列）；不过，这两个缓冲区的位置，限制和标记值是独立的
    public ByteBuffer slice() {
        return new HeapByteBuffer(hb,
                                        -1,
                                        0,
                                        this.remaining(),
                                        this.remaining(),
                                        this.position() + offset);
    }

    // 基于当前缓冲区的当前位置、限制、标记、容量和限制，创建一个新的字节缓冲区
    // 注：对原始缓冲区内容的更改将在新缓冲区中可见，反之亦然。它们共享了底层的缓冲区内存（新的缓冲
    // 区使用原始内存的全部序列）；不过，这两个缓冲区的位置，限制和标记值是独立的
    public ByteBuffer duplicate() {
        return new HeapByteBuffer(hb,
                                        this.markValue(),
                                        this.position(),
                                        this.limit(),
                                        this.capacity(),
                                        offset);
    }

    // 基于当前缓冲区的当前位置、限制、标记、容量和限制，创建一个新的、只读的、字节缓冲区
    // 注：对原始缓冲区内容的更改将在新缓冲区中可见，但新的缓冲区只读。它们共享了底层的缓冲区内存（新
    // 的缓冲区使用原始内存的全部序列）；不过，这两个缓冲区的位置，限制和标记值是独立的
    public ByteBuffer asReadOnlyBuffer() {

        return new HeapByteBufferR(hb,
                                     this.markValue(),
                                     this.position(),
                                     this.limit(),
                                     this.capacity(),
                                     offset);



    }



    protected int ix(int i) {
        return i + offset;
    }

    public byte get() {
        return hb[ix(nextGetIndex())];
    }

    public byte get(int i) {
        return hb[ix(checkIndex(i))];
    }







    public ByteBuffer get(byte[] dst, int offset, int length) {
        checkBounds(offset, length, dst.length);
        if (length > remaining())
            throw new BufferUnderflowException();
        System.arraycopy(hb, ix(position()), dst, offset, length);
        position(position() + length);
        return this;
    }

    // 判断此缓冲区是否堆外缓冲区
    public boolean isDirect() {
        return false;
    }

    // 判断此缓冲区是否仅可读
    public boolean isReadOnly() {
        return false;
    }

    public ByteBuffer put(byte x) {

        hb[ix(nextPutIndex())] = x;
        return this;



    }

    public ByteBuffer put(int i, byte x) {

        hb[ix(checkIndex(i))] = x;
        return this;



    }

    // 将字节数组|src[offset:offset+length]|拷贝到当前缓冲区中。如果|length|为零，方法将立即返回
    // 注：内部会自动进行数组|src|是否越界校验，即，方法可能会抛出|IndexOutOfBoundsException|
    public ByteBuffer put(byte[] src, int offset, int length) {

        checkBounds(offset, length, src.length);

        // 写入数据超过缓冲区剩余内存，立即抛出缓冲区溢出异常
        if (length > remaining())
            throw new BufferOverflowException();

        // 按字节拷贝|src[offset:offset+length]|数组至|hb|中可用内存中
        System.arraycopy(src, offset, hb, ix(position()), length);

        // 当前偏移量向前推进|n|长度，表示写入了|n|字节数据
        position(position() + length);
        return this;



    }

    // 压缩此缓冲区（可选操作）。即，将缓冲区的当前|position|和它的|limit|之间的字节复制到缓冲区的
    // 开头，并将标记丢弃
    // 注：缓冲区的|position|设置为复制的字节数，而不是零，以便调用此方法后可以立即调用|put()|方法
    // 注：在缓冲区写入数据后调用此方法，以防写入不完整
    public ByteBuffer compact() {

        System.arraycopy(hb, ix(position()), hb, ix(0), remaining());
        position(remaining());
        limit(capacity());
        discardMark();
        return this;



    }

    // 将源缓冲区|ByteBuffer|数据拷贝到当前缓冲区中。一般使用场景：将读取的源缓冲区中数据拷贝到写入
    // 的目标缓冲区中可用内存
    // 注：如果读取的源缓冲区中数据长度超过了写入的目标缓冲区中可用内存，立即抛出缓冲区溢出异常
    // 注：当源缓冲区中的数据被拷贝到当前缓冲区中，会被视为数据已被消费，然后清除
    public ByteBuffer put(ByteBuffer src) {

        if (src instanceof HeapByteBuffer) {    // 源缓冲区为堆上缓冲区
            // 源与目标缓冲区不能为同一个
            if (src == this)
                throw new IllegalArgumentException();

            HeapByteBuffer sb = (HeapByteBuffer)src;
            int n = sb.remaining();

            // 如果读取的源缓冲区中数据长度超过了写入的目标缓冲区中可用内存，立即抛出缓冲区溢出异常
            if (n > remaining())
                throw new BufferOverflowException();

            // 按字节拷贝|src[offset:offset+length]|数组至|hb|中可用内存中
            System.arraycopy(sb.hb, sb.ix(sb.position()),
                    hb, ix(position()), n);

            // 源读取缓冲向前推进|n|长度，表示读取了|n|字节数据
            sb.position(sb.position() + n);

            // 当前偏移量向前推进|n|长度，表示写入了|n|字节数据
            position(position() + n);
        } else if (src.isDirect()) {    // 源缓冲区为堆外缓冲区
            int n = src.remaining();

            // 如果读取的源缓冲区中数据长度超过了写入的目标缓冲区中可用内存，立即抛出缓冲区溢出异常
            if (n > remaining())
                throw new BufferOverflowException();

            // 将源缓冲区中数据按字节拷贝数组至|hb|中可用内存中
            src.get(hb, ix(position()), n);
            position(position() + n);
        } else {
            // 循环遍历，逐个字节调用|put(byte)|拷贝数据
            super.put(src);
        }
        return this;



    }





    byte _get(int i) {                          // package-private
        return hb[i];
    }

    void _put(int i, byte b) {                  // package-private

        hb[i] = b;



    }

    // char


    // 以大端序，从当前缓冲区的当前位置，读取单个字符
    public char getChar() {
        return Bits.getChar(this, ix(nextGetIndex(2)), bigEndian);
    }

    // 以大端序，从当前缓冲区的指定位置|i|，读取单个字符
    public char getChar(int i) {
        return Bits.getChar(this, ix(checkIndex(i, 2)), bigEndian);
    }



    // 以大端序，在当前缓冲区的当前位置，写入字符|x|
    public ByteBuffer putChar(char x) {

        Bits.putChar(this, ix(nextPutIndex(2)), x, bigEndian);
        return this;



    }

    // 以大端序，在当前缓冲区的指定位置|i|，写入字符|x|
    public ByteBuffer putChar(int i, char x) {

        Bits.putChar(this, ix(checkIndex(i, 2)), x, bigEndian);
        return this;



    }

    // 将当前字节缓冲区装饰一个默认字节序的字符缓冲区。新缓冲区的内容将从该缓冲区的当前位置开始。
    // 此缓冲区内容的更改将在新缓冲区中可见，反之亦然；两个缓冲区的位置、限制和标记值将是独立的。
    // 注：将当前字节缓冲区封装为一个|char|型的视图。即，对外仅提供|char|型的操作接口
    public CharBuffer asCharBuffer() {
        int size = this.remaining() >> 1;
        int off = offset + position();
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
    }


    // short


    // 以大端序，从当前缓冲区的当前位置，读取单个|short|
    public short getShort() {
        return Bits.getShort(this, ix(nextGetIndex(2)), bigEndian);
    }

    // 以大端序，从当前缓冲区的指定位置|i|，读取单个|short|
    public short getShort(int i) {
        return Bits.getShort(this, ix(checkIndex(i, 2)), bigEndian);
    }



    // 以大端序，在当前缓冲区的当前位置，写入单个|short|
    public ByteBuffer putShort(short x) {

        Bits.putShort(this, ix(nextPutIndex(2)), x, bigEndian);
        return this;



    }

    // 以大端序，在当前缓冲区的指定位置|i|，写入单个|short|
    public ByteBuffer putShort(int i, short x) {

        Bits.putShort(this, ix(checkIndex(i, 2)), x, bigEndian);
        return this;



    }

    // 将当前字节缓冲区装饰一个默认字节序的字符缓冲区。新缓冲区的内容将从该缓冲区的当前位置开始。
    // 此缓冲区内容的更改将在新缓冲区中可见，反之亦然；两个缓冲区的位置、限制和标记值将是独立的。
    // 注：将当前字节缓冲区封装为一个|char|型的视图。即，对外仅提供|char|型的操作接口
    public ShortBuffer asShortBuffer() {
        int size = this.remaining() >> 1;
        int off = offset + position();
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
    }


    // int



    public int getInt() {
        return Bits.getInt(this, ix(nextGetIndex(4)), bigEndian);
    }

    public int getInt(int i) {
        return Bits.getInt(this, ix(checkIndex(i, 4)), bigEndian);
    }



    public ByteBuffer putInt(int x) {

        Bits.putInt(this, ix(nextPutIndex(4)), x, bigEndian);
        return this;



    }

    public ByteBuffer putInt(int i, int x) {

        Bits.putInt(this, ix(checkIndex(i, 4)), x, bigEndian);
        return this;



    }

    // 将当前字节缓冲区装饰一个默认字节序的整型缓冲区。新缓冲区的内容将从该缓冲区的当前位置开始。
    // 此缓冲区内容的更改将在新缓冲区中可见，反之亦然；两个缓冲区的位置、限制和标记值将是独立的。
    // 注：将当前字节缓冲区封装为一个|int|型的视图。即，对外仅提供|int|型的操作接口
    public IntBuffer asIntBuffer() {
        int size = this.remaining() >> 2;
        int off = offset + position();
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
    }


    // long



    public long getLong() {
        return Bits.getLong(this, ix(nextGetIndex(8)), bigEndian);
    }

    public long getLong(int i) {
        return Bits.getLong(this, ix(checkIndex(i, 8)), bigEndian);
    }



    public ByteBuffer putLong(long x) {

        Bits.putLong(this, ix(nextPutIndex(8)), x, bigEndian);
        return this;



    }

    public ByteBuffer putLong(int i, long x) {

        Bits.putLong(this, ix(checkIndex(i, 8)), x, bigEndian);
        return this;



    }

    // 将当前字节缓冲区装饰一个默认字节序的长整型缓冲区。新缓冲区的内容将从该缓冲区的当前位置开始。
    // 此缓冲区内容的更改将在新缓冲区中可见，反之亦然；两个缓冲区的位置、限制和标记值将是独立的。
    // 注：将当前字节缓冲区封装为一个|long|型的视图。即，对外仅提供|long|型的操作接口
    public LongBuffer asLongBuffer() {
        int size = this.remaining() >> 3;
        int off = offset + position();
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
    }


    // float



    public float getFloat() {
        return Bits.getFloat(this, ix(nextGetIndex(4)), bigEndian);
    }

    public float getFloat(int i) {
        return Bits.getFloat(this, ix(checkIndex(i, 4)), bigEndian);
    }



    public ByteBuffer putFloat(float x) {

        Bits.putFloat(this, ix(nextPutIndex(4)), x, bigEndian);
        return this;



    }

    public ByteBuffer putFloat(int i, float x) {

        Bits.putFloat(this, ix(checkIndex(i, 4)), x, bigEndian);
        return this;



    }

    // 将当前字节缓冲区装饰一个默认字节序的浮点缓冲区。新缓冲区的内容将从该缓冲区的当前位置开始。
    // 此缓冲区内容的更改将在新缓冲区中可见，反之亦然；两个缓冲区的位置、限制和标记值将是独立的。
    // 注：将当前字节缓冲区封装为一个|float|型的视图。即，对外仅提供|float|型的操作接口
    public FloatBuffer asFloatBuffer() {
        int size = this.remaining() >> 2;
        int off = offset + position();
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
    }


    // double



    public double getDouble() {
        return Bits.getDouble(this, ix(nextGetIndex(8)), bigEndian);
    }

    public double getDouble(int i) {
        return Bits.getDouble(this, ix(checkIndex(i, 8)), bigEndian);
    }



    public ByteBuffer putDouble(double x) {

        Bits.putDouble(this, ix(nextPutIndex(8)), x, bigEndian);
        return this;



    }

    public ByteBuffer putDouble(int i, double x) {

        Bits.putDouble(this, ix(checkIndex(i, 8)), x, bigEndian);
        return this;



    }

    // 将当前字节缓冲区装饰一个默认字节序的双精度缓冲区。新缓冲区的内容将从该缓冲区的当前位置开始。
    // 此缓冲区内容的更改将在新缓冲区中可见，反之亦然；两个缓冲区的位置、限制和标记值将是独立的。
    // 注：将当前字节缓冲区封装为一个|double|型的视图。即，对外仅提供|double|型的操作接口
    public DoubleBuffer asDoubleBuffer() {
        int size = this.remaining() >> 3;
        int off = offset + position();
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
    }











































}
