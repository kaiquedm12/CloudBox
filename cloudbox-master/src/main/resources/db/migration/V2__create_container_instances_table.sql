CREATE TABLE container_instances (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     image VARCHAR(255) NOT NULL,
                                     cpu_requested DECIMAL(6,2) NOT NULL,
                                     ram_requested_mb INTEGER NOT NULL,
                                     node_id UUID REFERENCES nodes(id),
                                     status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                     docker_container_id VARCHAR(255),
                                     created_at TIMESTAMP NOT NULL DEFAULT now(),
                                     updated_at TIMESTAMP NOT NULL DEFAULT now()
);