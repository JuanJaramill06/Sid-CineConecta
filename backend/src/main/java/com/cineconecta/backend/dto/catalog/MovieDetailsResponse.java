package com.cineconecta.backend.dto.catalog;

import java.util.List;

public record MovieDetailsResponse(Integer durationSeconds, List<String> directors,
                                    List<CastMemberResponse> cast) {
}
