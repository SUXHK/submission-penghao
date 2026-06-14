package com.defecttriage.repository;

import com.defecttriage.common.DefectStatus;
import com.defecttriage.entity.Defect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DefectRepository extends JpaRepository<Defect, Long> {

    List<Defect> findByStatusIn(List<DefectStatus> statuses);

    @Query("SELECT d FROM Defect d WHERE " +
           "(:keyword IS NULL OR d.title LIKE %:keyword% OR d.description LIKE %:keyword%)")
    List<Defect> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT d FROM Defect d WHERE " +
           "d.status IN :statuses AND " +
           "(:keyword IS NULL OR d.title LIKE %:keyword% OR d.description LIKE %:keyword%)")
    List<Defect> findByStatusInAndKeyword(
            @Param("statuses") List<DefectStatus> statuses,
            @Param("keyword") String keyword);
}
