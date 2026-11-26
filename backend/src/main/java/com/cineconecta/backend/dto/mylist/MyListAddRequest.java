package com.cineconecta.backend.dto.mylist;

import jakarta.validation.constraints.NotBlank;

public record MyListAddRequest(@NotBlank String contentId) {
}
