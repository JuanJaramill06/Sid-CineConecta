package com.cineconecta.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.catalog.CatalogSearchRequest;
import com.cineconecta.backend.dto.catalog.ContentDetailResponse;
import com.cineconecta.backend.dto.catalog.ContentSummaryResponse;
import com.cineconecta.backend.dto.common.PageResponse;
import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.security.CurrentProfile;
import com.cineconecta.backend.service.CatalogService;

/**
 * Browsing the catalog (RF32-RF35): needs an active profile (@CurrentProfile) and a current
 * subscription, checked by CatalogService. Already restricted to ROLE_CLIENT by SecurityConfig.
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public PageResponse<ContentSummaryResponse> search(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @CurrentProfile Profile profile,
                                                        @RequestParam(required = false) String title,
                                                        @RequestParam(required = false) ContentType type,
                                                        @RequestParam(required = false) String genre,
                                                        @RequestParam(required = false) String language,
                                                        @RequestParam(required = false) Integer year,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        CatalogSearchRequest request = new CatalogSearchRequest(title, type, genre, language, year, page, size);
        return catalogService.search(user.accountId(), profile, request);
    }

    /** Opening a series (RF35) returns its seasons and episodes in this same response. */
    @GetMapping("/{contentId}")
    public ContentDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser user,
                                        @CurrentProfile Profile profile,
                                        @PathVariable String contentId) {
        return catalogService.detail(user.accountId(), profile, contentId);
    }
}
