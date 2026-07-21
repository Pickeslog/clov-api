package com.korit.clovapi.domain.memory.controller;

import com.korit.clovapi.domain.memory.dto.CommitImageRequest;
import com.korit.clovapi.domain.memory.dto.CreateMemoryRequest;
import com.korit.clovapi.domain.memory.dto.MemoryDetailResponse;
import com.korit.clovapi.domain.memory.dto.MemoryFeedResponse;
import com.korit.clovapi.domain.memory.dto.MemoryImageResponse;
import com.korit.clovapi.domain.memory.dto.MemoryImagesResponse;
import com.korit.clovapi.domain.memory.dto.ReorderImagesRequest;
import com.korit.clovapi.domain.memory.dto.UpdateMemoryRequest;
import com.korit.clovapi.domain.memory.service.MemoryService;
import com.korit.clovapi.global.dto.PresignRequest;
import com.korit.clovapi.global.dto.PresignResponse;
import com.korit.clovapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping("/api/v1/plans/{planId}/memories")
    public ResponseEntity<ApiResponse<MemoryDetailResponse>> createFromPlan(
            Authentication authentication,
            @PathVariable long planId,
            @Valid @RequestBody CreateMemoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memoryService.createFromPlan(planId, currentUserId(authentication), request)));
    }

    @PostMapping("/api/v1/rooms/{roomId}/memories")
    public ResponseEntity<ApiResponse<MemoryDetailResponse>> createFree(
            Authentication authentication,
            @PathVariable long roomId,
            @Valid @RequestBody CreateMemoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memoryService.createFree(roomId, currentUserId(authentication), request)));
    }

    @GetMapping("/api/v1/rooms/{roomId}/memories")
    public ApiResponse<MemoryFeedResponse> findFeed(
            Authentication authentication,
            @PathVariable long roomId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long writerId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Long participantUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size
    ) {
        return ApiResponse.success(memoryService.findFeed(roomId, currentUserId(authentication), month, writerId,
                tag, participantUserId, page, size));
    }

    @GetMapping("/api/v1/memories/{memoryId}")
    public ApiResponse<MemoryDetailResponse> findDetail(Authentication authentication, @PathVariable long memoryId) {
        return ApiResponse.success(memoryService.getDetail(memoryId, currentUserId(authentication)));
    }

    @PatchMapping("/api/v1/memories/{memoryId}")
    public ApiResponse<MemoryDetailResponse> update(
            Authentication authentication,
            @PathVariable long memoryId,
            @Valid @RequestBody UpdateMemoryRequest request
    ) {
        return ApiResponse.success(memoryService.update(memoryId, currentUserId(authentication), request));
    }

    @DeleteMapping("/api/v1/memories/{memoryId}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable long memoryId) {
        memoryService.delete(memoryId, currentUserId(authentication));
        return ApiResponse.success(null);
    }

    @PostMapping("/api/v1/memories/{memoryId}/images/presign")
    public ApiResponse<PresignResponse> presignImage(Authentication authentication, @PathVariable long memoryId,
                                                     @Valid @RequestBody PresignRequest request) {
        return ApiResponse.success(memoryService.presignImage(memoryId, currentUserId(authentication), request));
    }

    @PostMapping("/api/v1/memories/{memoryId}/images")
    public ApiResponse<MemoryImageResponse> commitImage(Authentication authentication, @PathVariable long memoryId,
                                                        @Valid @RequestBody CommitImageRequest request) {
        return ApiResponse.success(memoryService.commitImage(memoryId, currentUserId(authentication), request));
    }

    @DeleteMapping("/api/v1/memory-images/{imageId}")
    public ApiResponse<Void> deleteImage(Authentication authentication, @PathVariable long imageId) {
        memoryService.deleteImage(imageId, currentUserId(authentication));
        return ApiResponse.success(null);
    }

    @PatchMapping("/api/v1/memories/{memoryId}/images/order")
    public ApiResponse<MemoryImagesResponse> reorderImages(Authentication authentication, @PathVariable long memoryId,
                                                           @Valid @RequestBody ReorderImagesRequest request) {
        return ApiResponse.success(memoryService.reorderImages(memoryId, currentUserId(authentication), request));
    }

    private long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
