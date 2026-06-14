package com.defecttriage.controller;

import com.defecttriage.common.DefectStatus;
import com.defecttriage.dto.*;
import com.defecttriage.service.DefectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/defects")
public class DefectController {

    private final DefectService defectService;

    public DefectController(DefectService defectService) {
        this.defectService = defectService;
    }

    @GetMapping
    public ResponseEntity<List<DefectListResponse>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(defectService.listDefects(Optional.ofNullable(status), Optional.ofNullable(keyword)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DefectResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(defectService.getDefect(id));
    }

    @PostMapping
    public ResponseEntity<DefectResponse> create(@RequestBody DefectCreateRequest req,
                                                  @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(defectService.createDefect(req, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DefectResponse> update(@PathVariable Long id,
                                                  @RequestBody DefectUpdateRequest req,
                                                  @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(defectService.updateDefect(id, req, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @RequestAttribute("userId") Long userId) {
        defectService.deleteDefect(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/transition")
    public ResponseEntity<DefectResponse> transition(@PathVariable Long id,
                                                      @RequestParam("to") DefectStatus targetStatus,
                                                      @RequestBody(required = false) TransitionRequest req,
                                                      @RequestAttribute("userId") Long userId) {
        if (req == null) req = new TransitionRequest();
        return ResponseEntity.ok(defectService.transition(id, targetStatus, req, userId));
    }

    @GetMapping("/{id}/transitions")
    public ResponseEntity<List<StateTransitionResponse>> getTransitions(@PathVariable Long id) {
        return ResponseEntity.ok(defectService.getTransitions(id));
    }
}
