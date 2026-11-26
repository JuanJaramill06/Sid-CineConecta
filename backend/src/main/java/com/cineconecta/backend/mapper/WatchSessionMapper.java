package com.cineconecta.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.cineconecta.backend.dto.playback.PlaybackStateResponse;
import com.cineconecta.backend.model.document.WatchSession;

@Mapper(componentModel = "spring")
public interface WatchSessionMapper {

    @Mapping(target = "sessionId", source = "id")
    PlaybackStateResponse toState(WatchSession session);
}
