<template>
  <teleport to="body">
    <div class="image-preview-overlay" role="presentation" @click.self="close">
      <section
        class="image-preview-window"
        role="dialog"
        aria-modal="true"
        :aria-label="imageName"
      >
        <button type="button" class="close-button" aria-label="Close image preview" @click="close">×</button>
        <img :src="image.url" :alt="imageName" />
        <p class="image-name">{{ imageName }}</p>
      </section>
    </div>
  </teleport>
</template>

<script>
export default {
  name: 'ImagePreview',
  emits: ['close'],
  props: {
    image: {
      type: Object,
      required: true
    }
  },
  computed: {
    imageName() {
      return this.cleanFileName(this.image?.filename || this.image?.name || 'Image preview');
    }
  },
  mounted() {
    window.addEventListener('keydown', this.handleKeydown);
  },
  beforeUnmount() {
    window.removeEventListener('keydown', this.handleKeydown);
  },
  methods: {
    close() {
      this.$emit('close');
    },
    handleKeydown(event) {
      if (event.key === 'Escape') {
        this.close();
      }
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
.image-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background: rgba(15, 23, 42, 0.62);
  backdrop-filter: blur(2px);
}

.image-preview-window {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: min(92vw, 980px);
  max-height: 90vh;
  padding: 1rem;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.28);
}

.image-preview-window img {
  width: 100%;
  max-height: calc(90vh - 6rem);
  object-fit: contain;
  border-radius: 6px;
  background: #f8fafc;
}

.close-button {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  padding: 0;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: #1f2933;
  cursor: pointer;
  font-size: 1.35rem;
  line-height: 1;
}

.close-button:hover,
.close-button:focus-visible {
  border-color: #cfe7ff;
  background: #eef6ff;
  color: #0f4c81;
  outline: none;
}

.image-name {
  margin: 0;
  color: #52606d;
  font-size: 0.85rem;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .image-preview-overlay {
    padding: 0.75rem;
  }

  .image-preview-window {
    width: 100%;
    padding: 0.75rem;
  }
}
</style>
