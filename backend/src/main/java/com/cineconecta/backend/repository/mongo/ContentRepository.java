package com.cineconecta.backend.repository.mongo;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.enums.ContentStatus;

public interface ContentRepository extends MongoRepository<Content, String> {

    // The admin list only needs the common fields: leaving out movie/series/documentary avoids
    // reading every season and episode of every series (5,000 contents in the volume load).
    String SUMMARY_FIELDS = "{ 'movie': 0, 'series': 0, 'documentary': 0 }";

    // Client-facing detail (RF34): only a published content is ever shown this way.
    Optional<Content> findByIdAndStatus(String id, ContentStatus status);

    // Admin list (any status), one page at a time. status/type are the enum names.
    @Query(value = "{}", fields = SUMMARY_FIELDS)
    Page<Content> findSummaries(Pageable pageable);

    @Query(value = "{ 'status': ?0 }", fields = SUMMARY_FIELDS)
    Page<Content> findSummariesByStatus(String status, Pageable pageable);

    @Query(value = "{ 'type': ?0 }", fields = SUMMARY_FIELDS)
    Page<Content> findSummariesByType(String type, Pageable pageable);

    @Query(value = "{ 'status': ?0, 'type': ?1 }", fields = SUMMARY_FIELDS)
    Page<Content> findSummariesByStatusAndType(String status, String type, Pageable pageable);
}
