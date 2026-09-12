package com.cloudbox.agent.client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public final class NetworkAddressValidator {

    private static final int MAX_DNS_NAME_LENGTH = 253;
    private static final Pattern DNS_LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");

    private NetworkAddressValidator() {
    }

    public static String normalizeAdvertiseAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String address = value.trim();
        InetAddress literal = parseIpLiteral(address);
        if (literal != null) {
            if (literal.isAnyLocalAddress()) {
                throw new IllegalArgumentException("AGENT_ADVERTISE_ADDRESS nao pode ser wildcard");
            }
            return address;
        }

        if (!isDnsName(address)) {
            throw new IllegalArgumentException(
                    "AGENT_ADVERTISE_ADDRESS deve ser um hostname DNS ou IP literal sem esquema, porta ou caminho");
        }
        return address;
    }

    public static String requireIpLiteral(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " deve ser um IP literal");
        }
        String address = value.trim();
        if (parseIpLiteral(address) == null) {
            throw new IllegalArgumentException(fieldName + " deve ser um IP literal IPv4 ou IPv6");
        }
        return address;
    }

    private static InetAddress parseIpLiteral(String value) {
        if (value.indexOf(':') >= 0) {
            if (value.indexOf('%') >= 0 || value.indexOf('[') >= 0 || value.indexOf(']') >= 0
                    || value.indexOf('/') >= 0) {
                return null;
            }
            try {
                return InetAddress.getByName(value);
            } catch (UnknownHostException exception) {
                return null;
            }
        }

        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty() || octet.length() > 3 || (octet.length() > 1 && octet.charAt(0) == '0')
                    || !octet.matches("[0-9]+")) {
                return null;
            }
            int parsed = Integer.parseInt(octet);
            if (parsed > 255) {
                return null;
            }
            bytes[index] = (byte) parsed;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("Endereco IPv4 com tamanho inesperado", exception);
        }
    }

    private static boolean isDnsName(String value) {
        boolean absolute = value.endsWith(".");
        String name = absolute ? value.substring(0, value.length() - 1) : value;
        if (name.isEmpty() || name.length() > MAX_DNS_NAME_LENGTH || name.matches("[0-9.]+")) {
            return false;
        }
        String[] labels = name.split("\\.", -1);
        for (String label : labels) {
            if (!DNS_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }
}
