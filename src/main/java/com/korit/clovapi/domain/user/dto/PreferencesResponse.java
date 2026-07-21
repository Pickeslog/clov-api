package com.korit.clovapi.domain.user.dto;

import com.korit.clovapi.domain.user.entity.UserPreference;

public record PreferencesResponse(
        Boolean darkMode,
        String customColor,
        String wallpaperIcon,
        String dashboardBackground,
        String letterTheme,
        String memoryCardTheme,
        String mascotType
) {
    public static PreferencesResponse from(UserPreference preference) {
        return new PreferencesResponse(
                preference.getDarkMode(),
                preference.getCustomColor(),
                preference.getWallpaperIcon(),
                preference.getDashboardBackground(),
                preference.getLetterTheme(),
                preference.getMemoryCardTheme(),
                preference.getMascotType()
        );
    }
}
