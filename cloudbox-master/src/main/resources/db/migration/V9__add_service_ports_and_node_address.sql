ALTER TABLE nodes
    ADD COLUMN advertise_address VARCHAR(253);

CREATE TABLE container_ports (
    container_id UUID NOT NULL REFERENCES container_instances(id) ON DELETE CASCADE,
    port_order INTEGER NOT NULL,
    container_port INTEGER NOT NULL CHECK (container_port BETWEEN 1 AND 65535),
    host_port INTEGER CHECK (host_port BETWEEN 1 AND 65535),
    protocol VARCHAR(3) NOT NULL CHECK (protocol IN ('TCP', 'UDP')),
    exposure VARCHAR(8) NOT NULL CHECK (exposure IN ('INTERNAL', 'HTTP', 'TCP', 'UDP')),
    bind_address VARCHAR(45),
    PRIMARY KEY (container_id, port_order),
    UNIQUE (container_id, container_port, protocol),
    CHECK (exposure <> 'INTERNAL' OR (host_port IS NULL AND bind_address IS NULL)),
    CHECK ((exposure NOT IN ('HTTP', 'TCP') OR protocol = 'TCP')
        AND (exposure <> 'UDP' OR protocol = 'UDP'))
);

CREATE TABLE container_endpoints (
    container_id UUID NOT NULL REFERENCES container_instances(id) ON DELETE CASCADE,
    endpoint_order INTEGER NOT NULL,
    container_port INTEGER NOT NULL CHECK (container_port BETWEEN 1 AND 65535),
    host_port INTEGER NOT NULL CHECK (host_port BETWEEN 1 AND 65535),
    protocol VARCHAR(3) NOT NULL CHECK (protocol IN ('TCP', 'UDP')),
    observed_address VARCHAR(45),
    resolved_address VARCHAR(253),
    endpoint_url VARCHAR(512),
    PRIMARY KEY (container_id, endpoint_order)
);

CREATE INDEX idx_container_ports_container_id ON container_ports (container_id);
CREATE INDEX idx_container_endpoints_container_id ON container_endpoints (container_id);
