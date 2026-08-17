ALTER TABLE container_instances
    ADD COLUMN node_id UUID;

CREATE INDEX idx_container_instances_node_id ON container_instances (node_id);