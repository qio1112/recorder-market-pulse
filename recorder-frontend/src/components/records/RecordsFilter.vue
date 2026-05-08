<template>
  <form class="filter-form" @submit.prevent="handleSubmit">
    <div class="field">
      <label for="title">Title contains</label>
      <input id="title" v-model.trim="form.titleContains" type="text" placeholder="Search titles" />
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
      <label>Exclude Labels</label>
      <div class="labels-row">
        <input
          v-model.trim="newExcludeLabel"
          type="text"
          placeholder="Add label to be excluded"
          @keyup.enter.prevent="addExcludeLabel"
        />
        <button type="button" class="add-btn" @click="addExcludeLabel">Add</button>
      </div>
      <div class="labels-list" v-if="form.excludeLabels.length">
        <span v-for="excludeLabel in form.excludeLabels" :key="excludeLabel" class="chip">
          {{ excludeLabel }}
          <button type="button" class="chip-remove" @click="removeExcludeLabel(excludeLabel)">×</button>
        </span>
      </div>
    </div>

    <div class="field grid dates-grid">
      <div>
        <label for="creationAfter">Created after </label>
        <input id="creationAfter" v-model="form.creationAfterDate" type="date" />
      </div>
      <div>
        <label for="creationBefore">Created before </label>
        <input id="creationBefore" v-model="form.creationBeforeDate" type="date" />
      </div>
      <div>
        <label for="modifiedAfter">Modified after </label>
        <input id="modifiedAfter" v-model="form.modifiedAfterDate" type="date" />
      </div>
      <div>
        <label for="modifiedBefore">Modified before </label>
        <input id="modifiedBefore" v-model="form.modifiedBeforeDate" type="date" />
      </div>
    </div>

    <div class="field check-row">
      <label>
        <input type="checkbox" v-model="form.isCreatedByUserOnly" />
        Created by me only
      </label>
    </div>

    <div class="field">
      <label for="sortBy">Sort by</label>
      <select id="sortBy" v-model="form.sortBy">
        <option value="title">Title</option>
        <option value="title_r">Title (reversed)</option>
        <option value="creationTime">Creation time</option>
        <option value="creationTime_r">Creation time (reversed)</option>
        <option value="lastModifiedTime">Last modified time</option>
        <option value="lastModifiedTime_r">Last modified time (reversed)</option>
      </select>
    </div>

    <div class="actions">
      <button type="submit" class="primary">Apply filters</button>
      <button type="button" class="secondary" @click="resetForm">Reset</button>
    </div>

    <div class="query-row">
      <label for="queryText">Query by description</label>
      <div class="query-input-row">
        <input
          id="queryText"
          v-model.trim="queryText"
          type="text"
          placeholder="Describe what you're looking for"
          @keydown.enter.stop.prevent="handleQuerySearch"
        />
        <button type="button" class="primary" :disabled="!queryText" @click="handleQuerySearch">Search</button>
      </div>
    </div>
  </form>
</template>

<script>
import { ListRecordRequest } from '../../api/RecordService.js'

export default {
  emits: ['submit', 'query'],
  props: {
    initialFilters: {
      type: Object,
      default: null
    },
    initialQueryText: {
      type: String,
      default: ''
    }
  },
  data() {
    const defaults = new ListRecordRequest(this.initialFilters || {});
    return {
      form: this.mapRequestToForm(defaults),
      newLabel: '',
      newExcludeLabel: '',
      queryText: this.initialQueryText || ''
    }
  },
  watch: {
    initialFilters: { // this is used by navigation/back/forward changes on page
      deep: true,
      handler(newVal) {
        const request = new ListRecordRequest(newVal || {});
        this.form = this.mapRequestToForm(request);
      }
    },
    initialQueryText(newVal) {
      this.queryText = newVal || '';
    }
  },
  methods: {
    mapRequestToForm(request) {
      return {
        titleContains: request.titleContains,
        labels: [...request.labels],
        excludeLabels: [...request.excludeLabels],
        creationAfterDate: request.creationAfterDate,
        creationBeforeDate: request.creationBeforeDate,
        modifiedAfterDate: request.modifiedAfterDate,
        modifiedBeforeDate: request.modifiedBeforeDate,
        isCreatedByUserOnly: request.isCreatedByUserOnly,
        sortBy: request.sortBy
      };
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
    addExcludeLabel() {
      const value = this.newExcludeLabel.trim();
      if (!value || this.form.excludeLabels.includes(value)) return;
      this.form.excludeLabels.push(value);
      this.newExcludeLabel = '';
    },
    removeExcludeLabel(excludeLabel) {
      this.form.excludeLabels = this.form.excludeLabels.filter((l) => l !== excludeLabel);
    },
    resetForm() {
      const defaults = new ListRecordRequest();
      this.form = this.mapRequestToForm(defaults);
      this.newLabel = '';
    },
    handleSubmit() {
      const request = new ListRecordRequest({
        ...this.form,
        labels: [...this.form.labels]
      });
      this.$emit('submit', request);
    },
    handleQuerySearch() {
      this.$emit('query', this.queryText || '');
    }
  }
}
</script>

<style scoped>
.filter-form {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 1rem;
  border: 1px solid #cfd7e2;
  border-radius: 4px;
  background: #ffffff;
  width: 100%;
  max-width: none;
  margin: 0;
  box-shadow: 0 8px 24px rgba(15, 76, 129, 0.04);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

label {
  font-weight: 600;
  color: #1f2933;
  font-size: 0.75rem;
}

input,
select {
  width: 100%;
  box-sizing: border-box;
  padding: 0.55rem 0.65rem;
  border-radius: 4px;
  border: 1px solid #cfd7e2;
  font-size: 0.75rem;
}

.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.55rem;
}

.dates-grid {
  grid-template-columns: 1fr;
}

.check-row {
  flex-direction: row;
  align-items: center;
  gap: 1rem;
}

.check-row label {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  white-space: nowrap;
}

.labels-row {
  display: flex;
  gap: 0.5rem;
}

.labels-row input {
  min-width: 0;
}

.add-btn {
  padding: 0.55rem 0.9rem;
  border-radius: 4px;
  border: 1px solid #cfd7e2;
  background: #f7fafc;
  cursor: pointer;
  font-size: 0.75rem;
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
  border-radius: 4px;
  background: #e5f3ff;
  color: #0f4c81;
  border: 1px solid #cde7ff;
  font-size: 0.75rem;
}

.chip-remove {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 0.75rem;
  line-height: 1;
}

.actions {
  display: flex;
  gap: 0.6rem;
}

.actions button {
  flex: 1;
}

.query-row {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.query-input-row {
  display: flex;
  gap: 0.6rem;
  align-items: stretch;
  flex-direction: column;
}

.query-input-row input {
  flex: 1;
}

.query-input-row button {
  white-space: nowrap;
}

.query-input-row button:disabled {
  background: #e5e7eb;
  color: #9aa5b1;
  border-color: #d1d5db;
  cursor: not-allowed;
}

.primary,
.secondary {
  padding: 0.6rem 0.9rem;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid transparent;
  font-size: 0.75rem;
}

.primary {
  background: #0f4c81;
  color: white;
  border-color: #0f4c81;
}

.secondary {
  background: #f7fafc;
  color: #0f4c81;
  border-color: #cfd7e2;
}

select#sortBy {
  max-width: none;
}

@media (min-width: 1180px) {
  .dates-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .filter-form {
    margin: 0;
  }

  .dates-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .query-input-row {
    flex-direction: row;
    align-items: center;
  }
}

@media (max-width: 560px) {
  .labels-row,
  .actions,
  .query-input-row {
    flex-direction: column;
  }

  .dates-grid {
    grid-template-columns: 1fr;
  }
}
</style>
