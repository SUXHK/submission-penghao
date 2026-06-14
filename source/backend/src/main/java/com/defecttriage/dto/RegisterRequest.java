package com.defecttriage.dto;

import com.defecttriage.common.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    private String username;
    @NotBlank @Size(min = 6)
    private String password;
    @NotBlank
    private String displayName;
    @NotNull
    private UserRole role;
}
