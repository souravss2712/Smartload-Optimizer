package com.smartload.service;

import com.smartload.model.OptimizeRequest;
import com.smartload.model.OptimizeResponse;

public interface LoadOptimizerService {

    OptimizeResponse optimize(OptimizeRequest request);
}
