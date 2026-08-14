CREATE TABLE container_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    image_name VARCHAR(255) NOT NULL,
    cpu_cores INTEGER NOT NULL,
    memory_mb INTEGER NOT NULL,
    disk_mb INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
