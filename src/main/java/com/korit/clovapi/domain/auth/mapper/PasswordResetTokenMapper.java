package com.korit.clovapi.domain.auth.mapper;

import com.korit.clovapi.domain.auth.entity.PasswordResetToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface PasswordResetTokenMapper {

    void insert(PasswordResetToken token);

    Optional<PasswordResetToken> findValidByTokenHash(
            @Param("tokenHash") String tokenHash,
            @Param("now") LocalDateTime now
    );

    /** 1회용 소모. 이미 사용·폐기된 행은 건드리지 않으므로 반환값 1을 확인해 동시 사용을 막는다. */
    int markUsedByTokenHash(@Param("tokenHash") String tokenHash);

    /** 재요청 시 이전 미사용 토큰을 전부 폐기 — 살아 있는 링크를 항상 최대 1개로 유지한다. */
    int revokeAllActiveByUserId(@Param("userId") long userId);
}
