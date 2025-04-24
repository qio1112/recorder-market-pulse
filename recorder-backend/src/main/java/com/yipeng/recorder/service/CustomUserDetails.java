package com.yipeng.recorder.service;

import com.yipeng.recorder.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.stream.Collectors;

public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Convert user roles to GrantedAuthority format for Spring Security
        return user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return user.getPassword();  // Get the password from the User entity
    }

    @Override
    public String getUsername() {
        return user.getUsername();  // Get the username from the User entity
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;  // Custom logic to check if account is expired
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;  // Custom logic to check if account is locked
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;  // Custom logic to check if credentials are expired
    }

    @Override
    public boolean isEnabled() {
        return true;  // Custom logic to check if user is enabled
    }
}
