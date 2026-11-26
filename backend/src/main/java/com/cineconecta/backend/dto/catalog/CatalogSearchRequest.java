package com.cineconecta.backend.dto.catalog;

import com.cineconecta.backend.model.enums.ContentType;

/**
 * Filters for GET /api/catalog (RF32-RF34). Every field but page/size is optional:
 * title does a $text search (ix_content_title_text), the rest are equality filters.
 */
public record CatalogSearchRequest(String title, ContentType type, String genre, String language,
                                    Integer year, int page, int size) {
}
