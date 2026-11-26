package com.cineconecta.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cineconecta.backend.dto.mylist.MyListAddRequest;
import com.cineconecta.backend.dto.mylist.MyListItemResponse;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.security.CurrentProfile;
import com.cineconecta.backend.service.MyListService;

import jakarta.validation.Valid;

/** 'Mi lista' (RF36-RF39): scoped to the profile in X-Profile-Id, no subscription required. */
@RestController
@RequestMapping("/api/my-list")
public class MyListController {

    private final MyListService myListService;

    public MyListController(MyListService myListService) {
        this.myListService = myListService;
    }

    @GetMapping
    public List<MyListItemResponse> list(@CurrentProfile Profile profile) {
        return myListService.list(profile.getProfileId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MyListItemResponse add(@CurrentProfile Profile profile, @Valid @RequestBody MyListAddRequest request) {
        return myListService.add(profile, request.contentId());
    }

    @DeleteMapping("/{contentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentProfile Profile profile, @PathVariable String contentId) {
        myListService.remove(profile.getProfileId(), contentId);
    }
}
