package com.defecttriage.repository;

import com.defecttriage.common.KnowledgeType;
import com.defecttriage.entity.KnowledgeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, Long> {

    @Query("SELECT k FROM KnowledgeItem k WHERE " +
           "(:type IS NULL OR k.type = :type) AND " +
           "(:keyword IS NULL OR k.title LIKE %:keyword% OR k.content LIKE %:keyword%)")
    List<KnowledgeItem> findByTypeAndKeyword(
            @Param("type") KnowledgeType type,
            @Param("keyword") String keyword);
}
