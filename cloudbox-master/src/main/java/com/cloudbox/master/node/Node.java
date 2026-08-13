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
    private UUID id;

    private String name;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    private NodeStatus status;

    @Column(name = "cpu_total")
    private BigDecimal cpuTotal;

    @Column(name = "cpu_free")
    private BigDecimal cpuFree;

    @Column(name = "ram_total_mb")
    private Integer ramTotalMb;

    @Column(name = "ram_free_mb")
    private Integer ramFreeMb;

    @Column(name = "disk_total_mb")
    private Integer diskTotalMb;

    @Column(name = "disk_free_mb")
    private Integer diskFreeMb;

    @Column(name = "temperature_celsius")
    private BigDecimal temperatureCelsius;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "created_at")
    private Instant createdAt;

    // getters, setters, construtores
}