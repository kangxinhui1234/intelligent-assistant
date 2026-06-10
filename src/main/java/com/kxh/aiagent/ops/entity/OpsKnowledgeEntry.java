package com.kxh.aiagent.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_knowledge_base")
public class OpsKnowledgeEntry {

    @TableId(type = IdType.INPUT)
    private String id;

    private String serviceName;
    private String errorClass;
    private String severity;
    private String title;
    private String summary;
    private String resolution;
    private String tags;
    private String source;     // manual / auto / imported
    private String status;     // active / archived
    private String createdBy;
    private String updatedBy;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
