ALTER TABLE container_instances
    ADD COLUMN docker_container_id VARCHAR(255),
    ADD COLUMN error_message TEXT;
