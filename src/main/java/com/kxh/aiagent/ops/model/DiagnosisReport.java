package com.kxh.aiagent.ops.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiagnosisReport(
        String incidentId,
        List<Hypothesis> hypotheses,
        double topConfidence,
        String suggestedAction,
        long totalDurationMs,
        int totalTokens
) {}
