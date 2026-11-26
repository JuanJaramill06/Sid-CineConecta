package com.cineconecta.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.catalog.ContentDetailResponse;
import com.cineconecta.backend.dto.catalog.ContentRequest;
import com.cineconecta.backend.dto.catalog.ContentSummaryResponse;
import com.cineconecta.backend.service.ContentAdminService;

import jakarta.validation.Valid;

/** Content administration (RF24-RF31, RF60-RF63). Already restricted to ROLE_ADMIN by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {

    private final ContentAdminService contentAdminService;

    public AdminContentController(ContentAdminService contentAdminService) {
        this.contentAdminService = contentAdminService;
    }

    @GetMapping
    public List<ContentSummaryResponse> list() {
        return contentAdminService.list();
    }

    @GetMapping("/{contentId}")
    public ContentDetailResponse get(@PathVariable String contentId) {
        return contentAdminService.get(contentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContentDetailResponse create(@Valid @RequestBody ContentRequest request) {
        return contentAdminService.create(request);
    }

    @PutMapping("/{contentId}")
    public ContentDetailResponse update(@PathVariable String contentId,
                                        @Valid @RequestBody ContentRequest request) {
        return contentAdminService.update(contentId, request);
    }

    @PostMapping("/{contentId}/publish")
    public ContentDetailResponse publish(@PathVariable String contentId) {
        return contentAdminService.publish(contentId);
    }

    @PostMapping("/{contentId}/withdraw")
    public ContentDetailResponse withdraw(@PathVariable String contentId) {
        return contentAdminService.withdraw(contentId);
    }
}
