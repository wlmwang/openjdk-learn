/*
 * Copyright (c) 2007, 2008, Oracle and/or its affiliates. All rights reserved.
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
package java.net;

import java.io.IOException;
import java.io.FileDescriptor;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import jdk.net.*;

import static sun.net.ExtendedOptionsImpl.*;

/*
 * On Unix systems we simply delegate to native methods.
 *
 * @author Chris Hegarty
 */

class PlainSocketImpl extends AbstractPlainSocketImpl
{
    static {
        initProto();
    }

    /**
     * Constructs an empty instance.
     */
    PlainSocketImpl() { }

    /**
     * Constructs an instance with the given file descriptor.
     */
    PlainSocketImpl(FileDescriptor fd) {
        this.fd = fd;
    }

    protected <T> void setOption(SocketOption<T> name, T value) throws IOException {
        if (!name.equals(ExtendedSocketOptions.SO_FLOW_SLA)) {
            super.setOption(name, value);
        } else {
            if (isClosedOrPending()) {
                throw new SocketException("Socket closed");
            }
            checkSetOptionPermission(name);
            checkValueType(value, SocketFlow.class);
            setFlowOption(getFileDescriptor(), (SocketFlow)value);
        }
    }

    protected <T> T getOption(SocketOption<T> name) throws IOException {
        if (!name.equals(ExtendedSocketOptions.SO_FLOW_SLA)) {
            return super.getOption(name);
        }
        if (isClosedOrPending()) {
            throw new SocketException("Socket closed");
        }
        checkGetOptionPermission(name);
        SocketFlow flow = SocketFlow.create();
        getFlowOption(getFileDescriptor(), flow);
        return (T)flow;
    }

    // 在|OS|层创建一个网络套接字，并将描述符设置到|fd.fd|字段上
    // 注：参数|isServer|表示：创建的套接字是流|SOCK_STREAM|、还是报文|SOCK_DGRAM|套接字
    // 注：若是一个服务端套接字，则将其设置为|O_NONBLOCK|、|SO_REUSEADDR|
    // 注：底层调用|socket(AF_INET, SOCK_STREAM, 0)|创建套接字，当然协议簇也可以是|AF_INET6|
    native void socketCreate(boolean isServer) throws IOException;

    // 在|OS|层将套接字连接到指定的服务端的套接字地址上。该方法会一直阻塞，直到连接被建立、或发现异常
    // 注：当|timeout==0|时，连接无超时限制；当|timeout>0|时，则在超时前仍未建立连接时，抛出异常
    // 注：当连接成功，服务端的地址与端口分别被设置到|adress|和|port|字段上；而客户端的描述符将被设置
    // 到|fd.fd|字段上，客户端实际绑定端口将被设置到|localPort|字段上
    // 注：底层使用|poll(&pfd, 1, timeout)|实现超时连接服务端，其中|pfd.events=POLLOUT|。文件描
    // 述符连接前，会被设置为|O_NONBLOCK|，并在连接后，非阻塞状态自动重去掉
    // 注：底层使用|connect(fd, (struct sockaddr *)&him, len)|连接服务器，并使用|getsockopt|
    // 获取连接的错误信息，即|getsockopt(fd, SOL_SOCKET, SO_ERROR, (void*)&connect_rv, &optlen)|
    native void socketConnect(InetAddress address, int port, int timeout)
        throws IOException;

    // 在|OS|层将套接字与网络地址、端口号进行绑定，并将地址设置到|address|、以及将实际端口号设置到|localPort|字段上
    // 注：设置绑定的端口号到|localPort|字段上时，若端口号参数为|0|，则需要获取实际的绑定端口号
    // 注：底层调用|bind(fd, (struct sockaddr*) &him, sizeof(struct sockaddr_in))|，也可以是|sockaddr_in6|
    native void socketBind(InetAddress address, int port)
        throws IOException;

    // 在|OS|层将套接字设置为监听套接字，其最大挂起连接数为|backlog|
    // 注：此时套接字状态将被设置为|LISTEN|。即，服务端套接字，准备接受客户端连接
    // 注：底层调用|listen(fd, backlog)|
    native void socketListen(int count) throws IOException;

    // 在|OS|层监听并接受一个客户端连接，该方法会一直阻塞，直到一个连接被创建并设置到|s|参数中
    // 注：若设置了|timeout|超时限制，在超时前仍未获得连接时，将会抛出|SocketTimeoutException|异常
    // 注：底层使用|poll(&pfd, 1, timeout)|实现超时并监听客户端连接，其中|pfd.events=POLLIN|POLLERR|
    // 注：底层使用|accept(fd, (struct sockaddr *)&him, (socklen_t *)&len)|接受客户端连接
    native void socketAccept(SocketImpl s) throws IOException;

    // 返回可以从此输入流读取或跳过的剩余字节数的估计值，而不会使下一次读取或跳过这么多字节时被阻塞
    // 注：系统调用|ioctl(fd, FIONREAD, &n)|可以得到描述符|fd|的缓冲区里有多少字节可被读取
    native int socketAvailable() throws IOException;

    // 关闭一个文件描述符。若参数为|true|时，则是预关闭套接字（不释放|OS|层文件描述符）；若为|false|时，
    // 则是实际的套接字的关闭（释放|OS|层文件描述符）
    // 注：预关闭是指，当前的文件描述符句柄在|OS|层并未释放，但此时它已经被重新定位至|marker_fd|上
    // 注：底层使用|dup2(marker_fd, fd)|实现文件描述符|fd|的预关闭。其中|marker_fd|是一个使用
    // |socketpair(AF_UNIX), SOCK_STREAM, 0, sv)|创建的套接字对中的|sv[0]|，且该套接字任何
    // 读取都将得到|EOF|，而写入将会得到错误，因为|shutdown(sv[0], 2)|，|close(sv[1])|
    // 注：不释放|OS|层文件描述符，主要为了在缓冲区中已发送的数据能顺利发送到对端。由于未传输的数据以
    // 及较长的传输延迟间隔，此操作可能会很长
    native void socketClose0(boolean useDeferredClose) throws IOException;

    native void socketShutdown(int howto) throws IOException;

    static native void initProto();

    native void socketSetOption(int cmd, boolean on, Object value)
        throws SocketException;

    native int socketGetOption(int opt, Object iaContainerObj) throws SocketException;

    native void socketSendUrgentData(int data) throws IOException;
}
