CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE nodes (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       name VARCHAR(100) NOT NULL,
                       token VARCHAR(255) NOT NULL UNIQUE,
                       status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
                       cpu_total DECIMAL(6,2) NOT NULL,
                       cpu_free DECIMAL(6,2) NOT NULL,
                       ram_total_mb INTEGER NOT NULL,
                       ram_free_mb INTEGER NOT NULL,
                       disk_total_mb INTEGER NOT NULL,
                       disk_free_mb INTEGER NOT NULL,
                       temperature_celsius DECIMAL(5,2),
                       last_heartbeat TIMESTAMP,
                       created_at TIMESTAMP NOT NULL DEFAULT now()
);