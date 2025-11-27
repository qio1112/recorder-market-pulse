<template>
  <section class="records-list">
    <div class="loading" v-if="isLoading">Loading records…</div>
    <div class="list" v-else-if="records.length">
      <record-preview v-for="rec in records" :key="rec.id" :record="rec" />
    </div>
    <p v-else class="empty">No records found.</p>

    <div class="pagination">
      <button type="button" @click="changePage(-1)" :disabled="currentPage === 0">Prev</button>
      <span>
        Page {{ currentPage + 1 }} of {{ totalPages || 1 }}
        <span class="total">({{ totalElements }} items)</span>
      </span>
      <button type="button" @click="changePage(1)" :disabled="!hasNext">Next</button>
    </div>
  </section>
</template>

<script>
import RecordPreview from './RecordPreview.vue'
import { getRecords, ListRecordRequest } from '../../api/RecordService.js'

export default {
  name: 'RecordsList',
  components: { RecordPreview },
  props: {
    filters: {
      type: Object,
      default: null
    }
  },
  data() {
    return {
      records: [],
      currentPage: 0,
      totalPages: 0,
      totalElements: 0,
      pageSize: 10,
      hasNext: false,
      isLoading: false
    }
  },
  watch: {
    filters: {
      deep: true,
      handler() {
        this.currentPage = 0;
        this.fetchRecords();
      }
    }
  },
  mounted() {
    this.fetchRecords();
  },
  methods: {
    async fetchRecords() {
      this.isLoading = true;
      const request = new ListRecordRequest({
        ...(this.filters || {}),
        page: this.currentPage
      });
      this.pageSize = request.pageSize;
      const data = await getRecords(request);
      this.records = data?.content || [];
      const pageInfo = data?.pageable || {};
      this.currentPage = pageInfo.pageNumber ?? 0;
      this.totalPages = data?.totalPages ?? 0;
      this.totalElements = data?.totalElements ?? 0;
      const responsePageSize = pageInfo.pageSize ?? request.pageSize ?? this.records.length;
      this.hasNext = data?.last === true ? false : data?.last === false ? true : this.records.length === responsePageSize;
      this.isLoading = false;
    },
    async changePage(delta) {
      const next = this.currentPage + delta;
      if (next < 0) return;
      if (delta > 0 && !this.hasNext) return;
      this.currentPage = next;
      await this.fetchRecords();
    }
  }
}
</script>

<style scoped>
  .records-list {
    margin: 1rem auto;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    max-width: 960px;
  }

.list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
  border: 1px solid #e5e8ed;
  border-radius: 10px;
  overflow: hidden;
}

.empty {
  color: #52606d;
  font-size: 0.95rem;
}

.pagination {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  justify-content: center;
}

.pagination .total {
  color: #52606d;
  font-size: 0.9rem;
}

.pagination button {
  padding: 0.5rem 0.9rem;
  border-radius: 7px;
  border: 1px solid #cfd7e2;
  background: #f7fafc;
  cursor: pointer;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
