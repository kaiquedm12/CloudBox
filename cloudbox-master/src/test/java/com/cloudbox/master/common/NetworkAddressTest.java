package com.cloudbox.master.common;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class NetworkAddressTest {

    @Test
    void acceptsDnsIpv4Ipv6AndTrailingDotFqdn() {
        assertThat(NetworkAddress.isValidAdvertiseAddress("node.example.internal")).isTrue();
        assertThat(NetworkAddress.isValidAdvertiseAddress("node.example.internal.")).isTrue();
        assertThat(NetworkAddress.isValidAdvertiseAddress("192.0.2.10")).isTrue();
        assertThat(NetworkAddress.isValidAdvertiseAddress("2001:db8::10")).isTrue();
        assertThat(NetworkAddress.isValidAdvertiseAddress("::ffff:192.0.2.10")).isTrue();
        assertThat(NetworkAddress.isIpv6Literal("::ffff:192.0.2.10")).isTrue();
    }

    @Test
    void rejectsWildcardsUrlsBracketsPortsAndInvalidDns() {
        assertThat(NetworkAddress.isValidAdvertiseAddress("0.0.0.0")).isFalse();
        assertThat(NetworkAddress.isValidAdvertiseAddress("::")).isFalse();
        assertThat(NetworkAddress.isValidAdvertiseAddress("http://node.example")).isFalse();
        assertThat(NetworkAddress.isValidAdvertiseAddress("node.example:8080")).isFalse();
        assertThat(NetworkAddress.isValidAdvertiseAddress("[2001:db8::10]")).isFalse();
        assertThat(NetworkAddress.isValidAdvertiseAddress("bad_name.example")).isFalse();
    }

    @Test
    void rejectsAmbiguousAndNonAsciiIpv4Octets() {
        assertThat(NetworkAddress.isIpLiteral("192.168.001.10")).isFalse();
        assertThat(NetworkAddress.isIpLiteral("１９２.168.1.10")).isFalse();
        assertThat(NetworkAddress.isValidAdvertiseAddress("192.168.001.10")).isFalse();
    }

    @Test
    void comparesEquivalentIpv6Representations() {
        assertThat(NetworkAddress.sameIp("2001:db8::1", "2001:0db8:0:0:0:0:0:1")).isTrue();
    }
}
