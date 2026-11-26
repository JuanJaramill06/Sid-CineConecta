package com.cineconecta.backend.service.impl;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.cineconecta.backend.dto.mylist.MyListItemResponse;
import com.cineconecta.backend.exception.ConflictException;
import com.cineconecta.backend.exception.ResourceNotFoundException;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.document.MyListItem;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.repository.mongo.ContentRepository;
import com.cineconecta.backend.repository.mongo.MyListRepository;
import com.cineconecta.backend.service.MyListService;

@Service
public class MyListServiceImpl implements MyListService {

    private static final String DUPLICATE = "El contenido ya está en Mi lista";

    private final MyListRepository myList;
    private final ContentRepository contents;

    public MyListServiceImpl(MyListRepository myList, ContentRepository contents) {
        this.myList = myList;
        this.contents = contents;
    }

    @Override
    public List<MyListItemResponse> list(Integer profileId) {
        List<MyListItem> items = myList.findByProfileId(profileId);
        if (items.isEmpty()) {
            return List.of();
        }
        Map<String, Content> byId = contents.findAllById(items.stream().map(MyListItem::getContentId).toList())
                .stream().collect(Collectors.toMap(Content::getId, c -> c));
        return items.stream()
                .sorted(Comparator.comparing(MyListItem::getAddedAt).reversed())
                .map(item -> toResponse(item, byId.get(item.getContentId())))
                .toList();
    }

    @Override
    public MyListItemResponse add(Integer profileId, String contentId) {
        if (myList.existsByProfileIdAndContentId(profileId, contentId)) {
            throw new ConflictException(DUPLICATE);
        }
        Content content = contents.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("El contenido no existe: " + contentId));
        MyListItem item = new MyListItem();
        item.setProfileId(profileId);
        item.setContentId(contentId);
        item.setAddedAt(Instant.now());
        try {
            myList.save(item);
        } catch (DuplicateKeyException e) { // ux_mylist_profile_content: backstop for a race
            throw new ConflictException(DUPLICATE);
        }
        return toResponse(item, content);
    }

    @Override
    public void remove(Integer profileId, String contentId) {
        myList.deleteByProfileIdAndContentId(profileId, contentId);
    }

    // RF39: a withdrawn content stays in the list, marked unavailable, never removed.
    private MyListItemResponse toResponse(MyListItem item, Content content) {
        if (content == null) { // defensive: content is never deleted, only withdrawn
            return new MyListItemResponse(item.getContentId(), null, null, null, item.getAddedAt(), false);
        }
        return new MyListItemResponse(item.getContentId(), content.getTitle(), content.getType(),
                content.getCoverUrl(), item.getAddedAt(), content.getStatus() == ContentStatus.PUBLISHED);
    }
}
