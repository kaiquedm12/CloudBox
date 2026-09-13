package com.cloudbox.master.node.dto;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class NodeRegisterRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void remainsCompatibleWithoutAdvertiseAddress() {
        NodeRegisterRequest request = new NodeRegisterRequest("legacy", BigDecimal.valueOf(4), 4096, 10240);

        assertThat(request.advertiseAddress()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void acceptsDnsIpv4Ipv6AndTrailingDot() {
        assertValid("node.example.internal");
        assertValid("node.example.internal.");
        assertValid("192.0.2.20");
        assertValid("2001:db8::20");
        assertValid("::ffff:192.0.2.20");
    }

    @Test
    void rejectsWildcardUrlBracketedIpv6AndAmbiguousIpv4() {
        assertInvalid("0.0.0.0");
        assertInvalid("::");
        assertInvalid("https://node.example");
        assertInvalid("[2001:db8::20]");
        assertInvalid("192.168.001.20");
    }

    private void assertValid(String address) {
        assertThat(validator.validate(request(address))).isEmpty();
    }

    private void assertInvalid(String address) {
        assertThat(validator.validate(request(address))).isNotEmpty();
    }

    private NodeRegisterRequest request(String address) {
        return new NodeRegisterRequest("node", BigDecimal.valueOf(4), 4096, 10240, address);
    }
}
