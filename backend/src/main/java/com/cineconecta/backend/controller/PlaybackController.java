package com.cineconecta.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.playback.PlaybackEventRequest;
import com.cineconecta.backend.dto.playback.PlaybackStartRequest;
import com.cineconecta.backend.dto.playback.PlaybackStateResponse;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.security.CurrentProfile;
import com.cineconecta.backend.service.PlaybackService;

import jakarta.validation.Valid;

/** Simulated playback (RF40-56). Every route after start() takes the session from the URL, scoped to the profile. */
@RestController
@RequestMapping("/api/playback")
public class PlaybackController {

    private final PlaybackService playbackService;

    public PlaybackController(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public PlaybackStateResponse start(@AuthenticationPrincipal AuthenticatedUser user,
                                       @CurrentProfile Profile profile,
                                       @Valid @RequestBody PlaybackStartRequest request) {
        return playbackService.start(user.accountId(), profile, request);
    }

    @PostMapping("/{sessionId}/pause")
    public PlaybackStateResponse pause(@CurrentProfile Profile profile, @PathVariable String sessionId) {
        return playbackService.pause(profile.getProfileId(), sessionId);
    }

    @PostMapping("/{sessionId}/resume")
    public PlaybackStateResponse resume(@CurrentProfile Profile profile, @PathVariable String sessionId) {
        return playbackService.resume(profile.getProfileId(), sessionId);
    }

    @PostMapping("/{sessionId}/seek")
    public PlaybackStateResponse seek(@CurrentProfile Profile profile, @PathVariable String sessionId,
                                      @Valid @RequestBody PlaybackEventRequest request) {
        return playbackService.seek(profile.getProfileId(), sessionId, request);
    }

    @PostMapping("/{sessionId}/finish")
    public PlaybackStateResponse finish(@CurrentProfile Profile profile, @PathVariable String sessionId) {
        return playbackService.finish(profile.getProfileId(), sessionId);
    }
}
