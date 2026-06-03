package com.kxh.aiagent.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_action_log")
public class OpsActionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String incidentId;
    private String actionType;
    private String target;
    private String triggeredBy;
    private String confirmedBy;
    private LocalDateTime triggeredAt;
    private LocalDateTime executedAt;
    private String resultStatus;
    private String resultMessage;
}
