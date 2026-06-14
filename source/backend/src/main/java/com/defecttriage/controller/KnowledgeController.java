package com.defecttriage.controller;

import com.defecttriage.common.KnowledgeType;
import com.defecttriage.dto.KnowledgeItemResponse;
import com.defecttriage.dto.KnowledgeUpdateRequest;
import com.defecttriage.service.KnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    public ResponseEntity<List<KnowledgeItemResponse>> list(
            @RequestParam(required = false) KnowledgeType type,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(knowledgeService.listAll(Optional.ofNullable(type), Optional.ofNullable(keyword)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeItemResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(knowledgeService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeItemResponse> update(@PathVariable Long id,
                                                         @RequestBody KnowledgeUpdateRequest req) {
        return ResponseEntity.ok(knowledgeService.update(id, req));
    }

    @PutMapping("/{id}/publish")
    public ResponseEntity<KnowledgeItemResponse> publish(@PathVariable Long id) {
        return ResponseEntity.ok(knowledgeService.publish(id));
    }
}
