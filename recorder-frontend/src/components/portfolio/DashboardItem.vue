<template>
  <section
    class="dashboard-item"
    :style="{
      gridColumn: gridColumn || undefined,
      gridRow: gridRow || undefined
    }"
  >
    <header v-if="title" class="item-head">
      <h2>{{ title }}</h2>
      <p v-if="subtitle" class="muted">{{ subtitle }}</p>
    </header>
    <div class="item-body">
      <slot></slot>
    </div>
  </section>
</template>

<script>
export default {
  name: 'DashboardItem',
  props: {
    title: {
      type: String,
      default: ''
    },
    subtitle: {
      type: String,
      default: ''
    },
    gridColumn: {
      type: String,
      default: ''
    },
    gridRow: {
      type: String,
      default: ''
    }
  }
}
</script>

<style scoped>
.dashboard-item {
  border: 1px solid #e5e8ed;
  border-radius: 10px;
  background: #fff;
  padding: 0.75rem;
  box-shadow: 0 6px 18px rgba(16, 42, 67, 0.05);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.item-head h2 {
  margin: 0;
  font-size: 1.05rem;
  color: #102a43;
}

.muted {
  color: #52606d;
  margin: 0.1rem 0 0 0;
}

.item-body {
  flex: 1;
  min-height: 120px;
}
</style>
