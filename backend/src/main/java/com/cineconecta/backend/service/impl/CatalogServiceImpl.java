package com.cineconecta.backend.service.impl;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.cineconecta.backend.dto.catalog.CatalogSearchRequest;
import com.cineconecta.backend.dto.catalog.ContentDetailResponse;
import com.cineconecta.backend.dto.catalog.ContentSummaryResponse;
import com.cineconecta.backend.dto.common.PageResponse;
import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.mapper.ContentMapper;
import com.cineconecta.backend.model.document.Content;
import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.repository.mongo.ContentRepository;
import com.cineconecta.backend.service.CatalogService;
import com.cineconecta.backend.service.ReferenceValidationService;

/**
 * Search/filter/pagination (RF32-34) and series detail (RF35). Only PUBLISHED content with
 * ageRating &lt;= the profile's maxAgeRating is ever returned to a client; "not found" and
 * "not allowed for this profile" answer the same way, so neither reveals the other.
 */
@Service
public class CatalogServiceImpl implements CatalogService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String NOT_AVAILABLE = "El contenido no existe o no está disponible";

    private final MongoTemplate mongoTemplate;
    private final ContentRepository contents;
    private final ContentMapper mapper;
    private final ReferenceValidationService references;

    public CatalogServiceImpl(MongoTemplate mongoTemplate, ContentRepository contents, ContentMapper mapper,
                              ReferenceValidationService references) {
        this.mongoTemplate = mongoTemplate;
        this.contents = contents;
        this.mapper = mapper;
        this.references = references;
    }

    @Override
    public PageResponse<ContentSummaryResponse> search(Integer accountId, Profile profile,
                                                        CatalogSearchRequest request) {
        references.requireActiveSubscription(accountId);

        int page = Math.max(request.page(), 0);
        int size = request.size() > 0 ? request.size() : DEFAULT_PAGE_SIZE;

        long total = mongoTemplate.count(buildQuery(profile, request), Content.class);
        Query query = buildQuery(profile, request).with(PageRequest.of(page, size));
        List<Content> results = mongoTemplate.find(query, Content.class);

        return new PageResponse<>(mapper.toSummaries(results), page, size, total);
    }

    @Override
    public ContentDetailResponse detail(Integer accountId, Profile profile, String contentId) {
        references.requireActiveSubscription(accountId);

        Content content = contents.findByIdAndStatus(contentId, ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ForbiddenOperationException(NOT_AVAILABLE));
        if (content.getAgeRating() > profile.getMaxAgeRating()) {
            throw new ForbiddenOperationException(NOT_AVAILABLE);
        }
        return mapper.toDetail(content);
    }

    // ---- Query building ---------------------------------------------------------------------

    // Only PUBLISHED and within the profile's age rating (RF34); the rest of the filters are
    // optional. Rebuilt for both the count and the data fetch, so neither mutates the other.
    private Criteria baseCriteria(Profile profile, CatalogSearchRequest request) {
        Criteria criteria = Criteria.where("status").is(ContentStatus.PUBLISHED)
                .and("ageRating").lte(profile.getMaxAgeRating());
        if (request.type() != null) {
            criteria = criteria.and("type").is(request.type());
        }
        if (StringUtils.hasText(request.genre())) {
            criteria = criteria.and("genres").is(request.genre());
        }
        if (StringUtils.hasText(request.language())) {
            criteria = criteria.and("audioLanguages").is(request.language());
        }
        if (request.year() != null) {
            criteria = criteria.and("releaseYear").is(request.year());
        }
        return criteria;
    }

    // Title present: $text search (ix_content_title_text), sorted by relevance. Otherwise,
    // newest first (ix_content_status_type_year).
    private Query buildQuery(Profile profile, CatalogSearchRequest request) {
        Criteria criteria = baseCriteria(profile, request);
        if (StringUtils.hasText(request.title())) {
            TextQuery query = TextQuery.queryText(TextCriteria.forDefaultLanguage().matching(request.title()));
            query.addCriteria(criteria);
            query.sortByScore();
            return query;
        }
        return Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "releaseYear"));
    }
}
