package com.cineconecta.backend.model.document;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;

import com.cineconecta.backend.model.enums.ContentStatus;
import com.cineconecta.backend.model.enums.ContentType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Catalog item. Exactly one of movie/series/documentary is set, matching {@link #type}. */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "Content")
public class Content implements Persistable<String> {

    @Id
    private String id; // stable code: MOV-00001, SER-00001, DOC-00001 (never reused)

    private ContentType type;
    private ContentStatus status;
    private String title;
    private String synopsis;
    private Integer releaseYear;
    private List<String> genres;
    private List<String> productionCountries;
    private List<String> audioLanguages;
    private List<String> subtitleLanguages;
    private Integer ageRating;
    private String coverUrl;

    private MovieDetails movie;
    private SeriesDetails series;
    private DocumentaryDetails documentary;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // Ids are assigned, so Spring cannot detect new documents by a null id and would
    // never fill @CreatedDate. Documents loaded by the scripts already have createdAt.
    @Override
    public boolean isNew() {
        return createdAt == null;
    }
}
