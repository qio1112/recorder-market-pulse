package com.yipeng.recorder.service;

import com.yipeng.recorder.model.User;
import com.yipeng.recorder.utils.RunScriptResult;
import com.yipeng.recorder.utils.ScriptUtil;
import jakarta.mail.MessagingException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StockDataScriptService {

    private static final Logger logger = LoggerFactory.getLogger(StockDataScriptService.class);

    private final SendEmailService sendEmailService;

    @Autowired
    public StockDataScriptService(SendEmailService sendEmailService) {
        this.sendEmailService = sendEmailService;
    }

    public String runUpdateStockDataScript(Map<String, String> arguments, User user) {
        RunScriptResult runScriptResult = ScriptUtil.runScript("update_stock_data", arguments);
        int exitCode = runScriptResult.getExitCode();
        String output = runScriptResult.getOutput();

        // send email
        String userEmail = user.getEmail();
        try {
            if (StringUtils.isNotBlank(userEmail)) {
                sendEmailService.sendEmail(userEmail, "Run script: update_stock_data", output, null);
            }
            logger.info("Email sent to user {} for running script update_stock_data", user.getUsername());
        } catch (MessagingException e) {
            logger.warn("Failed to send email to user {} for running script update_stock_data: {}", user.getUsername(), e.getMessage());
        }
        return "exitCode=" + exitCode + "\n" + output;
    }
}
