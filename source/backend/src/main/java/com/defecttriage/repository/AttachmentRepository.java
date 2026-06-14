package com.defecttriage.repository;

import com.defecttriage.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByDefectId(Long defectId);
    int countByDefectId(Long defectId);
}
