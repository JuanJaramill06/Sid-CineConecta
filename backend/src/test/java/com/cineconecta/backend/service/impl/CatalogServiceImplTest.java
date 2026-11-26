package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.cineconecta.backend.dto.catalog.CatalogSearchRequest;
import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.mapper.ContentMapper;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.repository.mongo.ContentRepository;
import com.cineconecta.backend.service.ReferenceValidationService;

/** RF32-RF35 with the subscription/age-rating checks already covered by ReferenceValidationService. */
class CatalogServiceImplTest {

    private static final int ACCOUNT = 4;

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ContentRepository contents = mock(ContentRepository.class);
    private final ReferenceValidationService references = mock(ReferenceValidationService.class);
    private final CatalogServiceImpl service = new CatalogServiceImpl(mongoTemplate, contents,
            Mappers.getMapper(ContentMapper.class), references);

    private final Profile kidsProfile = mock(Profile.class);

    @BeforeEach
    void profileAllowsUpTo7() {
        when(kidsProfile.getMaxAgeRating()).thenReturn(7);
    }

    private static Content content(String id, int ageRating, ContentStatus status) {
        Content content = new Content();
        content.setId(id);
        content.setType(ContentType.MOVIE);
        content.setStatus(status);
        content.setAgeRating(ageRating);
        content.setTitle("Title " + id);
        return content;
    }

    @Test
    void searchRequiresACurrentSubscription() {
        when(references.requireActiveSubscription(ACCOUNT)).thenThrow(new BusinessRuleException("no subscription"));

        assertThatThrownBy(() -> service.search(ACCOUNT, kidsProfile, new CatalogSearchRequest(
                null, null, null, null, null, 0, 20)))
                .isInstanceOf(BusinessRuleException.class);
        verify(mongoTemplate, never()).find(any(), eq(Content.class));
    }

    @Test
    void searchWrapsTheMongoResultsInAPageResponse() {
        when(mongoTemplate.count(any(Query.class), eq(Content.class))).thenReturn(5L);
        when(mongoTemplate.find(any(Query.class), eq(Content.class)))
                .thenReturn(List.of(content("MOV-00001", 7, ContentStatus.PUBLISHED)));

        var page = service.search(ACCOUNT, kidsProfile,
                new CatalogSearchRequest("frontera", ContentType.MOVIE, "Drama", "es", 2023, 1, 10));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo("MOV-00001");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalItems()).isEqualTo(5);
        verify(references).requireActiveSubscription(ACCOUNT);
    }

    @Test
    void detailOfAMissingContentIsForbiddenNotNotFound() {
        when(contents.findByIdAndStatus("MOV-99999", ContentStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(ACCOUNT, kidsProfile, "MOV-99999"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void detailAboveTheProfilesRatingIsForbidden() {
        Content adult = content("MOV-00002", 18, ContentStatus.PUBLISHED);
        when(contents.findByIdAndStatus("MOV-00002", ContentStatus.PUBLISHED)).thenReturn(Optional.of(adult));

        assertThatThrownBy(() -> service.detail(ACCOUNT, kidsProfile, "MOV-00002"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void detailWithinTheProfilesRatingIsReturned() {
        Content kidsMovie = content("MOV-00003", 7, ContentStatus.PUBLISHED);
        when(contents.findByIdAndStatus("MOV-00003", ContentStatus.PUBLISHED)).thenReturn(Optional.of(kidsMovie));

        var detail = service.detail(ACCOUNT, kidsProfile, "MOV-00003");

        assertThat(detail.id()).isEqualTo("MOV-00003");
        assertThat(detail.title()).isEqualTo("Title MOV-00003");
    }
}
