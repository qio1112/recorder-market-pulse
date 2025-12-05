package com.yipeng.recorder.service;

import com.yipeng.recorder.model.*;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.repository.AlertScheduleRepository;
import com.yipeng.recorder.repository.LabelRepository;
import com.yipeng.recorder.repository.RecFileRepository;
import com.yipeng.recorder.repository.RecordRepository;
import com.yipeng.recorder.utils.DateTimeUtils;
import com.yipeng.recorder.utils.LabelType;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecordService {

    private static final Logger logger = LoggerFactory.getLogger(RecordService.class);

    private final RecordRepository recordRepository;

    private final LabelRepository labelRepository;

    private final RecFileRepository recFileRepository;

    private final AlertScheduleRepository alertScheduleRepository;

    private final DateTimeUtils dateTimeUtils;

    private final ScheduleAlertService scheduleAlertService;

    @Autowired
    public RecordService(RecordRepository recordRepository,
                         LabelRepository labelRepository,
                         RecFileRepository recFileRepository,
                         DateTimeUtils dateTimeUtils,
                         ScheduleAlertService scheduleAlertService,
                         AlertScheduleRepository alertScheduleRepository) {
        this.recordRepository = recordRepository;
        this.labelRepository = labelRepository;
        this.recFileRepository = recFileRepository;
        this.dateTimeUtils = dateTimeUtils;
        this.scheduleAlertService = scheduleAlertService;
        this.alertScheduleRepository = alertScheduleRepository;
    }

    @Transactional
    public Record createRecord(Record record, List<RecFile> images, List<RecFile> regularFiles, List<String> labelNames,
                               User user, AlertSchedule alertSchedule, boolean isPublic, Map<String, String> metadata) {
        if (labelNames.contains("INVESTMENT_REC")) {
            if (metadata.containsKey("symbol") && !labelNames.contains(metadata.get("symbol"))) {
                labelNames.add(metadata.get("symbol"));
            }
            if (metadata.containsKey("date") && !labelNames.contains(metadata.get("date"))) {
                labelNames.add(metadata.get("date"));
            }
        }
        List<Label> labels = createLabelsIfNotExistThenGet(labelNames, user, true);
        List<RecFile> allRecFiles = new ArrayList<>(images);
        allRecFiles.addAll(regularFiles);
        recFileRepository.saveAll(allRecFiles);
        record.setRecFiles(allRecFiles);
        record.setLastModifiedTime(ZonedDateTime.now());
        record.setAlertSchedule(alertSchedule);
        record.setMetadata(metadata);
        record.setLabels(labels);
        Record newRecord = recordRepository.save(record);
        scheduleAlertService.scheduleAlert(alertSchedule);
        logger.info("Created new record. ID: {}, title: {}, createdBy: {}, isPublic: {}", newRecord.getId(), newRecord.getTitle(), newRecord.getCreatedBy().getUsername(), isPublic);
        return newRecord;
    }

    public Record getRecordById(Long id) {
        return recordRepository.findById(id).orElse(null);
    }

    @Transactional
    public Record updateRecord(Record record, List<Long> deleteFileIds, List<RecFile> images, List<RecFile> regularFiles, List<String> labelNames, User user,
                               AlertSchedule alertSchedule, boolean isCancelAlert) {
        // create and update labels
        List<Label> labels = createLabelsIfNotExistThenGet(labelNames, user, false);
        record.setLabels(labels);
        // delete files
        List<Path> pathsToBeDeleted = getRecFilePathsByIDs(deleteFileIds);
        deleteFileIds.forEach(id -> record.getRecFiles().removeIf(file -> file.getId().equals(id)));
        recFileRepository.deleteByIds(deleteFileIds);
        // create new files
        List<RecFile> allRecFiles = new ArrayList<>(images);
        allRecFiles.addAll(regularFiles);
        recFileRepository.saveAll(allRecFiles);

        // Handle alert schedule update properly
        AlertSchedule oldAlertSchedule = record.getAlertSchedule();
        if (isCancelAlert) {
            // Remove alert schedule if cancel alert is requested
            if (oldAlertSchedule != null) {
                alertScheduleRepository.delete(oldAlertSchedule);
            }
            record.setAlertSchedule(null);
        } else if (alertSchedule != null) {
            if (oldAlertSchedule != null) {
                // Update existing alert schedule instead of creating new one
                oldAlertSchedule.setAlertType(alertSchedule.getAlertType());
                oldAlertSchedule.setTimeAt(alertSchedule.getTimeAt());
                oldAlertSchedule.setWeekdays(alertSchedule.getWeekdays());
                record.setAlertSchedule(oldAlertSchedule);
            } else {
                // Create new alert schedule if none exists
                record.setAlertSchedule(alertSchedule);
            }
        }

        // add new files to record
        record.getRecFiles().addAll(allRecFiles);
        record.setLastModifiedTime(ZonedDateTime.now());
        Record savedRecord = recordRepository.save(record);

        // Handle scheduling changes
        if (isCancelAlert) {
            // Alert schedule removed
            scheduleAlertService.cancelAlertsForRecord(record.getId());
        } else if (oldAlertSchedule == null && alertSchedule != null) {
            // New alert schedule added
            scheduleAlertService.scheduleAlert(savedRecord.getAlertSchedule());
        } else if (oldAlertSchedule != null && alertSchedule != null && !oldAlertSchedule.isSameAlert(alertSchedule)) {
            // Alert schedule modified
            scheduleAlertService.cancelAlertsForRecord(record.getId());
            scheduleAlertService.scheduleAlert(savedRecord.getAlertSchedule());
        }

        deleteRecFilesByPaths(pathsToBeDeleted);
        logger.info("Updated record. ID: {}, title: {}, createdBy: {}", record.getId(), record.getTitle(), record.getCreatedBy().getUsername());
        return savedRecord;
    }

    @Transactional
    public void deleteRecord(Record record) {
        List<Path> pathsToBeDeleted = record.getRecFiles().stream().map(RecFile::getPath).toList();
        scheduleAlertService.cancelAlertsForRecord(record.getId());
        recordRepository.delete(record);
        logger.info("Deleted record id {}, title: {}", record.getId(), record.getTitle());
        deleteRecFilesByPaths(pathsToBeDeleted);
    }

    private List<Label> createLabelsIfNotExistThenGet(List<String> labelNames, User user, boolean createCurrentDateLabel) {
        if (createCurrentDateLabel) {
            String curDate = dateTimeUtils.getCurrentDateString();
            if (!labelNames.contains(curDate)) {
                labelNames.add(curDate);
            }
        }
        List<Label> existingLabels = labelRepository.findAllByLabelNameIn(labelNames);
        Set<String> existingLabelNames = existingLabels.stream().map(Label::getLabelName).collect(Collectors.toSet());
        List<Label> nonExistingLabels = labelNames
                .stream()
                .filter(labelName -> !existingLabelNames.contains(labelName))
                .distinct()
                .map(labelName -> new Label(labelName, user, dateTimeUtils.isValidDateString(labelName) ? LabelType.DATE : LabelType.REGULAR))
                .toList();
        List<Label> resultLabels = labelRepository.saveAll(nonExistingLabels);
        resultLabels.addAll(existingLabels);
        return resultLabels;
    }

    public Page<Record> listRecordsCoreDataWithFilter(List<String> labels, List<String> excludeLabels,
                                                      String title,
                                                     LocalDate creationAfterDate, LocalDate creationBeforeDate,
                                                     LocalDate modifiedAfterDate, LocalDate modifiedBeforeDate,
                                                     Boolean isPublic, Boolean isCreatedByUserOnly,
                                                      Integer pageSize, Integer pageNum, String sortBy, User user) {

        if (labels != null && labels.isEmpty()) {
            labels = null;
        }
        if (excludeLabels != null && excludeLabels.isEmpty()) {
            excludeLabels = null;
        }
        if (pageSize == null) {
            pageSize = 10;
        }
        if (pageNum == null) {
            pageNum = 0;
        }
        if (isCreatedByUserOnly == null) {
            isCreatedByUserOnly = false;
        }
        Pageable page = PageRequest.of(pageNum, pageSize, parseSortByForRecords(sortBy));
        ZonedDateTime creationAfterDateTime = creationAfterDate == null ? null : dateTimeUtils.getZonedDateTimeFromString(dateTimeUtils.convertLocalDateToString(creationAfterDate), true);
        ZonedDateTime creationBeforeDateTime = creationBeforeDate == null ? null : dateTimeUtils.getZonedDateTimeFromString(dateTimeUtils.convertLocalDateToString(creationBeforeDate), false);
        ZonedDateTime modifiedAfterDateTime = modifiedAfterDate == null ? null : dateTimeUtils.getZonedDateTimeFromString(dateTimeUtils.convertLocalDateToString(modifiedAfterDate), true);
        ZonedDateTime modifiedBeforeDateTime = modifiedBeforeDate == null ? null : dateTimeUtils.getZonedDateTimeFromString(dateTimeUtils.convertLocalDateToString(modifiedBeforeDate), false);

        return recordRepository.filterRecords(labels,
                labels == null ? 0 : (long)labels.size(),
                excludeLabels,
                title,
                creationAfterDateTime, creationBeforeDateTime, modifiedAfterDateTime, modifiedBeforeDateTime,
                isPublic, isCreatedByUserOnly,
                user.getId(), user.isAdmin(), page);
    }

    private Sort parseSortByForRecords(String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            sortBy = "title";
        }
        String[] sortByItems = sortBy.split("\\|");
        List<Sort.Order> sortOrders = new ArrayList<>();
        for (String sortByItem : sortByItems) {
            if ("title".equalsIgnoreCase(sortByItem)) {
                sortOrders.add(Sort.Order.asc("title"));
            } else if ("title_r".equalsIgnoreCase(sortByItem)) {
                sortOrders.add(Sort.Order.desc("title"));
            } else if ("creationTime".equalsIgnoreCase(sortByItem)) {
                sortOrders.add(Sort.Order.desc("creationTime"));
            } else if ("creationTime_r".equalsIgnoreCase(sortByItem)) {
                sortOrders.add(Sort.Order.asc("creationTime"));
            } else if ("lastModifiedTime".equalsIgnoreCase(sortByItem)) {
                sortOrders.add(Sort.Order.desc("lastModifiedTime"));
            } else if ("lastModifiedTime_r".equalsIgnoreCase(sortByItem)) {
                sortOrders.add(Sort.Order.asc("lastModifiedTime"));
            }
        }
        return Sort.by(sortOrders);
    }

    private List<Path> getRecFilePathsByIDs(List<Long> fileIDs) {
        return recFileRepository.findAllById(fileIDs).stream()
                .map(RecFile::getPath)
                .toList();
    }

    private void deleteRecFilesByPaths(List<Path> pathsToBeDeleted) {
        if (pathsToBeDeleted == null || pathsToBeDeleted.isEmpty()) {
            return ;
        }
        for (Path deletePath : pathsToBeDeleted) {
            try {
                Files.deleteIfExists(deletePath);
                logger.info("Deleted file {}", deletePath);
            } catch (IOException e) {
                logger.warn("Failed to delete file {} \n with exception {}", deletePath, e.getMessage());
            }
        }
    }
}
