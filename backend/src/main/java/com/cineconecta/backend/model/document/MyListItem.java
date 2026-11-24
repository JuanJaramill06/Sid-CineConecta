package com.cineconecta.backend.model.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One content in a profile's "Mi lista". (profileId, contentId) is unique. */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "MyList")
public class MyListItem {

    @Id
    private String id;

    private Integer profileId; // Oracle PROFILES.profileId, verified by the app before writing
    private String contentId;
    private Instant addedAt;
}
