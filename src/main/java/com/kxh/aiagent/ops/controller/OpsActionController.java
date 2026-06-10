package com.kxh.aiagent.ops.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kxh.aiagent.ops.action.ActionExecutor;
import com.kxh.aiagent.ops.action.ActionExecutorRegistry;
import com.kxh.aiagent.ops.action.ActionType;
import com.kxh.aiagent.ops.entity.OpsActionLog;
import com.kxh.aiagent.ops.mapper.OpsActionLogMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HITL 审批端点 — Agent 提议的动作在这里完成"批准 / 驳回 / 执行"。
 *
 * 路由总览:
 *   GET  /ops/actions                      — 列表(可按 status 过滤)
 *   GET  /ops/actions/{id}                 — 单条详情
 *   GET  /ops/actions/{id}/approve-page?token=    — 邮件 magic-link 落地页(GET 友好)
 *   GET  /ops/actions/{id}/reject-page?token=     — 同上
 *   POST /ops/actions/{id}/approve         — 管理页调用 (form/json,token 走 body 或 query)
 *   POST /ops/actions/{id}/reject          — 同上
 */
@RestController
@RequestMapping("/ops/actions")
public class OpsActionController {

    private static final Logger log = LoggerFactory.getLogger(OpsActionController.class);

    @Resource(name = "opsActionLogMapper")
    private OpsActionLogMapper actionMapper;

    @Resource
    private ActionExecutorRegistry registry;

    // ============ 查询 ============

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String incidentId,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<OpsActionLog> q = new QueryWrapper<>();
        if (status != null && !status.isBlank()) q.eq("status", status.toUpperCase());
        if (incidentId != null && !incidentId.isBlank()) q.eq("incident_id", incidentId);
        q.orderByDesc("id");
        Page<OpsActionLog> result = actionMapper.selectPage(new Page<>(page, Math.min(size, 100)), q);
        Map<String, Object> resp = new HashMap<>();
        resp.put("total", result.getTotal());
        resp.put("page", result.getCurrent());
        resp.put("size", result.getSize());
        resp.put("records", result.getRecords());
        return resp;
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable("id") Long id) {
        OpsActionLog row = actionMapper.selectById(id);
        if (row == null) return Map.of("error", "not_found", "id", id);
        return row;
    }

    // ============ 邮件 magic-link 落地页 (返回简易 HTML 即可) ============

    @GetMapping(value = "/{id}/approve-page", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> approvePage(@PathVariable("id") Long id,
                                              @RequestParam("token") String token) {
        Result r = doApprove(id, token, "email-magic-link");
        return ResponseEntity.ok(landingPage("批准", r));
    }

    @GetMapping(value = "/{id}/reject-page", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> rejectPage(@PathVariable("id") Long id,
                                             @RequestParam("token") String token) {
        Result r = doReject(id, token, "email-magic-link");
        return ResponseEntity.ok(landingPage("驳回", r));
    }

    // ============ 管理页 / API 调用 ============

    @PostMapping("/{id}/approve")
    public Map<String, Object> approveApi(@PathVariable("id") Long id,
                                          @RequestParam("token") String token,
                                          @RequestParam(value = "operator", required = false) String operator) {
        Result r = doApprove(id, token, operator == null ? "admin-page" : operator);
        return toMap(r);
    }

    @PostMapping("/{id}/reject")
    public Map<String, Object> rejectApi(@PathVariable("id") Long id,
                                         @RequestParam("token") String token,
                                         @RequestParam(value = "operator", required = false) String operator) {
        Result r = doReject(id, token, operator == null ? "admin-page" : operator);
        return toMap(r);
    }

    // ============ 内部逻辑 ============

    private Result doApprove(Long id, String token, String operator) {
        OpsActionLog row = actionMapper.selectById(id);
        if (row == null) return Result.fail("not_found id=" + id);
        if (!"PENDING".equals(row.getStatus())) return Result.fail("非 PENDING 状态: " + row.getStatus());
        if (row.getApproveToken() == null || !row.getApproveToken().equals(token)) {
            return Result.fail("token 无效");
        }

        UpdateWrapper<OpsActionLog> w = new UpdateWrapper<>();
        w.eq("id", id).eq("status", "PENDING")    // 防并发: 只有还是 PENDING 时能改
                .set("status", "APPROVED")
                .set("confirmed_by", operator)
                .set("confirmed_at", LocalDateTime.now());
        int updated = actionMapper.update(null, w);
        if (updated == 0) return Result.fail("已被其他流程处理");

        // 异步执行,接口立即返回
        CompletableFuture.runAsync(() -> executeAsync(id));
        log.info("[Action #{}] APPROVED by {}, 异步执行中", id, operator);
        return Result.ok("已批准, 正在执行 actionId=" + id);
    }

    private Result doReject(Long id, String token, String operator) {
        OpsActionLog row = actionMapper.selectById(id);
        if (row == null) return Result.fail("not_found id=" + id);
        if (!"PENDING".equals(row.getStatus())) return Result.fail("非 PENDING 状态: " + row.getStatus());
        if (row.getApproveToken() == null || !row.getApproveToken().equals(token)) {
            return Result.fail("token 无效");
        }
        UpdateWrapper<OpsActionLog> w = new UpdateWrapper<>();
        w.eq("id", id).eq("status", "PENDING")
                .set("status", "REJECTED")
                .set("confirmed_by", operator)
                .set("confirmed_at", LocalDateTime.now());
        int updated = actionMapper.update(null, w);
        if (updated == 0) return Result.fail("已被其他流程处理");
        log.info("[Action #{}] REJECTED by {}", id, operator);
        return Result.ok("已驳回 actionId=" + id);
    }

    private void executeAsync(Long id) {
        OpsActionLog row = actionMapper.selectById(id);
        if (row == null || !"APPROVED".equals(row.getStatus())) return;

        ActionType type = ActionType.fromString(row.getActionType());
        ActionExecutor executor = type == null ? null : registry.get(type);
        ActionExecutor.ExecutionResult result;
        if (executor == null) {
            result = ActionExecutor.ExecutionResult.failed("无对应执行器: " + row.getActionType());
        } else {
            try {
                result = executor.execute(row);
            } catch (Exception e) {
                log.error("[Action #{}] executor 抛异常: {}", id, e.getMessage(), e);
                result = ActionExecutor.ExecutionResult.failed("exception: " + e.getMessage());
            }
        }

        UpdateWrapper<OpsActionLog> w = new UpdateWrapper<>();
        w.eq("id", id)
                .set("status", "EXECUTED".equals(result.status()) ? "EXECUTED" : "FAILED")
                .set("result_status", result.status())
                .set("result_message", truncate(result.message(), 60000))
                .set("executed_at", LocalDateTime.now());
        actionMapper.update(null, w);
        log.info("[Action #{}] 执行完成 status={} msg={}", id, result.status(),
                truncate(result.message(), 200));
    }

    private String truncate(String s, int n) {
        if (s == null) return null;
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }

    // ============ 辅助类 ============

    private record Result(boolean ok, String message) {
        static Result ok(String m)   { return new Result(true,  m); }
        static Result fail(String m) { return new Result(false, m); }
    }

    private Map<String, Object> toMap(Result r) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", r.ok());
        m.put("message", r.message());
        return m;
    }

    private String landingPage(String label, Result r) {
        String color = r.ok() ? "#1f883d" : "#cf222e";
        String icon  = r.ok() ? "✅" : "⚠️";
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<title>" + label + "结果</title></head>"
                + "<body style='font-family:Helvetica,Arial,sans-serif;text-align:center;padding:80px 20px'>"
                + "<div style='font-size:48px'>" + icon + "</div>"
                + "<h2 style='color:" + color + "'>" + label + (r.ok() ? "成功" : "失败") + "</h2>"
                + "<p style='color:#444'>" + escape(r.message()) + "</p>"
                + "<p style='margin-top:40px'><a href='/api/ops/actions.html'>← 返回管理页</a></p>"
                + "</body></html>";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
