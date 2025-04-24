package com.yipeng.recorder.service;

import com.yipeng.recorder.model.User;
import com.yipeng.recorder.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Find user by username (email, or other identifier)
        Optional<User> user = userRepository.findByUsername(username);

        // Throw exception if user not found
        if (user.isEmpty()) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }

        // Convert User entity to Spring Security's UserDetails object
        return new CustomUserDetails(user.get());
    }
}

