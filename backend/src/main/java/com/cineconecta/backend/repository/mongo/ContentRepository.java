package com.cineconecta.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cineconecta.backend.model.document.Content;

public interface ContentRepository extends MongoRepository<Content, String> {
}
