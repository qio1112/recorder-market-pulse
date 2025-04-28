package com.yipeng.recorder;

import com.yipeng.recorder.model.Label;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.Role;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.repository.LabelRepository;
import com.yipeng.recorder.repository.RecordRepository;
import com.yipeng.recorder.repository.RoleRepository;
import com.yipeng.recorder.repository.UserRepository;
import com.yipeng.recorder.service.CronService;
import com.yipeng.recorder.service.SendEmailService;
import com.yipeng.recorder.utils.*;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.mail.MessagingException;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.constraints.AssertTrue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
class RecorderApplicationTests {

    private static final Logger logger = LoggerFactory.getLogger(RecorderApplicationTests.class);

    private final String baseUrl = "http://localhost:8080";
    private String jwtToken;
    private final String testUsernameAdmin = "test_user_admin";

    private boolean testDataCreated = false;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final LabelRepository labelRepository;
    private final RecordRepository recordRepository;
    private final JwtUtil jwtUtil;
    private final SendEmailService sendEmailService;
    private final DateTimeUtils dateTimeUtils;
    private final CronService cronService;

    @Value("${recfile.upload.dir}")
    private String uploadDir;

    @Value("${spring.mail.username}")
    private String email;

    @Autowired
    public RecorderApplicationTests(UserRepository userRepository, RoleRepository roleRepository,
                         PasswordEncoder passwordEncoder, LabelRepository labelRepository,
                         RecordRepository recordRepository, JwtUtil jwtUtil, SendEmailService sendEmailService,
                                    DateTimeUtils dateTimeUtils, CronService cronService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.labelRepository = labelRepository;
        this.recordRepository = recordRepository;
        this.jwtUtil = jwtUtil;
        this.sendEmailService = sendEmailService;
        this.dateTimeUtils = dateTimeUtils;
        this.cronService = cronService;
    }

    @Test
    void testGetJWTTokenAPI() {
        prepareTestData();
        String username = "test_user_admin";
        String password = "12345678";
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{ \"username\": \"" + username + "\", \"password\": \"" + password + "\" }")
                .post(baseUrl + "/api/auth/authenticate");

        // Validate response
        response.then()
                .statusCode(200)
                .body("token", notNullValue());

        // Extract the token
        jwtToken = response.jsonPath().getString("token");
        logger.info("jwt token created for user: {}, token: {}", username, jwtToken);
    }

    @Test
    void testCreateRecordNoFileAPI() {
        prepareTestData();
        getTestJWTToken();
        String jsonBody = """
                      {
                        "title": "create record test 1",
                        "content": "content of creating record test 1\\n this is long",
                        "isPublic": true,
                        "labels": ["TEST1", "TEST2", "NEW_LABEL", "2024-12-28"]
                      }
                      """;
        Response response = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.MULTIPART)
                .multiPart("newRecordRequest", jsonBody, "application/json") // Add JSON as a multipart field
                .log().all()
                .post(baseUrl + "/api/records/create-record");

        response.then()
                .log().all()
                .statusCode(201);

        logger.info(response.asString());
    }

    @Test
    void testCreateRecordWithFileThenUpdateThenDeleteAPI() {
        prepareTestData();
        getTestJWTToken();

        File imageFile1 = new File("src/test/resources/test_images/test_image1.png");
        File imageFile2 = new File("src/test/resources/test_images/test_image2.png");
        File imageFile3 = new File("src/test/resources/test_images/test_image3.png");
        File regularFile1 = new File("src/test/resources/test_files/test_file1.txt");
        File regularFile2 = new File("src/test/resources/test_files/test_file2.txt");
        File regularFile3 = new File("src/test/resources/test_files/test_file3.txt");

        // create record
        String jsonBody1 = """
                      {
                        "title": "create record test 2 with files",
                        "content": "content of creating record test 1 with files files files\\n this is long",
                        "isPublic": true,
                        "labels": ["TEST1", "TEST2", "NEW_LABEL", "2024-12-28", "FILES", "TEST1"]
                      }
                      """;
        Response response1 = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.MULTIPART)
                .multiPart("images", imageFile1)
                .multiPart("images", imageFile2)
                .multiPart("files", regularFile1)
                .multiPart("files", regularFile2)
                .multiPart("newRecordRequest", jsonBody1, "application/json") // Add JSON as a multipart field
                .log().all()
                .post(baseUrl + "/api/records/create-record");

        response1.then()
                .log().all()
                .statusCode(201);

        logger.info(response1.asString());

        List<Map<String, String>> recFilesInfo = response1.body().jsonPath().getList("recFiles");
        List<Path> filePaths = recFilesInfo.stream()
                .map(m -> {
                    String filename = m.get("filename");
                    Path p = Paths.get(uploadDir).resolve(filename);
                    return p;
                })
                .toList();

        List<Long> fileIDs = recFilesInfo.stream()
                .map(m -> Long.parseLong(m.get("fileID")))
                .toList();

        Assertions.assertEquals(4, recFilesInfo.size());

        for (Path path : filePaths) {
            boolean fileCheck = Files.exists(path) && Files.isRegularFile(path);
            Assertions.assertTrue(fileCheck, "file not uploaded " + path);
        }

        long recordID = response1.body().jsonPath().getLong("id");

        // update record
        Long fileIDToDelete = fileIDs.get(0);
        String jsonBody2 = """
                      {
                        "id": %d,
                        "title": "updated title create record test 2 with files",
                        "content": "updated content of creating record test 1 with files files files\\n this is long",
                        "isPublic": false,
                        "labels": ["TEST3", "NEW_LABEL", "2024-12-28", "FILES", "2024-12-29"],
                        "removeFileIDs": [%d]
                      }
                      """.formatted(recordID, fileIDToDelete);

        logger.info(jsonBody2);
        Response response2 = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.MULTIPART)
                .multiPart("images", imageFile3)
                .multiPart("files", regularFile3)
                .multiPart("updateRecordRequest", jsonBody2, "application/json") // Add JSON as a multipart field
                .log().all()
                .post(baseUrl + "/api/records/update-record");

        response2.then()
                .log().all()
                .statusCode(200);

        logger.info(response2.asString());

        List<Map<String, String>> recFilesInfo2 = response2.body().jsonPath().getList("recFiles");
        Assertions.assertEquals(5, recFilesInfo2.size());

        // delete record
        Response response3 = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.JSON)
                .get(baseUrl + "/api/records/delete-record/" + recordID);

        response3.then()
                .log().all()
                .statusCode(200);

        logger.info(response3.asString());

        for (Path path : filePaths) {
            boolean fileCheck = Files.exists(path) && Files.isRegularFile(path);
            Assertions.assertFalse(fileCheck, "file not deleted " + path);
        }
    }

    @Test
    void testCreateRecordWithOneTimeAlert() throws InterruptedException {
        prepareTestData();
        getTestJWTToken();

        File imageFile1 = new File("src/test/resources/test_images/test_image1.png");
        File regularFile1 = new File("src/test/resources/test_files/test_file1.txt");

        ZonedDateTime alertTime1 = dateTimeUtils.getCurrentDateTimeWithDelay(5);
        String alertTimeString1 = alertTime1.format(dateTimeUtils.getTimestampFormatter());
        // create record
        String jsonBody1 = """
                      {
                        "title": "create record test 4 with alert",
                        "content": "content of creating record test 4 with files files files\\n this is long",
                        "isPublic": true,
                        "labels": ["ALERT", "TEST1", "TEST2", "NEW_LABEL", "2024-12-28", "FILES", "TEST1"],
                        "alertType": "ONE_TIME",
                        "alertTime": "%s"
                      }
                      """.formatted(alertTimeString1);
        Response response1 = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.MULTIPART)
                .multiPart("images", imageFile1)
                .multiPart("files", regularFile1)
                .multiPart("newRecordRequest", jsonBody1, "application/json") // Add JSON as a multipart field
                .post(baseUrl + "/api/records/create-record");

        response1.then()
                .statusCode(201);

        logger.info(response1.asString());
        Thread.sleep(10000);
    }

    @Test
    void testCreateRecordWithOneTimeAlertButUpdated() throws InterruptedException {
        prepareTestData();
        getTestJWTToken();

        File imageFile1 = new File("src/test/resources/test_images/test_image1.png");
        File regularFile1 = new File("src/test/resources/test_files/test_file1.txt");

        ZonedDateTime alertTime1 = dateTimeUtils.getCurrentDateTimeWithDelay(5);
        String alertTimeString1 = alertTime1.format(dateTimeUtils.getTimestampFormatter());
        // create record
        String jsonBody1 = """
                      {
                        "title": "create record test 4 with alert",
                        "content": "content of creating record test 4 with files files files\\n this is long",
                        "isPublic": true,
                        "labels": ["ALERT", "TEST1", "TEST2", "NEW_LABEL", "2024-12-28", "FILES", "TEST1"],
                        "alertType": "ONE_TIME",
                        "alertTime": "%s"
                      }
                      """.formatted(alertTimeString1);
        Response response1 = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.MULTIPART)
                .multiPart("images", imageFile1)
                .multiPart("files", regularFile1)
                .multiPart("newRecordRequest", jsonBody1, "application/json") // Add JSON as a multipart field
//                .log().all()
                .post(baseUrl + "/api/records/create-record");

        response1.then()
                .statusCode(201);

//        logger.info(response1.asString());

        long recordID = response1.body().jsonPath().getLong("id");

        // update record
        String jsonBody2 = """
                      {
                        "id": %d,
                        "title": "updated title create record test 2 with files",
                        "content": "updated content of creating record test 1 with files files files\\n this is long",
                        "isPublic": true,
                        "labels": ["TEST1", "TEST2", "NEW_LABEL", "2024-12-28", "FILES", "TEST1"],
                        "alertType": "ONE_TIME",
                        "alertTime": "%s"
                      }
                      """.formatted(recordID, alertTimeString1);

        logger.info(jsonBody2);
        Response response2 = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.MULTIPART)
                .multiPart("updateRecordRequest", jsonBody2, "application/json") // Add JSON as a multipart field
                .post(baseUrl + "/api/records/update-record");

        response2.then()
                .statusCode(200);

        logger.info(response2.asString());
        Thread.sleep(10000);
    }

    @Test
    void testCreateRecordWithNoAlertButUpdated() throws InterruptedException {
        prepareTestData();
        getTestJWTToken();

        File imageFile1 = new File("src/test/resources/test_images/test_image1.png");
        File regularFile1 = new File("src/test/resources/test_files/test_file1.txt");

        ZonedDateTime alertTime1 = dateTimeUtils.getCurrentDateTimeWithDelay(5);
        String alertTimeString1 = alertTime1.format(dateTimeUtils.getTimestampFormatter());
        // create record
        String jsonBody1 = """
                      {
                        "title": "create record test 4 with alert",
                        "content": "content of creating record test 4 with files files files\\n this is long",
                        "isPublic": true,
                        "labels": ["ALERT", "TEST1", "TEST2", "NEW_LABEL", "2024-12-28", "FILES", "TEST1"]
                      }
                      """;
        Response response1 = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.MULTIPART)
                .multiPart("images", imageFile1)
                .multiPart("files", regularFile1)
                .multiPart("newRecordRequest", jsonBody1, "application/json") // Add JSON as a multipart field
//                .log().all()
                .post(baseUrl + "/api/records/create-record");

        response1.then()
                .statusCode(201);

        logger.info(response1.asString());

        long recordID = response1.body().jsonPath().getLong("id");

        // update record
        String jsonBody2 = """
                      {
                        "id": %d,
                        "title": "updated title create record test 2 with files",
                        "content": "updated content of creating record test 1 with files files files\\n this is long",
                        "isPublic": true,
                        "labels": ["ALERT", "TEST1", "TEST2", "NEW_LABEL", "2024-12-28"],
                        "alertType": "ONE_TIME",
                        "alertTime": "%s"
                      }
                      """.formatted(recordID, alertTimeString1);

        logger.info(jsonBody2);
        Response response2 = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.MULTIPART)
                .multiPart("updateRecordRequest", jsonBody2, "application/json") // Add JSON as a multipart field
                .post(baseUrl + "/api/records/update-record");

        response2.then()
                .statusCode(200);

        logger.info(response2.asString());
        Thread.sleep(10000);
    }

    @Test
    void testRecordQueryAPI() {
        prepareTestData();
        getTestJWTToken();

        String jsonBody1 = """
                      {
                        "titleContains": "default user",
                        "labels": ["TEST1", "2024-12-01"],
                        "pageSize": 3,
                        "page": 0
                      }
                      """;
        Response response1 = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.JSON)
                .body(jsonBody1)
                .post(baseUrl + "/api/records/list-records");

        response1.then()
                .log().all()
                .statusCode(200);

        logger.info(response1.asString());

    }

    @Test
    void testRecordQueryTimestampAPI() {
        prepareTestData();
        getTestJWTToken();

        String jsonBody1 = """
                      {
                        "labels": ["TEST1"],
                        "pageSize": 3,
                        "page": 0,
                        "creationAfterDate": "2024-12-12",
                        "creationBeforeDate": "2024-12-31"
                      }
                      """;
        Response response1 = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.JSON)
                .body(jsonBody1)
                .post(baseUrl + "/api/records/list-records");

        response1.then()
                .log().all()
                .statusCode(200);

        logger.info(response1.asString());

    }

    @Test
    void testGetRecordAPI() {
        prepareTestData();
        getTestJWTToken();
        Response response = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.JSON)
                .get(baseUrl + "/api/records/record/1");

        response.then()
                .statusCode(200);

        logger.info(response.asString());
    }

    @Test
    void testSendEmailService() throws MessagingException {
        File imageFile3 = new File("src/test/resources/test_images/test_image3.png");
        File regularFile1 = new File("src/test/resources/test_files/test_file1.txt");
        List<File> attachments = new ArrayList<>();
        attachments.add(regularFile1);
        attachments.add(imageFile3);

        sendEmailService.sendEmail(email,
                "test sending email with attachments",
                "content of test sending alert email. \n testing sending email. 你好！",
                attachments);


        sendEmailService.sendEmail(email,
                "test sending email no attachments",
                "content of test sending alert email. \n testing sending email. 你好！",
                null);
    }

    @Test
    void testUserSignUpAndLogin() {
        String username = "test_user_signup";
        String password = "123456789";
        String testEmail = email;

        // signup
        String jsonBody1 = """
                      {
                        "username": "%s",
                        "password": "%s",
                        "email": "%s"
                      }
                      """.formatted(username, password, testEmail);
        Response response1 = given()
                .contentType(ContentType.JSON)
                .body(jsonBody1)
                .post(baseUrl + "/api/auth/signup");

        response1.then()
                .statusCode(200);

        logger.info(response1.asString());

        // login
        String jsonBody2 = """
                      {
                        "username": "%s",
                        "password": "%s"
                      }
                      """.formatted(username, password);

        Response response2 = given()
                .contentType(ContentType.JSON)
                .body(jsonBody2)
                .post(baseUrl + "/api/auth/authenticate");

        response2.then()
                .statusCode(200);

        logger.info(response2.asString());
    }


    private void getTestJWTToken() {
        try {
            jwtToken = jwtUtil.generateToken("test_user_admin");
            logger.info("jwt token for user: {}, token: {}", testUsernameAdmin, jwtToken);
        } catch (Exception e) {
            logger.warn("failed to get jwt token for user: {}", testUsernameAdmin);
            e.printStackTrace();
            throw e;
        }
    }

    private void prepareTestData() {
        if (!testDataCreated) {
            List<User> users = addTestUser();
            List<Label> labels = addTestLabels(users.get(0));
            List<Record> records = addTestRecords(users.get(0), users.get(1), labels);
            testDataCreated = true;
        }
    }

    private List<User> addTestUser() {
        Optional<Role> adminRoleOptional = roleRepository.findByName(RoleType.ADMIN);
        Optional<Role> userRoleOptional = roleRepository.findByName(RoleType.USER);
        Role adminRole;
        Role userRole;
        if (adminRoleOptional.isPresent()) {
            adminRole = adminRoleOptional.get();
        } else {
            adminRole = new Role(RoleType.ADMIN);
            roleRepository.save(adminRole);
        }
        if (userRoleOptional.isPresent()) {
            userRole = userRoleOptional.get();
        } else {
            userRole = new Role(RoleType.USER);
            roleRepository.save(userRole);
        }

        List<User> testUsers = new ArrayList<>();

        String testUsernameAdmin = "test_user_admin";
        Optional<User> testUserAdminOptional = userRepository.findByUsername(testUsernameAdmin);
        User testUserAdmin;
        if (!testUserAdminOptional.isPresent()) {
            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            roles.add(userRole);
            String password = passwordEncoder.encode("12345678");
            testUserAdmin = new User(testUsernameAdmin, password, "test_url", email, roles);
            userRepository.save(testUserAdmin);
        } else {
            testUserAdmin = testUserAdminOptional.get();
        }
        testUsers.add(testUserAdmin);

        String testUsernameDefault = "test_user_user1";
        Optional<User> testUserDefaultOptional = userRepository.findByUsername(testUsernameDefault);
        User testUserDefault;
        if (!testUserDefaultOptional.isPresent()) {
            Set<Role> roles = new HashSet<>();
            roles.add(userRole);
            String password = passwordEncoder.encode("12345678");
            testUserDefault = new User(testUsernameDefault, password, "test_url", "test1@test1.com", roles);
            userRepository.save(testUserDefault);
        } else {
            testUserDefault = testUserDefaultOptional.get();
        }
        testUsers.add(testUserDefault);

        return testUsers;
    }

    private List<Label> addTestLabels(User user) {
        List<Label> testLabels = new ArrayList<>();
        List<String> testDates = Arrays.asList("2024-12-01", "2024-12-02", "2024-12-10", "2024-12-11", "2024-12-12", "2024-12-13", "2024-12-14");

        for (int i = 1; i <= 10; i ++) {
            String labelName = "TEST" + i;
            Optional<Label> testLabelOptional = labelRepository.findByLabelName(labelName);
            if (testLabelOptional.isPresent()) {
                testLabels.add(testLabelOptional.get());
            } else {
                Label newTestLabel = new Label(labelName, user, LabelType.REGULAR);
                labelRepository.save(newTestLabel);
                testLabels.add(newTestLabel);
            }
        }

        for (String testDate : testDates) {
            Optional<Label> testLabelOptional = labelRepository.findByLabelName(testDate);
            if (testLabelOptional.isPresent()) {
                testLabels.add(testLabelOptional.get());
            } else {
                Label newTestLabel = new Label(testDate, user, LabelType.DATE);
                labelRepository.save(newTestLabel);
                testLabels.add(newTestLabel);
            }
        }

        return testLabels;
    }

    private List<com.yipeng.recorder.model.Record> addTestRecords(User userAdmin, User userDefault, List<Label> testLabels) {
        Map<String, Label> labelMap = new HashMap<>();
        testLabels.forEach(label -> labelMap.put(label.getLabelName(), label));

        List<com.yipeng.recorder.model.Record> testRecords = new ArrayList<>();
        com.yipeng.recorder.model.Record testRecord1 = new com.yipeng.recorder.model.Record("test record 1 public", userAdmin, "content of record 1 public", true);
        testRecord1.setLabels(Arrays.asList(labelMap.get("TEST1"), labelMap.get("TEST2"), labelMap.get("TEST3"), labelMap.get("2024-12-01")));
        recordRepository.save(testRecord1);
        testRecords.add(testRecord1);

        com.yipeng.recorder.model.Record testRecord2 = new com.yipeng.recorder.model.Record("test record 2 private", userAdmin, "content of record 2 private", false);
        testRecord2.setLabels(Arrays.asList(labelMap.get("TEST3"), labelMap.get("TEST4"), labelMap.get("TEST5"), labelMap.get("2024-12-01"), labelMap.get("2024-12-02")));
        recordRepository.save(testRecord2);
        testRecords.add(testRecord2);

        com.yipeng.recorder.model.Record testRecord3 = new com.yipeng.recorder.model.Record("test record 3 default user public", userDefault, "content of record 3 default public", true);
        testRecord3.setLabels(Arrays.asList(labelMap.get("TEST4"), labelMap.get("TEST5"), labelMap.get("TEST6"), labelMap.get("2024-12-01"),  labelMap.get("2024-12-03")));
        recordRepository.save(testRecord3);
        testRecords.add(testRecord3);

        com.yipeng.recorder.model.Record testRecord4 = new Record("test record 3 default user private", userDefault, "content of record 4 default private", false);
        testRecord4.setLabels(Arrays.asList(labelMap.get("TEST1"), labelMap.get("TEST5"), labelMap.get("TEST6"), labelMap.get("2024-12-01"),  labelMap.get("2024-12-04")));
        recordRepository.save(testRecord4);
        testRecords.add(testRecord4);

        com.yipeng.recorder.model.Record testRecord5 = new com.yipeng.recorder.model.Record("test record 5 public", userAdmin, "content of record 5 public", true);
        testRecord5.setLabels(Arrays.asList(labelMap.get("TEST1"), labelMap.get("TEST6"), labelMap.get("TEST8"), labelMap.get("2024-12-10")));
        recordRepository.save(testRecord5);
        testRecords.add(testRecord5);

        return testRecords;
    }

    @Test
    void testRunScript() {
        prepareTestData();
        getTestJWTToken();
        Response response = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.JSON)
                .get(baseUrl + "/api/run-script/update_stock_data");

        response.then()
                .statusCode(200);
    }

    @Test
    void testRunScriptNoAdminRole() {
        prepareTestData();
        jwtToken = jwtUtil.generateToken("test_user_user1");
        Response response = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.JSON)
                .get(baseUrl + "/api/run-script/update_stock_data");

        response.then()
                .statusCode(403);

    }

//    @Test
//    void testCronUpdateStockData() {
//        cronService.updateStockJob("test");
//    }

    @Test
    void testGetIP() {
        String privateIP = IPUtil.getPrivateIP();
        String publicIP = IPUtil.getPublicIP();
        logger.info("privateIP: {}", privateIP);
        logger.info("publicIP: {}", publicIP);
    }
}
