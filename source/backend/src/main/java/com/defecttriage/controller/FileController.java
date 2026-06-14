package com.defecttriage.controller;

import com.defecttriage.dto.AttachmentResponse;
import com.defecttriage.service.FileService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/defects/{defectId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> list(@PathVariable Long defectId) {
        return ResponseEntity.ok(fileService.getAttachments(defectId));
    }

    @PostMapping("/defects/{defectId}/attachments")
    public ResponseEntity<AttachmentResponse> upload(@PathVariable Long defectId,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestAttribute("userId") Long userId) throws IOException {
        return ResponseEntity.ok(fileService.upload(defectId, file, userId));
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Resource resource = fileService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @RequestAttribute("userId") Long userId) {
        fileService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
