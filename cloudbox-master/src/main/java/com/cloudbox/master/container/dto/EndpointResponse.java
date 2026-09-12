package com.cloudbox.master.container.dto;

import com.cloudbox.master.container.PortProtocol;

public record EndpointResponse(
        Integer containerPort,
        Integer hostPort,
        PortProtocol protocol,
        String address,
        String url) {
}
