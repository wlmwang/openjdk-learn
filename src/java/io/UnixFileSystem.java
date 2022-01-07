/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessController;
import sun.security.action.GetPropertyAction;


class UnixFileSystem extends FileSystem {

    private final char slash;
    private final char colon;
    private final String javaHome;

    public UnixFileSystem() {
        // 同一个路径字符串的分隔符。比如"path/test1.jar"分隔符为"/"
        // 连续多个路径字符串的分隔符。比如"test1.jar;test2.jar"分隔符为";"
        slash = AccessController.doPrivileged(
            new GetPropertyAction("file.separator")).charAt(0);
        colon = AccessController.doPrivileged(
            new GetPropertyAction("path.separator")).charAt(0);
        javaHome = AccessController.doPrivileged(
            new GetPropertyAction("java.home"));
    }


    /* -- Normalization and construction -- */

    // 同一个路径字符串的分隔符。比如"path/test1.jar"分隔符为"/"
    public char getSeparator() {
        return slash;
    }

    // 连续多个路径字符串的分隔符。比如"test1.jar;test2.jar"分隔符为";"
    public char getPathSeparator() {
        return colon;
    }

    /* A normal Unix pathname contains no duplicate slashes and does not end
       with a slash.  It may be the empty string. */

    /* Normalize the given pathname, whose length is len, starting at the given
       offset; everything before this offset is already normal. */
    // 将给定的路径进行通用处理。包括：将连续'\\'字符转换成'\'；将结尾所有'\'字符去除
    // 注：|len|是路径字符串总长度；|off|是第一个连续'\\'字符串出现之前的索引值
    private String normalize(String pathname, int len, int off) {
        if (len == 0) return pathname;
        int n = len;
        // 循环检查结尾字符串是否为'\'，将超尾索引|n|递减，直到第一个不为'\'为止
        // 注：将结尾所有'\'字符去除
        while ((n > 0) && (pathname.charAt(n - 1) == '/')) n--;

        // 路径仅包括根目录字符
        if (n == 0) return "/";

        // |off|前字符无需通用化处理，直接拷贝到最终结果
        StringBuffer sb = new StringBuffer(pathname.length());
        if (off > 0) sb.append(pathname.substring(0, off));

        // 将连续'\\'字符转换成'\'
        char prevChar = 0;
        for (int i = off; i < n; i++) {
            char c = pathname.charAt(i);
            if ((prevChar == '/') && (c == '/')) continue;
            sb.append(c);
            prevChar = c;
        }
        return sb.toString();
    }

    /* Check that the given pathname is normal.  If not, invoke the real
       normalizer on the part of the pathname that requires normalization.
       This way we iterate through the whole pathname string only once. */
    // 将给定的路径进行通用处理。包括：将连续'\\'字符转换成'\'；将结尾所有'\'字符去除
    public String normalize(String pathname) {
        int n = pathname.length();
        char prevChar = 0;
        for (int i = 0; i < n; i++) {
            char c = pathname.charAt(i);
            // 搜索到第一个连续'\\'字符
            if ((prevChar == '/') && (c == '/'))
                return normalize(pathname, n, i - 1);
            prevChar = c;
        }
        // 将结尾所有'\'字符去除
        if (prevChar == '/') return normalize(pathname, n, n - 1);
        return pathname;
    }

    // 计算给定的路径前缀的长度。在|UNIX|平台上，若首字符为'\'，返回1；否则返回0
    // 注：在|Win|平台上，若路径前缀是"\\"，则返回1，若为"\\\\"，则返回2，若路径前缀
    // 含有驱动器，是"C:\\"，则返回3；都不是的话，则返回0
    public int prefixLength(String pathname) {
        if (pathname.length() == 0) return 0;
        return (pathname.charAt(0) == '/') ? 1 : 0;
    }

    // 拼接父子路径。|path|最终是一个规范化的|parent + '/' + child|路径
    // 注：子路径没有所谓的绝对路径，它始终需要和父路径进行拼接（除非父路径为"\"，没有拼接的必要）
    public String resolve(String parent, String child) {
        if (child.equals("")) return parent;
        if (child.charAt(0) == '/') {
            if (parent.equals("/")) return child;
            return parent + child;
        }
        if (parent.equals("/")) return parent + child;
        return parent + '/' + child;
    }

    public String getDefaultParent() {
        return "/";
    }

    // 对给定的|URI|路径字符串进行处理。在|UNIX|中，去除结尾"/"字符
    // 注：在|WIN|中，会将"/c:/foo"转换为"c:/foo"
    public String fromURIPath(String path) {
        String p = path;
        if (p.endsWith("/") && (p.length() > 1)) {
            // "/foo/" --> "/foo", but "/" --> "/"
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }


    /* -- Path operations -- */

    public boolean isAbsolute(File f) {
        return (f.getPrefixLength() != 0);
    }

    // 获取|f|文件的绝对路径
    // 注：若|f|文件的路径是相对路径，自定拼接父目录|System.getProperty("user.dir")|
    public String resolve(File f) {
        if (isAbsolute(f)) return f.getPath();
        return resolve(System.getProperty("user.dir"), f.getPath());
    }

    // Caches for canonicalization results to improve startup performance.
    // The first cache handles repeated canonicalizations of the same path
    // name. The prefix cache handles repeated canonicalizations within the
    // same directory, and must not create results differing from the true
    // canonicalization algorithm in canonicalize_md.c. For this reason the
    // prefix cache is conservative and is not used for complex path names.
    private ExpiringCache cache = new ExpiringCache();
    // On Unix symlinks can jump anywhere in the file system, so we only
    // treat prefixes in java.home as trusted and cacheable in the
    // canonicalization algorithm
    private ExpiringCache javaHomePrefixCache = new ExpiringCache();

    // 获取文件路径的绝对唯一的标准规范路径名。此方法，首先将此路径名转换为绝对形式，就像调
    // 用|getAbsolutePath()|方法一样，然后以系统相关的方式将其映射到其唯一路径上
    // 注：如果路径中包含"."或".."路径表示法，则会从路径名中删除，并使用真实路径代替。另外，
    // 比较重要的是，它还会解析软链接（在|UNIX|平台上）以及将驱动器号（在|Win|平台上），将
    // 它们转换为标准实际路径
    public String canonicalize(String path) throws IOException {
        if (!useCanonCaches) {
            return canonicalize0(path);
        } else {
            // 使用路径的缓存。默认
            String res = cache.get(path);
            if (res == null) {  // 路径缓存失效
                String dir = null;
                String resDir = null;
                // 使用目录规范化的缓存。默认
                if (useCanonPrefixCache) {
                    // Note that this can cause symlinks that should
                    // be resolved to a destination directory to be
                    // resolved to the directory they're contained in
                    // 删除父路径中的包含"."或".."路径表示法
                    dir = parentOrNull(path);
                    if (dir != null) {
                        resDir = javaHomePrefixCache.get(dir);
                        if (resDir != null) {
                            // Hit only in prefix cache; full path is canonical
                            String filename = path.substring(1 + dir.length());
                            res = resDir + slash + filename;
                            cache.put(dir + slash + filename, res);
                        }
                    }
                }
                if (res == null) {
                    // 获取路径的规范化路径
                    res = canonicalize0(path);
                    cache.put(path, res);
                    if (useCanonPrefixCache &&
                        dir != null && dir.startsWith(javaHome)) {
                        resDir = parentOrNull(res);
                        // Note that we don't allow a resolved symlink
                        // to elsewhere in java.home to pollute the
                        // prefix cache (java.home prefix cache could
                        // just as easily be a set at this point)
                        if (resDir != null && resDir.equals(dir)) {
                            File f = new File(res);
                            if (f.exists() && !f.isDirectory()) {
                                javaHomePrefixCache.put(dir, resDir);
                            }
                        }
                    }
                }
            }
            return res;
        }
    }
    // 获取文件路径的绝对唯一的标准规范路径名。此方法，首先将此路径名转换为绝对形式，就像调
    // 用|getAbsolutePath()|方法一样，然后以系统相关的方式将其映射到其唯一路径上
    // 注：如果路径中包含"."或".."路径表示法，则会从路径名中删除，并使用真实路径代替。另外，
    // 比较重要的是，它还会解析软链接（在|UNIX|平台上）以及将驱动器号（在|Win|平台上），将
    // 它们转换为标准实际路径
    // 注：底层核心是使用了|realpath()|系统调用
    private native String canonicalize0(String path) throws IOException;
    // Best-effort attempt to get parent of this path; used for
    // optimization of filename canonicalization. This must return null for
    // any cases where the code in canonicalize_md.c would throw an
    // exception or otherwise deal with non-simple pathnames like handling
    // of "." and "..". It may conservatively return null in other
    // situations as well. Returning null will cause the underlying
    // (expensive) canonicalization routine to be called.
    // 删除父路径中的包含"."或".."路径表示法
    static String parentOrNull(String path) {
        if (path == null) return null;
        char sep = File.separatorChar;
        int last = path.length() - 1;
        int idx = last;
        int adjacentDots = 0;
        int nonDotCount = 0;
        while (idx > 0) {
            char c = path.charAt(idx);
            if (c == '.') {
                if (++adjacentDots >= 2) {
                    // Punt on pathnames containing . and ..
                    return null;
                }
            } else if (c == sep) {
                if (adjacentDots == 1 && nonDotCount == 0) {
                    // Punt on pathnames containing . and ..
                    return null;
                }
                if (idx == 0 ||
                    idx >= last - 1 ||
                    path.charAt(idx - 1) == sep) {
                    // Punt on pathnames containing adjacent slashes
                    // toward the end
                    return null;
                }
                return path.substring(0, idx);
            } else {
                ++nonDotCount;
                adjacentDots = 0;
            }
            --idx;
        }
        return null;
    }

    /* -- Attribute accessors -- */

    // 底层使用了|stat64()|系统调用来获取文件属性
    // 注：获取的属性包括：文件|S_IFREG=0100000|、目录|S_IFDIR=0040000|
    public native int getBooleanAttributes0(File f);

    // 返回|f|的部分文件属性。底层使用了|stat64()|系统调用来获取文件属性
    // 注：属性包括：文件|BA_REGULAR|、目录|BA_DIRECTORY|、隐藏|BA_HIDDEN|、存在|BA_EXISTS|
    public int getBooleanAttributes(File f) {
        int rv = getBooleanAttributes0(f);
        String name = f.getName();  // 获取路径中文件名
        // 文件是否是隐藏文件（以'.'开头命名的文件）
        boolean hidden = (name.length() > 0) && (name.charAt(0) == '.');
        return rv | (hidden ? BA_HIDDEN : 0);
    }

    // 检查文件访问权限。底层使用|access()|系统调用
    public native boolean checkAccess(File f, int access);
    // 获取文件最后修改时间。底层使用|stat64()|系统调用，返回|stat64.st_mtime|
    public native long getLastModifiedTime(File f);
    // 获取文件长度。底层使用|stat64()|系统调用，返回|stat64.st_size|
    public native long getLength(File f);
    public native boolean setPermission(File f, int access, boolean enable, boolean owneronly);

    /* -- File operations -- */

    // 当文件不存在时，以原子方式创建它。底层使用|open(path, O_RDWR|O_CREAT|O_EXCL,0666)|系统调用
    // 注：创建文件成功后，会被自动|close()|关闭掉
    public native boolean createFileExclusively(String path)
        throws IOException;
    public boolean delete(File f) {
        // Keep canonicalization caches in sync after file deletion
        // and renaming operations. Could be more clever than this
        // (i.e., only remove/update affected entries) but probably
        // not worth it since these entries expire after 30 seconds
        // anyway.
        cache.clear();
        javaHomePrefixCache.clear();
        return delete0(f);
    }
    // 删除|f|表示的文件或目录。如果|f|表示一个目录，则该目录必须为空才能被删除。底层使用|remove()|系统调用
    // 注：系统调用|remove()|操作目录时，类似于|rmdir()|；操作文件时，类似于|unlink()|
    private native boolean delete0(File f);
    public native String[] list(File f);
    public native boolean createDirectory(File f);
    public boolean rename(File f1, File f2) {
        // Keep canonicalization caches in sync after file deletion
        // and renaming operations. Could be more clever than this
        // (i.e., only remove/update affected entries) but probably
        // not worth it since these entries expire after 30 seconds
        // anyway.
        cache.clear();
        javaHomePrefixCache.clear();
        return rename0(f1, f2);
    }
    private native boolean rename0(File f1, File f2);
    public native boolean setLastModifiedTime(File f, long time);
    public native boolean setReadOnly(File f);


    /* -- Filesystem interface -- */

    public File[] listRoots() {
        try {
            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                security.checkRead("/");
            }
            return new File[] { new File("/") };
        } catch (SecurityException x) {
            return new File[0];
        }
    }

    /* -- Disk usage -- */
    public native long getSpace(File f, int t);

    /* -- Basic infrastructure -- */

    public int compare(File f1, File f2) {
        return f1.getPath().compareTo(f2.getPath());
    }

    public int hashCode(File f) {
        return f.getPath().hashCode() ^ 1234321;
    }


    private static native void initIDs();

    static {
        initIDs();
    }

}
