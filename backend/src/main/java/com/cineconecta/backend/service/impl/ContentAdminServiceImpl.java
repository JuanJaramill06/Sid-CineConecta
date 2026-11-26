package com.cineconecta.backend.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.cineconecta.backend.dto.catalog.CastMemberRequest;
import com.cineconecta.backend.dto.catalog.ContentDetailResponse;
import com.cineconecta.backend.dto.catalog.ContentRequest;
import com.cineconecta.backend.dto.catalog.ContentSummaryResponse;
import com.cineconecta.backend.dto.catalog.DocumentaryRequest;
import com.cineconecta.backend.dto.catalog.EpisodeRequest;
import com.cineconecta.backend.dto.catalog.MovieRequest;
import com.cineconecta.backend.dto.catalog.SeasonRequest;
import com.cineconecta.backend.dto.catalog.SeriesRequest;
import com.cineconecta.backend.exception.BusinessRuleException;
import com.cineconecta.backend.exception.ResourceNotFoundException;
import com.cineconecta.backend.mapper.ContentMapper;
import com.cineconecta.backend.model.document.CastMember;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.document.DocumentaryDetails;
import com.cineconecta.backend.model.document.Episode;
import com.cineconecta.backend.model.document.MovieDetails;
import com.cineconecta.backend.model.document.Season;
import com.cineconecta.backend.model.document.SeriesDetails;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.repository.mongo.ContentIdGenerator;
import com.cineconecta.backend.repository.mongo.ContentRepository;
import com.cineconecta.backend.service.ContentAdminService;

@Service
public class ContentAdminServiceImpl implements ContentAdminService {

    private final ContentRepository contents;
    private final ContentIdGenerator idGenerator;
    private final ContentMapper mapper;

    public ContentAdminServiceImpl(ContentRepository contents, ContentIdGenerator idGenerator,
                                   ContentMapper mapper) {
        this.contents = contents;
        this.idGenerator = idGenerator;
        this.mapper = mapper;
    }

    @Override
    public ContentDetailResponse create(ContentRequest request) {
        requireMatchingSubdocument(request);
        if (request.series() != null) {
            requireUniqueSeasonsAndEpisodes(request.series());
        }
        String id = idGenerator.nextContentId(request.type());
        Content content = new Content();
        content.setId(id);
        content.setType(request.type());
        content.setStatus(ContentStatus.DRAFT);
        applyCommonFields(content, request);
        applySubdocuments(content, id, request);
        return mapper.toDetail(contents.save(content));
    }

    @Override
    public ContentDetailResponse update(String contentId, ContentRequest request) {
        Content existing = requireExisting(contentId);
        if (existing.getType() != request.type()) {
            throw new BusinessRuleException("El tipo de un contenido no se puede cambiar");
        }
        requireMatchingSubdocument(request);
        if (request.series() != null) {
            requireUniqueSeasonsAndEpisodes(request.series());
        }
        applyCommonFields(existing, request);
        applySubdocuments(existing, contentId, request);
        return mapper.toDetail(contents.save(existing));
    }

    @Override
    public ContentDetailResponse get(String contentId) {
        return mapper.toDetail(requireExisting(contentId));
    }

    @Override
    public List<ContentSummaryResponse> list() {
        return mapper.toSummaries(contents.findAll());
    }

    @Override
    public ContentDetailResponse publish(String contentId) {
        Content content = requireExisting(contentId);
        content.setStatus(ContentStatus.PUBLISHED);
        return mapper.toDetail(contents.save(content));
    }

    @Override
    public ContentDetailResponse withdraw(String contentId) {
        Content content = requireExisting(contentId);
        content.setStatus(ContentStatus.WITHDRAWN); // never deleteById (RF60-RF62)
        return mapper.toDetail(contents.save(content));
    }

    // ---- Rules -----------------------------------------------------------------------------

    private Content requireExisting(String contentId) {
        return contents.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("El contenido no existe: " + contentId));
    }

    // RF25-RF31: the subdocument of request.type() is present and the other two are not.
    // The $jsonSchema oneOf enforces this too, but failing fast here gives a clearer message.
    private void requireMatchingSubdocument(ContentRequest request) {
        boolean movie = request.movie() != null;
        boolean series = request.series() != null;
        boolean documentary = request.documentary() != null;
        boolean valid = switch (request.type()) {
            case MOVIE -> movie && !series && !documentary;
            case SERIES -> series && !movie && !documentary;
            case DOCUMENTARY -> documentary && !movie && !series;
        };
        if (!valid) {
            throw new BusinessRuleException(
                    "El contenido debe traer únicamente los datos de su tipo (" + request.type() + ")");
        }
    }

    // RF29/RF30: $jsonSchema cannot compare a document with itself, so this is checked here.
    private void requireUniqueSeasonsAndEpisodes(SeriesRequest series) {
        Set<Integer> seasonNumbers = new HashSet<>();
        for (SeasonRequest season : series.seasons()) {
            if (!seasonNumbers.add(season.seasonNumber())) {
                throw new BusinessRuleException("El número de temporada " + season.seasonNumber()
                        + " está repetido");
            }
            Set<Integer> episodeNumbers = new HashSet<>();
            for (EpisodeRequest episode : season.episodes()) {
                if (!episodeNumbers.add(episode.number())) {
                    throw new BusinessRuleException("El episodio " + episode.number()
                            + " está repetido en la temporada " + season.seasonNumber());
                }
            }
        }
    }

    // ---- Request -> Content (manual: needs id generation, not a declarative mapping) --------

    private void applyCommonFields(Content content, ContentRequest request) {
        content.setTitle(request.title());
        content.setSynopsis(request.synopsis());
        content.setReleaseYear(request.releaseYear());
        content.setGenres(request.genres());
        content.setProductionCountries(request.productionCountries());
        content.setAudioLanguages(request.audioLanguages());
        content.setSubtitleLanguages(request.subtitleLanguages() == null ? List.of() : request.subtitleLanguages());
        content.setAgeRating(request.ageRating());
        content.setCoverUrl(request.coverUrl());
    }

    private void applySubdocuments(Content content, String contentId, ContentRequest request) {
        content.setMovie(request.movie() == null ? null : toMovieDetails(request.movie()));
        content.setSeries(request.series() == null ? null : toSeriesDetails(contentId, request.series()));
        content.setDocumentary(request.documentary() == null ? null : toDocumentaryDetails(request.documentary()));
    }

    private MovieDetails toMovieDetails(MovieRequest request) {
        MovieDetails details = new MovieDetails();
        details.setDurationSeconds(request.durationSeconds());
        details.setDirectors(request.directors());
        details.setCast(request.cast() == null ? List.of()
                : request.cast().stream().map(this::toCastMember).toList());
        return details;
    }

    private CastMember toCastMember(CastMemberRequest request) {
        CastMember member = new CastMember();
        member.setActor(request.actor());
        member.setCharacter(request.character());
        return member;
    }

    private SeriesDetails toSeriesDetails(String seriesId, SeriesRequest request) {
        SeriesDetails details = new SeriesDetails();
        details.setSeasons(request.seasons().stream().map(season -> toSeason(seriesId, season)).toList());
        return details;
    }

    private Season toSeason(String seriesId, SeasonRequest request) {
        Season season = new Season();
        season.setSeasonNumber(request.seasonNumber());
        season.setDescription(request.description());
        season.setEpisodes(request.episodes().stream()
                .map(episode -> toEpisode(seriesId, request.seasonNumber(), episode))
                .toList());
        return season;
    }

    private Episode toEpisode(String seriesId, int seasonNumber, EpisodeRequest request) {
        Episode episode = new Episode();
        episode.setId(idGenerator.episodeId(seriesId, seasonNumber, request.number()));
        episode.setNumber(request.number());
        episode.setTitle(request.title());
        episode.setSynopsis(request.synopsis());
        episode.setDurationSeconds(request.durationSeconds());
        return episode;
    }

    private DocumentaryDetails toDocumentaryDetails(DocumentaryRequest request) {
        DocumentaryDetails details = new DocumentaryDetails();
        details.setDurationSeconds(request.durationSeconds());
        details.setDirectors(request.directors());
        details.setTopics(request.topics());
        details.setInterviewees(request.interviewees());
        details.setSources(request.sources() == null ? List.of() : request.sources());
        return details;
    }
}
