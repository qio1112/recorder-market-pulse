package com.yipeng.recorder.controller;

import com.yipeng.recorder.model.User;
import com.yipeng.recorder.service.SendEmailService;
import com.yipeng.recorder.service.UserService;
import com.yipeng.recorder.utils.RunScriptResult;
import com.yipeng.recorder.utils.ScriptUtil;
import jakarta.mail.MessagingException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ScriptController {

    private static final Logger logger = LoggerFactory.getLogger(ScriptController.class);

    private final UserService userService;
    private final SendEmailService sendEmailService;

    @Autowired
    public ScriptController(UserService userService, SendEmailService sendEmailService) {
        this.userService = userService;
        this.sendEmailService = sendEmailService;
    }

    @PostMapping("/run-script/{script-name}")
    public ResponseEntity<String> runScript(@PathVariable("script-name") String scriptName,
                                            @RequestBody Map<String, String> arguments) {

        RunScriptResult runScriptResult = ScriptUtil.runScript(scriptName, arguments);
        int exitCode = runScriptResult.getExitCode();
        String output = runScriptResult.getOutput();

        // send email
        User user = userService.findUserFromAuthentication();
        String userEmail = user.getEmail();
        try {
            if (StringUtils.isNotBlank(userEmail)) {
                sendEmailService.sendEmail(userEmail, "Run script: " + scriptName, output, null);
            }
            logger.info("Email sent to user {} for running script {}", user.getUsername(), scriptName);
        } catch (MessagingException e) {
            logger.warn("Failed to send email to user {} for running script {}: {}", user.getUsername(), scriptName, e.getMessage());
        }

        if (exitCode != 0) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Script execution failed with exit code " + exitCode
                            + ".\nOutput:\n" + output);
        }

        logger.info("Script {} execution completed successfully.", scriptName);
        return ResponseEntity.ok("Script executed successfully.\nOutput:\n" + output);
    }
}

