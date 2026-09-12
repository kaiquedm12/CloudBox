package com.cloudbox.master.container;

import java.util.Objects;
import com.cloudbox.master.container.dto.PortSpec;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class ContainerPort {

    @Column(name = "container_port", nullable = false)
    private Integer containerPort;

    @Column(name = "host_port")
    private Integer hostPort;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 3)
    private PortProtocol protocol;

    @Enumerated(EnumType.STRING)
    @Column(name = "exposure", nullable = false, length = 8)
    private PortExposure exposure;

    @Column(name = "bind_address", length = 45)
    private String bindAddress;

    public static ContainerPort from(PortSpec port) {
        ContainerPort entity = new ContainerPort();
        entity.containerPort = port.containerPort();
        entity.hostPort = port.hostPort();
        entity.protocol = port.protocol();
        entity.exposure = port.exposure();
        entity.bindAddress = port.bindAddress();
        return entity;
    }

    public PortSpec toSpec() {
        return new PortSpec(containerPort, hostPort, protocol, exposure, bindAddress);
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

    public PortExposure getExposure() {
        return exposure;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContainerPort port)) {
            return false;
        }
        return Objects.equals(containerPort, port.containerPort)
                && Objects.equals(hostPort, port.hostPort)
                && protocol == port.protocol
                && exposure == port.exposure
                && Objects.equals(bindAddress, port.bindAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(containerPort, hostPort, protocol, exposure, bindAddress);
    }
}
