package com.swiftroute.authservice.service;

import org.springframework.stereotype.Service;

import com.swiftroute.authservice.dto.request.LoginRequestDTO;
import com.swiftroute.authservice.dto.request.RegisterRequestDTO;
import com.swiftroute.authservice.dto.response.LoginResponseDTO;

import jakarta.servlet.http.HttpServletRequest;

@Service
public interface AuthService {

    public LoginResponseDTO login(LoginRequestDTO request, HttpServletRequest httpRequest);

    void register(RegisterRequestDTO request);

    public LoginResponseDTO refreshAccessToken(String refreshToken, HttpServletRequest httpRequest);

    public void logout(String refreshToken);

}
