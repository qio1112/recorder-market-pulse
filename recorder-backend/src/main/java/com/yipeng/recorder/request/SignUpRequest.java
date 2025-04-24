package com.yipeng.recorder.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SignUpRequest {

    @NotBlank
    @Size(min = 6, max = 20)
    private String username;

    @NotBlank
    @Size(min = 8, max = 24)
    private String password;

    @Email
    @NotBlank
    private String email;

    public @NotBlank @Size(min = 6, max = 20) String getUsername() {
        return username;
    }

    public void setUsername(@NotBlank @Size(min = 6, max = 20) String username) {
        this.username = username;
    }

    public @NotBlank @Size(min = 8, max = 24) String getPassword() {
        return password;
    }

    public void setPassword(@NotBlank @Size(min = 8, max = 24) String password) {
        this.password = password;
    }

    public @Email @NotBlank String getEmail() {
        return email;
    }

    public void setEmail(@Email @NotBlank String email) {
        this.email = email;
    }
}
