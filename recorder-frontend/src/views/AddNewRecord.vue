<template>
  <section class="add-record">
    <h1>Create New Record</h1>
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
  max-width: 960px;
  margin: 1.5rem auto;
  padding: 1rem;
}

.error {
  color: #d64045;
}
</style>
