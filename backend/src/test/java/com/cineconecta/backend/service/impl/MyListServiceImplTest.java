package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cineconecta.backend.dto.mylist.MyListItemResponse;
import com.cineconecta.backend.exception.ConflictException;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.document.MyListItem;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.repository.mongo.ContentRepository;
import com.cineconecta.backend.repository.mongo.MyListRepository;

/** RF36-RF39 with mocks of the Mongo repositories. */
class MyListServiceImplTest {

    private static final int PROFILE = 2;

    private final MyListRepository myList = mock(MyListRepository.class);
    private final ContentRepository contents = mock(ContentRepository.class);
    private final MyListServiceImpl service = new MyListServiceImpl(myList, contents);

    private final Profile profile = mock(Profile.class); // seed profile 2 "Ana": max rating 12

    @BeforeEach
    void anaRatedTwelve() {
        when(profile.getProfileId()).thenReturn(PROFILE);
        when(profile.getMaxAgeRating()).thenReturn(12);
    }

    private static Content content(String id, ContentStatus status) {
        return content(id, status, 12);
    }

    private static Content content(String id, ContentStatus status, int ageRating) {
        Content content = new Content();
        content.setId(id);
        content.setType(ContentType.MOVIE);
        content.setStatus(status);
        content.setTitle("Title " + id);
        content.setAgeRating(ageRating);
        return content;
    }

    private static MyListItem item(Integer profileId, String contentId, Instant addedAt) {
        MyListItem item = new MyListItem();
        item.setProfileId(profileId);
        item.setContentId(contentId);
        item.setAddedAt(addedAt);
        return item;
    }

    @Test
    void addingAnExistingContentIsAConflict() {
        when(myList.existsByProfileIdAndContentId(PROFILE, "MOV-00001")).thenReturn(true);

        assertThatThrownBy(() -> service.add(profile, "MOV-00001")).isInstanceOf(ConflictException.class);
        verify(myList, never()).save(any());
    }

    @Test
    void missingDraftOrWithdrawnContentCannotBeAdded() {
        // findByIdAndStatus(..., PUBLISHED) is empty for all three: MOV-00007 is a DRAFT in the seed
        when(contents.findByIdAndStatus("MOV-00007", ContentStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(profile, "MOV-00007")).isInstanceOf(ForbiddenOperationException.class);
        verify(myList, never()).save(any());
    }

    @Test
    void contentAboveTheProfileRatingCannotBeAdded() {
        when(contents.findByIdAndStatus("MOV-00002", ContentStatus.PUBLISHED))
                .thenReturn(Optional.of(content("MOV-00002", ContentStatus.PUBLISHED, 18)));

        assertThatThrownBy(() -> service.add(profile, "MOV-00002")).isInstanceOf(ForbiddenOperationException.class);
        verify(myList, never()).save(any());
    }

    @Test
    void addReturnsTheNewItem() {
        when(myList.existsByProfileIdAndContentId(PROFILE, "MOV-00001")).thenReturn(false);
        when(contents.findByIdAndStatus("MOV-00001", ContentStatus.PUBLISHED))
                .thenReturn(Optional.of(content("MOV-00001", ContentStatus.PUBLISHED)));

        MyListItemResponse response = service.add(profile, "MOV-00001");

        assertThat(response.contentId()).isEqualTo("MOV-00001");
        assertThat(response.available()).isTrue();
        verify(myList).save(any());
    }

    @Test
    void withdrawnContentStaysInTheListMarkedUnavailable() {
        Instant now = Instant.now();
        when(myList.findByProfileId(PROFILE)).thenReturn(List.of(item(PROFILE, "MOV-00006", now)));
        when(contents.findAllById(List.of("MOV-00006")))
                .thenReturn(List.of(content("MOV-00006", ContentStatus.WITHDRAWN)));

        List<MyListItemResponse> list = service.list(PROFILE);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).available()).isFalse();
    }

    @Test
    void listIsOrderedByMostRecentlyAdded() {
        Instant older = Instant.now().minusSeconds(60);
        Instant newer = Instant.now();
        when(myList.findByProfileId(PROFILE)).thenReturn(List.of(
                item(PROFILE, "MOV-00001", older), item(PROFILE, "MOV-00002", newer)));
        when(contents.findAllById(List.of("MOV-00001", "MOV-00002"))).thenReturn(List.of(
                content("MOV-00001", ContentStatus.PUBLISHED), content("MOV-00002", ContentStatus.PUBLISHED)));

        List<MyListItemResponse> list = service.list(PROFILE);

        assertThat(list).extracting(MyListItemResponse::contentId).containsExactly("MOV-00002", "MOV-00001");
    }

    @Test
    void removeDelegatesToTheRepository() {
        service.remove(PROFILE, "MOV-00001");

        verify(myList).deleteByProfileIdAndContentId(PROFILE, "MOV-00001");
    }
}
