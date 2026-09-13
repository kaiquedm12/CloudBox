package com.cloudbox.master.container;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.cloudbox.master.common.NetworkAddress;
import com.cloudbox.master.container.dto.ObservedEndpointRequest;
import com.cloudbox.master.node.Node;

@Component
public class EndpointResolver {

    public List<ContainerEndpoint> resolve(List<ContainerPort> requestedPorts,
                                           List<ObservedEndpointRequest> reports,
                                           Node node) {
        Map<PortKey, ContainerPort> requestedByKey = new LinkedHashMap<>();
        for (ContainerPort requestedPort : requestedPorts) {
            PortKey key = new PortKey(requestedPort.getContainerPort(), requestedPort.getProtocol());
            if (requestedByKey.putIfAbsent(key, requestedPort) != null) {
                throw new IllegalArgumentException("Especificação contém porta/protocolo duplicados: " + key);
            }
        }

        return reports.stream()
                .map(report -> resolveOne(requestedByKey, report, node))
                .toList();
    }

    private ContainerEndpoint resolveOne(Map<PortKey, ContainerPort> requestedByKey,
                                         ObservedEndpointRequest report,
                                         Node node) {
        validateReportShape(report);
        PortKey key = new PortKey(report.containerPort(), report.protocol());
        ContainerPort requested = requestedByKey.get(key);
        if (requested == null) {
            throw new IllegalArgumentException("Endpoint reportado para porta não solicitada: " + key);
        }
        if (requested.getExposure() == PortExposure.INTERNAL) {
            throw new IllegalArgumentException("Endpoint não pode ser reportado para porta INTERNAL: " + key);
        }
        if (requested.getHostPort() != null && !requested.getHostPort().equals(report.hostPort())) {
            throw new IllegalArgumentException("hostPort reportada diverge da porta solicitada: " + key);
        }
        if (requested.getBindAddress() != null && report.address() != null
                && !NetworkAddress.sameIp(requested.getBindAddress(), report.address())) {
            throw new IllegalArgumentException("address reportado diverge do bindAddress solicitado: " + key);
        }

        String resolvedAddress = resolveAddress(requested, report.address(), node);
        String url = requested.getExposure() == PortExposure.HTTP && resolvedAddress != null
                ? httpUrl(resolvedAddress, report.hostPort())
                : null;
        return new ContainerEndpoint(report.containerPort(), report.hostPort(), report.protocol(),
                report.address(), resolvedAddress, url);
    }

    private void validateReportShape(ObservedEndpointRequest report) {
        if (report == null || report.containerPort() == null || report.hostPort() == null
                || report.protocol() == null
                || report.containerPort() < 1 || report.containerPort() > 65535
                || report.hostPort() < 1 || report.hostPort() > 65535
                || (report.address() != null && !NetworkAddress.isIpLiteral(report.address()))) {
            throw new IllegalArgumentException("Endpoint observado inválido");
        }
    }

    private String resolveAddress(ContainerPort requested, String observedAddress, Node node) {
        String requestedBind = requested.getBindAddress();
        if (requestedBind != null && !NetworkAddress.isWildcard(requestedBind)) {
            return requestedBind;
        }
        if (requestedBind == null && observedAddress != null && !NetworkAddress.isWildcard(observedAddress)) {
            return observedAddress;
        }
        String advertiseAddress = node == null ? null : node.getAdvertiseAddress();
        return NetworkAddress.isValidAdvertiseAddress(advertiseAddress)
                ? NetworkAddress.normalizeOptional(advertiseAddress)
                : null;
    }

    private String httpUrl(String address, Integer hostPort) {
        String host = NetworkAddress.isIpv6Literal(address) ? "[" + address + "]" : address;
        return "http://" + host + ":" + hostPort;
    }

    private record PortKey(Integer containerPort, PortProtocol protocol) {
        @Override
        public String toString() {
            return containerPort + "/" + protocol;
        }
    }
}
