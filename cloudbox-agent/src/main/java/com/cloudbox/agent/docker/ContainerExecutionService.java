package com.cloudbox.agent.docker;

import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.command.PullImageResultCallback;

@Service
public class ContainerExecutionService {

    private final DockerClient dockerClient;

    public ContainerExecutionService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public void pullImage(String image) {
        try {
            dockerClient.pullImageCmd(image)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Download da imagem Docker interrompido: " + image, exception);
        }
    }

    public String createContainer(String image, String name) {
        CreateContainerResponse response = dockerClient.createContainerCmd(image)
                .withName(name)
                .exec();
        return response.getId();
    }

    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    public void stopContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
    }

    public ContainerStatus inspectContainer(String containerId) {
        InspectContainerResponse response = dockerClient.inspectContainerCmd(containerId).exec();
        InspectContainerResponse.ContainerState state = response.getState();
        return new ContainerStatus(
                response.getId(),
                state == null ? null : state.getStatus(),
                state != null && Boolean.TRUE.equals(state.getRunning()),
                state == null ? null : state.getExitCodeLong());
    }

    public void removeContainer(String containerId) {
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
    }
}
