package com.cloudbox.agent.docker;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.cloudbox.agent.client.RestartPolicySpec;

/** Validated configuration. Values are never embedded in labels or diagnostic strings. */
public record ExecutionOptions(Map<String, String> environment, List<String> command,
                               List<String> args, RestartPolicySpec restartPolicy) {
    public static final ExecutionOptions DEFAULT = new ExecutionOptions(null, null, null, null);

    public ExecutionOptions {
        environment = environment == null ? Map.of() : environment;
        if (environment.size() > 128) throw new IllegalArgumentException("environment excede 128 entradas");
        int bytes = 2;
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z_][A-Za-z0-9_]{0,127}")
                    || entry.getValue() == null || entry.getValue().length() > 4096
                    || entry.getValue().indexOf('\0') >= 0) {
                throw new IllegalArgumentException("environment possui nome ou valor invalido");
            }
            bytes += jsonBytes(entry.getKey()) + jsonBytes(entry.getValue()) + 2;
        }
        if (!environment.isEmpty()) bytes--;
        if (bytes > 65_536) throw new IllegalArgumentException("environment excede 64 KiB");
        environment = Collections.unmodifiableMap(new TreeMap<>(environment));
        command = validateArguments(command, "command");
        args = validateArguments(args, "args");
        restartPolicy = restartPolicy == null ? RestartPolicySpec.NONE : restartPolicy;
        restartPolicy.validate();
    }

    private static List<String> validateArguments(List<String> values, String field) {
        if (values == null) return null;
        if (values.isEmpty() || values.size() > 64 || values.getFirst() == null || values.getFirst().isBlank()
                || values.stream().anyMatch(value -> value == null || value.length() > 4096 || value.indexOf('\0') >= 0)) {
            throw new IllegalArgumentException(field + " deve ser um array valido de 1 a 64 argumentos");
        }
        return List.copyOf(values);
    }

    private static int jsonBytes(String value) {
        int bytes = 2 + value.getBytes(StandardCharsets.UTF_8).length;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\' || c == '\b' || c == '\f' || c == '\n' || c == '\r' || c == '\t') bytes++;
            else if (c < 0x20) bytes += 5;
        }
        return bytes;
    }

    public boolean isDefault() {
        return environment.isEmpty() && command == null && args == null && RestartPolicySpec.NONE.equals(restartPolicy);
    }

    public String fingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(environment.size());
                for (var entry : environment.entrySet()) {
                    output.writeUTF(entry.getKey());
                    output.writeUTF(entry.getValue());
                }
                writeArguments(output, command);
                writeArguments(output, args);
                output.writeUTF(restartPolicy.name());
                output.writeInt(restartPolicy.maximumRetryCount());
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Nao foi possivel identificar a configuracao do container", exception);
        }
    }

    private static void writeArguments(DataOutputStream output, List<String> arguments) throws IOException {
        output.writeInt(arguments == null ? -1 : arguments.size());
        if (arguments != null) for (String argument : arguments) output.writeUTF(argument);
    }

    @Override
    public String toString() {
        return "ExecutionOptions[configuration omitted]";
    }
}
