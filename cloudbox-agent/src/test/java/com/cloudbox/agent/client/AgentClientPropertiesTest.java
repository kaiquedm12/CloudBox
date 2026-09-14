package com.cloudbox.agent.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class AgentClientPropertiesTest {

    @Test
    void shouldKeepAdvertiseAndBindAddressesSeparate() {
        AgentClientProperties properties = new AgentClientProperties();

        properties.setAdvertiseAddress("node-a.example.test");
        properties.setPortBindAddress("::");

        assertThat(properties.getAdvertiseAddress()).isEqualTo("node-a.example.test");
        assertThat(properties.getPortBindAddress()).isEqualTo("::");
    }

    @Test
    void shouldAcceptAbsoluteDnsNameAndIpv4MappedIpv6() {
        AgentClientProperties properties = new AgentClientProperties();

        properties.setAdvertiseAddress("node-a.example.test.");
        assertThat(properties.getAdvertiseAddress()).isEqualTo("node-a.example.test.");

        properties.setAdvertiseAddress("::ffff:192.0.2.1");
        assertThat(properties.getAdvertiseAddress()).isEqualTo("::ffff:192.0.2.1");
    }

    @Test
    void shouldAutoDetectAdvertiseAddressWhenConfigurationIsBlank() {
        AtomicInteger detections = new AtomicInteger();
        AgentClientProperties properties = new AgentClientProperties(masterUrl -> {
            detections.incrementAndGet();
            assertThat(masterUrl).isEqualTo("https://master.example.test");
            return "192.168.1.50";
        });

        properties.setMasterUrl("https://master.example.test");
        properties.setAdvertiseAddress("  ");

        assertThat(properties.getAdvertiseAddress()).isEqualTo("192.168.1.50");
        assertThat(properties.getAdvertiseAddress()).isEqualTo("192.168.1.50");
        assertThat(detections).hasValue(1);
        assertThat(properties.getPortBindAddress()).isEqualTo("0.0.0.0");
    }

    @Test
    void shouldAllowAutomaticDetectionToBeDisabled() {
        AgentClientProperties properties = new AgentClientProperties(masterUrl -> "192.168.1.50");

        properties.setAdvertiseAddress(" ");
        properties.setAutoDetectAdvertiseAddress(false);

        assertThat(properties.getAdvertiseAddress()).isNull();
    }

    @Test
    void shouldPreferConfiguredAddressOverAutomaticDetection() {
        AtomicInteger detections = new AtomicInteger();
        AgentClientProperties properties = new AgentClientProperties(masterUrl -> {
            detections.incrementAndGet();
            return "192.168.1.50";
        });

        properties.setAdvertiseAddress("node-a.example.test");

        assertThat(properties.getAdvertiseAddress()).isEqualTo("node-a.example.test");
        assertThat(detections).hasValue(0);
    }

    @Test
    void shouldPreferPrivateIpv4WhenSelectingAnInterfaceAddress() throws UnknownHostException {
        String selected = AdvertiseAddressDetector.selectBestAddress(List.of(
                InetAddress.getByName("2001:db8::10"),
                InetAddress.getByName("203.0.113.10"),
                InetAddress.getByName("192.168.1.50"),
                InetAddress.getLoopbackAddress()));

        assertThat(selected).isEqualTo("192.168.1.50");
    }

    @Test
    void shouldRejectUnsafeAdvertiseAddressesAndHostnameBindings() {
        AgentClientProperties properties = new AgentClientProperties();

        assertThatThrownBy(() -> properties.setAdvertiseAddress("0.0.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setAdvertiseAddress("http://node-a.example.test:8080/path"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setPortBindAddress("node-a.example.test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setAdvertiseAddress("192.168.001.10"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setAdvertiseAddress("１２７.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
