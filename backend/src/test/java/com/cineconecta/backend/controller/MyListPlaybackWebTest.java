package com.cineconecta.backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cineconecta.backend.config.CorsConfig;
import com.cineconecta.backend.config.SecurityConfig;
import com.cineconecta.backend.config.WebConfig;
import com.cineconecta.backend.exception.GlobalExceptionHandler;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.security.Role;
import com.cineconecta.backend.service.ContinueWatchingService;
import com.cineconecta.backend.service.JwtService;
import com.cineconecta.backend.service.MyListService;
import com.cineconecta.backend.service.PlaybackService;
import com.cineconecta.backend.service.ReferenceValidationService;
import com.cineconecta.backend.service.impl.JwtServiceImpl;
import com.cineconecta.backend.model.relational.Profile;

/** Package 4 routes: ROLE_CLIENT + an active profile via X-Profile-Id, same as package 3. */
@WebMvcTest(controllers = { MyListController.class, PlaybackController.class, ContinueWatchingController.class })
@Import({ SecurityConfig.class, CorsConfig.class, WebConfig.class, GlobalExceptionHandler.class, JwtServiceImpl.class })
class MyListPlaybackWebTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean MyListService myListService;
    @MockitoBean PlaybackService playbackService;
    @MockitoBean ContinueWatchingService continueWatchingService;
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
    void myListNeedsAToken() throws Exception {
        mvc.perform(get("/api/my-list")).andExpect(status().isUnauthorized());
        verifyNoInteractions(myListService);
    }

    @Test
    void myListNeedsAProfileHeader() throws Exception {
        mvc.perform(get("/api/my-list").header(HttpHeaders.AUTHORIZATION, clientToken("4")))
           .andExpect(status().isBadRequest());
    }

    @Test
    void anAdminTokenCannotUseMyList() throws Exception {
        mvc.perform(get("/api/my-list").header(HttpHeaders.AUTHORIZATION, adminToken()))
           .andExpect(status().isForbidden());
        verifyNoInteractions(myListService);
    }

    @Test
    void startingPlaybackNeedsAToken() throws Exception {
        mvc.perform(post("/api/playback/start").contentType(MediaType.APPLICATION_JSON)
                   .content("{\"contentId\":\"MOV-00001\"}"))
           .andExpect(status().isUnauthorized());
        verifyNoInteractions(playbackService);
    }

    @Test
    void continueWatchingWorksForAClientWithAProfile() throws Exception {
        Profile profile = mock(Profile.class);
        when(profile.getProfileId()).thenReturn(4);
        when(references.requireActiveProfile(4, 4)).thenReturn(profile);
        when(continueWatchingService.list(4)).thenReturn(List.of());

        mvc.perform(get("/api/continue-watching").header(HttpHeaders.AUTHORIZATION, clientToken("4"))
                   .header("X-Profile-Id", "4"))
           .andExpect(status().isOk());
    }
}
