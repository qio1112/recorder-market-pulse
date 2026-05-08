package com.yipeng.recorder.service;

import com.yipeng.recorder.exception.ForbiddenException;
import com.yipeng.recorder.exception.InvalidRequestException;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerNewUser(User user) {
        // Encode the raw password before saving
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User findByUsername(String username) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        return userOptional.orElse(null);
    }

    public boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public void changePassword(User user, String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new InvalidRequestException("Current password is required.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidRequestException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8) {
            throw new InvalidRequestException("New password must be at least 8 characters long.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public User findUserFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = this.findByUsername(username);
        if (user == null) {
            throw new ForbiddenException();
        }
        return user;
    }

    public boolean userCanSeeRecord(User user, Record record) {
        return userCanSeeResource(user, record.isPublic(), record.getCreatedBy());
    }

    public boolean userCanModifyRecord(User user, Record record) {
        return userCanModifyResource(user, record.getCreatedBy());
    }

    public boolean userCanSeeResource(User user, boolean resourceIsPublic, User resourceOwner) {
        return user.isAdmin() || resourceIsPublic || resourceOwner.getId().equals(user.getId());
    }

    public boolean userCanModifyResource(User user, User resourceOwner) {
        return user.isAdmin() || resourceOwner.getId().equals(user.getId());
    }
}
