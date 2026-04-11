package com.opspilot.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String displayName;
    private Integer role;
}
