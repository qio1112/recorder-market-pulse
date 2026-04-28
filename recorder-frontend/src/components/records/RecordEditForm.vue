<template>
  <form class="record-edit-form" @submit.prevent="submit">
    <div class="field field-wide">
      <label>Title</label>
      <input v-model="form.title" type="text" required />
    </div>

    <div class="field field-wide">
      <label>Labels</label>
      <div class="labels-row">
        <input
          v-model.trim="newLabel"
          type="text"
          placeholder="Add label"
          @keyup.enter.prevent="addLabel"
        />
        <button type="button" class="add-btn" @click="addLabel">Add</button>
        <label class="trade-check">
          <input type="checkbox" v-model="isTrade" />
          is_trade
        </label>
      </div>
      <div class="labels-list" v-if="form.labels.length">
        <span v-for="label in form.labels" :key="label" class="chip">
          {{ label }}
          <button type="button" class="chip-remove" @click="removeLabel(label)">×</button>
        </span>
      </div>
    </div>

    <div class="field field-wide">
      <label>Content</label>
      <textarea v-model="form.content" rows="16"></textarea>
    </div>

    <div class="field checkbox">
      <label><input type="checkbox" v-model="form.isPublic" /> Public</label>
    </div>

    <div class="field field-wide alert-field">
      <div class="alert-grid">
        <div class="alert-control">
          <label>Alert Type</label>
          <select v-model="form.alertType">
            <option :value="null">None</option>
            <option value="ONE_TIME">One-time</option>
            <option value="RECURRING">Recurring</option>
          </select>
        </div>
        <div class="alert-control" v-if="form.alertType">
          <label>Alert Time (ISO)</label>
          <input v-model="form.alertTime" type="text" placeholder="2025-10-11T17:04:15-04:00" />
        </div>
        <div class="alert-control alert-control-wide" v-if="form.alertType === 'RECURRING'">
          <label>Recurring Weekdays (comma-separated, e.g. MONDAY,TUESDAY,SATURDAY)</label>
          <input v-model="form.recurringAlertWeekDays" type="text" />
        </div>
      </div>
      <label class="inline-check" v-if="mode === 'edit'">
        <input type="checkbox" v-model="form.cancelAlert" />
        Cancel Alert
      </label>
    </div>

    <div class="field field-wide">
      <label>Images</label>
      <input ref="imageInput" type="file" accept="image/*" multiple @change="onImagesChange" />
      <div v-if="imagePreviews.length" class="image-preview-list">
        <div
          v-for="preview in imagePreviews"
          :key="preview.url"
          class="image-preview-card"
        >
          <button type="button" class="preview-remove" @click="removeImage(preview)">×</button>
          <img :src="preview.url" :alt="preview.name" />
          <p>{{ preview.name }}</p>
        </div>
      </div>
    </div>

    <div class="field field-wide">
      <label>Files</label>
      <input ref="fileInput" type="file" multiple @change="onFilesChange" />
      <div v-if="form.files.length" class="file-list">
        <label v-for="file in form.files" :key="fileKey(file)" class="file-row">
          <span class="file-row-main">
            <input type="checkbox" checked disabled />
            <span>{{ file.name }}</span>
          </span>
          <button type="button" class="file-remove" @click.prevent="removeFile(file)">×</button>
        </label>
      </div>
    </div>

    <div class="field field-wide">
      <label>Metadata</label>
      <div class="meta-rows">
        <div class="meta-row" v-for="(item, index) in metadataRows" :key="index">
          <input
            v-model="item.key"
            type="text"
            placeholder="Key"
            class="meta-input"
          />
          <input
            v-model="item.value"
            type="text"
            placeholder="Value"
            class="meta-input"
          />
          <button type="button" class="meta-remove" @click="removeMetadata(index)">×</button>
        </div>
      </div>
      <button type="button" class="add-btn" @click="addMetadata">Add metadata</button>
    </div>

    <div class="field field-wide file-removal-field" v-if="existingFiles.length">
      <label>Remove existing files</label>
      <div v-if="existingImageFiles.length" class="image-preview-list">
        <label
          v-for="file in existingImageFiles"
          :key="`existing-image-${file.fileID}`"
          class="image-preview-card existing-file-card"
        >
          <input
            class="existing-file-check"
            type="checkbox"
            :value="file.fileID"
            v-model="form.removeFileIDs"
          />
          <img :src="file.url" :alt="file.filename" />
          <p>{{ cleanFileName(file.filename) }}</p>
        </label>
      </div>
      <div v-if="existingOtherFiles.length" class="file-list">
        <label v-for="file in existingOtherFiles" :key="file.fileID" class="file-row">
          <span class="file-row-main">
            <input
              type="checkbox"
              :value="file.fileID"
              v-model="form.removeFileIDs"
            />
            <span>{{ cleanFileName(file.filename) }}</span>
          </span>
        </label>
      </div>
    </div>

    <div class="form-actions">
      <base-button mode="primary" type="submit">
        {{ mode === 'edit' ? 'Save Changes' : 'Create Record' }}
      </base-button>
    </div>
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
  </form>
</template>

<script>
import { getRecFile } from '../../api/RecordService.js'

export default {
  name: 'RecordEditForm',
  emits: ['submit'],
  props: {
    initialRecord: {
      type: Object,
      default: () => ({})
    },
    mode: {
      type: String,
      default: 'create'
    },
    errorMessage: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      form: {
        title: this.initialRecord.title || '',
        labels: this.initialRecord.labels ? this.initialRecord.labels.map((l) => l.labelName) : [],
        content: this.initialRecord.content || '',
        isPublic: this.initialRecord.isPublic ?? false,
        alertType: this.initialRecord.alertSchedule?.alertType || null,
        alertTime: this.initialRecord.alertSchedule?.timeAt || null,
        recurringAlertWeekDays: this.initialRecord.alertSchedule?.weekdays || null,
        images: [],
        files: [],
        removeFileIDs: [],
        cancelAlert: this.initialRecord.cancelAlert ?? false
      },
      newLabel: '',
      isTrade: this.initialRecord.labels
        ? this.initialRecord.labels.some((l) => l.labelName === 'INVESTMENT_REC' || l === 'INVESTMENT_REC')
        : false,
      imagePreviews: [],
      existingImageFiles: [],
      metadataRows: this.initialRecord.metadata
        ? Object.entries(this.initialRecord.metadata).map(([key, value]) => ({ key, value }))
        : []
    }
  },
  mounted() {
    this.loadExistingImageFiles();
  },
  beforeUnmount() {
    this.cleanupImagePreviews();
    this.cleanupExistingImageFiles();
  },
  computed: {
    existingFiles() {
      return this.initialRecord.recFiles || [];
    },
    existingOtherFiles() {
      return this.existingFiles.filter((file) => file.fileType !== 'IMAGE');
    }
  },
  watch: {
    'form.labels': {
      deep: true,
      handler() {
        this.isTrade = this.form.labels.includes('INVESTMENT_REC');
      }
    },
    isTrade(val) {
      if (val) {
        if (!this.form.labels.includes('INVESTMENT_REC')) {
          this.form.labels.push('INVESTMENT_REC');
        }
        this.ensureTradeMetadata();
      } else {
        this.form.labels = this.form.labels.filter((l) => l !== 'INVESTMENT_REC');
      }
    }
  },
  methods: {
    onImagesChange(e) {
      this.form.images = Array.from(e.target.files).filter((file) => file.type.startsWith('image/'));
      e.target.value = '';
      this.refreshImagePreviews();
    },
    onFilesChange(e) {
      this.form.files = Array.from(e.target.files);
      e.target.value = '';
    },
    refreshImagePreviews() {
      this.cleanupImagePreviews();
      this.imagePreviews = this.form.images.map((file) => ({
        name: file.name,
        file,
        url: URL.createObjectURL(file)
      }));
    },
    cleanupImagePreviews() {
      this.imagePreviews.forEach((preview) => {
        URL.revokeObjectURL(preview.url);
      });
      this.imagePreviews = [];
    },
    async loadExistingImageFiles() {
      this.cleanupExistingImageFiles();
      const imageFiles = this.existingFiles.filter((file) => file.fileType === 'IMAGE');
      const previews = await Promise.all(
        imageFiles.map(async (file) => {
          const blob = await getRecFile(file.fileID);
          if (!blob) return null;
          return {
            ...file,
            url: URL.createObjectURL(blob)
          };
        })
      );
      this.existingImageFiles = previews.filter(Boolean);
    },
    cleanupExistingImageFiles() {
      this.existingImageFiles.forEach((file) => {
        if (file.url) URL.revokeObjectURL(file.url);
      });
      this.existingImageFiles = [];
    },
    removeImage(preview) {
      this.form.images = this.form.images.filter((file) => file !== preview.file);
      URL.revokeObjectURL(preview.url);
      this.imagePreviews = this.imagePreviews.filter((item) => item !== preview);
    },
    removeFile(fileToRemove) {
      this.form.files = this.form.files.filter((file) => file !== fileToRemove);
    },
    fileKey(file) {
      return `${file.name}-${file.size}-${file.lastModified}`;
    },
    addLabel() {
      const value = this.newLabel.trim();
      if (!value || this.form.labels.includes(value)) return;
      this.form.labels.push(value);
      this.newLabel = '';
    },
    removeLabel(label) {
      this.form.labels = this.form.labels.filter((l) => l !== label);
      if (label === 'INVESTMENT_REC') {
        this.isTrade = false;
      }
    },
    addMetadata() {
      this.metadataRows.push({ key: '', value: '' });
    },
    removeMetadata(index) {
      this.metadataRows.splice(index, 1);
    },
    ensureTradeMetadata() {
      const requiredKeys = ['symbol', 'action', 'price', 'shares', 'date'];
      const existing = new Set(this.metadataRows.map((m) => (m.key || '').toLowerCase()));
      requiredKeys.forEach((key) => {
        if (!existing.has(key)) {
          this.metadataRows.push({ key, value: '' });
        }
      });
    },
    cleanFileName(name) {
      if (!name) return '';
      const parts = String(name).split('__');
      return parts.length > 1 ? parts.slice(1).join('__') : name;
    },
    submit() {
      const metadata = {};
      this.metadataRows.forEach(({ key, value }) => {
        if (key) {
          metadata[key] = value ?? '';
        }
      });
      this.$emit('submit', { ...this.form, metadata });
    }
  }
}
</script>

<style scoped>
.record-edit-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 1.15rem;
  padding: 1.25rem;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.06);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.field.checkbox,
.file-removal-field {
  padding: 0.8rem;
  border: 1px solid #e5e8ed;
  border-radius: 8px;
  background: #f8fafc;
}

.alert-field {
  padding: 0.8rem;
  border: 1px solid #e5e8ed;
  border-radius: 8px;
  background: #f8fafc;
}

.alert-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}

.alert-control {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.alert-control-wide {
  grid-column: 1 / -1;
}

.image-preview-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.7rem;
  margin-top: 0.45rem;
}

.image-preview-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.5rem;
  border: 1px solid #e5e8ed;
  border-radius: 8px;
  background: #f8fafc;
}

.image-preview-card img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 6px;
  background: #fff;
}

.image-preview-card p {
  margin: 0;
  font-size: 0.72rem;
  color: #52606d;
  word-break: break-word;
}

.preview-remove,
.file-remove {
  border: 1px solid #f0c7c1;
  background: #fff5f3;
  color: #b42318;
  cursor: pointer;
}

.preview-remove {
  position: absolute;
  top: 0.45rem;
  right: 0.45rem;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 1.6rem;
  height: 1.6rem;
  padding: 0;
  border-radius: 999px;
  font-size: 1rem;
  line-height: 1;
}

.preview-remove:hover,
.file-remove:hover {
  background: #fdecea;
}

.existing-file-card {
  cursor: pointer;
}

.existing-file-card:has(.existing-file-check:checked) {
  border-color: #f0a89b;
  background: #fff7f5;
}

.existing-file-check {
  position: absolute;
  top: 0.55rem;
  left: 0.55rem;
  z-index: 1;
}

@media (max-width: 900px) {
  .image-preview-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .image-preview-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .alert-grid {
    grid-template-columns: 1fr;
  }
}

label {
  font-weight: 600;
  font-size: 0.82rem;
  color: #1f2933;
}

input,
textarea,
select {
  width: 100%;
  box-sizing: border-box;
  padding: 0.65rem 0.75rem;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: #ffffff;
  color: #1f2933;
  font: inherit;
  font-size: 0.9rem;
  line-height: 1.45;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

textarea {
  min-height: 24rem;
  resize: vertical;
}

input:focus,
textarea:focus,
select:focus {
  outline: none;
  border-color: #2f80ed;
  box-shadow: 0 0 0 3px rgba(47, 128, 237, 0.14);
}

input[type="checkbox"] {
  width: auto;
  padding: 0;
  accent-color: #1f6feb;
}

input[type="file"] {
  padding: 0.6rem;
  background: #f8fafc;
}

.checkbox {
  flex-direction: row;
  align-items: center;
  gap: 0.5rem;
}

.labels-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.labels-row input[type="text"] {
  flex: 1 1 220px;
}

.trade-check {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  color: #1f2933;
  font-size: 0.85rem;
}

.inline-check {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.15rem;
  width: fit-content;
  color: #1f2933;
  font-size: 0.85rem;
}

.add-btn {
  flex: 0 0 auto;
  padding: 0.6rem 0.9rem;
  border-radius: 7px;
  border: 1px solid #b8c4d4;
  background: #f8fafc;
  color: #1f2933;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 600;
}

.add-btn:hover {
  background: #eef4fb;
}

.labels-list {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.3rem 0.55rem;
  border-radius: 999px;
  background: #eef6ff;
  color: #0f4c81;
  border: 1px solid #cfe7ff;
  font-size: 0.8rem;
}

.chip-remove {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 0.6rem;
  line-height: 1;
}

.meta-rows {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.meta-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 0.5rem;
  align-items: center;
}

.meta-input {
  padding: 0.45rem 0.6rem;
}

.meta-remove {
  border: 1px solid #cfd7e2;
  background: #fdecea;
  color: #c0392b;
  border-radius: 6px;
  cursor: pointer;
  width: 32px;
  height: 32px;
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 0.45rem;
}

.file-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.65rem 0.75rem;
  border: 1px solid #e5e8ed;
  border-radius: 8px;
  background: #f8fafc;
  font-size: 0.85rem;
}

.file-row-main {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 0.5rem;
}

.file-row-main span {
  overflow-wrap: anywhere;
}

.file-remove {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 1.8rem;
  height: 1.8rem;
  padding: 0;
  border-radius: 6px;
  font-size: 1rem;
  line-height: 1;
}

.checkbox-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.45rem;
}

.checkbox-item {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.error {
  color: #d64045;
  margin: 0;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 0.35rem;
}

.form-actions :deep(.primary) {
  min-width: 10rem;
  padding: 0.65rem 1rem;
  border-radius: 7px;
  border-color: #0f6abf;
  background: #0f6abf;
  font-size: 0.9rem;
  font-weight: 700;
  line-height: 1.2;
  text-align: center;
}

.form-actions :deep(.primary:hover),
.form-actions :deep(.primary:active) {
  border-color: #0b579f;
  background: #0b579f;
}

@media (min-width: 900px) {
  .record-edit-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .field-wide,
  .form-actions,
  .error {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .record-edit-form {
    padding: 1rem;
  }

  textarea {
    min-height: 18rem;
  }

  .meta-row {
    grid-template-columns: 1fr auto;
  }

  .meta-row .meta-input:first-child {
    grid-column: 1 / -1;
  }

  .form-actions {
    justify-content: stretch;
  }

  .form-actions :deep(.primary) {
    width: 100%;
  }
}
</style>
