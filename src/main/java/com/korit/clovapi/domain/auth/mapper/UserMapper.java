package com.korit.clovapi.domain.auth.mapper;

import com.korit.clovapi.domain.auth.entity.User;
import com.korit.clovapi.domain.user.dto.UpdateProfileRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

    Optional<User> findByEmail(@Param("email") String email);

    Optional<User> findById(@Param("id") long id);

    Optional<User> findByOauth(
            @Param("oauthProvider") String oauthProvider,
            @Param("oauthSubject") String oauthSubject
    );


    void insert(User user);
    int updateProfile(@Param("userId") long userId, @Param("request") UpdateProfileRequest request);
    int updatePassword(@Param("userId") long userId, @Param("password") String password);
    int anonymize(@Param("userId") long userId);

    /** #159 — 탈퇴 직후에도 이미 발급된 액세스 토큰이 TTL(30분) 동안 그대로 쓰이던 것을
     *  JwtAuthenticationFilter에서 매 요청 확인하기 위한 가벼운 조회. */
    boolean isAnonymized(@Param("id") long id);
}
