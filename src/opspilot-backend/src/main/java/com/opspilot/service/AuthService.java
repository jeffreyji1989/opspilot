package com.opspilot.service;

import com.opspilot.dto.LoginRequest;
import com.opspilot.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request, String clientIp);
}
