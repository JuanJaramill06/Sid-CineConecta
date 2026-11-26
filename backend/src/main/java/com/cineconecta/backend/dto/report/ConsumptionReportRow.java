package com.cineconecta.backend.dto.report;

import com.cineconecta.backend.model.enums.ContentType;

/** One row of the consumption-by-plan-and-type report (RF67-69): minutes watched and distinct profiles. */
public record ConsumptionReportRow(Integer planId, String planName, ContentType contentType,
                                    double minutesWatched, long distinctProfiles) {
}
