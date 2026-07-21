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

    boolean existsByPersonalInviteCode(@Param("personalInviteCode") String personalInviteCode);

    void insert(User user);
    int updateProfile(@Param("userId") long userId, @Param("request") UpdateProfileRequest request);
    int updatePassword(@Param("userId") long userId, @Param("password") String password);
    int anonymize(@Param("userId") long userId);
}
