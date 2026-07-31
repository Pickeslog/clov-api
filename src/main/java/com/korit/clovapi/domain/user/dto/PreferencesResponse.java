package com.korit.clovapi.domain.user.dto;

import com.korit.clovapi.domain.user.entity.UserPreference;
import com.korit.clovapi.global.dto.EquippedItemResponse;

public record PreferencesResponse(
        Boolean darkMode,
        String customColor,
        String wallpaperIcon,
        String dashboardBackground,
        String letterTheme,
        String memoryCardTheme,
        String mascotType,
        EquippedItemResponse equippedItem
) {
    public static PreferencesResponse from(UserPreference preference) {
        EquippedItemResponse equippedItem = preference.getEquippedItemId() == null ? null
                : new EquippedItemResponse(String.valueOf(preference.getEquippedItemId()),
                        preference.getEquippedItemName(), preference.getEquippedItemImageUrl());

        return new PreferencesResponse(
                preference.getDarkMode(),
                preference.getCustomColor(),
                preference.getWallpaperIcon(),
                preference.getDashboardBackground(),
                preference.getLetterTheme(),
                preference.getMemoryCardTheme(),
                preference.getMascotType(),
                equippedItem
        );
    }
}
