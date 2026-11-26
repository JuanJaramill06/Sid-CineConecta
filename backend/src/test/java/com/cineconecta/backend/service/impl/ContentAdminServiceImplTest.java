package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.cineconecta.backend.dto.catalog.ContentRequest;
import com.cineconecta.backend.dto.catalog.DocumentaryRequest;
import com.cineconecta.backend.dto.catalog.EpisodeRequest;
import com.cineconecta.backend.dto.catalog.MovieRequest;
import com.cineconecta.backend.dto.catalog.SeasonRequest;
import com.cineconecta.backend.dto.catalog.SeriesRequest;
import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.ResourceNotFoundException;
import com.cineconecta.backend.mapper.ContentMapper;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.enums.ContentType;
import com.cineconecta.backend.repository.mongo.ContentIdGenerator;
import com.cineconecta.backend.repository.mongo.ContentRepository;

/** RF24-RF31, RF60-RF63 with mocks of the Mongo repository and id generator. */
class ContentAdminServiceImplTest {

    private final ContentRepository contents = mock(ContentRepository.class);
    private final ContentIdGenerator idGenerator = mock(ContentIdGenerator.class);
    private final ContentAdminServiceImpl service = new ContentAdminServiceImpl(contents, idGenerator,
            Mappers.getMapper(ContentMapper.class));

    private static MovieRequest movie() {
        return new MovieRequest(5400, List.of("Directora"), List.of());
    }

    private static ContentRequest movieRequest(MovieRequest movie, SeriesRequest series,
                                               DocumentaryRequest documentary) {
        return new ContentRequest(ContentType.MOVIE, "Título", "Sinopsis", 2023, List.of("Drama"),
                List.of("Colombia"), List.of("es"), List.of(), 16, "https://img/cover.jpg",
                movie, series, documentary);
    }

    // save() returns whatever it was given, like a real repository
    private static void stubSave(ContentRepository contents) {
        when(contents.save(any(Content.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createGeneratesTheIdFromTheCounterAndStartsAsDraft() {
        stubSave(contents);
        when(idGenerator.nextContentId(ContentType.MOVIE)).thenReturn("MOV-00123");

        var response = service.create(movieRequest(movie(), null, null));

        assertThat(response.id()).isEqualTo("MOV-00123");
        assertThat(response.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(response.movie().durationSeconds()).isEqualTo(5400);
    }

    @Test
    void createRejectsASubdocumentThatDoesNotMatchType() {
        SeriesRequest series = new SeriesRequest(List.of(
                new SeasonRequest(1, "T1", List.of(new EpisodeRequest(1, "Ep1", "...", 1200)))));

        assertThatThrownBy(() -> service.create(movieRequest(movie(), series, null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(idGenerator, never()).nextContentId(any());
        verify(contents, never()).save(any());
    }

    @Test
    void createRejectsARepeatedSeasonNumber() {
        SeriesRequest series = new SeriesRequest(List.of(
                new SeasonRequest(1, "T1", List.of(new EpisodeRequest(1, "Ep1", "...", 1200))),
                new SeasonRequest(1, "T1 otra vez", List.of(new EpisodeRequest(1, "Ep1", "...", 1200)))));
        ContentRequest request = new ContentRequest(ContentType.SERIES, "Serie", "Sinopsis", 2023,
                List.of("Drama"), List.of("Colombia"), List.of("es"), List.of(), 16, "https://img/cover.jpg",
                null, series, null);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createRejectsARepeatedEpisodeNumberWithinASeason() {
        SeriesRequest series = new SeriesRequest(List.of(new SeasonRequest(1, "T1", List.of(
                new EpisodeRequest(1, "Ep1", "...", 1200),
                new EpisodeRequest(1, "Ep1 otra vez", "...", 1200)))));
        ContentRequest request = new ContentRequest(ContentType.SERIES, "Serie", "Sinopsis", 2023,
                List.of("Drama"), List.of("Colombia"), List.of("es"), List.of(), 16, "https://img/cover.jpg",
                null, series, null);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createDerivesEpisodeIdsFromTheSeries() {
        stubSave(contents);
        when(idGenerator.nextContentId(ContentType.SERIES)).thenReturn("SER-00050");
        when(idGenerator.episodeId("SER-00050", 1, 1)).thenReturn("SER-00050-S01E01");
        SeriesRequest series = new SeriesRequest(List.of(
                new SeasonRequest(1, "T1", List.of(new EpisodeRequest(1, "Piloto", "...", 1200)))));
        ContentRequest request = new ContentRequest(ContentType.SERIES, "Serie", "Sinopsis", 2023,
                List.of("Drama"), List.of("Colombia"), List.of("es"), List.of(), 16, "https://img/cover.jpg",
                null, series, null);

        var response = service.create(request);

        assertThat(response.series().seasons().get(0).episodes().get(0).id()).isEqualTo("SER-00050-S01E01");
    }

    @Test
    void updateKeepsTheExistingIdAndNeverCallsTheGenerator() {
        Content existing = new Content();
        existing.setId("MOV-00010");
        existing.setType(ContentType.MOVIE);
        existing.setStatus(ContentStatus.DRAFT);
        when(contents.findById("MOV-00010")).thenReturn(Optional.of(existing));
        stubSave(contents);

        var response = service.update("MOV-00010", movieRequest(movie(), null, null));

        assertThat(response.id()).isEqualTo("MOV-00010");
        verify(idGenerator, never()).nextContentId(any());
    }

    @Test
    void updateRejectsChangingTheType() {
        Content existing = new Content();
        existing.setId("MOV-00010");
        existing.setType(ContentType.MOVIE);
        when(contents.findById("MOV-00010")).thenReturn(Optional.of(existing));
        SeriesRequest series = new SeriesRequest(List.of(
                new SeasonRequest(1, "T1", List.of(new EpisodeRequest(1, "Ep1", "...", 1200)))));
        ContentRequest asSeries = new ContentRequest(ContentType.SERIES, "Serie", "Sinopsis", 2023,
                List.of("Drama"), List.of("Colombia"), List.of("es"), List.of(), 16, "https://img/cover.jpg",
                null, series, null);

        assertThatThrownBy(() -> service.update("MOV-00010", asSeries)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void getOfAMissingContentIsNotFound() {
        when(contents.findById("MOV-99999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("MOV-99999")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void withdrawFlipsStatusAndNeverDeletes() {
        Content existing = new Content();
        existing.setId("MOV-00010");
        existing.setType(ContentType.MOVIE);
        existing.setStatus(ContentStatus.PUBLISHED);
        when(contents.findById("MOV-00010")).thenReturn(Optional.of(existing));
        stubSave(contents);

        var response = service.withdraw("MOV-00010");

        assertThat(response.status()).isEqualTo(ContentStatus.WITHDRAWN);
        verify(contents, never()).deleteById(anyString());
        verify(contents, never()).delete(any());
    }
}
