package com.cineconecta.backend.service;

import com.cineconecta.backend.dto.auth.LoginRequest;
import com.cineconecta.backend.dto.auth.RegisterRequest;
import com.cineconecta.backend.dto.auth.TokenResponse;

/** Registration and login for customers and administrators. */
public interface AuthService {

    /** Creates the account (RF1) and logs it in. Duplicate email, any letter case: 409 (RF2). */
    TokenResponse register(RegisterRequest request);

    /** Customer login against ACCOUNTS (RF3). Wrong email or password: 401. */
    TokenResponse login(LoginRequest request);

    /** Administrator login against Admin_user (RF64). Wrong email or password: 401. */
    TokenResponse adminLogin(LoginRequest request);
}
