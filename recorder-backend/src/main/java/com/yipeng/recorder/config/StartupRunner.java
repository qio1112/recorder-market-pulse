package com.yipeng.recorder.config;

import com.yipeng.recorder.model.*;
import com.yipeng.recorder.repository.*;
import com.yipeng.recorder.service.ScheduleAlertService;
import com.yipeng.recorder.utils.LabelType;
import com.yipeng.recorder.utils.RoleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class StartupRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(StartupRunner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final LabelRepository labelRepository;
    private final RecordRepository recordRepository;
    private final AlertScheduleRepository alertScheduleRepository;
    private final ScheduleAlertService scheduleAlertService;

    @Value("${admin.username}")
    private String adminUsername;
    @Value("${admin.password}")
    private String adminPassword;
    @Value("${admin.email}")
    private String adminEmail;

    @Autowired
    public StartupRunner(UserRepository userRepository, RoleRepository roleRepository,
                         PasswordEncoder passwordEncoder, LabelRepository labelRepository,
                         RecordRepository recordRepository, AlertScheduleRepository alertScheduleRepository,
                         ScheduleAlertService scheduleAlertService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.labelRepository = labelRepository;
        this.recordRepository = recordRepository;
        this.alertScheduleRepository = alertScheduleRepository;
        this.scheduleAlertService = scheduleAlertService;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting application startup runner...");
        createAdminUser();
        createDefaultLabels();
        scheduleExistingAlerts();
        logger.info("Completed application startup runner...");

    }

    private void createAdminUser() {
        if (userRepository.findByUsername(adminUsername).isPresent()) {
            logger.info("Admin user already exists: {}", adminUsername);
            return ;
        }
        Role adminRole = new Role(RoleType.ADMIN);;
        Role userRole = new Role(RoleType.USER);;
        roleRepository.save(adminRole);
        roleRepository.save(userRole);

        if (adminUsername != null && adminPassword != null && adminEmail != null) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setEmail(adminEmail);
            Set<Role> adminRoles = new HashSet<>();
            adminRoles.add(adminRole);
            adminRoles.add(userRole);
            admin.setRoles(adminRoles);
            userRepository.save(admin);
            logger.info("Created ADMIN user: {}", admin.getUsername());
        }
    }

    private void createDefaultLabels() {
        User adminUser = userRepository.findByUsername(adminUsername).get();
        List<String> defaultLabels = Arrays.asList("ALERT", "INVESTMENT_REC", "VALID_TRADE", "INVALID_TRADE");
        defaultLabels.forEach(label -> createDefaultLabel(adminUser, label));
    }

    private void createDefaultLabel(User adminUser, String labelName) {
        if (labelRepository.existsByLabelName(labelName)) {
            Label alertLabel = labelRepository.findByLabelName(labelName).get();
            if (null == alertLabel.getCreatedBy()) {
                alertLabel.setCreatedBy(adminUser);
                labelRepository.save(alertLabel);
            }
            logger.info("Label '{}' already exists", labelName);
            return ;
        }
        Label alertLabel = new Label(labelName, adminUser, LabelType.DEFAULT);
        labelRepository.save(alertLabel);
    }

    private void scheduleExistingAlerts() {
        List<AlertSchedule> existingAlerts = alertScheduleRepository.findActiveSchedules();
        if (existingAlerts!= null && !existingAlerts.isEmpty()) {
            logger.info("Found {} existing alerts, making schedules", existingAlerts.size());
            for (AlertSchedule alertSchedule : existingAlerts) {
                try {
                    scheduleAlertService.scheduleAlert(alertSchedule);
                } catch (Exception e) {
                    logger.error("Failed to schedule: \n{}", e.getMessage(), e);
                }
            }
        }
    }
}
