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
      <input type="file" multiple @change="onImagesChange" />
    </div>

    <div class="field">
      <label>Files</label>
      <input type="file" multiple @change="onFilesChange" />
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
      newLabel: ''
    }
  },
  computed: {
    existingFiles() {
      return this.initialRecord.recFiles || [];
    }
  },
  methods: {
    onImagesChange(e) {
      this.form.images = Array.from(e.target.files);
    },
    onFilesChange(e) {
      this.form.files = Array.from(e.target.files);
    },
    addLabel() {
      const value = this.newLabel.trim();
      if (!value || this.form.labels.includes(value)) return;
      this.form.labels.push(value);
      this.newLabel = '';
    },
    removeLabel(label) {
      this.form.labels = this.form.labels.filter((l) => l !== label);
    },
    cleanFileName(name) {
      if (!name) return '';
      const parts = String(name).split('__');
      return parts.length > 1 ? parts.slice(1).join('__') : name;
    },
    submit() {
      this.$emit('submit', { ...this.form });
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
