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

    /** itemId=null이면 해제. 값 유무로 분기해야 해서 범용 update()의 부분갱신 패턴과 별도로 둔다. */
    void updateEquippedItem(@Param("userId") long userId, @Param("itemId") Long itemId);
}
