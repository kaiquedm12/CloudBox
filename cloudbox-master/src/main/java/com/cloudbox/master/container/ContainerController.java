package com.cloudbox.master.container;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.cloudbox.master.container.dto.ContainerRequest;
import com.cloudbox.master.container.dto.ContainerResponse;

@RestController
@RequestMapping("/api/containers")
public class ContainerController {

    private final ContainerService containerService;

    public ContainerController(ContainerService containerService) {
        this.containerService = containerService;
    }

    @PostMapping
    public ResponseEntity<ContainerResponse> create(@Valid @RequestBody ContainerRequest request) {
        ContainerResponse response = containerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ContainerResponse>> findAll() {
        return ResponseEntity.ok(containerService.findAll());
    }
}
