package com.cineconecta.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cineconecta.backend.model.document.WatchSession;

public interface WatchSessionRepository extends MongoRepository<WatchSession, String> {
}
