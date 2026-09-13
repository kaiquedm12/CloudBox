package com.cloudbox.master.container;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "container_instances")
public class ContainerInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "image_name", nullable = false, length = 255)
    private String imageName;

    @Column(name = "cpu_cores", nullable = false)
    private Integer cpuCores;

    @Column(name = "memory_mb", nullable = false)
    private Integer memoryMb;

    @Column(name = "disk_mb", nullable = false)
    private Integer diskMb;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ContainerStatus status;

    @Column(name = "node_id")
    private UUID nodeId;

    @Column(name = "docker_container_id", length = 255)
    private String dockerContainerId;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @ElementCollection
    @CollectionTable(name = "container_ports", joinColumns = @JoinColumn(name = "container_id"))
    @OrderColumn(name = "port_order")
    private List<ContainerPort> ports = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "container_endpoints", joinColumns = @JoinColumn(name = "container_id"))
    @OrderColumn(name = "endpoint_order")
    private List<ContainerEndpoint> endpoints = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public Integer getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(Integer cpuCores) {
        this.cpuCores = cpuCores;
    }

    public Integer getMemoryMb() {
        return memoryMb;
    }

    public void setMemoryMb(Integer memoryMb) {
        this.memoryMb = memoryMb;
    }

    public Integer getDiskMb() {
        return diskMb;
    }

    public void setDiskMb(Integer diskMb) {
        this.diskMb = diskMb;
    }

    public ContainerStatus getStatus() {
        return status;
    }

    public void setStatus(ContainerStatus status) {
        this.status = status;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public void setNodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    public String getDockerContainerId() {
        return dockerContainerId;
    }

    public void setDockerContainerId(String dockerContainerId) {
        this.dockerContainerId = dockerContainerId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<ContainerPort> getPorts() {
        return ports;
    }

    public void setPorts(List<ContainerPort> ports) {
        this.ports.clear();
        if (ports != null) {
            this.ports.addAll(ports);
        }
    }

    public List<ContainerEndpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<ContainerEndpoint> endpoints) {
        this.endpoints.clear();
        if (endpoints != null) {
            this.endpoints.addAll(endpoints);
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
