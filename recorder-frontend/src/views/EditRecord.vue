<template>
  <section class="edit-record">
    <h1>Edit Record</h1>
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
  max-width: 960px;
  margin: 1.5rem auto;
  padding: 1rem;
}

.muted {
  color: #52606d;
}

.error {
  margin-top: 0.75rem;
  color: #d64045;
}
</style>
