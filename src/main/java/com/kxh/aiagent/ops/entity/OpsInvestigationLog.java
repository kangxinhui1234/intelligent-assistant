package com.kxh.aiagent.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_investigation_log")
public class OpsInvestigationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String incidentId;
    private String agentName;
    private String outputText;
    private Long durationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private LocalDateTime createdAt;
}
