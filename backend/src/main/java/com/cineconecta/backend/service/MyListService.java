package com.cineconecta.backend.service;

import java.util.List;

import com.cineconecta.backend.dto.mylist.MyListItemResponse;
import com.cineconecta.backend.model.relational.Profile;

/** Add / remove / list 'Mi lista' (no duplicates per profile). */
public interface MyListService {

    /** RF36, RF39: every item, including withdrawn content marked available = false. */
    List<MyListItemResponse> list(Integer profileId);

    /**
     * RF36, RF37: 409 if the content is already in the list. Only PUBLISHED content allowed
     * for the profile's maxAgeRating can be added; anything else answers 403, like the catalog.
     */
    MyListItemResponse add(Profile profile, String contentId);

    /** RF36: removes it; a no-op if it was not there. */
    void remove(Integer profileId, String contentId);
}
