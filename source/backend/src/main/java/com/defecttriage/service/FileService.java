package com.defecttriage.service;

import com.defecttriage.common.BusinessException;
import com.defecttriage.dto.AttachmentResponse;
import com.defecttriage.entity.Attachment;
import com.defecttriage.entity.Defect;
import com.defecttriage.entity.User;
import com.defecttriage.repository.AttachmentRepository;
import com.defecttriage.repository.DefectRepository;
import com.defecttriage.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "text/plain", "application/pdf");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final int MAX_FILES_PER_DEFECT = 5;

    private final AttachmentRepository attachmentRepository;
    private final DefectRepository defectRepository;
    private final UserRepository userRepository;
    private final Path uploadDir;

    public FileService(AttachmentRepository attachmentRepository,
                       DefectRepository defectRepository,
                       UserRepository userRepository,
                       @Value("${file.upload-dir}") String uploadDir) {
        this.attachmentRepository = attachmentRepository;
        this.defectRepository = defectRepository;
        this.userRepository = userRepository;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public AttachmentResponse upload(Long defectId, MultipartFile file, Long userId) throws IOException {
        Defect defect = defectRepository.findById(defectId)
                .orElseThrow(() -> new BusinessException("缺陷不存在"));

        if (file.isEmpty()) throw new BusinessException("文件为空");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException("文件大小不能超过 5MB");
        if (!ALLOWED_TYPES.contains(file.getContentType()))
            throw new BusinessException("不支持的文件类型: " + file.getContentType());
        if (attachmentRepository.countByDefectId(defectId) >= MAX_FILES_PER_DEFECT)
            throw new BusinessException("每个缺陷最多上传 5 个附件");

        User uploader = userRepository.findById(userId).orElseThrow();
        String filename = UUID.randomUUID().toString();
        Path defectDir = uploadDir.resolve(defectId.toString());
        Files.createDirectories(defectDir);
        Path filePath = defectDir.resolve(filename);
        file.transferTo(filePath.toFile());

        Attachment attachment = new Attachment();
        attachment.setDefect(defect);
        attachment.setFilename(filename);
        attachment.setOriginalFilename(file.getOriginalFilename());
        attachment.setFilePath(filePath.toString());
        attachment.setFileSize(file.getSize());
        attachment.setMimeType(file.getContentType());
        attachment.setUploadedBy(uploader);
        attachment = attachmentRepository.save(attachment);

        return AttachmentResponse.builder()
                .id(attachment.getId())
                .originalFilename(attachment.getOriginalFilename())
                .fileSize(attachment.getFileSize())
                .mimeType(attachment.getMimeType())
                .uploadedAt(attachment.getUploadedAt())
                .build();
    }

    public List<AttachmentResponse> getAttachments(Long defectId) {
        return attachmentRepository.findByDefectId(defectId).stream()
                .map(a -> AttachmentResponse.builder()
                        .id(a.getId())
                        .originalFilename(a.getOriginalFilename())
                        .fileSize(a.getFileSize())
                        .mimeType(a.getMimeType())
                        .uploadedAt(a.getUploadedAt())
                        .build())
                .toList();
    }

    public Resource download(Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException("附件不存在"));
        Resource resource = new FileSystemResource(Paths.get(attachment.getFilePath()));
        if (!resource.exists()) throw new BusinessException("文件不存在");
        return resource;
    }

    public void delete(Long attachmentId, Long userId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException("附件不存在"));
        try {
            Files.deleteIfExists(Paths.get(attachment.getFilePath()));
        } catch (IOException ignored) {}
        attachmentRepository.delete(attachment);
    }
}
