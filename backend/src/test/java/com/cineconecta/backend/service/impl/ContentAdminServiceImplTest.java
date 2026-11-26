package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.cineconecta.backend.model.document.Episode;
import com.cineconecta.backend.model.document.Season;
import com.cineconecta.backend.model.document.SeriesDetails;
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

    // ---- Admin list: paginated summaries ---------------------------------------------------------

    @Test
    void listUsesTheFilteredSummaryQueryAndCapsThePageSize() {
        Content draft = new Content();
        draft.setId("MOV-00007");
        draft.setType(ContentType.MOVIE);
        draft.setStatus(ContentStatus.DRAFT);
        when(contents.findSummariesByStatusAndType(org.mockito.ArgumentMatchers.eq("DRAFT"),
                org.mockito.ArgumentMatchers.eq("MOVIE"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(draft),
                        org.springframework.data.domain.PageRequest.of(0, 100), 1));

        var page = service.list(ContentStatus.DRAFT, ContentType.MOVIE, 0, 5_000);

        assertThat(page.size()).isEqualTo(100);
        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items()).extracting(s -> s.id()).containsExactly("MOV-00007");
        verify(contents, never()).findAll();
    }

    // ---- Episode ids are never reused (RF29, RF63) ------------------------------------------

    private static Episode storedEpisode(String id, int number) {
        Episode episode = new Episode();
        episode.setId(id);
        episode.setNumber(number);
        episode.setTitle("Ep" + number);
        episode.setSynopsis("...");
        episode.setDurationSeconds(1800);
        return episode;
    }

    /** SER-00002 with season 1 = episodes 1 and 2, already stored with the given status. */
    private Content storedSeries(ContentStatus status) {
        Season season = new Season();
        season.setSeasonNumber(1);
        season.setDescription("T1");
        season.setEpisodes(List.of(storedEpisode("SER-00002-S01E01", 1), storedEpisode("SER-00002-S01E02", 2)));
        SeriesDetails details = new SeriesDetails();
        details.setSeasons(List.of(season));
        Content existing = new Content();
        existing.setId("SER-00002");
        existing.setType(ContentType.SERIES);
        existing.setStatus(status);
        existing.setSeries(details);
        when(contents.findById("SER-00002")).thenReturn(Optional.of(existing));
        when(idGenerator.episodeId(anyString(), anyInt(), anyInt())).thenAnswer(inv ->
                "%s-S%02dE%02d".formatted(inv.getArgument(0), (int) inv.getArgument(1), (int) inv.getArgument(2)));
        stubSave(contents);
        return existing;
    }

    private static ContentRequest seriesRequest(List<EpisodeRequest> seasonOneEpisodes) {
        return new ContentRequest(ContentType.SERIES, "Serie", "Sinopsis", 2022, List.of("Drama"),
                List.of("Colombia"), List.of("es"), List.of(), 16, "https://img/cover.jpg",
                null, new SeriesRequest(List.of(new SeasonRequest(1, "T1", seasonOneEpisodes))), null);
    }

    private static EpisodeRequest episode(int number) {
        return new EpisodeRequest(number, "Ep" + number, "...", 1800);
    }

    @Test
    void publishedSeriesCannotDropAnEpisode() {
        storedSeries(ContentStatus.PUBLISHED);

        // Episode 2 removed and a different "episode 2" would later reuse SER-00002-S01E02
        assertThatThrownBy(() -> service.update("SER-00002", seriesRequest(List.of(episode(1)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SER-00002-S01E02");
        verify(contents, never()).save(any());
    }

    @Test
    void withdrawnSeriesKeepsTheSameRule() {
        storedSeries(ContentStatus.WITHDRAWN);

        assertThatThrownBy(() -> service.update("SER-00002", seriesRequest(List.of(episode(2)))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void publishedSeriesCanAddEpisodesAndKeepsTheExistingIds() {
        storedSeries(ContentStatus.PUBLISHED);

        var response = service.update("SER-00002", seriesRequest(List.of(episode(1), episode(2), episode(3))));

        assertThat(response.series().seasons().get(0).episodes())
                .extracting(e -> e.id())
                .containsExactly("SER-00002-S01E01", "SER-00002-S01E02", "SER-00002-S01E03");
    }

    @Test
    void draftSeriesCanStillBeRestructured() {
        storedSeries(ContentStatus.DRAFT);

        var response = service.update("SER-00002", seriesRequest(List.of(episode(1))));

        assertThat(response.series().seasons().get(0).episodes()).hasSize(1);
    }
}
