package com.korit.clovapi.domain.user.dto;

public record UpdatePreferencesRequest(
        Boolean darkMode,
        String customColor,
        String wallpaperIcon,
        String dashboardBackground,
        String letterTheme,
        String memoryCardTheme,
        String mascotType
) {
}
