package com.nebula.uid.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * 网络工具类，提供本机 IP 地址获取能力。
 *
 * <p>派生自 baidu/uid-generator (Apache License 2.0)</p>
 *
 * @author nebula
 */
public abstract class NetUtils {

    /** 预加载的本机地址 */
    public static InetAddress localAddress;

    static {
        try {
            localAddress = getLocalInetAddress();
        } catch (SocketException e) {
            throw new RuntimeException("fail to get local ip.");
        }
    }

    /**
     * 获取第一个可用的本机 IP 地址（公网与局域网地址均视为有效）
     */
    public static InetAddress getLocalInetAddress() throws SocketException {
        Enumeration<NetworkInterface> enu = NetworkInterface.getNetworkInterfaces();

        while (enu.hasMoreElements()) {
            NetworkInterface ni = enu.nextElement();
            if (ni.isLoopback()) {
                continue;
            }

            Enumeration<InetAddress> addressEnumeration = ni.getInetAddresses();
            while (addressEnumeration.hasMoreElements()) {
                InetAddress address = addressEnumeration.nextElement();

                if (address.isLinkLocalAddress() || address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                    continue;
                }

                return address;
            }
        }

        throw new RuntimeException("No validated local address!");
    }

    /**
     * 返回本机 IP 地址字符串
     */
    public static String getLocalAddress() {
        return localAddress.getHostAddress();
    }
}
