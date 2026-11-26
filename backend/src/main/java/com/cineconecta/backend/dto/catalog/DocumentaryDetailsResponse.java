package com.cineconecta.backend.dto.catalog;

import java.util.List;

/** sources is empty when there is nothing to credit (RF31). */
public record DocumentaryDetailsResponse(Integer durationSeconds, List<String> directors,
                                          List<String> topics, List<String> interviewees,
                                          List<String> sources) {
}
