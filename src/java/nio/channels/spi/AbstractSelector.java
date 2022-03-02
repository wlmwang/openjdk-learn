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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.Set;
import sun.nio.ch.Interruptible;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Base implementation class for selectors.
 *
 * <p> This class encapsulates the low-level machinery required to implement
 * the interruption of selection operations.  A concrete selector class must
 * invoke the {@link #begin begin} and {@link #end end} methods before and
 * after, respectively, invoking an I/O operation that might block
 * indefinitely.  In order to ensure that the {@link #end end} method is always
 * invoked, these methods should be used within a
 * <tt>try</tt>&nbsp;...&nbsp;<tt>finally</tt> block:
 *
 * <blockquote><pre>
 * try {
 *     begin();
 *     // Perform blocking I/O operation here
 *     ...
 * } finally {
 *     end();
 * }</pre></blockquote>
 *
 * <p> This class also defines methods for maintaining a selector's
 * cancelled-key set and for removing a key from its channel's key set, and
 * declares the abstract {@link #register register} method that is invoked by a
 * selectable channel's {@link AbstractSelectableChannel#register register}
 * method in order to perform the actual work of registering a channel.  </p>
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public abstract class AbstractSelector
    extends Selector
{

    // 初始化当前筛选器为打开状态
    private AtomicBoolean selectorOpen = new AtomicBoolean(true);

    // The provider that created this selector
    private final SelectorProvider provider;

    /**
     * Initializes a new instance of this class.
     *
     * @param  provider
     *         The provider that created this selector
     */
    protected AbstractSelector(SelectorProvider provider) {
        this.provider = provider;
    }

    // 已经被取消的筛选器令牌集合
    private final Set<SelectionKey> cancelledKeys = new HashSet<SelectionKey>();

    // 取消当前的筛选器令牌
    void cancel(SelectionKey k) {                       // package-private
        synchronized (cancelledKeys) {
            cancelledKeys.add(k);
        }
    }

    /**
     * Closes this selector.
     *
     * <p> If the selector has already been closed then this method returns
     * immediately.  Otherwise it marks the selector as closed and then invokes
     * the {@link #implCloseSelector implCloseSelector} method in order to
     * complete the close operation.  </p>
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public final void close() throws IOException {
        boolean open = selectorOpen.getAndSet(false);
        if (!open)
            return;
        implCloseSelector();
    }

    /**
     * Closes this selector.
     *
     * <p> This method is invoked by the {@link #close close} method in order
     * to perform the actual work of closing the selector.  This method is only
     * invoked if the selector has not yet been closed, and it is never invoked
     * more than once.
     *
     * <p> An implementation of this method must arrange for any other thread
     * that is blocked in a selection operation upon this selector to return
     * immediately as if by invoking the {@link
     * java.nio.channels.Selector#wakeup wakeup} method. </p>
     *
     * @throws  IOException
     *          If an I/O error occurs while closing the selector
     */
    protected abstract void implCloseSelector() throws IOException;

    // 获取当前筛选器是否处于打开状态
    public final boolean isOpen() {
        return selectorOpen.get();
    }

    /**
     * Returns the provider that created this channel.
     *
     * @return  The provider that created this channel
     */
    public final SelectorProvider provider() {
        return provider;
    }

    /**
     * Retrieves this selector's cancelled-key set.
     *
     * <p> This set should only be used while synchronized upon it.  </p>
     *
     * @return  The cancelled-key set
     */
    protected final Set<SelectionKey> cancelledKeys() {
        return cancelledKeys;
    }

    /**
     * Registers the given channel with this selector.
     *
     * <p> This method is invoked by a channel's {@link
     * AbstractSelectableChannel#register register} method in order to perform
     * the actual work of registering the channel with this selector.  </p>
     *
     * @param  ch
     *         The channel to be registered
     *
     * @param  ops
     *         The initial interest set, which must be valid
     *
     * @param  att
     *         The initial attachment for the resulting key
     *
     * @return  A new key representing the registration of the given channel
     *          with this selector
     */
    // 将指定的套接字通道注册到当前多路复用筛选器中，以监听该通道的|ops|事件。返回一个筛选器
    // 令牌，该令牌中保存了套接字通道与多路复用筛选器的引用
    protected abstract SelectionKey register(AbstractSelectableChannel ch,
                                             int ops, Object att);

    /**
     * Removes the given key from its channel's key set.
     *
     * <p> This method must be invoked by the selector for each channel that it
     * deregisters.  </p>
     *
     * @param  key
     *         The selection key to be removed
     */
    protected final void deregister(AbstractSelectionKey key) {
        ((AbstractSelectableChannel)key.channel()).removeKey(key);
    }


    // -- Interruption machinery --

    private Interruptible interruptor = null;

    /**
     * Marks the beginning of an I/O operation that might block indefinitely.
     *
     * <p> This method should be invoked in tandem with the {@link #end end}
     * method, using a <tt>try</tt>&nbsp;...&nbsp;<tt>finally</tt> block as
     * shown <a href="#be">above</a>, in order to implement interruption for
     * this selector.
     *
     * <p> Invoking this method arranges for the selector's {@link
     * Selector#wakeup wakeup} method to be invoked if a thread's {@link
     * Thread#interrupt interrupt} method is invoked while the thread is
     * blocked in an I/O operation upon the selector.  </p>
     */
    // 标记一个可能无限期阻塞|I/O|的开始。此方法应与|end()|方法一起调用，以实现筛选器中断可响应
    // 注：本质上，此处仅仅是注册一个当前线程中断时，可自动调用的钩子方法
    protected final void begin() {
        if (interruptor == null) {
            interruptor = new Interruptible() {
                    public void interrupt(Thread ignore) {
                        // 唤醒多路复用筛选器，以唤醒阻塞的线程
                        AbstractSelector.this.wakeup();
                    }};
        }
        // 设置线程中断的钩子方法。即，当线程中断时，自动执行|interruptor.interrupt()|方法
        AbstractInterruptibleChannel.blockedOn(interruptor);

        // 在标记时，当前线程已经被中断，立即执行中断方法
        Thread me = Thread.currentThread();
        if (me.isInterrupted())
            interruptor.interrupt(me);
    }

    /**
     * Marks the end of an I/O operation that might block indefinitely.
     *
     * <p> This method should be invoked in tandem with the {@link #begin begin}
     * method, using a <tt>try</tt>&nbsp;...&nbsp;<tt>finally</tt> block as
     * shown <a href="#be">above</a>, in order to implement interruption for
     * this selector.  </p>
     */
    // 标记一个可能无限期阻塞|I/O|的结束。此方法应与|begin()|方法一起调用，以实现筛选器中断可响应
    protected final void end() {
        // 清除线程中断的钩子方法
        AbstractInterruptibleChannel.blockedOn(null);
    }

}
