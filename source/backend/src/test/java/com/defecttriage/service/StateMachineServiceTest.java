package com.defecttriage.service;

import com.defecttriage.common.BusinessException;
import com.defecttriage.common.DefectStatus;
import com.defecttriage.common.ForbiddenException;
import com.defecttriage.common.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StateMachineServiceTest {

    private final StateMachineService service = new StateMachineService();

    @Test
    void shouldAllowValidTransition() {
        assertDoesNotThrow(() -> service.validateTransition(DefectStatus.DRAFT, DefectStatus.REPORTED, UserRole.SUBMITTER));
        assertDoesNotThrow(() -> service.validateTransition(DefectStatus.REPORTED, DefectStatus.TRIAGING, UserRole.ENGINEER));
        assertDoesNotThrow(() -> service.validateTransition(DefectStatus.FIXED, DefectStatus.VERIFIED, UserRole.QA));
    }

    @Test
    void shouldRejectSameStatusTransition() {
        var ex = assertThrows(BusinessException.class,
                () -> service.validateTransition(DefectStatus.DRAFT, DefectStatus.DRAFT, UserRole.SUBMITTER));
        assertTrue(ex.getMessage().contains("相同状态"));
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void shouldRejectInvalidTransition(DefectStatus from, DefectStatus to, UserRole role) {
        assertThrows(BusinessException.class, () -> service.validateTransition(from, to, role));
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(DefectStatus.DRAFT, DefectStatus.TRIAGING, UserRole.SUBMITTER),
                Arguments.of(DefectStatus.REPORTED, DefectStatus.FIXED, UserRole.ENGINEER),
                Arguments.of(DefectStatus.REPORTED, DefectStatus.CLOSED, UserRole.QA),
                Arguments.of(DefectStatus.TRIAGING, DefectStatus.IN_REPAIR, UserRole.ENGINEER),
                Arguments.of(DefectStatus.IN_REPAIR, DefectStatus.CLOSED, UserRole.ENGINEER),
                Arguments.of(DefectStatus.FIXED, DefectStatus.CLOSED, UserRole.QA),
                Arguments.of(DefectStatus.CLOSED, DefectStatus.IN_REPAIR, UserRole.ENGINEER),
                Arguments.of(DefectStatus.DRAFT, DefectStatus.CLOSED, UserRole.SUBMITTER)
        );
    }

    @Test
    void shouldRejectWrongRoleTransition() {
        assertThrows(ForbiddenException.class,
                () -> service.validateTransition(DefectStatus.REPORTED, DefectStatus.TRIAGING, UserRole.SUBMITTER));
        assertThrows(ForbiddenException.class,
                () -> service.validateTransition(DefectStatus.FIXED, DefectStatus.VERIFIED, UserRole.SUBMITTER));
        assertThrows(ForbiddenException.class,
                () -> service.validateTransition(DefectStatus.FIXED, DefectStatus.VERIFIED, UserRole.ENGINEER));
    }
}
