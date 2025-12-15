package com.yipeng.recorder.controller;

import com.yipeng.recorder.exception.ForbiddenException;
import com.yipeng.recorder.exception.InvalidRequestException;
import com.yipeng.recorder.exception.ResourceNotFoundException;
import com.yipeng.recorder.model.AlertSchedule;
import com.yipeng.recorder.model.RecFile;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.request.ListRecordsRequest;
import com.yipeng.recorder.request.NewRecordRequest;
import com.yipeng.recorder.request.UpdateRecordRequest;
import com.yipeng.recorder.service.LabelService;
import com.yipeng.recorder.service.RecFileService;
import com.yipeng.recorder.service.RecordService;
import com.yipeng.recorder.service.UserService;
import com.yipeng.recorder.utils.AlertType;
import com.yipeng.recorder.utils.RecFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/records")
public class RecordController {

    private static final Logger logger = LoggerFactory.getLogger(RecordController.class);

    @Value("${recfile.upload.dir}")
    private String uploadDir;

    @Value("${recfile.image.file.size}")
    private int imageFileSizeLimit;

    @Value("${recfile.regular.file.size}")
    private int regularFileSizeLimit;

    private final UserService userService;

    private final RecordService recordService;

    private final LabelService labelService;

    private final RecFileService recFileService;

    @Autowired
    public RecordController(UserService userService, RecordService recordService, LabelService labelService, RecFileService recFileService) {
        this.userService = userService;
        this.recordService = recordService;
        this.labelService = labelService;
        this.recFileService = recFileService;
    }

    @PostMapping(value = "/create-record", consumes = "multipart/form-data")
    public ResponseEntity<Record> createRecord(
            @RequestPart("newRecordRequest") NewRecordRequest newRecordRequest,
            @RequestPart(value = "images", required=false) List<MultipartFile> images,
            @RequestPart(value = "files", required=false) List<MultipartFile> files) {

        // Extract the current authenticated user from the JWT token, then get the user
        User user = userService.findUserFromAuthentication();

        // Create a new Record object
        Record newRecord = new Record(newRecordRequest.getTitle(), user, newRecordRequest.getContent(), newRecordRequest.isPublic());

        AlertSchedule alertSchedule = getAlertSchedule(newRecordRequest, newRecord);

        // get or create labels, save files, create recFiles objects
        List<RecFile> imageFiles = handleMultipartFiles(images, RecFileType.IMAGE.name(), imageFileSizeLimit, user);
        List<RecFile> regularFiles = handleMultipartFiles(files, RecFileType.REGULAR_FILE.name(), regularFileSizeLimit, user);

        // Use the service to handle the creation, including labels and recFiles
        newRecord = recordService.createRecord(newRecord, imageFiles, regularFiles, newRecordRequest.getLabels(), user, alertSchedule,
                newRecordRequest.isPublic(), newRecordRequest.getMetadata());
        return ResponseEntity.status(HttpStatus.CREATED).body(newRecord);
    }

    private static AlertSchedule getAlertSchedule(NewRecordRequest newRecordRequest, Record newRecord) {
        AlertSchedule alertSchedule = null;
        if (newRecordRequest.getLabels().contains("ALERT")
                && newRecordRequest.getAlertType() != null
                && (newRecordRequest.getAlertType() == AlertType.ONE_TIME && newRecordRequest.getAlertTime() != null)
                    || (newRecordRequest.getAlertType() == AlertType.RECURRING && newRecordRequest.getRecurringAlertWeekDays() != null && !newRecordRequest.getRecurringAlertWeekDays().isBlank())) {
            alertSchedule = new AlertSchedule(newRecord, newRecordRequest.getAlertType(), newRecordRequest.getAlertTime(), newRecordRequest.getRecurringAlertWeekDays());
        }
        return alertSchedule;
    }

    private List<RecFile> handleMultipartFiles(List<MultipartFile> multipartFiles, String fileType, long fileSizeLimit, User user) {
        List<RecFile> files = new ArrayList<>();
        // Handle the file upload logic (e.g., save them to a server or database)
        // todo - add number of files limit
        if (multipartFiles != null) {
            for (MultipartFile file : multipartFiles) {
                if (!file.isEmpty()) {
                    if (file.getSize() > fileSizeLimit * 1024 * 1024) {
                        throw new InvalidRequestException("File size exceeds the limit " + fileSizeLimit + "MB. file: " + file.getOriginalFilename());
                    }
                    String filename =  System.currentTimeMillis() + "__" + file.getOriginalFilename();
                    try {
                        Path uploadPath = Paths.get(uploadDir);
                        if (!Files.exists(uploadPath)) {
                            Files.createDirectories(uploadPath);
                        }
                        Path filePath = uploadPath.resolve(filename);
                        Files.copy(file.getInputStream(), filePath);
                        RecFile recFile = new RecFile(filename, RecFileType.valueOf(fileType), uploadDir, user);
                        files.add(recFile);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to save file " + filename, e);
                    }
                }
            }
        }
        return files;
    }

    @PostMapping(value="/update-record", consumes="multipart/form-data")
    public ResponseEntity<Record> updateRecord(@RequestPart("updateRecordRequest") UpdateRecordRequest updateRecordRequest,
                               @RequestPart(value = "images", required = false) List<MultipartFile> images,
                               @RequestPart(value = "files", required=false) List<MultipartFile> files) {

        // find record by ID
        Record record = recordService.getRecordById(updateRecordRequest.getId());
        if (record == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // validate user of the record, only auther can modify the record
        User recordUser = record.getCreatedBy();
        User user = userService.findUserFromAuthentication();
        if (recordUser == null || user == null) {
            throw new ForbiddenException();
        }
        if (!userService.userCanModifyRecord(user, record)) {
            throw new ForbiddenException();
        }

        // set simple columns
        record.setTitle(updateRecordRequest.getTitle());
        record.setContent(updateRecordRequest.getContent());
        record.setPublic(updateRecordRequest.isPublic());
        record.setMetadata(updateRecordRequest.getMetadata());

        AlertSchedule alertSchedule = getAlertSchedule(updateRecordRequest, record);

        // find selected images and files to be deleted if really exist in the record
        Set<Long> existingFileIds = record.getRecFiles().stream().map(RecFile::getId).collect(Collectors.toSet());
        List<Long> deleteFileIds = updateRecordRequest.getRemoveFileIDs().stream()
                .filter(existingFileIds::contains)
                .toList();

        // add new images and files if any
        List<RecFile> imageFiles = handleMultipartFiles(images, RecFileType.IMAGE.name(), imageFileSizeLimit, user);
        List<RecFile> regularFiles = handleMultipartFiles(files, RecFileType.REGULAR_FILE.name(), regularFileSizeLimit, user);

        // update and save record, labels, recFiles
        record = recordService.updateRecord(record, deleteFileIds, imageFiles, regularFiles, updateRecordRequest.getLabels(), user, alertSchedule,
                updateRecordRequest.isCancelAlert(), updateRecordRequest.getMetadata());

        return ResponseEntity.ok().body(record);
    }

    private static AlertSchedule getAlertSchedule(UpdateRecordRequest updateRecordRequest, Record record) {
        AlertSchedule alertSchedule = null;
        if (!updateRecordRequest.isCancelAlert()) {
            if (updateRecordRequest.getLabels().contains("ALERT")
                    && updateRecordRequest.getAlertType() != null
                    && (updateRecordRequest.getAlertType() == AlertType.ONE_TIME && updateRecordRequest.getAlertTime() != null)
                    || (updateRecordRequest.getAlertType() == AlertType.RECURRING && updateRecordRequest.getRecurringAlertWeekDays() != null && !updateRecordRequest.getRecurringAlertWeekDays().isBlank())) {
                alertSchedule = new AlertSchedule(record, updateRecordRequest.getAlertType(), updateRecordRequest.getAlertTime(), updateRecordRequest.getRecurringAlertWeekDays());
            }
        }
        return alertSchedule;
    }

    @GetMapping(value="/record/{id}")
    public ResponseEntity<Record> getRecord(@PathVariable("id") Long id) {
        // get user and validate
        User user = userService.findUserFromAuthentication();

        Record record = recordService.getRecordById(id);
        if (record == null) {
            throw new ResourceNotFoundException("Record not found");
        }
        if (!userService.userCanSeeRecord(user, record)) {
            throw new ForbiddenException();
        }
        return ResponseEntity.ok().body(record);
    }

    @GetMapping(value="/delete-record/{id}")
    public ResponseEntity<String> deleteRecord(@PathVariable("id") Long id) {
        // get user and validate
        User user = userService.findUserFromAuthentication();

        Record record = recordService.getRecordById(id);
        if (record == null) {
            throw new ResourceNotFoundException("Record not found");
        }
        if (!userService.userCanModifyRecord(user, record)) {
            throw new ForbiddenException();
        }
        recordService.deleteRecord(record);
        return ResponseEntity.ok().body("Deleted record");
    }

    @PostMapping(value="/list-records", consumes="application/json")
    public ResponseEntity<Page<Record>> listRecords(@RequestBody ListRecordsRequest listRecordsRequest) {
        User user = userService.findUserFromAuthentication();
        if (user == null) {
            throw new ForbiddenException();
        }
        Page<Record> recordPage = recordService.listRecordsCoreDataWithFilter(
                listRecordsRequest.getLabels(),
                listRecordsRequest.getExcludeLabels(),
                listRecordsRequest.getTitleContains(),
                listRecordsRequest.getCreationAfterDate(),listRecordsRequest.getCreationBeforeDate(),
                listRecordsRequest.getModifiedAfterDate(), listRecordsRequest.getModifiedBeforeDate(),
                listRecordsRequest.getPublic(), listRecordsRequest.getIsCreatedByUserOnly(),
                listRecordsRequest.getPageSize(), listRecordsRequest.getPage(), listRecordsRequest.getSortBy(),
                user);

        return ResponseEntity.ok().body(recordPage);
    }
}
