<template>
  <form class="record-edit-form" @submit.prevent="submit">
    <div class="field">
      <label>Title</label>
      <input v-model="form.title" type="text" required />
    </div>

    <div class="field">
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

    <div class="field">
      <label>Content</label>
      <textarea v-model="form.content" rows="5"></textarea>
    </div>

    <div class="field checkbox">
      <label><input type="checkbox" v-model="form.isPublic" /> Public</label>
    </div>

    <div class="field" v-if="mode === 'edit'">
      <label><input type="checkbox" v-model="form.cancelAlert" /> Cancel Alert</label>
    </div>

    <div class="field">
      <label>Alert Type</label>
      <select v-model="form.alertType">
        <option :value="null">None</option>
        <option value="ONE_TIME">One-time</option>
        <option value="RECURRING">Recurring</option>
      </select>
    </div>

    <div class="field" v-if="form.alertType">
      <label>Alert Time (ISO)</label>
      <input v-model="form.alertTime" type="text" placeholder="2025-10-11T17:04:15-04:00" />
    </div>

    <div class="field" v-if="form.alertType === 'RECURRING'">
      <label>Recurring Weekdays (comma-separated, e.g. MONDAY,TUESDAY,SATURDAY)</label>
      <input v-model="form.recurringAlertWeekDays" type="text" />
    </div>

    <div class="field">
      <label>Images</label>
      <input type="file" accept="image/*" multiple @change="onImagesChange" />
      <div v-if="imagePreviews.length" class="image-preview-list">
        <div
          v-for="preview in imagePreviews"
          :key="preview.url"
          class="image-preview-card"
        >
          <img :src="preview.url" :alt="preview.name" />
          <p>{{ preview.name }}</p>
        </div>
      </div>
    </div>

    <div class="field">
      <label>Files</label>
      <input type="file" multiple @change="onFilesChange" />
    </div>

    <div class="field">
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

    <div class="field" v-if="existingFiles.length">
      <label>Remove existing files</label>
      <div class="checkbox-list">
        <label v-for="file in existingFiles" :key="file.fileID" class="checkbox-item">
          <input
            type="checkbox"
            :value="file.fileID"
            v-model="form.removeFileIDs"
          />
          {{ cleanFileName(file.filename) }}
        </label>
      </div>
    </div>

    <base-button mode="primary" type="submit">
      {{ mode === 'edit' ? 'Save Changes' : 'Create Record' }}
    </base-button>
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
  </form>
</template>

<script>
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
      metadataRows: this.initialRecord.metadata
        ? Object.entries(this.initialRecord.metadata).map(([key, value]) => ({ key, value }))
        : []
    }
  },
  beforeUnmount() {
    this.cleanupImagePreviews();
  },
  computed: {
    existingFiles() {
      return this.initialRecord.recFiles || [];
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
      this.refreshImagePreviews();
    },
    onFilesChange(e) {
      this.form.files = Array.from(e.target.files);
    },
    refreshImagePreviews() {
      this.cleanupImagePreviews();
      this.imagePreviews = this.form.images.map((file) => ({
        name: file.name,
        url: URL.createObjectURL(file)
      }));
    },
    cleanupImagePreviews() {
      this.imagePreviews.forEach((preview) => {
        URL.revokeObjectURL(preview.url);
      });
      this.imagePreviews = [];
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
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.image-preview-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.5rem;
  margin-top: 0.35rem;
}

.image-preview-card {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.45rem;
  border: 1px solid #e5e8ed;
  border-radius: 8px;
  background: #f7fafc;
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

@media (max-width: 900px) {
  .image-preview-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .image-preview-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

label {
  font-weight: 600;
  font-size: 0.9rem;
}

input,
textarea,
select {
  padding: 0.55rem 0.65rem;
  border: 1px solid #cfd7e2;
  border-radius: 7px;
  font-size: 0.8rem;
}

.checkbox {
  flex-direction: row;
  align-items: center;
  gap: 0.5rem;
}

.labels-row {
  display: flex;
  gap: 0.5rem;
}

.trade-check {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  color: #1f2933;
  font-size: 0.85rem;
}

.add-btn {
  padding: 0.5rem 0.8rem;
  border-radius: 7px;
  border: 1px solid #cfd7e2;
  background: #f7fafc;
  cursor: pointer;
  font-size: 0.8rem;
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
  padding: 0.25rem 0.5rem;
  border-radius: 999px;
  background: #e5f3ff;
  color: #0f4c81;
  border: 1px solid #cde7ff;
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
  gap: 0.4rem;
}

.meta-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 0.35rem;
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

.checkbox-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.35rem;
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
</style>
