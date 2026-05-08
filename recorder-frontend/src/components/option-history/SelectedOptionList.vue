<template>
  <section class="selected-list">
    <header class="list-head">
      <h2>Selected Options</h2>
      <span class="count">{{ options.length }}</span>
    </header>
    <p v-if="!options.length" class="muted">No options selected.</p>
    <div v-else class="rows">
      <article v-for="option in options" :key="option.id" class="row">
        <label class="toggle">
          <input
            type="checkbox"
            :checked="option.show"
            @change="$emit('toggle-show', option.id)"
          />
          <span>{{ option.label }}</span>
        </label>
        <button type="button" class="delete" @click="$emit('remove', option.id)">Delete</button>
      </article>
    </div>
  </section>
</template>

<script>
export default {
  name: 'SelectedOptionList',
  props: {
    options: {
      type: Array,
      default: () => []
    }
  },
  emits: ['toggle-show', 'remove']
}
</script>

<style scoped>
.selected-list {
  border: 1px solid #d9e2ec;
  border-radius: 4px;
  background: #ffffff;
  padding: 0.75rem;
}

.list-head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.5rem;
}

h2 {
  margin: 0;
  font-size: 0.95rem;
  color: #0f4c81;
}

.count,
.muted {
  color: #627d98;
  font-size: 0.82rem;
}

.rows {
  display: grid;
  gap: 0.35rem;
}

.row {
  min-height: 2.25rem;
  border: 1px solid #e5e8ed;
  border-radius: 4px;
  padding: 0.35rem 0.45rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.55rem;
}

.toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: #334e68;
  font-size: 0.82rem;
  font-weight: 700;
}

.delete {
  border: 1px solid #f2b8b5;
  border-radius: 4px;
  background: #fff5f5;
  color: #b42318;
  padding: 0.3rem 0.5rem;
  cursor: pointer;
  font: inherit;
  font-size: 0.78rem;
}
</style>
