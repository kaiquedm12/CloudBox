package com.cloudbox.agent.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NodeRegistrationServiceTest {

    @Test
    void shouldConvertBytesToWholeMegabytes() {
        assertThat(NodeRegistrationService.bytesToMb(1_572_864L)).isEqualTo(1);
        assertThat(NodeRegistrationService.bytesToMb(10L * 1024L * 1024L)).isEqualTo(10);
    }
}
