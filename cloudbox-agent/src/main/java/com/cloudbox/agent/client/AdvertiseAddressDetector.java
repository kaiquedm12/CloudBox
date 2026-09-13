package com.cloudbox.agent.client;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

final class AdvertiseAddressDetector {

    private AdvertiseAddressDetector() {
    }

    static String detect(String masterUrl) {
        String routedAddress = detectAddressUsedToReachMaster(masterUrl);
        return routedAddress != null ? routedAddress : detectFromNetworkInterfaces();
    }

    private static String detectAddressUsedToReachMaster(String masterUrl) {
        try {
            URI uri = new URI(masterUrl);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }

            int port = uri.getPort();
            if (port < 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName(host), port);
                return selectBestAddress(List.of(socket.getLocalAddress()));
            }
        } catch (URISyntaxException | UnknownHostException | SocketException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static String detectFromNetworkInterfaces() {
        try {
            List<InetAddress> addresses = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return null;
            }

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> interfaceAddresses = networkInterface.getInetAddresses();
                while (interfaceAddresses.hasMoreElements()) {
                    addresses.add(interfaceAddresses.nextElement());
                }
            }
            return selectBestAddress(addresses);
        } catch (SocketException exception) {
            return null;
        }
    }

    static String selectBestAddress(List<InetAddress> addresses) {
        return addresses.stream()
                .filter(AdvertiseAddressDetector::isUsable)
                .sorted(Comparator
                        .comparing((InetAddress address) -> !(address instanceof Inet4Address))
                        .thenComparing(address -> !address.isSiteLocalAddress()))
                .map(InetAddress::getHostAddress)
                .findFirst()
                .orElse(null);
    }

    private static boolean isUsable(InetAddress address) {
        return address != null
                && !address.isAnyLocalAddress()
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isMulticastAddress();
    }
}
