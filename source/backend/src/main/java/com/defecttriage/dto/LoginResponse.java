package com.defecttriage.dto;

import com.defecttriage.common.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String displayName;
    private UserRole role;
}
