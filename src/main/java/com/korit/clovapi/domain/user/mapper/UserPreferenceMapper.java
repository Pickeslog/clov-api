package com.korit.clovapi.domain.user.mapper;

import com.korit.clovapi.domain.user.dto.UpdatePreferencesRequest;
import com.korit.clovapi.domain.user.entity.UserPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserPreferenceMapper {

    Optional<UserPreference> findByUserId(@Param("userId") long userId);

    void insertDefault(@Param("userId") long userId);

    int update(@Param("userId") long userId, @Param("request") UpdatePreferencesRequest request);
}
