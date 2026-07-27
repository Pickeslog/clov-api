package com.korit.clovapi.domain.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        // 계약 §6: 앞뒤 공백 제거 후 2~20자. 문자 종류는 제한하지 않는다("제주 가자!"·이모지 허용).
        @NotBlank @Size(min = 2, max = 20) String name,
        @Size(max = 60) String description,
        @Size(max = 20) String themeColor,
        @Size(max = 20) String transportType,
        @Size(max = 512) String coverPhotoUrl,
        @Size(max = 100) String coverTitle
) {
    /**
     * 역직렬화 직후 name을 trim한다. 검증(@Size)은 이 생성자 뒤에 돌기 때문에
     * "  a  "처럼 공백으로 부풀린 이름이 길이 검사를 통과하는 것을 막고, 저장되는 값도 trim된 값이 된다.
     */
    public CreateRoomRequest {
        name = name == null ? null : name.trim();
    }
}
