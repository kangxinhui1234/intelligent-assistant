package com.kxh.aiagent.ops.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kxh.aiagent.ops.entity.OpsKnowledgeEntry;
import com.kxh.aiagent.ops.service.KnowledgeBaseService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ops/knowledge")
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService service;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<OpsKnowledgeEntry> result = this.service.list(service, status, keyword, page, size);
        Map<String, Object> resp = new HashMap<>();
        resp.put("total", result.getTotal());
        resp.put("page", result.getCurrent());
        resp.put("size", result.getSize());
        resp.put("records", result.getRecords());
        return resp;
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable("id") String id) {
        OpsKnowledgeEntry e = service.get(id);
        return e == null ? Map.of("error", "not_found", "id", id) : e;
    }

    @PostMapping
    public OpsKnowledgeEntry create(@RequestBody OpsKnowledgeEntry entry,
                                     @RequestParam(value = "operator", required = false) String operator) {
        return service.create(entry, operator == null ? "anonymous" : operator);
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable("id") String id,
                          @RequestBody OpsKnowledgeEntry patch,
                          @RequestParam(value = "operator", required = false) String operator) {
        OpsKnowledgeEntry e = service.update(id, patch, operator == null ? "anonymous" : operator);
        return e == null ? Map.of("error", "not_found", "id", id) : e;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") String id) {
        boolean ok = service.delete(id);
        return Map.of("ok", ok, "id", id);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() { return service.stats(); }
}
