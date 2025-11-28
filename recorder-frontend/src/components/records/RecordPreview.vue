<template>
  <article class="record-preview" @click="goToDetail" role="button" tabindex="0" @keypress.enter="goToDetail">
    <div class="info">
      <div class="title-row">
        <h3 class="title">{{ record.title }}</h3>
        <p class="meta">by {{ record.createdBy }}</p>
      </div>
      <div class="labels" v-if="labelNames.length">
        <span v-for="label in labelNames" :key="label" class="chip">{{ label }}</span>
      </div>
    </div>

    <div class="images" v-if="imageFiles.length">
      <img
        v-for="file in imageFiles"
        :key="file.fileID"
        :src="file.url"
        :alt="file.filename"
      />
    </div>
  </article>
</template>

<script>
import { getRecFile } from '../../api/RecordService.js'

export default {
  name: 'RecordPreview',
  props: {
    record: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      imageFiles: []
    }
  },
  computed: {
    labelNames() {
      return (this.record.labels || []).slice(0, 6).map((l) => l.labelName);
    }
  },
  watch: {
    record: {
      deep: true,
      immediate: true,
      handler() {
        this.loadImages();
      }
    }
  },
  beforeUnmount() {
    this.cleanupObjectUrls();
  },
  methods: {
    goToDetail() {
      if (this.record?.id) {
        this.$router.push(`/records/${this.record.id}`);
      }
    },
    cleanupObjectUrls() {
      this.imageFiles.forEach((f) => {
        if (f.url) URL.revokeObjectURL(f.url);
      });
      this.imageFiles = [];
    },
    async loadImages() {
      this.cleanupObjectUrls();
      const files = (this.record.recFiles || [])
        .filter((f) => f.fileType === 'IMAGE')
        .slice(0, 2);

      const results = await Promise.all(
        files.map(async (f) => {
          const blob = await getRecFile(f.fileID);
          if (!blob) return null;
          const url = URL.createObjectURL(blob);
          return { ...f, url };
        })
      );
      this.imageFiles = results.filter(Boolean);
    }
  }
}
</script>

<style scoped>
.record-preview {
  border-bottom: 1px solid #e5e8ed;
  padding: 0.75rem 1rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  background: #fff;
  transition: background-color 0.15s ease;
}

.record-preview:last-child {
  border-bottom: none;
}

.record-preview:hover {
  background: #f7fafc;
}

.info {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  flex: 1;
}

.title-row {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.title {
  margin: 0;
  font-size: 1rem;
  color: #102a43;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta {
  margin: 0;
  font-size: 0.9rem;
  color: #52606d;
}

.labels {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  overflow: hidden;
}

.chip {
  background: #e5f3ff;
  color: #0f4c81;
  padding: 0.25rem 0.55rem;
  border-radius: 999px;
  font-size: 0.85rem;
  white-space: nowrap;
}

.images {
  display: flex;
  gap: 0.5rem;
}

.images img {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #e5e8ed;
  background: #f6f9fc;
}
</style>
