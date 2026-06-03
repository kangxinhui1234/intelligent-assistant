package com.kxh.aiagent.ops.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Evidence(
        String source,
        String reference,
        String snippet
) {}
