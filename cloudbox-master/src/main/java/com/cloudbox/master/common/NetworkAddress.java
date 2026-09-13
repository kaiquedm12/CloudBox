package com.cloudbox.master.common;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NetworkAddress {

    private static final Pattern DNS_LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private static final Pattern DOTTED_NUMERIC = Pattern.compile("[0-9.]+");

    private NetworkAddress() {
    }

    public static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean isIpLiteral(String value) {
        String candidate = normalizeOptional(value);
        if (candidate == null || candidate.contains("%")) {
            return false;
        }
        return candidate.contains(":") ? ipv6(candidate) != null : ipv4(candidate) != null;
    }

    public static boolean isValidAdvertiseAddress(String value) {
        String candidate = normalizeOptional(value);
        if (candidate == null) {
            return true;
        }
        if (isIpLiteral(candidate)) {
            return !isWildcard(candidate);
        }
        return isDnsHostname(candidate);
    }

    public static boolean isWildcard(String value) {
        InetAddress address = literal(value);
        return address != null && address.isAnyLocalAddress();
    }

    public static boolean isIpv6Literal(String value) {
        return ipv6(normalizeOptional(value)) != null;
    }

    public static boolean sameIp(String first, String second) {
        InetAddress firstAddress = literal(first);
        InetAddress secondAddress = literal(second);
        return firstAddress != null && secondAddress != null
                && firstAddress.getClass().equals(secondAddress.getClass())
                && java.util.Arrays.equals(firstAddress.getAddress(), secondAddress.getAddress());
    }

    public static String normalizedHostname(String value) {
        String candidate = normalizeOptional(value);
        return candidate == null ? null : candidate.toLowerCase(Locale.ROOT);
    }

    private static boolean isDnsHostname(String value) {
        if (value.length() > 253 || value.contains(":") || value.contains("/")
                || value.contains("\\") || value.contains("@") || value.contains("?")
                || value.contains("#") || value.contains("[") || value.contains("]")
                || DOTTED_NUMERIC.matcher(value).matches()) {
            return false;
        }
        String hostname = value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
        if (hostname.isEmpty()) {
            return false;
        }
        for (String label : hostname.split("\\.", -1)) {
            if (!DNS_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }

    private static InetAddress literal(String value) {
        String candidate = normalizeOptional(value);
        if (candidate == null) {
            return null;
        }
        return candidate.contains(":") ? ipv6(candidate) : ipv4(candidate);
    }

    private static InetAddress ipv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].isEmpty() || parts[index].length() > 3) {
                return null;
            }
            if (parts[index].length() > 1 && parts[index].startsWith("0")) {
                return null;
            }
            for (int character = 0; character < parts[index].length(); character++) {
                char digit = parts[index].charAt(character);
                if (digit < '0' || digit > '9') {
                    return null;
                }
            }
            int octet = Integer.parseInt(parts[index]);
            if (octet > 255) {
                return null;
            }
            bytes[index] = (byte) octet;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static InetAddress ipv6(String value) {
        if (value == null || !value.contains(":") || value.contains("%")
                || value.contains("[") || value.contains("]")) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
