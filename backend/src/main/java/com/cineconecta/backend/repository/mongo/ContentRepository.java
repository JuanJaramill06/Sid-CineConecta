package com.cineconecta.backend.repository.mongo;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.enums.ContentStatus;

public interface ContentRepository extends MongoRepository<Content, String> {

    // Client-facing detail (RF34): only a published content is ever shown this way.
    Optional<Content> findByIdAndStatus(String id, ContentStatus status);
}
