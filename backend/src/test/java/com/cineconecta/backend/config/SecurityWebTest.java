package com.cineconecta.backend.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.controller.AdminAuthController;
import com.cineconecta.backend.controller.AuthController;
import com.cineconecta.backend.dto.auth.TokenResponse;
import com.cineconecta.backend.exception.ConflictException;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.exception.GlobalExceptionHandler;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.security.CurrentProfile;
import com.cineconecta.backend.security.Role;
import com.cineconecta.backend.service.AuthService;
import com.cineconecta.backend.service.JwtService;
import com.cineconecta.backend.service.ReferenceValidationService;
import com.cineconecta.backend.service.impl.JwtServiceImpl;

/** Web layer only (no database): both filter chains, roles, CORS, X-Profile-Id and error bodies. */
@WebMvcTest(controllers = { AuthController.class, AdminAuthController.class, SecurityWebTest.ProbeController.class })
@Import({ SecurityConfig.class, CorsConfig.class, WebConfig.class, GlobalExceptionHandler.class,
          JwtServiceImpl.class, SecurityWebTest.ProbeController.class })
class SecurityWebTest {

    /** Stands in for the profile-scoped controllers of packages 3 and 4. */
    @RestController
    static class ProbeController {
        @GetMapping("/api/probe/profile")
        Integer profile(@CurrentProfile Profile profile) {
            return profile.getProfileId();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean AuthService authService;
    @MockitoBean ReferenceValidationService references;

    private String clientToken() {
        return "Bearer " + jwt.issue(new AuthenticatedUser("1", "juan.perez@example.com", "Juan", Role.CLIENT, null)).token();
    }

    private String adminToken() {
        return "Bearer " + jwt.issue(new AuthenticatedUser("abc123", "admin@cineconecta.com", "Admin", Role.ADMIN, null)).token();
    }

    // ---- Authentication -------------------------------------------------------------------------

    @Test
    void protectedRouteWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/me"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tamperedTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, clientToken() + "x"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void clientTokenReachesClientRoutes() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, clientToken()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value("1"))
           .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    void adminTokenReachesAdminRoutes() throws Exception {
        mvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, adminToken()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ---- Roles never mix (RNF3, RNF4) -----------------------------------------------------------

    @Test
    void clientTokenOnAdminRouteIs403() throws Exception {
        mvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, clientToken()))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminTokenOnClientRouteIs403() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, adminToken()))
           .andExpect(status().isForbidden());
    }

    @Test
    void routesOutsideTheApiAreDenied() throws Exception {
        mvc.perform(get("/anything").header(HttpHeaders.AUTHORIZATION, clientToken()))
           .andExpect(status().isForbidden());
    }

    @Test
    void plansArePublic() throws Exception {
        // No PlanController handler yet (package 2): 404 proves security let the request through.
        mvc.perform(get("/api/plans")).andExpect(status().isNotFound());
    }

    // ---- Login and register -----------------------------------------------------------------------

    @Test
    void loginIsPublicAndReturnsToken() throws Exception {
        when(authService.login(any())).thenReturn(new TokenResponse("t", Instant.now(), "CLIENT", "Juan"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                   .content("{\"email\":\"juan.perez@example.com\",\"password\":\"x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.token").value("t"));
    }

    @Test
    void wrongPasswordIs401WithoutSayingWhy() throws Exception {
        when(authService.adminLogin(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                   .content("{\"email\":\"admin@cineconecta.com\",\"password\":\"wrong\"}"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void invalidRegisterBodyIs400WithFieldDetails() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                   .content("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"123\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION"))
           .andExpect(jsonPath("$.details.length()").value(3));
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        when(authService.register(any())).thenThrow(new ConflictException("Ya existe una cuenta con ese correo"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                   .content("{\"fullName\":\"Juan\",\"email\":\"JUAN.Perez@Example.COM\",\"password\":\"Cliente123\"}"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ---- CORS ---------------------------------------------------------------------------------------

    @Test
    void corsPreflightFromTheFrontendIsAllowed() throws Exception {
        mvc.perform(options("/api/auth/login")
                   .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                   .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                   .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-profile-id"))
           .andExpect(status().isOk())
           .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void corsFromAnotherOriginIsRejected() throws Exception {
        mvc.perform(options("/api/auth/login")
                   .header(HttpHeaders.ORIGIN, "http://evil.example")
                   .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
           .andExpect(status().isForbidden());
    }

    // ---- X-Profile-Id (RNF2, RF12) --------------------------------------------------------------------

    @Test
    void profileIsCheckedAgainstTheAccountInTheToken() throws Exception {
        Profile profile = mock(Profile.class);
        when(profile.getProfileId()).thenReturn(2);
        when(references.requireActiveProfile(1, 2)).thenReturn(profile);

        mvc.perform(get("/api/probe/profile").header(HttpHeaders.AUTHORIZATION, clientToken())
                   .header(CurrentProfile.HEADER, "2"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$").value(2));
        verify(references).requireActiveProfile(1, 2); // accountId from the JWT, never from the client
    }

    @Test
    void profileOfAnotherAccountIs403() throws Exception {
        when(references.requireActiveProfile(1, 4)).thenThrow(new ForbiddenOperationException("El perfil no pertenece a la cuenta"));

        mvc.perform(get("/api/probe/profile").header(HttpHeaders.AUTHORIZATION, clientToken())
                   .header(CurrentProfile.HEADER, "4"))
           .andExpect(status().isForbidden());
    }

    @Test
    void missingOrInvalidProfileHeaderIs400() throws Exception {
        mvc.perform(get("/api/probe/profile").header(HttpHeaders.AUTHORIZATION, clientToken()))
           .andExpect(status().isBadRequest());
        mvc.perform(get("/api/probe/profile").header(HttpHeaders.AUTHORIZATION, clientToken())
                   .header(CurrentProfile.HEADER, "abc"))
           .andExpect(status().isBadRequest());
    }
}
