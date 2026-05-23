<template>
  <section class="admin-tools">
    <header class="page-header">
      <div>
        <h1>Admin Tools</h1>
      </div>
      <base-button mode="primary" :disabled="isLoading" @click="loadDashboard">
        Refresh
      </base-button>
    </header>

    <div v-if="message" class="banner" :class="messageType">{{ message }}</div>

    <section class="status-grid">
      <article v-for="panel in statusPanels" :key="panel.key" class="status-panel">
        <span class="panel-label">{{ panel.label }}</span>
        <strong :class="['status-text', panel.statusClass]">{{ panel.status }}</strong>
        <small>{{ panel.summary }}</small>
      </article>
    </section>

    <section class="section-block">
      <div class="section-header">
        <h2>Status Checks</h2>
        <div class="section-actions">
          <base-button mode="flat" :disabled="isRunningStatusChecks" @click="runAllStatusChecks">
            {{ isRunningStatusChecks ? 'Running...' : 'Run All' }}
          </base-button>
        </div>
      </div>

      <div class="job-table-wrap">
        <table class="job-table">
          <colgroup>
            <col class="job-col">
            <col class="schedule-col">
            <col class="date-col">
            <col class="date-col">
            <col class="status-col">
            <col class="actions-col">
          </colgroup>
          <thead>
            <tr>
              <th>Job</th>
              <th>Schedule</th>
              <th>Next Run</th>
              <th>Last Run</th>
              <th>Last Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <template v-for="group in statusJobGroups" :key="group.groupKey">
              <tr>
                <td>
                  <strong>{{ group.displayName }}</strong>
                  <small>{{ group.jobType }}</small>
                </td>
                <td>
                  <div class="schedule-list">
                    <button
                      v-for="job in group.jobs"
                      :key="job.id"
                      type="button"
                      class="schedule-chip"
                      :class="{ disabled: !job.enabled }"
                      @click="editJob(job)"
                    >
                      {{ scheduleText(job) }}
                    </button>
                  </div>
                </td>
                <td>{{ formatDate(group.nextRunAt) }}</td>
                <td>{{ formatDate(group.latestRunAt) }}</td>
                <td>
                  <span :class="['status-pill', statusClass(displayStatus(group))]">
                    {{ displayStatus(group) || 'No runs' }}
                  </span>
                </td>
                <td class="row-actions">
                  <button
                    type="button"
                    :class="{ spinning: isGroupTriggering(group) }"
                    :disabled="isGroupTriggering(group)"
                    @click="runGroup(group)"
                  >
                    {{ isGroupTriggering(group) ? 'Starting' : 'Run' }}
                  </button>
                  <button type="button" @click="toggleHistory(group.groupKey)">
                    History
                  </button>
                  <button
                    v-for="job in group.deletableJobs"
                    :key="`delete-${job.id}`"
                    type="button"
                    @click="removeJob(job)"
                  >
                    Delete
                  </button>
                </td>
              </tr>
              <tr v-if="expandedHistoryGroupKey === group.groupKey" class="history-row">
                <td colspan="6">
                  <div class="history-list">
                    <button
                      v-for="execution in group.history"
                      :key="execution.id"
                      type="button"
                      class="history-item"
                      @click="toggleExecution(execution.id)"
                    >
                      <span>#{{ execution.id }} · {{ execution.jobKey }}</span>
                      <span>{{ formatDate(execution.createdAt) }}</span>
                      <span :class="['status-pill', statusClass(execution.status)]">{{ execution.status }}</span>
                    </button>
                  </div>
                  <pre v-if="selectedExecutionForGroup(group)">{{ prettyExecution(selectedExecutionForGroup(group)) }}</pre>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </section>

    <section class="section-block">
      <div class="section-header">
        <h2>Data Update Jobs</h2>
      </div>

      <div class="job-table-wrap">
        <table class="job-table">
          <colgroup>
            <col class="job-col">
            <col class="schedule-col">
            <col class="date-col">
            <col class="date-col">
            <col class="status-col">
            <col class="actions-col">
          </colgroup>
          <thead>
            <tr>
              <th>Job</th>
              <th>Schedules</th>
              <th>Next Run</th>
              <th>Last Run</th>
              <th>Last Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <template v-for="group in dataUpdateJobGroups" :key="group.groupKey">
              <tr>
                <td>
                  <strong>{{ group.displayName }}</strong>
                  <small>{{ group.jobType }}</small>
                </td>
                <td>
                  <div class="schedule-list">
                    <button
                      v-for="job in group.jobs"
                      :key="job.id"
                      type="button"
                      class="schedule-chip"
                      :class="{ disabled: !job.enabled }"
                      @click="editJob(job)"
                    >
                      {{ scheduleText(job) }}
                    </button>
                  </div>
                </td>
                <td>{{ formatDate(group.nextRunAt) }}</td>
                <td>{{ formatDate(group.latestRunAt) }}</td>
                <td>
                  <span :class="['status-pill', statusClass(displayStatus(group))]">
                    {{ displayStatus(group) || 'No runs' }}
                  </span>
                </td>
                <td class="row-actions">
                  <button
                    type="button"
                    :class="{ spinning: isGroupTriggering(group) }"
                    :disabled="isGroupTriggering(group)"
                    @click="runGroup(group)"
                  >
                    {{ isGroupTriggering(group) ? 'Starting' : 'Run' }}
                  </button>
                  <button type="button" @click="toggleHistory(group.groupKey)">
                    History
                  </button>
                  <button
                    v-for="job in group.deletableJobs"
                    :key="`delete-${job.id}`"
                    type="button"
                    @click="removeJob(job)"
                  >
                    Delete
                  </button>
                </td>
              </tr>
              <tr v-if="expandedHistoryGroupKey === group.groupKey" class="history-row">
                <td colspan="6">
                  <div class="history-list">
                    <button
                      v-for="execution in group.history"
                      :key="execution.id"
                      type="button"
                      class="history-item"
                      @click="toggleExecution(execution.id)"
                    >
                      <span>#{{ execution.id }} · {{ execution.jobKey }}</span>
                      <span>{{ formatDate(execution.createdAt) }}</span>
                      <span :class="['status-pill', statusClass(execution.status)]">{{ execution.status }}</span>
                    </button>
                  </div>
                  <pre v-if="selectedExecutionForGroup(group)">{{ prettyExecution(selectedExecutionForGroup(group)) }}</pre>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="otherJobGroups.length" class="section-block">
      <div class="section-header">
        <h2>Other Jobs</h2>
      </div>

      <div class="job-table-wrap">
        <table class="job-table">
          <colgroup>
            <col class="job-col">
            <col class="schedule-col">
            <col class="date-col">
            <col class="date-col">
            <col class="status-col">
            <col class="actions-col">
          </colgroup>
          <thead>
            <tr>
              <th>Job</th>
              <th>Schedules</th>
              <th>Next Run</th>
              <th>Last Run</th>
              <th>Last Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <template v-for="group in otherJobGroups" :key="group.groupKey">
              <tr>
                <td>
                  <strong>{{ group.displayName }}</strong>
                  <small>{{ group.jobType }}</small>
                </td>
                <td>
                  <div class="schedule-list">
                    <button
                      v-for="job in group.jobs"
                      :key="job.id"
                      type="button"
                      class="schedule-chip"
                      :class="{ disabled: !job.enabled }"
                      @click="editJob(job)"
                    >
                      {{ scheduleText(job) }}
                    </button>
                  </div>
                </td>
                <td>{{ formatDate(group.nextRunAt) }}</td>
                <td>{{ formatDate(group.latestRunAt) }}</td>
                <td>
                  <span :class="['status-pill', statusClass(displayStatus(group))]">
                    {{ displayStatus(group) || 'No runs' }}
                  </span>
                </td>
                <td class="row-actions">
                  <button
                    type="button"
                    :class="{ spinning: isGroupTriggering(group) }"
                    :disabled="isGroupTriggering(group)"
                    @click="runGroup(group)"
                  >
                    {{ isGroupTriggering(group) ? 'Starting' : 'Run' }}
                  </button>
                  <button type="button" @click="toggleHistory(group.groupKey)">
                    History
                  </button>
                  <button
                    v-for="job in group.deletableJobs"
                    :key="`delete-${job.id}`"
                    type="button"
                    @click="removeJob(job)"
                  >
                    Delete
                  </button>
                </td>
              </tr>
              <tr v-if="expandedHistoryGroupKey === group.groupKey" class="history-row">
                <td colspan="6">
                  <div class="history-list">
                    <button
                      v-for="execution in group.history"
                      :key="execution.id"
                      type="button"
                      class="history-item"
                      @click="toggleExecution(execution.id)"
                    >
                      <span>#{{ execution.id }} · {{ execution.jobKey }}</span>
                      <span>{{ formatDate(execution.createdAt) }}</span>
                      <span :class="['status-pill', statusClass(execution.status)]">{{ execution.status }}</span>
                    </button>
                  </div>
                  <pre v-if="selectedExecutionForGroup(group)">{{ prettyExecution(selectedExecutionForGroup(group)) }}</pre>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="editingJob" class="section-block edit-block">
      <div class="section-header">
        <h2>Edit Schedule</h2>
        <button type="button" class="link-button" @click="cancelEdit">Cancel</button>
      </div>

      <form class="job-form" @submit.prevent="saveJob">
        <label>
          Job Key
          <input v-model.trim="editingJob.jobKey" disabled required>
        </label>
        <label>
          Display Name
          <input v-model.trim="editingJob.displayName" disabled required>
        </label>
        <label>
          Job Type
          <select v-model="editingJob.jobType" disabled required>
            <option v-for="type in jobTypes" :key="type" :value="type">{{ type }}</option>
          </select>
        </label>
        <label>
          Schedule Type
          <select v-model="editingJob.scheduleType" disabled required>
            <option value="MANUAL">MANUAL</option>
            <option value="CRON">CRON</option>
            <option value="FIXED_INTERVAL">FIXED_INTERVAL</option>
          </select>
        </label>
        <label>
          Enabled
          <select v-model="editingJob.enabled">
            <option :value="true">true</option>
            <option :value="false">false</option>
          </select>
        </label>
        <label>
          Cron Expression
          <input v-model.trim="editingJob.cronExpression" placeholder="0 0 8 * * *">
        </label>
        <label>
          Interval Seconds
          <input v-model.number="editingJob.intervalSeconds" type="number" min="1">
        </label>
        <label>
          Max Runtime Seconds
          <input v-model.number="editingJob.maxRuntimeSeconds" type="number" min="1">
        </label>
        <label>
          Retry Count
          <input v-model.number="editingJob.retryCount" type="number" min="0">
        </label>
        <label>
          Retry Delay Seconds
          <input v-model.number="editingJob.retryDelaySeconds" type="number" min="0">
        </label>
        <label>
          Parameters JSON
          <textarea v-model="editingJob.parametersJson" rows="5" spellcheck="false"></textarea>
        </label>
        <label class="checkbox-row">
          <input v-model="editingJob.allowConcurrentRuns" type="checkbox">
          Allow concurrent runs
        </label>
        <div class="form-actions">
          <base-button mode="primary" :disabled="isSaving" type="submit">
            {{ isSaving ? 'Saving...' : 'Save' }}
          </base-button>
        </div>
      </form>
    </section>

  </section>
</template>

<script>
import {
  deleteJobConfig,
  getJobDashboard,
  triggerJob,
  updateJobConfig
} from '../api/AdminToolsService.js'

const DATA_HEALTH_JOB_KEYS = [
  'market-pulse-health-check',
  'llm-health-check',
  'stock-data-freshness-check',
  'option-data-freshness-check',
  'qdrant-consistency-check'
]

const STATUS_CHECK_JOB_TYPES = [
  'MARKET_PULSE_HEALTH_CHECK',
  'LLM_HEALTH_CHECK',
  'STOCK_DATA_FRESHNESS_CHECK',
  'OPTION_DATA_FRESHNESS_CHECK',
  'QDRANT_CONSISTENCY_CHECK'
]

const DATA_UPDATE_JOB_TYPES = [
  'STOCK_OPTION_DATA_UPDATE',
  'STOCK_DAILY_HISTORY_UPDATE',
  'STOCK_AFTER_CLOSE_REFRESH',
  'MARKET_NEWS_SUMMARY_RECORD',
  'COMBINE_EXPIRED_OPTION_PARQUET'
]

const HIDDEN_JOB_TYPES = [
  'QDRANT_RECORD_UPSERT',
  'QDRANT_RECORD_DELETE',
  'OPTION_PARTITION_CHECK'
]

const JOB_TYPE_LABELS = {
  MARKET_PULSE_HEALTH_CHECK: 'Market Pulse health check',
  LLM_HEALTH_CHECK: 'LLM health check',
  STOCK_DATA_FRESHNESS_CHECK: 'Stock data freshness check',
  OPTION_DATA_FRESHNESS_CHECK: 'Option data freshness check',
  QDRANT_CONSISTENCY_CHECK: 'Qdrant consistency check',
  QDRANT_DATAFIX: 'Qdrant datafix',
  STOCK_OPTION_DATA_UPDATE: 'Update day time option data',
  STOCK_DAILY_HISTORY_UPDATE: 'Update stock daily history',
  STOCK_AFTER_CLOSE_REFRESH: 'After-close stock data refresh',
  MARKET_NEWS_SUMMARY_RECORD: 'Update market news summary',
  COMBINE_EXPIRED_OPTION_PARQUET: 'Combine expired option parquet files',
  SERVER_STATUS_EMAIL: 'Server status email',
  JOB_EXECUTION_CLEANUP: 'Job execution cleanup'
}

export default {
  name: 'AdminToolsPage',
  data() {
    return {
      isLoading: false,
      isSaving: false,
      triggeringId: null,
      message: '',
      messageType: '',
      configs: [],
      latestByJobKey: {},
      historyByJobType: {},
      editingJob: null,
      expandedExecutionId: null,
      expandedHistoryGroupKey: null,
      optimisticStatusByJobType: {},
      triggeringByJobType: {},
      isRunningStatusChecks: false,
      jobTypes: [
        'MARKET_PULSE_HEALTH_CHECK',
        'LLM_HEALTH_CHECK',
        'SERVER_STATUS_EMAIL',
        'STOCK_OPTION_DATA_UPDATE',
        'STOCK_DAILY_HISTORY_UPDATE',
        'STOCK_AFTER_CLOSE_REFRESH',
        'MARKET_NEWS_SUMMARY_RECORD',
        'COMBINE_EXPIRED_OPTION_PARQUET',
        'JOB_EXECUTION_CLEANUP',
        'STOCK_DATA_FRESHNESS_CHECK',
        'OPTION_DATA_FRESHNESS_CHECK',
        'QDRANT_CONSISTENCY_CHECK'
      ]
    }
  },
  computed: {
    statusPanels() {
      return DATA_HEALTH_JOB_KEYS.map((key) => {
        const execution = this.latestByJobKey[key];
        const config = this.configs.find((job) => job.jobKey === key);
        const status = this.optimisticStatusByJobType[config?.jobType] || execution?.status || 'No runs';
        return {
          key,
          label: config?.displayName || key,
          status,
          statusClass: this.statusClass(status),
          summary: execution?.summary || (config?.enabled ? 'Waiting for first run.' : 'Disabled.')
        }
      });
    },
    visibleConfigs() {
      return this.configs.filter((job) => !HIDDEN_JOB_TYPES.includes(job.jobType));
    },
    groupedJobs() {
      const groups = new Map();
      this.visibleConfigs.forEach((job) => {
        const groupKey = job.jobType;
        if (!groups.has(groupKey)) {
          groups.set(groupKey, []);
        }
        groups.get(groupKey).push(job);
      });
      return Array.from(groups.entries())
        .map(([jobType, jobs]) => this.toJobGroup(jobType, jobs))
        .sort((a, b) => a.displayName.localeCompare(b.displayName));
    },
    statusJobGroups() {
      return this.groupedJobs.filter((group) => STATUS_CHECK_JOB_TYPES.includes(group.jobType));
    },
    dataUpdateJobGroups() {
      return this.groupedJobs.filter((group) => DATA_UPDATE_JOB_TYPES.includes(group.jobType));
    },
    otherJobGroups() {
      return this.groupedJobs.filter((group) =>
        !STATUS_CHECK_JOB_TYPES.includes(group.jobType)
        && !DATA_UPDATE_JOB_TYPES.includes(group.jobType)
      );
    }
  },
  mounted() {
    this.loadDashboard();
  },
  methods: {
    async loadDashboard() {
      this.isLoading = true;
      this.message = '';
      try {
        const dashboard = await getJobDashboard();
        this.configs = dashboard.configs || [];
        this.latestByJobKey = dashboard.latestByJobKey || {};
        this.historyByJobType = dashboard.historyByJobType || {};
      } catch (error) {
        this.showMessage('Failed to load admin job dashboard.', 'failed');
      } finally {
        this.isLoading = false;
      }
    },
    editJob(job) {
      this.editingJob = { ...job };
    },
    cancelEdit() {
      this.editingJob = null;
    },
    async saveJob() {
      if (!this.editingJob) return;
      this.isSaving = true;
      try {
        await updateJobConfig(this.editingJob.id, {
          enabled: this.editingJob.enabled,
          cronExpression: this.emptyToNull(this.editingJob.cronExpression),
          intervalSeconds: this.editingJob.intervalSeconds,
          parametersJson: this.editingJob.parametersJson,
          maxRuntimeSeconds: this.editingJob.maxRuntimeSeconds,
          retryCount: this.editingJob.retryCount,
          retryDelaySeconds: this.editingJob.retryDelaySeconds,
          allowConcurrentRuns: this.editingJob.allowConcurrentRuns
        });
        this.showMessage('Schedule updated.', 'success');
        this.cancelEdit();
        await this.loadDashboard();
      } catch (error) {
        this.showMessage('Failed to save schedule.', 'failed');
      } finally {
        this.isSaving = false;
      }
    },
    async runGroup(group, { quiet = false } = {}) {
      if (!group?.triggerJob) return;
      const jobType = group.jobType;
      const job = group.triggerJob;
      const startedAt = Date.now();
      this.triggeringId = job.id;
      this.triggeringByJobType = { ...this.triggeringByJobType, [jobType]: true };
      this.optimisticStatusByJobType = { ...this.optimisticStatusByJobType, [jobType]: 'STARTING' };
      try {
        const execution = await triggerJob(job.id);
        await this.waitAtLeast(startedAt, 1000);
        this.optimisticStatusByJobType = { ...this.optimisticStatusByJobType, [jobType]: execution.status || 'QUEUED' };
        this.addExecutionToHistory(jobType, execution);
        if (!quiet) {
          this.showMessage(`Job queued as execution #${execution.id}.`, 'success');
        }
        await this.pollExecution(execution.id, jobType);
        await this.loadDashboard();
      } catch (error) {
        await this.waitAtLeast(startedAt, 1000);
        this.optimisticStatusByJobType = { ...this.optimisticStatusByJobType, [jobType]: 'FAILED' };
        if (!quiet) {
          this.showMessage('Failed to trigger job.', 'failed');
        }
      } finally {
        this.triggeringId = null;
        const remainingStatuses = { ...this.optimisticStatusByJobType };
        const remainingTriggers = { ...this.triggeringByJobType };
        delete remainingStatuses[jobType];
        delete remainingTriggers[jobType];
        this.optimisticStatusByJobType = remainingStatuses;
        this.triggeringByJobType = remainingTriggers;
      }
    },
    async runAllStatusChecks() {
      this.isRunningStatusChecks = true;
      try {
        await Promise.all(this.statusJobGroups.map((group) => this.runGroup(group, { quiet: true })));
        this.showMessage('Status checks queued.', 'success');
      } catch (error) {
        this.showMessage('Failed to run all status checks.', 'failed');
      } finally {
        this.isRunningStatusChecks = false;
      }
    },
    async removeJob(job) {
      await deleteJobConfig(job.id);
      this.showMessage('Schedule deleted.', 'success');
      await this.loadDashboard();
    },
    async pollExecution(id, jobType) {
      for (let attempt = 0; attempt < 10; attempt += 1) {
        await new Promise((resolve) => setTimeout(resolve, 1500));
        await this.loadDashboard();
        const execution = (this.historyByJobType[jobType] || []).find((item) => item.id === id);
        if (execution && !['QUEUED', 'RUNNING', 'RETRYING'].includes(execution.status)) {
          this.optimisticStatusByJobType = { ...this.optimisticStatusByJobType, [jobType]: execution.status };
          return;
        }
      }
    },
    toggleExecution(id) {
      this.expandedExecutionId = this.expandedExecutionId === id ? null : id;
    },
    prettyExecution(execution) {
      return JSON.stringify({
        summary: execution.summary,
        details: this.parseJson(execution.detailsJson),
        errorType: execution.errorType,
        errorMessage: execution.errorMessage,
        retryStatus: execution.retryStatusSummary,
        parameters: this.parseJson(execution.parametersJson)
      }, null, 2);
    },
    parseJson(value) {
      if (!value) return {};
      try {
        return JSON.parse(value);
      } catch (error) {
        return value;
      }
    },
    displayStatus(group) {
      return this.optimisticStatusByJobType[group.jobType] || group.latestExecution?.status || '';
    },
    isGroupTriggering(group) {
      return Boolean(this.triggeringByJobType[group.jobType]);
    },
    toggleHistory(groupKey) {
      this.expandedHistoryGroupKey = this.expandedHistoryGroupKey === groupKey ? null : groupKey;
      this.expandedExecutionId = null;
    },
    selectedExecutionForGroup(group) {
      if (!this.expandedExecutionId) return null;
      return group.history.find((execution) => execution.id === this.expandedExecutionId) || null;
    },
    addExecutionToHistory(jobType, execution) {
      if (!execution) return;
      const current = this.historyByJobType[jobType] || [];
      const next = [execution, ...current.filter((item) => item.id !== execution.id)];
      this.historyByJobType = { ...this.historyByJobType, [jobType]: next };
      this.latestByJobKey = { ...this.latestByJobKey, [execution.jobKey]: execution };
    },
    waitAtLeast(startedAt, minimumMs) {
      const remaining = minimumMs - (Date.now() - startedAt);
      if (remaining <= 0) {
        return Promise.resolve();
      }
      return new Promise((resolve) => setTimeout(resolve, remaining));
    },
    toJobGroup(jobType, jobs) {
      const sortedJobs = [...jobs].sort((a, b) => {
        const aText = this.scheduleSortText(a);
        const bText = this.scheduleSortText(b);
        return aText.localeCompare(bText);
      });
      const latestExecution = sortedJobs
        .map((job) => this.latestByJobKey[job.jobKey])
        .filter(Boolean)
        .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))[0] || null;
      const enabledNextRuns = sortedJobs
        .map((job) => job.nextRunAt)
        .filter(Boolean)
        .sort((a, b) => new Date(a) - new Date(b));
      return {
        groupKey: jobType,
        jobType,
        displayName: JOB_TYPE_LABELS[jobType] || sortedJobs[0]?.displayName || jobType,
        jobs: sortedJobs,
        deletableJobs: sortedJobs.filter((job) => !job.builtin),
        triggerJob: sortedJobs.find((job) => job.enabled) || sortedJobs[0],
        history: this.historyByJobType[jobType] || [],
        latestExecution,
        latestRunAt: latestExecution?.startedAt || latestExecution?.createdAt || null,
        nextRunAt: enabledNextRuns[0] || null
      };
    },
    scheduleText(job) {
      if (job.scheduleType === 'CRON') return job.cronExpression || 'CRON';
      if (job.scheduleType === 'FIXED_INTERVAL') return `${job.intervalSeconds || '?'}s interval`;
      return 'Manual';
    },
    scheduleSortText(job) {
      return `${job.scheduleType || ''}:${job.cronExpression || ''}:${job.intervalSeconds || ''}:${job.jobKey || ''}`;
    },
    formatDate(value) {
      if (!value) return '-';
      return new Date(value).toLocaleString();
    },
    statusClass(status) {
      if (status === 'STARTING') return 'running';
      if (status === 'SUCCESS') return 'success';
      if (['FAILED', 'CANCELLED'].includes(status)) return 'failed';
      if (['RUNNING', 'QUEUED', 'RETRYING'].includes(status)) return 'running';
      return 'muted';
    },
    emptyToNull(value) {
      return value === '' ? null : value;
    },
    showMessage(text, type) {
      this.message = text;
      this.messageType = type;
    }
  }
}
</script>

<style scoped>
.admin-tools {
  max-width: 1120px;
  margin: 1rem auto;
  padding: 0 0.75rem 1.5rem;
  color: #102a43;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.page-header,
.section-header {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  align-items: center;
}

.page-header h1,
.section-header h2 {
  margin: 0;
  font-size: 1.05rem;
}

.page-header h1 {
  font-size: 1.35rem;
}

.section-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.banner {
  padding: 0.5rem 0.75rem;
  border-radius: 4px;
  font-weight: 700;
  font-size: 0.9rem;
}

.banner.success {
  background: #e3f8ef;
  color: #0f7b4d;
}

.banner.failed {
  background: #fde8e8;
  color: #d64045;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.5rem;
}

.status-panel,
.section-block {
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #fff;
}

.status-panel {
  padding: 0.55rem 0.65rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.panel-label,
.status-panel small,
.job-table small,
.history-item {
  color: #52606d;
}

.status-text.success {
  color: #0f7b4d;
}

.status-text.failed {
  color: #d64045;
}

.status-text.running {
  color: #1d4ed8;
}

.section-block {
  padding: 0.75rem;
}

.job-table-wrap {
  overflow-x: auto;
}

.job-table {
  width: 100%;
  min-width: 1040px;
  table-layout: fixed;
  border-collapse: collapse;
  margin-top: 0.5rem;
  font-size: 0.88rem;
}

.job-col {
  width: 23%;
}

.schedule-col {
  width: 25%;
}

.date-col {
  width: 14%;
}

.status-col {
  width: 10%;
}

.actions-col {
  width: 14%;
}

.job-table th,
.job-table td {
  text-align: left;
  padding: 0.45rem 0.45rem;
  border-bottom: 1px solid #e5e8ed;
  vertical-align: middle;
  overflow-wrap: anywhere;
}

.job-table th {
  text-align: left;
  padding: 0.35rem 0.45rem;
  border-bottom: 1px solid #e5e8ed;
  vertical-align: middle;
  color: #52606d;
  font-size: 0.78rem;
  text-transform: uppercase;
}

.job-table td strong,
.job-table td small {
  display: block;
}

.schedule-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  align-items: flex-start;
  max-width: 100%;
}

.schedule-chip {
  border: 1px solid #bcccdc;
  border-radius: 999px;
  background: #f8fafc;
  color: #102a43;
  padding: 0.2rem 0.45rem;
  cursor: pointer;
  font: inherit;
  font-size: 0.78rem;
  max-width: 100%;
  white-space: normal;
  overflow-wrap: anywhere;
  text-align: left;
}

.schedule-chip.disabled {
  background: #f1f5f9;
  color: #829ab1;
  text-decoration: line-through;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.row-actions button,
.link-button {
  border: 1px solid #bcccdc;
  border-radius: 4px;
  background: #f8fafc;
  color: #102a43;
  padding: 0.3rem 0.45rem;
  cursor: pointer;
  font-size: 0.82rem;
}

.row-actions button:disabled {
  cursor: wait;
  opacity: 0.75;
}

.row-actions button.spinning::before {
  content: "";
  display: inline-block;
  width: 0.7rem;
  height: 0.7rem;
  margin-right: 0.3rem;
  border: 2px solid #93c5fd;
  border-top-color: #1d4ed8;
  border-radius: 50%;
  vertical-align: -0.1rem;
  animation: spin 0.75s linear infinite;
}

.status-pill {
  display: inline-block;
  min-width: 4.8rem;
  text-align: center;
  border-radius: 999px;
  padding: 0.18rem 0.42rem;
  background: #e2e8f0;
  color: #475569;
  font-size: 0.72rem;
  font-weight: 700;
}

.status-pill.success {
  background: #dcfce7;
  color: #166534;
}

.status-pill.failed {
  background: #fee2e2;
  color: #991b1b;
}

.status-pill.running {
  background: #dbeafe;
  color: #1e40af;
}

.job-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.65rem;
  margin-top: 0.55rem;
}

.job-form label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
  color: #334e68;
}

.job-form input,
.job-form select,
.job-form textarea {
  border: 1px solid #bcccdc;
  border-radius: 4px;
  padding: 0.4rem;
  font: inherit;
}

.job-form textarea {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.checkbox-row {
  justify-content: center;
}

.checkbox-row input {
  width: auto;
}

.form-actions {
  display: flex;
  align-items: flex-end;
}

.history-row td {
  background: #f8fafc;
  padding: 0.45rem;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-height: 22rem;
  overflow-y: auto;
}

.history-item {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(14rem, 1fr) minmax(11rem, 0.8fr) auto;
  gap: 0.5rem;
  align-items: center;
  text-align: left;
  border: 1px solid #e5e8ed;
  border-radius: 4px;
  background: #fff;
  padding: 0.3rem 0.45rem;
  cursor: pointer;
  font-size: 0.8rem;
}

.history-row pre {
  margin: 0;
  padding: 0.55rem;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 4px;
  font-size: 0.78rem;
  max-height: 18rem;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 820px) {
  .page-header,
  .section-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .job-form {
    grid-template-columns: 1fr;
  }

  .section-actions {
    width: 100%;
  }

  .history-item {
    grid-template-columns: 1fr;
  }
}
</style>
