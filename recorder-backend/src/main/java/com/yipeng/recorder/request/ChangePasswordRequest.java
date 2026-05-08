package com.yipeng.recorder.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 8)
    private String newPassword;

    public @NotBlank String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(@NotBlank String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public @NotBlank @Size(min = 8) String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(@NotBlank @Size(min = 8) String newPassword) {
        this.newPassword = newPassword;
    }
}
