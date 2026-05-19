<template>
  <section class="add-record workspace-page">
    <header class="record-editor-header workspace-header">
      <p class="workspace-eyebrow">Records</p>
      <h1>Create New Record</h1>
    </header>
    <record-edit-form mode="create" :error-message="errorMessage" @submit="handleSubmit" />
    <p v-if="errorMessage" class="error workspace-error">{{ errorMessage }}</p>
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
  max-width: 1080px;
}

.record-editor-header {
  display: block;
}

.error {
  margin-top: 0.75rem;
}
</style>
