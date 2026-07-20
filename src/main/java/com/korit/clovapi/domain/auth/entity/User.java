package com.korit.clovapi.domain.auth.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class User {

    private Long id;
    private String email;
    private String password;
    private String nickname;
    private String profileImageUrl;
    private LocalDate birthdate;
    private LocalDateTime termsAgreedAt;
    private LocalDateTime privacyAgreedAt;
    private LocalDateTime marketingAgreedAt;
    private String personalInviteCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public LocalDate getBirthdate() { return birthdate; }
    public void setBirthdate(LocalDate birthdate) { this.birthdate = birthdate; }
    public LocalDateTime getTermsAgreedAt() { return termsAgreedAt; }
    public void setTermsAgreedAt(LocalDateTime termsAgreedAt) { this.termsAgreedAt = termsAgreedAt; }
    public LocalDateTime getPrivacyAgreedAt() { return privacyAgreedAt; }
    public void setPrivacyAgreedAt(LocalDateTime privacyAgreedAt) { this.privacyAgreedAt = privacyAgreedAt; }
    public LocalDateTime getMarketingAgreedAt() { return marketingAgreedAt; }
    public void setMarketingAgreedAt(LocalDateTime marketingAgreedAt) { this.marketingAgreedAt = marketingAgreedAt; }
    public String getPersonalInviteCode() { return personalInviteCode; }
    public void setPersonalInviteCode(String personalInviteCode) { this.personalInviteCode = personalInviteCode; }
}
