package com.cineconecta.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.profile.ProfileRequest;
import com.cineconecta.backend.dto.profile.ProfileResponse;
import com.cineconecta.backend.mapper.ProfileMapper;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.security.CurrentProfile;
import com.cineconecta.backend.service.ProfileService;

import jakarta.validation.Valid;

/**
 * Profile management (RF6-RF12). The {profileId} in the URL is only accepted if it belongs
 * to the account in the token; the service checks it on every call.
 */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileMapper mapper;

    public ProfileController(ProfileService profileService, ProfileMapper mapper) {
        this.profileService = profileService;
        this.mapper = mapper;
    }

    @GetMapping
    public List<ProfileResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return profileService.list(user.accountId());
    }

    /**
     * Selecting a profile (RF12): the frontend sends X-Profile-Id and gets it back only if it
     * is an active profile of the account. Every profile-scoped route repeats this check.
     */
    @GetMapping("/current")
    public ProfileResponse current(@CurrentProfile Profile profile) {
        return mapper.toResponse(profile);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody ProfileRequest request) {
        return profileService.create(user.accountId(), request);
    }

    @PutMapping("/{profileId}")
    public ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable Integer profileId,
                                  @Valid @RequestBody ProfileRequest request) {
        return profileService.update(user.accountId(), profileId, request);
    }

    @PostMapping("/{profileId}/deactivate")
    public ProfileResponse deactivate(@AuthenticationPrincipal AuthenticatedUser user,
                                      @PathVariable Integer profileId) {
        return profileService.deactivate(user.accountId(), profileId);
    }

    @PostMapping("/{profileId}/activate")
    public ProfileResponse activate(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Integer profileId) {
        return profileService.activate(user.accountId(), profileId);
    }
}
