package com.cineconecta.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.playback.ContinueWatchingResponse;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.security.CurrentProfile;
import com.cineconecta.backend.service.ContinueWatchingService;

/** 'Continuar viendo' (RF57-RF61), ordered by recent activity. */
@RestController
@RequestMapping("/api/continue-watching")
public class ContinueWatchingController {

    private final ContinueWatchingService continueWatchingService;

    public ContinueWatchingController(ContinueWatchingService continueWatchingService) {
        this.continueWatchingService = continueWatchingService;
    }

    @GetMapping
    public List<ContinueWatchingResponse> list(@CurrentProfile Profile profile) {
        return continueWatchingService.list(profile.getProfileId());
    }
}
