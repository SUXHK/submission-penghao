package com.defecttriage.service;

import com.defecttriage.common.BusinessException;
import com.defecttriage.common.DefectStatus;
import com.defecttriage.common.ForbiddenException;
import com.defecttriage.common.UserRole;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class StateMachineService {

    private static final Map<DefectStatus, Set<DefectStatus>> VALID_TRANSITIONS = Map.of(
            DefectStatus.DRAFT, Set.of(DefectStatus.REPORTED),
            DefectStatus.REPORTED, Set.of(DefectStatus.TRIAGING),
            DefectStatus.TRIAGING, Set.of(DefectStatus.ANALYZED),
            DefectStatus.ANALYZED, Set.of(DefectStatus.PLANNED),
            DefectStatus.PLANNED, Set.of(DefectStatus.IN_REPAIR),
            DefectStatus.IN_REPAIR, Set.of(DefectStatus.FIXED),
            DefectStatus.FIXED, Set.of(DefectStatus.VERIFIED, DefectStatus.IN_REPAIR),
            DefectStatus.VERIFIED, Set.of(DefectStatus.CLOSED),
            DefectStatus.CLOSED, Set.of(DefectStatus.REOPENED),
            DefectStatus.REOPENED, Set.of(DefectStatus.REPORTED)
    );

    private static final Map<DefectStatus, UserRole> REQUIRED_ROLES = Map.of(
            DefectStatus.REPORTED, UserRole.SUBMITTER,
            DefectStatus.TRIAGING, UserRole.ENGINEER,
            DefectStatus.ANALYZED, UserRole.ENGINEER,
            DefectStatus.PLANNED, UserRole.ENGINEER,
            DefectStatus.IN_REPAIR, UserRole.ENGINEER,
            DefectStatus.FIXED, UserRole.ENGINEER,
            DefectStatus.VERIFIED, UserRole.QA,
            DefectStatus.CLOSED, UserRole.QA,
            DefectStatus.REOPENED, UserRole.ENGINEER
    );

    // Edge cases where same target requires different role depending on source
    private static final Map<DefectStatus, Map<DefectStatus, UserRole>> SOURCE_SPECIFIC_ROLES = Map.of(
            DefectStatus.FIXED, Map.of(DefectStatus.IN_REPAIR, UserRole.QA),
            DefectStatus.REOPENED, Map.of(DefectStatus.REPORTED, UserRole.ENGINEER)
    );

    public void validateTransition(DefectStatus from, DefectStatus to, UserRole userRole) {
        if (from == to) {
            throw new BusinessException("不能流转到相同状态");
        }

        Set<DefectStatus> validTargets = VALID_TRANSITIONS.get(from);
        if (validTargets == null || !validTargets.contains(to)) {
            throw new BusinessException("不允许从 " + from + " 流转到 " + to);
        }

        UserRole requiredRole = null;
        Map<DefectStatus, UserRole> specificMap = SOURCE_SPECIFIC_ROLES.get(from);
        if (specificMap != null) {
            requiredRole = specificMap.get(to);
        }
        if (requiredRole == null) {
            requiredRole = REQUIRED_ROLES.get(to);
        }
        if (requiredRole != null && requiredRole != userRole) {
            throw new ForbiddenException("当前角色无权执行此操作，需要 " + requiredRole + " 角色");
        }
    }
}
