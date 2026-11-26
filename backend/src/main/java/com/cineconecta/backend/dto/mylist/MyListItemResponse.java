package com.cineconecta.backend.dto.mylist;

import java.time.Instant;

import com.cineconecta.backend.model.enums.ContentType;

/** available = false when the content was withdrawn from the catalog (RF39), never removed. */
public record MyListItemResponse(String contentId, String title, ContentType type, String coverUrl,
                                  Instant addedAt, boolean available) {
}
