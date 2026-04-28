<template>
  <section class="edit-record">
    <header class="record-editor-header">
      <p class="eyebrow">Records</p>
      <h1>Edit Record</h1>
    </header>
    <record-edit-form
      v-if="record"
      :initial-record="record"
      mode="edit"
      :error-message="errorMessage"
      @submit="handleSubmit"
    />
    <p v-else class="muted">Loading…</p>
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
  </section>
</template>

<script>
import { getRecordDetail, editRecord, EditRecordRequest } from '../api/RecordService.js'
import RecordEditForm from '../components/records/RecordEditForm.vue'

export default {
  name: 'EditRecordView',
  props: {
    recordID: {
      type: String,
      required: true
    }
  },
  components: { RecordEditForm },
  data() {
    return {
      record: null,
      errorMessage: ''
    }
  },
  async created() {
    this.record = await getRecordDetail(this.recordID);
  },
  methods: {
    async handleSubmit(formData) {
      this.errorMessage = '';
      const editRecordRequest = new EditRecordRequest({
        ...formData,
        id: this.recordID
      });
      const response = await editRecord(editRecordRequest);
      if (response?.id) {
        this.$router.replace(`/records/${response.id}`);
      } else {
        this.errorMessage = 'Failed to save changes. Please try again.';
      }
    }
  }
}
</script>

<style scoped>
.edit-record {
  max-width: 1040px;
  margin: 0 auto;
  padding: 1.75rem 1rem 2.5rem;
}

.record-editor-header {
  margin-bottom: 1rem;
}

.eyebrow {
  margin: 0 0 0.25rem;
  color: #52606d;
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h1 {
  margin: 0;
  color: #172033;
  font-size: 1.75rem;
  line-height: 1.2;
}

.muted {
  padding: 1rem;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #52606d;
}

.error {
  margin-top: 0.75rem;
  color: #d64045;
}
</style>
