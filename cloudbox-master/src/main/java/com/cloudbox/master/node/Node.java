package com.cloudbox.master.node;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "nodes")
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NodeStatus status = NodeStatus.OFFLINE;

    @Column(name = "cpu_total", nullable = false)
    private BigDecimal cpuTotal;

    @Column(name = "cpu_free", nullable = false)
    private BigDecimal cpuFree;

    @Column(name = "ram_total_mb", nullable = false)
    private Integer ramTotalMb;

    @Column(name = "ram_free_mb", nullable = false)
    private Integer ramFreeMb;

    @Column(name = "disk_total_mb", nullable = false)
    private Integer diskTotalMb;

    @Column(name = "disk_free_mb", nullable = false)
    private Integer diskFreeMb;

    @Column(name = "temperature_celsius")
    private BigDecimal temperatureCelsius;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public BigDecimal getCpuTotal() {
        return cpuTotal;
    }

    public void setCpuTotal(BigDecimal cpuTotal) {
        this.cpuTotal = cpuTotal;
    }

    public BigDecimal getCpuFree() {
        return cpuFree;
    }

    public void setCpuFree(BigDecimal cpuFree) {
        this.cpuFree = cpuFree;
    }

    public Integer getRamTotalMb() {
        return ramTotalMb;
    }

    public void setRamTotalMb(Integer ramTotalMb) {
        this.ramTotalMb = ramTotalMb;
    }

    public Integer getRamFreeMb() {
        return ramFreeMb;
    }

    public void setRamFreeMb(Integer ramFreeMb) {
        this.ramFreeMb = ramFreeMb;
    }

    public Integer getDiskTotalMb() {
        return diskTotalMb;
    }

    public void setDiskTotalMb(Integer diskTotalMb) {
        this.diskTotalMb = diskTotalMb;
    }

    public Integer getDiskFreeMb() {
        return diskFreeMb;
    }

    public void setDiskFreeMb(Integer diskFreeMb) {
        this.diskFreeMb = diskFreeMb;
    }

    public BigDecimal getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public void setTemperatureCelsius(BigDecimal temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}