package com.korit.clovapi.domain.user.entity;

public class UserPreference {

    private Boolean darkMode;
    private String customColor;
    private String wallpaperIcon;
    private String dashboardBackground;
    private String letterTheme;
    private String memoryCardTheme;
    private String mascotType;

    public Boolean getDarkMode() { return darkMode; }
    public void setDarkMode(Boolean darkMode) { this.darkMode = darkMode; }
    public String getCustomColor() { return customColor; }
    public void setCustomColor(String customColor) { this.customColor = customColor; }
    public String getWallpaperIcon() { return wallpaperIcon; }
    public void setWallpaperIcon(String wallpaperIcon) { this.wallpaperIcon = wallpaperIcon; }
    public String getDashboardBackground() { return dashboardBackground; }
    public void setDashboardBackground(String dashboardBackground) { this.dashboardBackground = dashboardBackground; }
    public String getLetterTheme() { return letterTheme; }
    public void setLetterTheme(String letterTheme) { this.letterTheme = letterTheme; }
    public String getMemoryCardTheme() { return memoryCardTheme; }
    public void setMemoryCardTheme(String memoryCardTheme) { this.memoryCardTheme = memoryCardTheme; }
    public String getMascotType() { return mascotType; }
    public void setMascotType(String mascotType) { this.mascotType = mascotType; }
}
