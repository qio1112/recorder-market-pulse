<template>
  <div>
    <records-filter
      :initial-filters="filtersFromRoute"
      :key="filterKey"
      :initial-query-text="activeQueryText"
      @submit="updateRecordList"
      @query="handleQuerySearch"
    ></records-filter>
    <records-list
      v-if="!activeQueryText"
      :filters="filtersFromRoute"
    ></records-list>
    <records-list
      v-else
      :filters="filtersFromRoute"
      :query-text="activeQueryText"
    ></records-list>
  </div>
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
      return JSON.stringify(this.$route.query || {});
    },
    activeQueryText() {
      const q = this.$route.query || {};
      return q['query-text'] || '';
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
      await this.$router.replace({ query });
    },
    async handleQuerySearch(queryText) {
      const baseQuery = this.$route.query || {};
      if (!queryText || !queryText.trim()) {
        this.$toast && this.$toast.error ? this.$toast.error('Query text cannot be empty') : alert('Query text cannot be empty');
        return;
      }
      const trimmed = queryText.trim().slice(0, 200);
      const newQuery = { ...baseQuery, 'query-text': trimmed || undefined };
      await this.$router.replace({ query: newQuery });
    }
  }
}
</script>
