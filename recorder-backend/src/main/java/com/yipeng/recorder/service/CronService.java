package com.yipeng.recorder.service;

import com.yipeng.recorder.utils.DateTimeUtils;
import com.yipeng.recorder.utils.IPUtil;
import com.yipeng.recorder.utils.RunScriptResult;
import com.yipeng.recorder.utils.ScriptUtil;
import jakarta.mail.MessagingException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CronService {

    private static final Logger logger = LoggerFactory.getLogger(CronService.class);

    @Value("${admin.email}")
    private String adminEmail;

    private final SendEmailService sendEmailService;
    private final DateTimeUtils dateTimeUtils;

    @Autowired
    public CronService(SendEmailService sendEmailService, DateTimeUtils dateTimeUtils) {
        this.sendEmailService = sendEmailService;
        this.dateTimeUtils = dateTimeUtils;
    }

//    @Scheduled(cron = "0 5 10 * * *")
//    public void runUpdateStockInfoTask1005() {
//        updateStockJob("10:05");
//    }
//
//    @Scheduled(cron = "0 30 13 * * *")
//    public void runUpdateStockInfoTask1330() {
//        updateStockJob("13:30");
//    }
//
//    @Scheduled(cron = "0 30 16 * * *")
//    public void runUpdateStockInfoTask1630() {
//        updateStockJob("16:30");
//    }
//
//    @Scheduled(cron = "0 0 21 * * *")
//    public void runUpdateStockInfoTask2100() {
//        updateStockJob("21:00");
//    }
//
//    @Scheduled(cron = "0 0 8 * * *")
//    public void runStatusUpdate0800() {
//        serverStatusEmail("08:00");
//    }
//
//    @Scheduled(cron = "0 0 12 * * *")
//    public void runStatusUpdate1200() {
//        serverStatusEmail("12:00");
//    }
//
//    @Scheduled(cron = "0 0 16 * * *")
//    public void runStatusUpdate1600() {
//        serverStatusEmail("16:00");
//    }
//
//    @Scheduled(cron = "0 0 20 * * *")
//    public void runStatusUpdate2000() {
//        serverStatusEmail("20:00");
//    }
//
//    @Scheduled(cron = "0 0 23 * * *")
//    public void runStatusUpdate2300() {
//        serverStatusEmail("23:00");
//    }

    public void updateStockJob(String timeName) {
        String today = dateTimeUtils.getCurrentDateString();
        String fullTimeName = today + " " + timeName;
        String scriptName = "update_stock_data";
        RunScriptResult result = ScriptUtil.runScript(scriptName, null);
        try {
            if (StringUtils.isNotBlank(adminEmail)) {
                sendEmailService.sendEmail(adminEmail, "Run script: " + scriptName + " " + fullTimeName, result.getOutput(), null);
            }
            logger.info("Email sent to adminEmail {} for running script {} {}", adminEmail, scriptName, fullTimeName);
        } catch (MessagingException e) {
            logger.warn("Failed to send email to adminEmail {} for running script {} {}: {}", adminEmail, scriptName, fullTimeName, e.getMessage());
        }
    }

    public void serverStatusEmail(String timeName) {
        String privateIP = IPUtil.getPrivateIP();
        String publicIP = IPUtil.getPublicIP();
        String today = dateTimeUtils.getCurrentDateString();
        String fullTimeName = today + " " + timeName;
        String content = """
                Server is on.
                private ip: %s
                public ip: %s
                """.formatted(privateIP, publicIP);
        try {
            sendEmailService.sendEmail(adminEmail, "STATUS: Server is on " + fullTimeName, content, null);
            logger.info("Email sent to adminEmail {} for server status check {}", adminEmail, fullTimeName);
        } catch (MessagingException e) {
            logger.warn("Failed to send email to adminEmail {} for server status check {}. Error: {}", adminEmail, fullTimeName, e.getMessage());
        }
    }
}
