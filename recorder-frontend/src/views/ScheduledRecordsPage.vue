<template>
  <section class="scheduled-records-page">
    <header class="page-header">
      <h1>Scheduled Records</h1>
      <base-button mode="primary" :disabled="isLoading" @click="loadSchedules">
        Refresh
      </base-button>
    </header>

    <div v-if="message" class="banner">{{ message }}</div>

    <section v-for="section in scheduleSections" :key="section.key" class="section-block">
      <h2>{{ section.title }}</h2>
      <div class="schedule-table-wrap">
        <table class="schedule-table">
          <colgroup>
            <col class="record-col">
            <col class="schedule-col">
            <col class="author-col">
            <col class="status-col">
          </colgroup>
          <thead>
            <tr>
              <th>Record</th>
              <th>Schedule</th>
              <th>Author</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in section.items"
              :key="item.id"
              tabindex="0"
              @click="openRecord(item.recordId)"
              @keydown.enter="openRecord(item.recordId)"
            >
              <td>
                <strong>{{ item.recordTitle }}</strong>
                <small>#{{ item.recordId }}</small>
              </td>
              <td>{{ scheduleText(item) }}</td>
              <td>{{ item.authorUsername }}</td>
              <td>
                <span class="status-pill">Next {{ formatDate(item.nextRunAt) }}</span>
                <small v-if="item.lastSentAt">Last sent {{ formatDate(item.lastSentAt) }}</small>
              </td>
            </tr>
            <tr v-if="section.items.length === 0" class="empty-row">
              <td colspan="4">No active schedules.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>

<script>
import { APP_TIME_ZONE } from '../api/config.js'
import { getAlertSchedules } from '../api/RecordService.js'

export default {
  name: 'ScheduledRecordsPage',
  data() {
    return {
      isLoading: false,
      schedules: [],
      message: ''
    }
  },
  computed: {
    isAdmin() {
      return this.$store.getters['user/isAdmin'];
    },
    currentUsername() {
      return this.$store.state.user.username;
    },
    mySchedules() {
      if (!this.isAdmin) {
        return this.schedules;
      }
      if (!this.currentUsername) {
        return [];
      }
      return this.schedules.filter((item) => item.authorUsername === this.currentUsername);
    },
    otherSchedules() {
      if (!this.isAdmin) {
        return [];
      }
      if (!this.currentUsername) {
        return this.schedules;
      }
      return this.schedules.filter((item) => item.authorUsername !== this.currentUsername);
    },
    scheduleSections() {
      if (!this.isAdmin) {
        return [{ key: 'all', title: 'Schedules', items: this.mySchedules }];
      }
      return [
        { key: 'mine', title: 'My Schedules', items: this.mySchedules },
        { key: 'others', title: "Other Users' Schedules", items: this.otherSchedules }
      ];
    }
  },
  async mounted() {
    if (!this.$store.getters['user/isUserInfoLoaded']) {
      await this.$store.dispatch('user/loadUserInfo');
    }
    await this.loadSchedules();
  },
  methods: {
    async loadSchedules() {
      this.isLoading = true;
      this.message = '';
      try {
        this.schedules = await getAlertSchedules();
      } catch (error) {
        this.message = 'Failed to load scheduled records.';
      } finally {
        this.isLoading = false;
      }
    },
    formatDate(value) {
      if (!value) return '-';
      return new Date(value).toLocaleString([], { timeZone: APP_TIME_ZONE });
    },
    scheduleText(schedule) {
      if (schedule.alertType === 'ONE_TIME') {
        return `One time at ${this.formatDate(schedule.timeAt)}`;
      }
      const weekdays = (schedule.weekdays || '')
        .split(',')
        .map((day) => day.trim())
        .filter(Boolean)
        .join(', ');
      const time = schedule.timeAt
        ? new Date(schedule.timeAt).toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
          timeZone: APP_TIME_ZONE
        })
        : '9:00 AM';
      return `${weekdays || 'Recurring'} at ${time}`;
    },
    openRecord(recordId) {
      if (!recordId) return;
      this.$router.push(`/records/${recordId}`);
    }
  }
}
</script>

<style scoped>
.scheduled-records-page {
  max-width: 1120px;
  margin: 1rem auto;
  padding: 0 0.75rem 1.5rem;
  color: #102a43;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.page-header,
.section-block h2 {
  margin: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
}

.page-header h1 {
  margin: 0;
  font-size: 1.35rem;
}

.section-block {
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #fff;
  padding: 0.75rem;
}

.section-block h2 {
  font-size: 1.05rem;
}

.banner {
  padding: 0.5rem 0.75rem;
  border-radius: 4px;
  background: #fde8e8;
  color: #d64045;
  font-weight: 700;
}

.schedule-table-wrap {
  overflow-x: auto;
}

.schedule-table {
  width: 100%;
  min-width: 860px;
  table-layout: fixed;
  border-collapse: collapse;
  margin-top: 0.5rem;
  font-size: 0.9rem;
}

.record-col {
  width: 30%;
}

.schedule-col {
  width: 30%;
}

.author-col {
  width: 16%;
}

.status-col {
  width: 24%;
}

.schedule-table th,
.schedule-table td {
  text-align: left;
  padding: 0.5rem;
  border-bottom: 1px solid #e5e8ed;
  vertical-align: middle;
  overflow-wrap: anywhere;
}

.schedule-table th {
  color: #52606d;
  font-size: 0.78rem;
  text-transform: uppercase;
}

.schedule-table tbody tr:not(.empty-row) {
  cursor: pointer;
}

.schedule-table tbody tr:not(.empty-row):hover,
.schedule-table tbody tr:not(.empty-row):focus {
  background: #f8fafc;
  outline: none;
}

.schedule-table td strong,
.schedule-table td small {
  display: block;
}

.schedule-table small {
  color: #52606d;
}

.status-pill {
  display: inline-block;
  border-radius: 999px;
  padding: 0.2rem 0.5rem;
  background: #dbeafe;
  color: #1e40af;
  font-size: 0.76rem;
  font-weight: 700;
}

.empty-row td {
  color: #52606d;
}

@media (max-width: 760px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
