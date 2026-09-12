package com.cloudbox.master.container;

import java.util.Objects;
import com.cloudbox.master.container.dto.EndpointResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class ContainerEndpoint {

    @Column(name = "container_port", nullable = false)
    private Integer containerPort;

    @Column(name = "host_port", nullable = false)
    private Integer hostPort;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 3)
    private PortProtocol protocol;

    @Column(name = "observed_address", length = 45)
    private String observedAddress;

    @Column(name = "resolved_address", length = 253)
    private String resolvedAddress;

    @Column(name = "endpoint_url", length = 512)
    private String url;

    public ContainerEndpoint() {
    }

    public ContainerEndpoint(Integer containerPort, Integer hostPort, PortProtocol protocol,
                             String observedAddress, String resolvedAddress, String url) {
        this.containerPort = containerPort;
        this.hostPort = hostPort;
        this.protocol = protocol;
        this.observedAddress = observedAddress;
        this.resolvedAddress = resolvedAddress;
        this.url = url;
    }

    public EndpointResponse toResponse() {
        return new EndpointResponse(containerPort, hostPort, protocol, resolvedAddress, url);
    }

    public String getResolvedAddress() {
        return resolvedAddress;
    }

    public Integer getContainerPort() {
        return containerPort;
    }

    public Integer getHostPort() {
        return hostPort;
    }

    public PortProtocol getProtocol() {
        return protocol;
    }

    public String getObservedAddress() {
        return observedAddress;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContainerEndpoint endpoint)) {
            return false;
        }
        return Objects.equals(containerPort, endpoint.containerPort)
                && Objects.equals(hostPort, endpoint.hostPort)
                && protocol == endpoint.protocol
                && Objects.equals(observedAddress, endpoint.observedAddress)
                && Objects.equals(resolvedAddress, endpoint.resolvedAddress)
                && Objects.equals(url, endpoint.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(containerPort, hostPort, protocol, observedAddress, resolvedAddress, url);
    }
}
