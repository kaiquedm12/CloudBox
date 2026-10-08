package com.cloudbox.agent.docker;

import static org.assertj.core.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.cloudbox.agent.client.RestartPolicySpec;
import org.junit.jupiter.api.Test;

class ExecutionOptionsTest {
    @Test
    void shouldPreserveDefaultsAndDefensivelyCopyConfiguration() {
        assertThat(new ExecutionOptions(null, null, null, null).isDefault()).isTrue();
        Map<String, String> env = new LinkedHashMap<>(Map.of("GREETING", "hello"));
        var options = new ExecutionOptions(env, List.of("echo"), List.of("a b", "$GREETING"), null);
        env.put("GREETING", "changed");
        assertThat(options.environment()).containsEntry("GREETING", "hello");
        assertThat(options.toString()).doesNotContain("hello", "GREETING");
        assertThatThrownBy(() -> options.environment().put("X", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldFingerprintWithoutPlaintextOrAmbiguousArgumentBoundaries() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("B", "sensitive-value");
        env.put("A", "x");
        var first = new ExecutionOptions(env, null, List.of("ab", "c"), null);
        var reordered = new ExecutionOptions(Map.of("A", "x", "B", "sensitive-value"), null, List.of("ab", "c"), null);
        var differentArgs = new ExecutionOptions(env, null, List.of("a", "bc"), null);
        assertThat(first.fingerprint()).isEqualTo(reordered.fingerprint()).matches("[a-f0-9]{64}");
        assertThat(first.fingerprint()).isNotEqualTo(differentArgs.fingerprint());
    }

    @Test
    void shouldRejectInvalidEnvironmentAndArgumentsWithoutPrintingValues() {
        assertThatThrownBy(() -> new ExecutionOptions(Map.of("A=B", "sensitive-value"), null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("sensitive-value");
        assertThatThrownBy(() -> new ExecutionOptions(null, List.of(), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionOptions(null, null, List.of("a\0b"), null)).isInstanceOf(IllegalArgumentException.class);
        Map<String, String> large = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) large.put("V" + i, "x".repeat(4096));
        assertThatThrownBy(() -> new ExecutionOptions(large, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("64 KiB");
    }

    @Test
    void shouldRejectUnsupportedRestartPoliciesAndRetryCombinations() {
        for (RestartPolicySpec policy : List.of(new RestartPolicySpec("SOMETHING", 0),
                new RestartPolicySpec("ALWAYS", 3), new RestartPolicySpec("ON_FAILURE", -1),
                new RestartPolicySpec("ON_FAILURE", 101))) {
            assertThatThrownBy(() -> new ExecutionOptions(null, null, null, policy))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
