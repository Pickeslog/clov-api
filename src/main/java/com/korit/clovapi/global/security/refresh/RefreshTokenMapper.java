package com.korit.clovapi.global.security.refresh;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface RefreshTokenMapper {

    void insert(RefreshToken refreshToken);

    Optional<RefreshToken> findValidByTokenHash(
            @Param("tokenHash") String tokenHash,
            @Param("now") LocalDateTime now
    );

    int revokeByTokenHash(@Param("tokenHash") String tokenHash);
}
