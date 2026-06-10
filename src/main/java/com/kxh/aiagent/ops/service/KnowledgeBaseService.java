package com.kxh.aiagent.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kxh.aiagent.ops.entity.OpsKnowledgeEntry;
import com.kxh.aiagent.ops.history.OpsHistoryRecord;
import com.kxh.aiagent.ops.history.OpsHistoryService;
import com.kxh.aiagent.ops.mapper.OpsKnowledgeEntryMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库服务 — 双写架构:
 *   - MySQL 是主库 (ops_knowledge_base)        — 增删改查方便, 是数据源
 *   - Milvus 是 RAG 索引 (ops_incident_kb)     — 检索时用, 状态 active 时同步
 * <p>
 * 写策略:
 *   - 新增 active 条目      → INSERT MySQL + UPSERT Milvus
 *   - 更新 active 条目      → UPDATE MySQL + UPSERT Milvus  (Milvus upsert 幂等)
 *   - 改 archived           → UPDATE MySQL + DELETE Milvus  (从 RAG 索引下线)
 *   - 删除                  → DELETE MySQL + DELETE Milvus
 * <p>
 * 一致性: Milvus 操作失败不抛错 (历史 RAG 是辅助索引,MySQL 才是真相)。
 * 后续可加 outbox / 定时对账保证最终一致。
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    @Resource(name = "opsKnowledgeEntryMapper")
    private OpsKnowledgeEntryMapper mapper;

    @Autowired(required = false)
    private OpsHistoryService historyService;

    public Page<OpsKnowledgeEntry> list(String serviceName, String status, String keyword,
                                         int page, int size) {
        QueryWrapper<OpsKnowledgeEntry> q = new QueryWrapper<>();
        if (serviceName != null && !serviceName.isBlank()) q.eq("service_name", serviceName);
        if (status != null && !status.isBlank()) q.eq("status", status);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword + "%";
            q.and(wrapper -> wrapper.like("title", like)
                    .or().like("summary", like)
                    .or().like("resolution", like)
                    .or().like("error_class", like)
                    .or().like("tags", like));
        }
        q.orderByDesc("updated_at");
        return mapper.selectPage(new Page<>(page, Math.min(size, 100)), q);
    }

    public OpsKnowledgeEntry get(String id) { return mapper.selectById(id); }

    public OpsKnowledgeEntry create(OpsKnowledgeEntry entry, String operator) {
        if (entry.getId() == null || entry.getId().isBlank()) {
            entry.setId("kb-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }
        if (entry.getSource() == null || entry.getSource().isBlank()) entry.setSource("manual");
        if (entry.getStatus() == null || entry.getStatus().isBlank()) entry.setStatus("active");
        entry.setCreatedBy(operator);
        entry.setUpdatedBy(operator);
        LocalDateTime now = LocalDateTime.now();
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        mapper.insert(entry);

        if ("active".equals(entry.getStatus())) syncToMilvus(entry);
        log.info("[KB] 创建 id={} title={} source={}", entry.getId(), entry.getTitle(), entry.getSource());
        return entry;
    }

    public OpsKnowledgeEntry update(String id, OpsKnowledgeEntry patch, String operator) {
        OpsKnowledgeEntry existing = mapper.selectById(id);
        if (existing == null) return null;

        String oldStatus = existing.getStatus();
        if (patch.getServiceName() != null) existing.setServiceName(patch.getServiceName());
        if (patch.getErrorClass()  != null) existing.setErrorClass(patch.getErrorClass());
        if (patch.getSeverity()    != null) existing.setSeverity(patch.getSeverity());
        if (patch.getTitle()       != null) existing.setTitle(patch.getTitle());
        if (patch.getSummary()     != null) existing.setSummary(patch.getSummary());
        if (patch.getResolution()  != null) existing.setResolution(patch.getResolution());
        if (patch.getTags()        != null) existing.setTags(patch.getTags());
        if (patch.getStatus()      != null) existing.setStatus(patch.getStatus());
        if (patch.getOccurredAt()  != null) existing.setOccurredAt(patch.getOccurredAt());
        existing.setUpdatedBy(operator);
        existing.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(existing);

        // 状态切换 → Milvus 同步
        if ("active".equals(existing.getStatus())) {
            syncToMilvus(existing);
        } else if ("active".equals(oldStatus)) {
            removeFromMilvus(existing.getId());
        }
        log.info("[KB] 更新 id={} status={}", id, existing.getStatus());
        return existing;
    }

    public boolean delete(String id) {
        OpsKnowledgeEntry existing = mapper.selectById(id);
        if (existing == null) return false;
        mapper.deleteById(id);
        removeFromMilvus(id);
        log.info("[KB] 删除 id={}", id);
        return true;
    }

    /** 给前端用的 stats — 总数 / active / archived / 按 source 分布 */
    public Map<String, Object> stats() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("total", mapper.selectCount(null));
            resp.put("active",   mapper.selectCount(new QueryWrapper<OpsKnowledgeEntry>().eq("status", "active")));
            resp.put("archived", mapper.selectCount(new QueryWrapper<OpsKnowledgeEntry>().eq("status", "archived")));
            QueryWrapper<OpsKnowledgeEntry> q = new QueryWrapper<>();
            q.select("source", "count(*) as count").groupBy("source");
            resp.put("bySource", mapper.selectMaps(q));
        } catch (Exception e) {
            resp.put("error", e.getMessage());
        }
        return resp;
    }

    /** 把一条知识 → OpsHistoryRecord upsert 到 Milvus */
    private void syncToMilvus(OpsKnowledgeEntry e) {
        if (historyService == null) {
            log.debug("[KB] historyService 不可用, 跳过 Milvus 同步 id={}", e.getId());
            return;
        }
        try {
            long occurredAtSec = e.getOccurredAt() == null
                    ? System.currentTimeMillis() / 1000
                    : e.getOccurredAt().atZone(ZoneId.systemDefault()).toEpochSecond();
            String summaryWithTitle = (e.getTitle() == null ? "" : (e.getTitle() + "\n\n"))
                    + (e.getSummary() == null ? "" : e.getSummary());
            OpsHistoryRecord record = new OpsHistoryRecord(
                    e.getId(),
                    e.getServiceName(),
                    e.getErrorClass(),
                    e.getSeverity(),
                    occurredAtSec,
                    summaryWithTitle,
                    e.getResolution() == null ? "" : e.getResolution()
            );
            historyService.upsert(record);
        } catch (Exception ex) {
            log.warn("[KB] Milvus 同步失败 id={}: {}", e.getId(), ex.getMessage());
        }
    }

    private void removeFromMilvus(String id) {
        if (historyService == null) return;
        try {
            historyService.deleteById(id);
        } catch (Exception ex) {
            log.warn("[KB] Milvus 删除失败 id={}: {}", id, ex.getMessage());
        }
    }
}
