package com.cloudbox.agent.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void shouldTreatBlankAdvertiseAddressAsAbsent() {
        AgentClientProperties properties = new AgentClientProperties();

        properties.setAdvertiseAddress("  ");

        assertThat(properties.getAdvertiseAddress()).isNull();
        assertThat(properties.getPortBindAddress()).isEqualTo("0.0.0.0");
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
