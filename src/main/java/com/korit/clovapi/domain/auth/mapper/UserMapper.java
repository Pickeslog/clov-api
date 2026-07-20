package com.korit.clovapi.domain.auth.mapper;

import com.korit.clovapi.domain.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

    Optional<User> findByEmail(@Param("email") String email);

    boolean existsByPersonalInviteCode(@Param("personalInviteCode") String personalInviteCode);

    void insert(User user);
}
