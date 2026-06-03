package com.kxh.aiagent.ops.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Hypothesis(
        String description,
        double confidence,
        List<Evidence> evidences
) {}
