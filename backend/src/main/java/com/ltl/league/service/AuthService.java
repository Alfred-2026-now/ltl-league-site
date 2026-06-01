package com.ltl.league.service;

import com.ltl.league.dto.LoginRequest;
import com.ltl.league.dto.LoginResponse;
import com.ltl.league.entity.Player;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse getCurrentUser(String token);

    void logout();
}
