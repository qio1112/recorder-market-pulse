<template>
  <section class="record-detail" v-if="!isLoading && record">
    <header class="header">
      <h1>{{ record.title }}</h1>
      <div class="labels" v-if="record.labels && record.labels.length">
        <span
          v-for="label in record.labels"
          :key="label.labelName"
          class="chip"
        >
          {{ label.labelName }}
        </span>
      </div>
    </header>

    <article class="content">
      <h2>Content</h2>
      <p v-if="record.content">{{ record.content }}</p>
      <p v-else class="muted">No content</p>
    </article>

    <section class="meta-grid">
      <div>
        <h2>Created</h2>
        <p class="muted">{{ formatDateTime(record.creationTime) }}</p>
      </div>
      <div>
        <h2>Last modified</h2>
        <p class="muted">{{ formatDateTime(record.lastModifiedTime) }}</p>
      </div>
      <div>
        <h2>Author</h2>
        <p class="muted">{{ record.createdBy }}</p>
      </div>
      <div>
        <h2>Public</h2>
        <p class="muted">{{ record.public }}</p>
      </div>
    </section>

    <section class="alert" v-if="alertInfo">
      <h2>Alert</h2>
      <p>{{ alertInfo }}</p>
    </section>

    <section class="files" v-if="record.recFiles && record.recFiles.length">
      <h2>Files</h2>
      <div class="file-list">
        <div
          v-for="file in imageFiles"
          :key="`img-${file.fileID}`"
          class="file-card"
        >
          <img :src="file.url" :alt="file.filename" />
          <p class="file-name">{{ cleanFileName(file.filename) }}</p>
        </div>
        <div
          v-for="file in otherFiles"
          :key="`file-${file.fileID}`"
          class="file-card file-download"
        >
          <p class="file-name">{{ cleanFileName(file.filename) }}</p>
          <button type="button" @click="downloadFile(file)">Download</button>
        </div>
      </div>
    </section>
    <section class="metadata" v-if="metadataEntries.length">
      <h2>Metadata</h2>
      <div class="metadata-table">
        <div class="metadata-row" v-for="item in metadataEntries" :key="item.key">
          <span class="meta-key">{{ item.key }}</span>
          <span class="meta-value">{{ item.value }}</span>
        </div>
      </div>
    </section>
    <div class="actions">
      <base-button mode="primary" @click="editRecord">Edit Record</base-button>
      <base-button class="danger" mode="outline" @click="handleDeleteClick">
        {{ confirmDelete ? 'Click again to confirm delete' : 'Delete Record' }}
      </base-button>
    </div>
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
  </section>
  <p v-else-if="isLoading" class="muted">Loading record…</p>
  <p v-else class="muted">Record not found.</p>
</template>

<script>
import { getRecordDetail, getRecFile, deleteRecord } from '../api/RecordService.js'

export default {
  name: 'RecordDetailView',
  props: {
    recordID: {
      type: String,
      required: true
    }
  },
  data() {
    return {
      record: null,
      isLoading: false,
      imageFiles: [],
      otherFiles: [],
      errorMessage: '',
      confirmDelete: false
    }
  },
  computed: {
    alertInfo() {
      const alert = this.record?.alertSchedule;
      if (!alert) return null;
      const ts = alert.timeAt;
      const weekdayStr = alert.weekdays;
      const formattedTs = ts ? this.formatDateTime(ts) : null;

      if (alert.alertType === 'ONE_TIME') {
        return formattedTs ? `One-time alert at ${formattedTs}` : 'One-time alert';
      }
      if (alert.alertType === 'RECURRING') {
        const days = weekdayStr ? weekdayStr.split(',').join(', ') : 'selected days';
        const timeOnly = ts ? this.formatTimePart(ts) : null;
        return `Recurring alert on ${days}${timeOnly ? ` at ${timeOnly}` : ''}`;
      }
      return null;
    },
    metadataEntries() {
      const meta = this.record?.metadata;
      if (!meta || typeof meta !== 'object') return [];
      return Object.entries(meta).map(([key, value]) => ({ key, value }));
    }
  },
  watch: {
    recordID: {
      immediate: true,
      handler() {
        this.fetchRecord();
      }
    }
  },
  methods: {
    async fetchRecord() {
      this.isLoading = true;
      this.record = await getRecordDetail(this.recordID);
      await this.loadFiles();
      this.isLoading = false;
    },
    editRecord() {
      if (this.record?.id) {
        this.$router.push(`/edit-record/${this.record.id}`);
      }
    },
    async handleDeleteClick() {
      if (!this.record?.id) return;
      this.errorMessage = '';
      if (!this.confirmDelete) {
        this.confirmDelete = true;
        return;
      }
      const result = await deleteRecord(this.record.id);
      if (result) {
        alert(`Record "${this.record.title}" has been deleted.`);
        this.$router.replace('/records');
      } else {
        this.errorMessage = 'Failed to delete record. Please try again.';
      }
      this.confirmDelete = false;
    },
    async loadFiles() {
      this.cleanupObjectUrls();
      const files = this.record?.recFiles || [];
      const images = files.filter((f) => f.fileType === 'IMAGE').slice(0, 4);
      const others = files.filter((f) => f.fileType !== 'IMAGE');

      const imgResults = await Promise.all(
        images.map(async (f) => {
          const blob = await getRecFile(f.fileID);
          if (!blob) return null;
          const url = URL.createObjectURL(blob);
          return { ...f, url };
        })
      );

      this.imageFiles = imgResults.filter(Boolean);
      this.otherFiles = others;
    },
    cleanupObjectUrls() {
      this.imageFiles.forEach((f) => {
        if (f.url) URL.revokeObjectURL(f.url);
      });
      this.imageFiles = [];
    },
    async downloadFile(file) {
      this.errorMessage = '';
      try {
        const blob = await getRecFile(file.fileID);
        if (!blob) throw new Error('Download failed');
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = this.cleanFileName(file.filename) || 'download';
        link.click();
        URL.revokeObjectURL(url);
      } catch (e) {
        this.errorMessage = 'Failed to download file. Please try again.';
      }
    },
    cleanFileName(name) {
      if (!name) return '';
      const parts = String(name).split('__');
      return parts.length > 1 ? parts.slice(1).join('__') : name;
    },
    formatDateTime(iso) {
      try {
        return new Date(iso).toLocaleString();
      } catch (e) {
        return iso;
      }
    },
    formatTimePart(iso) {
      try {
        const date = new Date(iso);
        return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      } catch (e) {
        return null;
      }
    }
  }
}
</script>

<style scoped>
.record-detail {
  max-width: 960px;
  margin: 1.5rem auto;
  padding: 1rem 1.25rem;
  border: 1px solid #e5e8ed;
  border-radius: 10px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.header h1 {
  margin: 0 0 0.5rem 0;
  color: #102a43;
}

.labels {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.chip {
  background: #e5f3ff;
  color: #0f4c81;
  padding: 0.25rem 0.6rem;
  border-radius: 999px;
  font-size: 0.9rem;
}

.content,
.alert,
.metadata,
.files {
  border-top: 1px solid #e5e8ed;
  padding-top: 0.75rem;
}

h2 {
  margin: 0 0 0.35rem 0;
  font-size: 1.05rem;
  color: #102a43;
}

p {
  margin: 0;
  color: #243b53;
  line-height: 1.5;
}

.content p {
  white-space: pre-wrap;
}

.muted {
  color: #52606d;
}

.error {
  color: #d64045;
}

.metadata-table {
  border: 1px solid #e5e8ed;
  border-radius: 8px;
  max-height: 200px;
  overflow: auto;
}

.metadata-row {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 0.5rem;
  padding: 0.5rem 0.65rem;
  border-bottom: 1px solid #e5e8ed;
}

.metadata-row:last-child {
  border-bottom: none;
}

.meta-key {
  font-weight: 700;
  color: #102a43;
}

.meta-value {
  color: #52606d;
  word-break: break-word;
}

.actions {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}

:deep(button.danger) {
  margin-left: auto;
  border-color: #d64045;
  color: #d64045;
}

:deep(button.danger:hover),
:deep(button.danger:active) {
  background: #d64045;
  color: #fff;
}

.file-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.75rem;
}

.file-card {
  border: 1px solid #e5e8ed;
  border-radius: 10px;
  padding: 0.65rem;
  background: #f7fafc;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  align-items: center;
}

.file-card img {
  width: 100%;
  max-height: 220px;
  object-fit: contain;
  border-radius: 8px;
  background: #fff;
}

.file-name {
  word-break: break-word;
  text-align: center;
}

.file-download button {
  padding: 0.5rem 0.9rem;
  border-radius: 7px;
  border: 1px solid #cfd7e2;
  background: #0f4c81;
  color: #fff;
  cursor: pointer;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.75rem;
}
</style>
