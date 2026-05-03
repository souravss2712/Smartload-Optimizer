package com.smartload.controller;

import com.smartload.model.OptimizeRequest;
import com.smartload.model.OptimizeResponse;
import com.smartload.service.LoadOptimizerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/load-optimizer")
@RequiredArgsConstructor
public class LoadOptimizerController {

    private final LoadOptimizerService loadOptimizerService;

    @PostMapping(
            value = "/optimize",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public OptimizeResponse optimize(@Valid @RequestBody OptimizeRequest request) {
        return loadOptimizerService.optimize(request);
    }
}
