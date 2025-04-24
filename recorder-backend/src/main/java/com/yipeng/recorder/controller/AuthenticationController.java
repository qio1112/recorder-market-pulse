package com.yipeng.recorder.controller;

import com.yipeng.recorder.exception.InvalidRequestException;
import com.yipeng.recorder.model.Role;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.repository.RoleRepository;
import com.yipeng.recorder.repository.UserRepository;
import com.yipeng.recorder.request.AuthenticationRequest;
import com.yipeng.recorder.request.SignUpRequest;
import com.yipeng.recorder.service.SendEmailService;
import com.yipeng.recorder.utils.JwtUtil;
import com.yipeng.recorder.utils.RoleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    private final AuthenticationManager authenticationManager;

    private final JwtUtil jwtUtil;

    private final UserDetailsService userDetailsService;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final RoleRepository roleRepository;

    private final SendEmailService sendEmailService;


    @Autowired
    public AuthenticationController(AuthenticationManager authenticationManager,
                                    JwtUtil jwtUtil,
                                    UserDetailsService userDetailsService,
                                    UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    RoleRepository roleRepository,
                                    SendEmailService sendEmailService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.sendEmailService = sendEmailService;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<?> createAuthenticationToken(@RequestBody AuthenticationRequest authenticationRequest) throws Exception {
        // Authenticate user
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            authenticationRequest.getUsername(),
                            authenticationRequest.getPassword()
                    )
            );
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        // Load user details and generate JWT
        final UserDetails userDetails = userDetailsService.loadUserByUsername(authenticationRequest.getUsername());
        final String jwt = jwtUtil.generateToken(userDetails.getUsername());
        Map<String, String> response = new HashMap<>();
        response.put("token", jwt);
        logger.info("JWT created for user: {}", userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<?> userSignUp(@RequestBody SignUpRequest signUpRequest) throws Exception {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            throw new InvalidRequestException("Username is already in use: " + signUpRequest.getUsername());
        }
        User user = new User();
        user.setUsername(signUpRequest.getUsername());
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));

        Role role = roleRepository.findByName(RoleType.USER).get();
        user.setRoles(Set.of(role));

        userRepository.save(user);
        logger.info("User created: {}", user.getUsername());
        sendEmailService.sendEmail(user.getEmail(), "Recorder User Created", "Username: " + user.getUsername(), null);
        return ResponseEntity.ok("Created new user: " + user.getUsername());
    }
}

