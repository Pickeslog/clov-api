package com.korit.clovapi.domain.auth.mapper;

import com.korit.clovapi.domain.auth.entity.User;
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
}
