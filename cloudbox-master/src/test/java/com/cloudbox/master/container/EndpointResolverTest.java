package com.cloudbox.master.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.cloudbox.master.container.dto.ObservedEndpointRequest;
import com.cloudbox.master.container.dto.PortSpec;
import com.cloudbox.master.node.Node;

class EndpointResolverTest {

    private final EndpointResolver resolver = new EndpointResolver();

    @Test
    void derivesHttpUrlFromAdvertiseAddressForWildcardBinding() {
        ContainerEndpoint endpoint = resolve(httpPort(null, null), report(80, 32768, "0.0.0.0"), "192.0.2.30");

        assertThat(endpoint.getResolvedAddress()).isEqualTo("192.0.2.30");
        assertThat(endpoint.getUrl()).isEqualTo("http://192.0.2.30:32768");
    }

    @Test
    void bracketsIpv6AndIpv4MappedIpv6InUrls() {
        ContainerEndpoint ipv6 = resolve(httpPort(null, null), report(80, 32768, "::"), "2001:db8::30");
        ContainerEndpoint mapped = resolve(httpPort(null, null),
                report(80, 32769, "::ffff:192.0.2.30"), "node.example");

        assertThat(ipv6.getUrl()).isEqualTo("http://[2001:db8::30]:32768");
        assertThat(mapped.getUrl()).isEqualTo("http://[::ffff:192.0.2.30]:32769");
    }

    @Test
    void explicitLoopbackIsNotReplacedByRemoteAdvertiseAddress() {
        ContainerEndpoint endpoint = resolve(httpPort(8080, "127.0.0.1"),
                report(80, 8080, "127.0.0.1"), "192.0.2.30");

        assertThat(endpoint.getResolvedAddress()).isEqualTo("127.0.0.1");
        assertThat(endpoint.getUrl()).isEqualTo("http://127.0.0.1:8080");
    }

    @Test
    void retainsDistinctIpv4AndIpv6BindingsForSameRequestedPort() {
        List<ContainerEndpoint> endpoints = resolver.resolve(
                List.of(ContainerPort.from(httpPort(null, null))),
                List.of(report(80, 32768, "0.0.0.0"), report(80, 32768, "::")),
                node("node.example"));

        assertThat(endpoints).hasSize(2);
    }

    @Test
    void doesNotCreateUrlForTcpExposure() {
        PortSpec tcp = new PortSpec(5432, null, PortProtocol.TCP, PortExposure.TCP, null);
        ContainerEndpoint endpoint = resolve(tcp,
                new ObservedEndpointRequest(5432, 32770, PortProtocol.TCP, null), "node.example");

        assertThat(endpoint.getResolvedAddress()).isEqualTo("node.example");
        assertThat(endpoint.getUrl()).isNull();
    }

    @Test
    void rejectsUnrequestedInternalAndDivergentBindings() {
        assertThatThrownBy(() -> resolver.resolve(
                List.of(ContainerPort.from(httpPort(null, null))),
                List.of(new ObservedEndpointRequest(81, 32768, PortProtocol.TCP, null)), node("node.example")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não solicitada");

        PortSpec internal = new PortSpec(80, null, PortProtocol.TCP, PortExposure.INTERNAL, null);
        assertThatThrownBy(() -> resolver.resolve(List.of(ContainerPort.from(internal)),
                List.of(report(80, 32768, null)), node("node.example")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("INTERNAL");

        assertThatThrownBy(() -> resolver.resolve(
                List.of(ContainerPort.from(httpPort(8080, "127.0.0.1"))),
                List.of(report(80, 8081, "127.0.0.1")), node("node.example")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hostPort");

        assertThatThrownBy(() -> resolver.resolve(
                List.of(ContainerPort.from(httpPort(8080, "127.0.0.1"))),
                List.of(report(80, 8080, "192.0.2.40")), node("node.example")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bindAddress");
    }

    private ContainerEndpoint resolve(PortSpec port, ObservedEndpointRequest report, String advertiseAddress) {
        return resolver.resolve(List.of(ContainerPort.from(port)), List.of(report), node(advertiseAddress)).getFirst();
    }

    private PortSpec httpPort(Integer hostPort, String bindAddress) {
        return new PortSpec(80, hostPort, PortProtocol.TCP, PortExposure.HTTP, bindAddress);
    }

    private ObservedEndpointRequest report(int containerPort, int hostPort, String address) {
        return new ObservedEndpointRequest(containerPort, hostPort, PortProtocol.TCP, address);
    }

    private Node node(String advertiseAddress) {
        Node node = new Node();
        node.setAdvertiseAddress(advertiseAddress);
        return node;
    }
}
