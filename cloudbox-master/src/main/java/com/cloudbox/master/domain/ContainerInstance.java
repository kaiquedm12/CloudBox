package com.cloudbox.master.domain;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "container_instances")
public class ContainerInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String image;

    @Column(name = "cpu_requested")
    private BigDecimal cpuRequested;

    @Column(name = "ram_requested_mb")
    private Integer ramRequestedMb;

    @ManyToOne
    @JoinColumn(name = "node_id")
    private Node node;

    @Enumerated(EnumType.STRING)
    private ContainerStatus status;

    @Column(name = "docker_container_id")
    private String dockerContainerId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // getters, setters, construtores
}