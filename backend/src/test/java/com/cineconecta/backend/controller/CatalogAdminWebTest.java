package com.cineconecta.backend.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cineconecta.backend.config.CorsConfig;
import com.cineconecta.backend.config.SecurityConfig;
import com.cineconecta.backend.config.WebConfig;
import com.cineconecta.backend.dto.common.PageResponse;
import com.cineconecta.backend.exception.GlobalExceptionHandler;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.security.Role;
import com.cineconecta.backend.service.CatalogService;
import com.cineconecta.backend.service.ContentAdminService;
import com.cineconecta.backend.service.JwtService;
import com.cineconecta.backend.service.ReferenceValidationService;
import com.cineconecta.backend.service.impl.JwtServiceImpl;

/** Package 3 routes: /api/catalog needs ROLE_CLIENT, /api/admin/content needs ROLE_ADMIN. */
@WebMvcTest(controllers = { CatalogController.class, AdminContentController.class })
@Import({ SecurityConfig.class, CorsConfig.class, WebConfig.class, GlobalExceptionHandler.class, JwtServiceImpl.class })
class CatalogAdminWebTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean CatalogService catalogService;
    @MockitoBean ContentAdminService contentAdminService;
    @MockitoBean ReferenceValidationService references;

    private String clientToken(String accountId) {
        return "Bearer " + jwt.issue(new AuthenticatedUser(accountId, "x@example.com", "X", Role.CLIENT, null))
                .token();
    }

    private String adminToken() {
        return "Bearer " + jwt.issue(new AuthenticatedUser("64f0", "admin@cineconecta.com", "Admin", Role.ADMIN, null))
                .token();
    }

    @Test
    void searchingTheCatalogNeedsAToken() throws Exception {
        mvc.perform(get("/api/catalog")).andExpect(status().isUnauthorized());
        verifyNoInteractions(catalogService);
    }

    @Test
    void searchingTheCatalogNeedsAProfileHeader() throws Exception {
        mvc.perform(get("/api/catalog").header(HttpHeaders.AUTHORIZATION, clientToken("4")))
           .andExpect(status().isBadRequest());
    }

    @Test
    void anAdminTokenCannotSearchTheCatalog() throws Exception {
        mvc.perform(get("/api/catalog").header(HttpHeaders.AUTHORIZATION, adminToken()))
           .andExpect(status().isForbidden());
        verifyNoInteractions(catalogService);
    }

    @Test
    void aClientTokenCannotManageContent() throws Exception {
        mvc.perform(get("/api/admin/content").header(HttpHeaders.AUTHORIZATION, clientToken("4")))
           .andExpect(status().isForbidden());
        verifyNoInteractions(contentAdminService);
    }

    @Test
    void manageContentNeedsAToken() throws Exception {
        mvc.perform(get("/api/admin/content")).andExpect(status().isUnauthorized());
        verifyNoInteractions(contentAdminService);
    }

    @Test
    void adminCanListContent() throws Exception {
        when(contentAdminService.list(ContentStatus.DRAFT, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

        mvc.perform(get("/api/admin/content").param("status", "DRAFT")
                   .header(HttpHeaders.AUTHORIZATION, adminToken()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.totalItems").value(0));
    }
}
