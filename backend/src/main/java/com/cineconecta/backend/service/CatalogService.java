package com.cineconecta.backend.service;

import com.cineconecta.backend.dto.catalog.CatalogSearchRequest;
import com.cineconecta.backend.dto.catalog.ContentDetailResponse;
import com.cineconecta.backend.dto.common.PageResponse;
import com.cineconecta.backend.dto.catalog.ContentSummaryResponse;
import com.cineconecta.backend.model.relational.Profile;

/** Search, filters, pagination and series detail (published + age rating). */
public interface CatalogService {

    /**
     * RF32-RF34. Requires a current subscription (422) and an active profile (checked by
     * @CurrentProfile before this is called). Only PUBLISHED content with
     * ageRating &lt;= profile.maxAgeRating is returned.
     */
    PageResponse<ContentSummaryResponse> search(Integer accountId, Profile profile, CatalogSearchRequest request);

    /**
     * RF35: opening a series returns its seasons and episodes in one call. Not published, or
     * rated above the profile's limit: ForbiddenOperationException (403) — same answer as a
     * content that does not exist, so it never reveals which one it is.
     */
    ContentDetailResponse detail(Integer accountId, Profile profile, String contentId);
}
