package com.yipeng.recorder.service;

import com.yipeng.recorder.job.BuiltInJobDefinition;
import com.yipeng.recorder.job.BuiltInJobDefinitions;
import com.yipeng.recorder.model.ScheduledJobConfig;
import com.yipeng.recorder.repository.ScheduledJobConfigRepository;
import com.yipeng.recorder.utils.JobScheduleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

@Service
public class BuiltInJobSeeder {

    private static final Logger logger = LoggerFactory.getLogger(BuiltInJobSeeder.class);
    private static final Set<String> OBSOLETE_BUILT_IN_JOB_KEYS = Set.of("option-partition-check");

    private final ScheduledJobConfigRepository scheduledJobConfigRepository;

    public BuiltInJobSeeder(ScheduledJobConfigRepository scheduledJobConfigRepository) {
        this.scheduledJobConfigRepository = scheduledJobConfigRepository;
    }

    @Transactional
    public void seedBuiltInJobs() {
        int inserted = 0;
        int reset = 0;
        List<BuiltInJobDefinition> definitions = BuiltInJobDefinitions.all();
        ZonedDateTime now = ZonedDateTime.now();
        for (BuiltInJobDefinition definition : definitions) {
            ScheduledJobConfig config = scheduledJobConfigRepository.findByJobKey(definition.jobKey())
                    .orElseGet(ScheduledJobConfig::new);
            boolean isNew = config.getId() == null;
            applyDefinition(config, definition, now);
            scheduledJobConfigRepository.save(config);
            if (isNew) {
                inserted++;
            } else {
                reset++;
            }
        }
        int disabledObsolete = disableObsoleteBuiltIns();
        logger.info("Built-in job seeding completed. Inserted: {}, reset: {}, total definitions: {}",
                inserted, reset, definitions.size());
        if (disabledObsolete > 0) {
            logger.info("Disabled {} obsolete built-in job definition(s).", disabledObsolete);
        }
    }

    private int disableObsoleteBuiltIns() {
        int disabled = 0;
        for (String jobKey : OBSOLETE_BUILT_IN_JOB_KEYS) {
            ScheduledJobConfig config = scheduledJobConfigRepository.findByJobKey(jobKey).orElse(null);
            if (config != null && config.isBuiltin()) {
                config.setEnabled(false);
                config.setNextRunAt(null);
                scheduledJobConfigRepository.save(config);
                disabled++;
            }
        }
        return disabled;
    }

    private void applyDefinition(ScheduledJobConfig config, BuiltInJobDefinition definition, ZonedDateTime now) {
        config.setJobKey(definition.jobKey());
        config.setDisplayName(definition.displayName());
        config.setJobType(definition.jobType());
        config.setEnabled(definition.enabled());
        config.setScheduleType(definition.scheduleType());
        config.setCronExpression(definition.cronExpression());
        config.setIntervalSeconds(definition.intervalSeconds());
        config.setTimezone(definition.timezone());
        config.setParametersJson(definition.parametersJson());
        config.setMaxRuntimeSeconds(definition.maxRuntimeSeconds());
        config.setRetryCount(definition.retryCount());
        config.setRetryDelaySeconds(definition.retryDelaySeconds());
        config.setAllowConcurrentRuns(definition.allowConcurrentRuns());
        config.setBuiltin(true);
        config.setDefaultDefinitionVersion(definition.defaultDefinitionVersion());
        config.setDescription(definition.description());
        config.setNextRunAt(computeNextRunAt(config, now));
    }

    public ZonedDateTime computeNextRunAt(ScheduledJobConfig config, ZonedDateTime from) {
        if (!config.isEnabled() || config.getScheduleType() == JobScheduleType.MANUAL) {
            return null;
        }
        ZoneId zoneId = ZoneId.of(config.getTimezone() == null ? BuiltInJobDefinitions.DEFAULT_TIMEZONE : config.getTimezone());
        ZonedDateTime zonedFrom = from.withZoneSameInstant(zoneId);
        if (config.getScheduleType() == JobScheduleType.CRON) {
            CronExpression cronExpression = CronExpression.parse(config.getCronExpression());
            return cronExpression.next(zonedFrom);
        }
        if (config.getScheduleType() == JobScheduleType.FIXED_INTERVAL && config.getIntervalSeconds() != null) {
            return zonedFrom.plusSeconds(config.getIntervalSeconds());
        }
        return null;
    }
}
