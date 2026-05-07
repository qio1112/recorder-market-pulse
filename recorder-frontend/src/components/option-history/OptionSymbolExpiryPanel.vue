<template>
  <section class="panel">
    <div class="field-row">
      <label class="field">
        <span>Symbol</span>
        <select :value="symbol" @change="$emit('update:symbol', $event.target.value)">
          <option value="" disabled>Select symbol</option>
          <option v-for="sym in symbols" :key="sym" :value="sym">{{ sym }}</option>
        </select>
      </label>
    </div>

    <div class="expiry-block">
      <div class="block-head">
        <h2>Expiries</h2>
        <span class="count">{{ expiries.length }}</span>
      </div>
      <div v-if="loading" class="muted">Loading expiries...</div>
      <div v-else-if="!symbol" class="muted">Select a symbol to see expiries.</div>
      <div v-else-if="!expiries.length" class="muted">No expiries found.</div>
      <div v-else class="expiry-list">
        <button
          v-for="expiry in expiries"
          :key="expiry.expiry"
          type="button"
          class="expiry-item"
          :class="{ active: expiry.expiry === selectedExpiry, expired: expiry.expired }"
          @click="$emit('select-expiry', expiry.expiry)"
        >
          <span>{{ expiry.expiry }}</span>
          <span class="status">{{ expiry.expired ? 'Expired' : 'Open' }}</span>
        </button>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: 'OptionSymbolExpiryPanel',
  props: {
    symbols: {
      type: Array,
      default: () => []
    },
    expiries: {
      type: Array,
      default: () => []
    },
    symbol: {
      type: String,
      default: ''
    },
    selectedExpiry: {
      type: String,
      default: ''
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  emits: ['update:symbol', 'select-expiry']
}
</script>

<style scoped>
.panel {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.field-row {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  color: #243b53;
  font-weight: 700;
  font-size: 0.9rem;
}

select {
  min-width: 12rem;
  border: 1px solid #bcccdc;
  border-radius: 7px;
  padding: 0.55rem 0.65rem;
  background: #ffffff;
  color: #102a43;
  font: inherit;
}

.block-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

h2 {
  margin: 0;
  font-size: 1rem;
  color: #102a43;
}

.count {
  color: #627d98;
  font-size: 0.85rem;
}

.expiry-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(10.5rem, 1fr));
  gap: 0.45rem;
}

.expiry-item {
  border: 1px solid #d9e2ec;
  border-radius: 7px;
  background: #f8fafc;
  color: #243b53;
  padding: 0.55rem 0.65rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  cursor: pointer;
  font: inherit;
  min-height: 2.6rem;
}

.expiry-item.active {
  border-color: #0f4c81;
  background: #eef6ff;
}

.expiry-item.expired {
  color: #7b8794;
}

.status {
  font-size: 0.78rem;
  color: #627d98;
}

.muted {
  color: #627d98;
  font-size: 0.9rem;
}
</style>
