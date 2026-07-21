package com.korit.clovapi.domain.letter.controller;

import com.korit.clovapi.domain.letter.dto.LetterFavoriteResponse;
import com.korit.clovapi.domain.letter.dto.LetterListResponse;
import com.korit.clovapi.domain.letter.dto.LetterReadResponse;
import com.korit.clovapi.domain.letter.dto.LetterSendRequest;
import com.korit.clovapi.domain.letter.service.LetterService;
import com.korit.clovapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LetterController {

    private final LetterService letterService;

    public LetterController(LetterService letterService) {
        this.letterService = letterService;
    }

    @PostMapping("/rooms/{roomId}/letters")
    public ResponseEntity<ApiResponse<Object>> send(
            @PathVariable long roomId,
            @Valid @RequestBody LetterSendRequest request,
            Authentication authentication
    ) {
        Object result = letterService.send(roomId, currentUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @GetMapping("/rooms/{roomId}/letters")
    public ApiResponse<LetterListResponse> getBox(
            @PathVariable long roomId,
            @RequestParam String box,
            Authentication authentication
    ) {
        return ApiResponse.success(letterService.getBox(roomId, currentUserId(authentication), box));
    }

    @PatchMapping("/letters/{letterId}/read")
    public ApiResponse<LetterReadResponse> markRead(
            @PathVariable long letterId,
            Authentication authentication
    ) {
        return ApiResponse.success(letterService.markRead(letterId, currentUserId(authentication)));
    }

    @PatchMapping("/letters/{letterId}/favorite")
    public ApiResponse<LetterFavoriteResponse> toggleFavorite(
            @PathVariable long letterId,
            Authentication authentication
    ) {
        return ApiResponse.success(letterService.toggleFavorite(letterId, currentUserId(authentication)));
    }

    private long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
