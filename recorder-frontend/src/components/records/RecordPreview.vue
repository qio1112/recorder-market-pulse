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

    <div class="images" v-if="imageFiles.length" @click.stop>
      <button
        v-for="file in imageFiles"
        :key="file.fileID"
        type="button"
        class="image-thumb"
        :aria-label="`Preview image ${cleanFileName(file.filename)}`"
        @mousedown.stop
        @click.stop.prevent="openImagePreview(file)"
        @keydown.enter.stop.prevent="openImagePreview(file)"
        @keydown.space.stop.prevent="openImagePreview(file)"
      >
        <img
          :src="file.url"
          :alt="file.filename"
        />
      </button>
    </div>
    <image-preview
      v-if="selectedImage"
      :image="selectedImage"
      @close="selectedImage = null"
    />
  </article>
</template>

<script>
import { getRecFile } from '../../api/RecordService.js'
import ImagePreview from './ImagePreview.vue'

export default {
  name: 'RecordPreview',
  components: { ImagePreview },
  props: {
    record: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      imageFiles: [],
      selectedImage: null
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
    goToDetail(event) {
      if (event?.target?.closest?.('.images')) {
        return;
      }
      if (this.record?.id) {
        this.$router.push(`/records/${this.record.id}`);
      }
    },
    openImagePreview(file) {
      this.selectedImage = file;
    },
    cleanupObjectUrls() {
      this.imageFiles.forEach((f) => {
        if (f.url) URL.revokeObjectURL(f.url);
      });
      this.imageFiles = [];
      this.selectedImage = null;
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
    },
    cleanFileName(name) {
      if (!name) return '';
      const parts = String(name).split('__');
      return parts.length > 1 ? parts.slice(1).join('__') : name;
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

.image-thumb {
  display: block;
  width: 64px;
  height: 64px;
  padding: 0;
  border: 1px solid #e5e8ed;
  border-radius: 8px;
  background: #f6f9fc;
  cursor: zoom-in;
  overflow: hidden;
}

.image-thumb:hover,
.image-thumb:focus-visible {
  border-color: #2f80ed;
  outline: none;
  box-shadow: 0 0 0 3px rgba(47, 128, 237, 0.14);
}

.image-thumb img {
  width: 64px;
  height: 64px;
  object-fit: cover;
  display: block;
}
</style>
