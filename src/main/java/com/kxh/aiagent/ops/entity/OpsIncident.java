package com.kxh.aiagent.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_incident")
public class OpsIncident {

    @TableId(type = IdType.INPUT)
    private String id;

    private String fingerprint;
    private String source;
    private String serviceName;
    private String severity;
    private String status;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
    private LocalDateTime resolvedAt;
    private String summary;
    private String reportPath;
    private LocalDateTime createdAt;
}
