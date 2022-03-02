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

package java.nio.channels.spi;

import java.io.IOException;
import java.nio.channels.*;


/**
 * Base implementation class for selectable channels.
 *
 * <p> This class defines methods that handle the mechanics of channel
 * registration, deregistration, and closing.  It maintains the current
 * blocking mode of this channel as well as its current set of selection keys.
 * It performs all of the synchronization required to implement the {@link
 * java.nio.channels.SelectableChannel} specification.  Implementations of the
 * abstract protected methods defined in this class need not synchronize
 * against other threads that might be engaged in the same operations.  </p>
 *
 *
 * @author Mark Reinhold
 * @author Mike McCloskey
 * @author JSR-51 Expert Group
 * @since 1.4
 */

// 可进行多路复用筛选的通道的抽象类
public abstract class AbstractSelectableChannel
    extends SelectableChannel
{

    // The provider that created this channel
    private final SelectorProvider provider;

    // Keys that have been created by registering this channel with selectors.
    // They are saved because if this channel is closed the keys must be
    // deregistered.  Protected by keyLock.
    //
    // 存储当前套接字通道已被注册的、所有的多路复用筛选器集合
    private SelectionKey[] keys = null;
    private int keyCount = 0;

    // Lock for key set and count
    private final Object keyLock = new Object();

    // Lock for registration and configureBlocking operations
    private final Object regLock = new Object();

    // Blocking mode, protected by regLock
    boolean blocking = true;

    /**
     * Initializes a new instance of this class.
     *
     * @param  provider
     *         The provider that created this channel
     */
    protected AbstractSelectableChannel(SelectorProvider provider) {
        this.provider = provider;
    }

    /**
     * Returns the provider that created this channel.
     *
     * @return  The provider that created this channel
     */
    public final SelectorProvider provider() {
        return provider;
    }


    // -- Utility methods for the key set --

    // 主要用于存储当前套接字通道已被注册至哪些多路复用筛选器中
    private void addKey(SelectionKey k) {
        // 外部必须已经持有该对象的内置锁
        assert Thread.holdsLock(keyLock);

        int i = 0;
        if ((keys != null) && (keyCount < keys.length)) {
            // Find empty element of key array
            for (i = 0; i < keys.length; i++)
                if (keys[i] == null)
                    break;
        } else if (keys == null) {
            // 初始化
            keys =  new SelectionKey[3];
        } else {
            // 扩容，每次扩容一倍
            // Grow key array
            int n = keys.length * 2;
            SelectionKey[] ks =  new SelectionKey[n];
            for (i = 0; i < keys.length; i++)
                ks[i] = keys[i];
            keys = ks;
            i = keyCount;
        }
        keys[i] = k;
        keyCount++;
    }

    private SelectionKey findKey(Selector sel) {
        synchronized (keyLock) {
            if (keys == null)
                return null;
            for (int i = 0; i < keys.length; i++)
                if ((keys[i] != null) && (keys[i].selector() == sel))
                    return keys[i];
            return null;
        }
    }

    void removeKey(SelectionKey k) {                    // package-private
        synchronized (keyLock) {
            for (int i = 0; i < keys.length; i++)
                if (keys[i] == k) {
                    keys[i] = null;
                    keyCount--;
                }
            ((AbstractSelectionKey)k).invalidate();
        }
    }

    // 检查当前套接字通道是否已经被加入到一个有效的多路复用筛选器中
    private boolean haveValidKeys() {
        synchronized (keyLock) {
            if (keyCount == 0)
                return false;
            for (int i = 0; i < keys.length; i++) {
                if ((keys[i] != null) && keys[i].isValid())
                    return true;
            }
            return false;
        }
    }


    // -- Registration --

    public final boolean isRegistered() {
        synchronized (keyLock) {
            return keyCount != 0;
        }
    }

    public final SelectionKey keyFor(Selector sel) {
        return findKey(sel);
    }

    /**
     * Registers this channel with the given selector, returning a selection key.
     *
     * <p>  This method first verifies that this channel is open and that the
     * given initial interest set is valid.
     *
     * <p> If this channel is already registered with the given selector then
     * the selection key representing that registration is returned after
     * setting its interest set to the given value.
     *
     * <p> Otherwise this channel has not yet been registered with the given
     * selector, so the {@link AbstractSelector#register register} method of
     * the selector is invoked while holding the appropriate locks.  The
     * resulting key is added to this channel's key set before being returned.
     * </p>
     *
     * @throws  ClosedSelectorException {@inheritDoc}
     *
     * @throws  IllegalBlockingModeException {@inheritDoc}
     *
     * @throws  IllegalSelectorException {@inheritDoc}
     *
     * @throws  CancelledKeyException {@inheritDoc}
     *
     * @throws  IllegalArgumentException {@inheritDoc}
     */
    // 将当前套接字通道注册到指定的多路复用筛选器|sel|中，以监听该通道的|ops|事件。返回一个筛选器
    // 令牌，该令牌中保存了套接字通道与多路复用筛选器的引用
    public final SelectionKey register(Selector sel, int ops,
                                       Object att)
        throws ClosedChannelException
    {
        synchronized (regLock) {
            // 当前套接字通道必须未被关闭
            if (!isOpen())
                throw new ClosedChannelException();

            // 验证注册的监听事件必须是被当前套接字通道所支持的
            // 注：比如服务器通道仅支持|SelectionKey.OP_ACCEPT|事件；而客户端套接字可以
            // 支持|OP_READ, OP_WRITE, OP_CONNECT|事件
            if ((ops & ~validOps()) != 0)
                throw new IllegalArgumentException();

            // 当前套接字通道必须是非阻塞模型
            if (blocking)
                throw new IllegalBlockingModeException();

            // 查找当前套接字通道是否已被注册过|sel|多路复用。若是，则只需修改通道的监听事件
            SelectionKey k = findKey(sel);

            // 若注册过，则修改套接字通道的监听事件、附加对象
            if (k != null) {
                k.interestOps(ops);
                k.attach(att);
            }
            // 若未注册过，则将当前套接字通道、事件类型注册至多路复用实例中
            if (k == null) {
                // New registration
                synchronized (keyLock) {
                    // 再次验证套接字通道关闭状态
                    if (!isOpen())
                        throw new ClosedChannelException();

                    // 将当前套接字通道、监听事件注册进多路复用中，并返回筛选器令牌
                    // 注：该筛选器令牌中保存了套接字通道与多路复用筛选器的引用
                    k = ((AbstractSelector)sel).register(this, ops, att);

                    // 将返回的筛选器令牌保存
                    // 注：主要用于存储当前套接字通道已被注册至哪些多路复用筛选器中
                    addKey(k);
                }
            }
            return k;
        }
    }


    // -- Closing --

    /**
     * Closes this channel.
     *
     * <p> This method, which is specified in the {@link
     * AbstractInterruptibleChannel} class and is invoked by the {@link
     * java.nio.channels.Channel#close close} method, in turn invokes the
     * {@link #implCloseSelectableChannel implCloseSelectableChannel} method in
     * order to perform the actual work of closing this channel.  It then
     * cancels all of this channel's keys.  </p>
     */
    // 关闭当前通道。并取消该通道注册的所有多路复用
    // 注1：总是会先执行一个预关闭动作（将通道的描述符指向一个半关闭的|socket pair|），从而使针对该
    // 通道的任何读写操作返回|EOF|或者|Pipe Error|。防止|fd|被回收，导致|ABA|问题
    // 注2：若在调用本关闭操作时，其他线程正在对当前通道执行|I/O|操作（执行|accept,read,write|方
    // 法），唤醒该|I/O|线程，当其继续执行|I/O|将会立即出错，从而触发最终的|close()|关闭
    // 注3：大部分场景中，最终的关闭动作并非由步骤二触发，而是由：自动取消当前通道注册的所有多路复用，
    // 所有被取消的筛选令牌被收集成一个集合，并在每次|select()|调用中被处理，触发最终的|close()|关闭
    protected final void implCloseChannel() throws IOException {
        // 关闭当前通道
        implCloseSelectableChannel();
        // 取消该通道注册的所有多路复用
        synchronized (keyLock) {
            int count = (keys == null) ? 0 : keys.length;
            for (int i = 0; i < count; i++) {
                SelectionKey k = keys[i];
                if (k != null)
                    k.cancel();
            }
        }
    }

    /**
     * Closes this selectable channel.
     *
     * <p> This method is invoked by the {@link java.nio.channels.Channel#close
     * close} method in order to perform the actual work of closing the
     * channel.  This method is only invoked if the channel has not yet been
     * closed, and it is never invoked more than once.
     *
     * <p> An implementation of this method must arrange for any other thread
     * that is blocked in an I/O operation upon this channel to return
     * immediately, either by throwing an exception or by returning normally.
     * </p>
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    // 关闭当前通道
    protected abstract void implCloseSelectableChannel() throws IOException;


    // -- Blocking --

    public final boolean isBlocking() {
        synchronized (regLock) {
            return blocking;
        }
    }

    public final Object blockingLock() {
        return regLock;
    }

    /**
     * Adjusts this channel's blocking mode.
     *
     * <p> If the given blocking mode is different from the current blocking
     * mode then this method invokes the {@link #implConfigureBlocking
     * implConfigureBlocking} method, while holding the appropriate locks, in
     * order to change the mode.  </p>
     */
    // 设置当前套接字通道的阻塞模式。若|block==true|，设置为阻塞模式；若|block==false|，设置
    // 为非阻塞模式
    // 注：如果给定的阻塞模式与当前的阻塞模式不同，调用|implConfigureBlocking()|方法
    public final SelectableChannel configureBlocking(boolean block)
        throws IOException
    {
        synchronized (regLock) {
            // 通道必须未被关闭
            if (!isOpen())
                throw new ClosedChannelException();

            // 若给定的阻塞模式与当前通道的阻塞模式相同，直接返回
            if (blocking == block)
                return this;

            // 检查当前套接字通道是否已经被加入到一个有效的多路复用筛选器中
            // 注：若已经加入到某个筛选器中，则当前通道将不能被设置为阻塞模式
            if (block && haveValidKeys())
                throw new IllegalBlockingModeException();

            // 设置套接字通道的阻塞模式
            implConfigureBlocking(block);

            // 保存设置的阻塞模式
            blocking = block;
        }
        return this;
    }

    /**
     * Adjusts this channel's blocking mode.
     *
     * <p> This method is invoked by the {@link #configureBlocking
     * configureBlocking} method in order to perform the actual work of
     * changing the blocking mode.  This method is only invoked if the new mode
     * is different from the current mode.  </p>
     *
     * @param  block  If <tt>true</tt> then this channel will be placed in
     *                blocking mode; if <tt>false</tt> then it will be placed
     *                non-blocking mode
     *
     * @throws IOException
     *         If an I/O error occurs
     */
    // 设置当前套接字通道的阻塞模式。是|configureBlocking()|的底层具体实现方法
    // 注：底层使用|fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) & (block?~O_NONBLOCK:O_NONBLOCK))|
    protected abstract void implConfigureBlocking(boolean block)
        throws IOException;

}
