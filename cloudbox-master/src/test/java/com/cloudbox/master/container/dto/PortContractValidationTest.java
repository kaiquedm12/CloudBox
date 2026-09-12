package com.cloudbox.master.container.dto;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import com.cloudbox.master.container.PortExposure;
import com.cloudbox.master.container.PortProtocol;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class PortContractValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsMissingPortsAndPortEnumsCompatibly() {
        ContainerRequest legacy = new ContainerRequest("nginx:alpine", 1, 256, 100, null);
        PortSpec defaults = new PortSpec(80, null, null, null, null);

        assertThat(legacy.ports()).isEmpty();
        assertThat(defaults.protocol()).isEqualTo(PortProtocol.TCP);
        assertThat(defaults.exposure()).isEqualTo(PortExposure.INTERNAL);
        assertThat(validator.validate(legacy)).isEmpty();
        assertThat(validator.validate(defaults)).isEmpty();
    }

    @Test
    void acceptsValidPublishedIpv4AndIpv6Bindings() {
        PortSpec ipv4 = new PortSpec(80, null, PortProtocol.TCP, PortExposure.HTTP, "0.0.0.0");
        PortSpec ipv6 = new PortSpec(53, 5353, PortProtocol.UDP, PortExposure.UDP, "2001:db8::2");

        assertThat(validator.validate(ipv4)).isEmpty();
        assertThat(validator.validate(ipv6)).isEmpty();
    }

    @Test
    void rejectsDuplicatesIncompatibleExposureAndInternalBindings() {
        PortSpec first = new PortSpec(80, null, PortProtocol.TCP, PortExposure.HTTP, null);
        PortSpec duplicate = new PortSpec(80, 8080, PortProtocol.TCP, PortExposure.TCP, null);
        ContainerRequest request = new ContainerRequest("nginx", 1, 128, 64, List.of(first, duplicate));
        PortSpec incompatible = new PortSpec(53, null, PortProtocol.TCP, PortExposure.UDP, null);
        PortSpec internalBound = new PortSpec(80, 8080, PortProtocol.TCP, PortExposure.INTERNAL, null);

        assertThat(validator.validate(request)).isNotEmpty();
        assertThat(validator.validate(incompatible)).isNotEmpty();
        assertThat(validator.validate(internalBound)).isNotEmpty();
    }

    @Test
    void rejectsInvalidPortAddressesAndMoreThanThirtyTwoPorts() {
        PortSpec bracketedIpv6 = new PortSpec(80, null, PortProtocol.TCP, PortExposure.HTTP, "[::1]");
        PortSpec ambiguousIpv4 = new PortSpec(81, null, PortProtocol.TCP, PortExposure.HTTP, "127.0.0.01");
        List<PortSpec> tooMany = IntStream.rangeClosed(1, 33)
                .mapToObj(port -> new PortSpec(port, null, PortProtocol.TCP, PortExposure.INTERNAL, null))
                .toList();

        assertThat(validator.validate(bracketedIpv6)).isNotEmpty();
        assertThat(validator.validate(ambiguousIpv4)).isNotEmpty();
        assertThat(validator.validate(new ContainerRequest("nginx", 1, 128, 64, tooMany))).isNotEmpty();
    }

    @Test
    void validatesObservedEndpointShapeAndAllowsWildcardAddresses() {
        ObservedEndpointRequest wildcard = new ObservedEndpointRequest(80, 32768, PortProtocol.TCP, "::");
        ObservedEndpointRequest bracketed = new ObservedEndpointRequest(80, 32768, PortProtocol.TCP, "[::]");

        assertThat(validator.validate(wildcard)).isEmpty();
        assertThat(validator.validate(bracketed)).isNotEmpty();
    }
}
