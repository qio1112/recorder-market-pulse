<template>
  <section class="add-record">
    <header class="record-editor-header">
      <p class="eyebrow">Records</p>
      <h1>Create New Record</h1>
    </header>
    <record-edit-form mode="create" :error-message="errorMessage" @submit="handleSubmit" />
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
  </section>
</template>

<script>
import { addNewRecord, AddRecordRequest } from '../api/RecordService.js'
import RecordEditForm from '../components/records/RecordEditForm.vue'
export default {
  name: 'AddNewRecordView',
  components: { RecordEditForm },
  data() {
    return {
      errorMessage: ''
    }
  },
  methods: {
    async handleSubmit(payload) {
      this.errorMessage = '';
      const request = new AddRecordRequest(payload);
      const response = await addNewRecord(request);
      if (response?.id) {
        this.$router.replace(`/records/${response.id}`);
      } else {
        this.errorMessage = 'Failed to create record. Please try again.';
      }
    }
  }
}
</script>

<style scoped>
.add-record {
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

.error {
  color: #d64045;
}
</style>
