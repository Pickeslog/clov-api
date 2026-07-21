package com.korit.clovapi.domain.invite.dto;

import java.util.List;

/** 내 가입 신청 목록(요청한 방). */
public record MyJoinRequestsResponse(List<MyJoinRequestResponse> items) {
}
