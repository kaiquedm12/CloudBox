package com.cloudbox.agent.client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cloudbox.agent")
public class AgentClientProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentClientProperties.class);

    private final Function<String, String> advertiseAddressDetector;
    private String masterUrl = "http://localhost:8080";
    private String name = defaultHostName();
    private String advertiseAddress;
    private volatile String detectedAdvertiseAddress;
    private boolean autoDetectAdvertiseAddress = true;
    private String portBindAddress = "0.0.0.0";
    private Path tokenFile = Path.of(System.getProperty("user.home"), ".cloudbox", "agent-credentials.properties");

    public AgentClientProperties() {
        this(AdvertiseAddressDetector::detect);
    }

    AgentClientProperties(Function<String, String> advertiseAddressDetector) {
        this.advertiseAddressDetector = advertiseAddressDetector;
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public void setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
        detectedAdvertiseAddress = null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAdvertiseAddress() {
        if (advertiseAddress != null || !autoDetectAdvertiseAddress) {
            return advertiseAddress;
        }

        String detected = detectedAdvertiseAddress;
        if (detected == null) {
            synchronized (this) {
                detected = detectedAdvertiseAddress;
                if (detected == null) {
                    detected = NetworkAddressValidator.normalizeAdvertiseAddress(
                            advertiseAddressDetector.apply(masterUrl));
                    detectedAdvertiseAddress = detected;
                    if (detected != null) {
                        LOGGER.info(
                                "AGENT_ADVERTISE_ADDRESS nao informado; endereco detectado automaticamente: {}",
                                detected);
                    } else {
                        LOGGER.warn(
                                "Nao foi possivel detectar automaticamente AGENT_ADVERTISE_ADDRESS; "
                                        + "workloads com portas publicadas nao serao agendados neste no");
                    }
                }
            }
        }
        return detected;
    }

    public void setAdvertiseAddress(String advertiseAddress) {
        this.advertiseAddress = NetworkAddressValidator.normalizeAdvertiseAddress(advertiseAddress);
        detectedAdvertiseAddress = null;
    }

    public boolean isAutoDetectAdvertiseAddress() {
        return autoDetectAdvertiseAddress;
    }

    public void setAutoDetectAdvertiseAddress(boolean autoDetectAdvertiseAddress) {
        this.autoDetectAdvertiseAddress = autoDetectAdvertiseAddress;
        detectedAdvertiseAddress = null;
    }

    public String getPortBindAddress() {
        return portBindAddress;
    }

    public void setPortBindAddress(String portBindAddress) {
        this.portBindAddress = NetworkAddressValidator.requireIpLiteral(
                portBindAddress, "AGENT_PORT_BIND_ADDRESS");
    }

    public Path getTokenFile() {
        return tokenFile;
    }

    public void setTokenFile(Path tokenFile) {
        this.tokenFile = tokenFile;
    }

    private static String defaultHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "cloudbox-agent";
        }
    }
}
