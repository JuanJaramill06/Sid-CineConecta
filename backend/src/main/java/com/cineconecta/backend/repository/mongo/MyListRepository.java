package com.cineconecta.backend.repository.mongo;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.cineconecta.backend.model.document.MyListItem;

public interface MyListRepository extends MongoRepository<MyListItem, String> {

    List<MyListItem> findByProfileId(Integer profileId);

    boolean existsByProfileIdAndContentId(Integer profileId, String contentId);

    long deleteByProfileIdAndContentId(Integer profileId, String contentId);
}
