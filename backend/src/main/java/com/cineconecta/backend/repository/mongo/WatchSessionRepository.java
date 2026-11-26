package com.cineconecta.backend.repository.mongo;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cineconecta.backend.model.document.WatchSession;

public interface WatchSessionRepository extends MongoRepository<WatchSession, String> {

    // Ownership check (RNF2): never trust a sessionId alone, it must belong to the profile.
    Optional<WatchSession> findByIdAndProfileId(String id, Integer profileId);
}
