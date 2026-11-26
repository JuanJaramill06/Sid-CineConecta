package com.cineconecta.backend.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.cineconecta.backend.dto.profile.ProfileResponse;
import com.cineconecta.backend.model.relational.Profile;

@Mapper(componentModel = "spring")
public interface ProfileMapper {

    @Mapping(target = "active", source = "active")
    ProfileResponse toResponse(Profile profile);

    List<ProfileResponse> toResponses(List<Profile> profiles);
}
