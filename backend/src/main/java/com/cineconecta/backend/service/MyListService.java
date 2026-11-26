package com.cineconecta.backend.service;

import java.util.List;

import com.cineconecta.backend.dto.mylist.MyListItemResponse;

/** Add / remove / list 'Mi lista' (no duplicates per profile). */
public interface MyListService {

    /** RF36, RF39: every item, including withdrawn content marked available = false. */
    List<MyListItemResponse> list(Integer profileId);

    /** RF36, RF37: 409 if the content is already in the list. */
    MyListItemResponse add(Integer profileId, String contentId);

    /** RF36: removes it; a no-op if it was not there. */
    void remove(Integer profileId, String contentId);
}
