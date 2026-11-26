package com.cineconecta.backend.service;

import com.cineconecta.backend.dto.catalog.ContentDetailResponse;
import com.cineconecta.backend.dto.catalog.ContentRequest;
import com.cineconecta.backend.dto.catalog.ContentSummaryResponse;
import com.cineconecta.backend.dto.common.PageResponse;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.enums.ContentType;

/** Register, update, publish and withdraw content. */
public interface ContentAdminService {

    /**
     * RF24-RF31. Generates the content id (MOV-00001/SER-00001/DOC-00001) and, for a series,
     * each episode's id; rejects a subdocument that does not match type() and repeated
     * season/episode numbers (RF29, RF30). Starts as DRAFT.
     */
    ContentDetailResponse create(ContentRequest request);

    /**
     * Loads the existing document and edits it in place (never builds a new one with the same
     * id: Content.isNew() would treat that as an insert and Mongo would reject the duplicate
     * _id). type() cannot change.
     */
    ContentDetailResponse update(String contentId, ContentRequest request);

    /** Any status, for the admin — unlike CatalogService.detail(), which only shows PUBLISHED. */
    ContentDetailResponse get(String contentId);

    /**
     * The admin's own listing: any status, optionally filtered by status and/or type, one page
     * at a time ordered by id (size capped at 100). Only the summary fields are read.
     */
    PageResponse<ContentSummaryResponse> list(ContentStatus status, ContentType type, int page, int size);

    /** Makes the content visible to clients (RF24). */
    ContentDetailResponse publish(String contentId);

    /** Hides the content from clients; never deletes it (RF60-RF62). */
    ContentDetailResponse withdraw(String contentId);
}
