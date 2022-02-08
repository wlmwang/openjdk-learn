/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Package private implementation of InetAddressImpl for dual
 * IPv4/IPv6 stack.
 * <p>
 * If InetAddress.preferIPv6Address is true then anyLocalAddress(),
 * loopbackAddress(), and localHost() will return IPv6 addresses,
 * otherwise IPv4 addresses.
 *
 * @since 1.4
 */

class Inet6AddressImpl implements InetAddressImpl {
    // 获取本机的主机名，如果获取失败，抛出异常
    // 注：先使用|getaddrinfo(hostname, NULL, &hints, &res)|，而|hints={ai_flags=AI_CANONNAME, ai_family=AF_UNSPEC}|
    // 再使用|getnameinfo(res->ai_addr, res->ai_addrlen, hostname, NI_MAXHOST, NULL, 0, NI_NAMEREQD)|
    public native String getLocalHostName() throws UnknownHostException;

    // 将主机名解析为|IPv6|的网络地址，如果解析失败，抛出异常
    // 注：底层使用|getaddrinfo(hostname, NULL, &hints, &res)|，而|hints={ai_flags=AI_CANONNAME, ai_family=AF_UNSPEC}|
    public native InetAddress[]
        lookupAllHostAddr(String hostname) throws UnknownHostException;
    public native String getHostByAddr(byte[] addr) throws UnknownHostException;
    private native boolean isReachable0(byte[] addr, int scope, int timeout, byte[] inf, int ttl, int if_scope) throws IOException;

    public boolean isReachable(InetAddress addr, int timeout, NetworkInterface netif, int ttl) throws IOException {
        byte[] ifaddr = null;
        int scope = -1;
        int netif_scope = -1;
        if (netif != null) {
            /*
             * Let's make sure we bind to an address of the proper family.
             * Which means same family as addr because at this point it could
             * be either an IPv6 address or an IPv4 address (case of a dual
             * stack system).
             */
            java.util.Enumeration<InetAddress> it = netif.getInetAddresses();
            InetAddress inetaddr = null;
            while (it.hasMoreElements()) {
                inetaddr = it.nextElement();
                if (inetaddr.getClass().isInstance(addr)) {
                    ifaddr = inetaddr.getAddress();
                    if (inetaddr instanceof Inet6Address) {
                        netif_scope = ((Inet6Address) inetaddr).getScopeId();
                    }
                    break;
                }
            }
            if (ifaddr == null) {
                // Interface doesn't support the address family of
                // the destination
                return false;
            }
        }
        if (addr instanceof Inet6Address)
            scope = ((Inet6Address) addr).getScopeId();
        return isReachable0(addr.getAddress(), scope, timeout, ifaddr, ttl, netif_scope);
    }

    public synchronized InetAddress anyLocalAddress() {
        if (anyLocalAddress == null) {
            if (InetAddress.preferIPv6Address) {
                anyLocalAddress = new Inet6Address();
                anyLocalAddress.holder().hostName = "::";
            } else {
                anyLocalAddress = (new Inet4AddressImpl()).anyLocalAddress();
            }
        }
        return anyLocalAddress;
    }

    // 返回|IPv6|的回环地址|hostname=localhost, address=::1|
    public synchronized InetAddress loopbackAddress() {
        if (loopbackAddress == null) {
             if (InetAddress.preferIPv6Address) {
                 byte[] loopback =
                        {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01};
                 loopbackAddress = new Inet6Address("localhost", loopback);
             } else {
                loopbackAddress = (new Inet4AddressImpl()).loopbackAddress();
             }
        }
        return loopbackAddress;
    }

    private InetAddress      anyLocalAddress;
    private InetAddress      loopbackAddress;
}
