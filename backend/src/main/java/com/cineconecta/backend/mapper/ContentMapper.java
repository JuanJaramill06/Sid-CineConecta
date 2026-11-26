package com.cineconecta.backend.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.cineconecta.backend.dto.catalog.ContentDetailResponse;
import com.cineconecta.backend.dto.catalog.ContentSummaryResponse;
import com.cineconecta.backend.model.document.Content;

/**
 * Content -> response only. Request -> Content needs logic (id generation, episode ids,
 * clearing the subdocuments that don't match type()), so it is written by hand in
 * ContentAdminServiceImpl instead of a declarative mapping.
 */
@Mapper(componentModel = "spring")
public interface ContentMapper {

    ContentSummaryResponse toSummary(Content content);

    List<ContentSummaryResponse> toSummaries(List<Content> contents);

    ContentDetailResponse toDetail(Content content);
}
