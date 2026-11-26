package com.cineconecta.backend.service;

import java.util.List;

import com.cineconecta.backend.dto.playback.ContinueWatchingResponse;

/** 'Continuar viendo' ordered by recent activity. */
public interface ContinueWatchingService {

    /**
     * RF57-61: the last session per content, newest activity first. A completed series moves to
     * its next episode from position 0; a withdrawn content, or a fully-watched movie/documentary
     * with nothing left after it, is excluded.
     */
    List<ContinueWatchingResponse> list(Integer profileId);
}
