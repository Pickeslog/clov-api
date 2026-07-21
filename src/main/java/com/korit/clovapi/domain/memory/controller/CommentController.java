package com.korit.clovapi.domain.memory.controller;

import com.korit.clovapi.domain.memory.dto.CommentResponse;
import com.korit.clovapi.domain.memory.dto.CommentsResponse;
import com.korit.clovapi.domain.memory.dto.CreateCommentRequest;
import com.korit.clovapi.domain.memory.service.MemoryService;
import com.korit.clovapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    private final MemoryService memoryService;

    public CommentController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping("/api/v1/memories/{memoryId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            Authentication authentication,
            @PathVariable long memoryId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memoryService.createComment(memoryId, currentUserId(authentication), request)));
    }

    @GetMapping("/api/v1/memories/{memoryId}/comments")
    public ApiResponse<CommentsResponse> findAll(Authentication authentication, @PathVariable long memoryId) {
        return ApiResponse.success(memoryService.findComments(memoryId, currentUserId(authentication)));
    }

    @DeleteMapping("/api/v1/comments/{commentId}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable long commentId) {
        memoryService.deleteComment(commentId, currentUserId(authentication));
        return ApiResponse.success(null);
    }

    private long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
