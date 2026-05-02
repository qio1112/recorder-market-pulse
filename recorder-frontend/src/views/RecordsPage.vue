<template>
  <section class="records-page">
    <aside class="records-sidebar">
      <records-filter
        :initial-filters="filtersFromRoute"
        :key="filterKey"
        :initial-query-text="activeQueryText"
        @submit="updateRecordList"
        @query="handleQuerySearch"
      ></records-filter>
    </aside>
    <main class="records-content">
      <records-list
        v-if="!activeQueryText"
        :filters="filtersFromRoute"
        :initial-page="activePage"
        @page-change="handlePageChange"
      ></records-list>
      <records-list
        v-else
        :filters="filtersFromRoute"
        :query-text="activeQueryText"
      ></records-list>
    </main>
  </section>
</template>

<script>
import { ListRecordRequest } from '../api/RecordService.js'
import RecordsFilterVue from '../components/records/RecordsFilter.vue';
import RecordsListVue from '../components/records/RecordsList.vue';


export default {
  components: {
    RecordsFilter: RecordsFilterVue,
    RecordsList: RecordsListVue
  },
  computed: {
    filtersFromRoute() {
      const base = new ListRecordRequest();
      const q = this.$route.query || {};
      const toBool = (val, fallback) => {
        if (val === undefined) return fallback;
        if (val === 'true' || val === true) return true;
        if (val === 'false' || val === false) return false;
        return fallback;
      };
      const toLabels = () => {
        if (q.labels === undefined) return [...base.labels];
        const raw = Array.isArray(q.labels) ? q.labels : String(q.labels).split(',');
        return raw.map((l) => l.trim()).filter(Boolean);
      };

      const toExcludeLabels = () => {
        if (q.excludeLabels === undefined) return [...base.excludeLabels];
        const raw = Array.isArray(q.excludeLabels) ? q.excludeLabels : String(q.excludeLabels).split(',');
        return raw.map((l) => l.trim()).filter(Boolean);
      }

      return {
        titleContains: q.titleContains ?? base.titleContains,
        labels: toLabels(),
        excludeLabels: toExcludeLabels(),
        creationAfterDate: q.creationAfterDate ?? base.creationAfterDate,
        creationBeforeDate: q.creationBeforeDate ?? base.creationBeforeDate,
        modifiedAfterDate: q.modifiedAfterDate ?? base.modifiedAfterDate,
        modifiedBeforeDate: q.modifiedBeforeDate ?? base.modifiedBeforeDate,
        isPublic: toBool(q.isPublic, base.isPublic),
        isCreatedByUserOnly: toBool(q.isCreatedByUserOnly, base.isCreatedByUserOnly),
        sortBy: q.sortBy ?? base.sortBy
      };
    },
    filterKey() {
      const filterQuery = { ...(this.$route.query || {}) };
      delete filterQuery.page;
      return JSON.stringify(filterQuery);
    },
    activeQueryText() {
      const q = this.$route.query || {};
      return q['query-text'] || '';
    },
    activePage() {
      const rawPage = this.$route.query?.page;
      const page = Number.parseInt(Array.isArray(rawPage) ? rawPage[0] : rawPage, 10);
      return Number.isInteger(page) && page > 0 ? page - 1 : 0;
    }
  },
  watch: {
    activeQueryText: {
      immediate: true,
      handler(queryText) {
        if (!queryText || this.$route.query?.page === undefined) return;
        this.$router.replace({
          query: {
            ...(this.$route.query || {}),
            page: undefined
          }
        });
      }
    }
  },
  methods: {
    buildQueryFromRequest(request) {
      return {
        titleContains: request.titleContains || undefined,
        labels: request.labels && request.labels.length ? request.labels : undefined,
        excludeLabels: request.excludeLabels && request.excludeLabels.length ? request.excludeLabels : undefined,
        creationAfterDate: request.creationAfterDate || undefined,
        creationBeforeDate: request.creationBeforeDate || undefined,
        modifiedAfterDate: request.modifiedAfterDate || undefined,
        modifiedBeforeDate: request.modifiedBeforeDate || undefined,
        isPublic: String(request.isPublic),
        isCreatedByUserOnly: String(request.isCreatedByUserOnly),
        sortBy: request.sortBy || undefined
      };
    },
    async updateRecordList(request) {
      const query = this.buildQueryFromRequest(request);
      // Clear query-text when doing standard filter search
      query['query-text'] = undefined;
      query.page = undefined;
      await this.$router.replace({ query });
    },
    async handleQuerySearch(queryText) {
      const baseQuery = this.$route.query || {};
      if (!queryText || !queryText.trim()) {
        this.$toast && this.$toast.error ? this.$toast.error('Query text cannot be empty') : alert('Query text cannot be empty');
        return;
      }
      const trimmed = queryText.trim().slice(0, 200);
      const newQuery = { ...baseQuery, 'query-text': trimmed || undefined, page: undefined };
      await this.$router.replace({ query: newQuery });
    },
    async handlePageChange(page) {
      const nextPage = Number.isInteger(page) && page > 0 ? String(page) : undefined;
      await this.$router.push({
        query: {
          ...(this.$route.query || {}),
          page: nextPage
        }
      });
    }
  }
}
</script>

<style scoped>
.records-page {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  align-items: start;
  gap: 1.25rem;
  width: min(1440px, calc(100% - 2rem));
  margin: 0 auto;
  padding: 1.25rem 0 2rem;
}

.records-sidebar {
  position: sticky;
  top: 1rem;
  min-width: 0;
}

.records-content {
  min-width: 0;
}

@media (max-width: 900px) {
  .records-page {
    grid-template-columns: 1fr;
    width: min(960px, calc(100% - 1.5rem));
  }

  .records-sidebar {
    position: static;
  }
}
</style>
