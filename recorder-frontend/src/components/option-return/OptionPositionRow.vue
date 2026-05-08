<template>
  <tr class="position-row">
    <td>{{ formatSymbol(item) }}</td>

    <td v-if="!item.isEditing" class="type-text">{{ item.positionType }}</td>
    <td v-else>
      <select v-model="editable.positionType">
        <option value="call">call</option>
        <option value="put">put</option>
        <option value="stock">stock</option>
      </select>
    </td>

    <td v-if="!item.isEditing">{{ formatStrike(item.strike, item.positionType) }}</td>
    <td v-else>
      <input
        v-model="editable.strike"
        type="number"
        step="0.1"
        min="0"
        :disabled="editable.positionType === 'stock'"
      />
    </td>

    <td v-if="!item.isEditing">{{ item.shares }}</td>
    <td v-else>
      <input v-model="editable.shares" type="number" step="1" min="0" />
    </td>

    <td v-if="!item.isEditing">${{ formatPrice(item.price) }}</td>
    <td v-else>
      <input v-model="editable.price" type="number" step="0.01" min="0" />
    </td>

    <td>{{ formatExpiry(item.expiry, item.positionType) }}</td>

    <td class="actions-cell">
      <template v-if="item.isEditing">
        <button type="button" class="action-btn primary-btn" @click="handleSave">Save</button>
        <button type="button" class="action-btn secondary-btn" @click="$emit('cancel', item.id)">Cancel</button>
      </template>
      <template v-else>
        <button type="button" class="action-btn secondary-btn" @click="$emit('edit', item.id)">Edit</button>
        <button type="button" class="action-btn danger-btn" @click="$emit('remove', item.id)">Delete</button>
      </template>
      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
    </td>
  </tr>
</template>

<script>
export default {
  name: 'OptionPositionRow',
  emits: ['edit', 'save', 'cancel', 'remove'],
  props: {
    item: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      editable: this.buildEditableState(this.item),
      errorMessage: ''
    }
  },
  methods: {
    buildEditableState(item) {
      return {
        positionType: item.positionType || 'stock',
        strike: item.strike,
        shares: item.shares,
        price: item.price
      }
    },
    handleSave() {
      const positionType = String(this.editable.positionType || '').trim().toLowerCase()
      const strike = Number.parseFloat(this.editable.strike)
      const shares = Number.parseInt(this.editable.shares, 10)
      const price = Number.parseFloat(this.editable.price)

      if (
        !['call', 'put', 'stock'].includes(positionType) ||
        (positionType !== 'stock' && (!Number.isFinite(strike) || strike < 0)) ||
        !Number.isInteger(shares) ||
        !Number.isFinite(price) ||
        price < 0
      ) {
        this.errorMessage = 'Save requires type, integer quantity, and non-negative price. Strike is required for call/put.'
        return
      }

      this.errorMessage = ''
      this.$emit('save', {
        id: this.item.id,
        positionType,
        strike: positionType === 'stock' ? null : strike,
        shares,
        price
      })
    },
    formatStrike(value, positionType) {
      if (positionType === 'stock') {
        return '-'
      }
      const strike = Number(value)
      return Number.isFinite(strike) ? strike.toFixed(1) : value
    },
    formatSymbol(item) {
      if (item.positionType === 'stock') {
        return item.symbol
      }

      const baseSymbol = String(item.symbol || '').trim().toUpperCase()
      const expiry = String(item.expiry || '').trim()
      const optionFlag = item.positionType === 'call' ? 'C' : 'P'
      const expiryCompact = expiry ? expiry.replace(/-/g, '').slice(2) : ''
      const strike = Number(item.strike)
      const strikeText = Number.isFinite(strike)
        ? String(strike.toFixed(1)).replace(/\.0$/, '')
        : ''

      return `${baseSymbol}${expiryCompact}${optionFlag}${strikeText}`
    },
    formatPrice(value) {
      const price = Number(value)
      return Number.isFinite(price) ? price.toFixed(2) : value
    },
    formatExpiry(value, positionType) {
      return positionType === 'stock' ? '-' : value
    },
    resetStrikeIfStock() {
      if (this.editable.positionType === 'stock') {
        this.editable.strike = ''
      }
    }
  },
  watch: {
    'editable.positionType'() {
      this.resetStrikeIfStock()
    },
    item: {
      deep: true,
      handler(newValue) {
        this.editable = this.buildEditableState(newValue)
        if (!newValue.isEditing) {
          this.errorMessage = ''
        }
      }
    }
  }
}
</script>

<style scoped>
.position-row td {
  padding: 0.72rem 0.8rem;
  border-bottom: 1px solid #e6edf5;
  color: #1f2933;
  font-size: 0.82rem;
  vertical-align: middle;
}

.type-text {
  text-transform: lowercase;
}

input,
select {
  width: 100%;
  min-width: 90px;
  padding: 0.45rem 0.55rem;
  border-radius: 4px;
  border: 1px solid #cfd7e2;
  font-size: 0.78rem;
  background: #ffffff;
}

input:focus,
select:focus {
  outline: none;
  border-color: #00768d;
  box-shadow: 0 0 0 2px rgba(0, 118, 141, 0.12);
}

input:disabled {
  background: #f4f7fb;
  color: #7b8794;
  cursor: not-allowed;
}

.actions-cell {
  text-align: right;
  white-space: nowrap;
}

.action-btn {
  padding: 0.42rem 0.7rem;
  border-radius: 4px;
  border: 1px solid transparent;
  cursor: pointer;
  font-size: 0.74rem;
  margin-left: 0.45rem;
}

.action-btn:first-child {
  margin-left: 0;
}

.primary-btn {
  background: #00768d;
  color: #ffffff;
  border-color: #00768d;
}

.secondary-btn {
  background: #f7fafc;
  color: #0f4c81;
  border-color: #cfd7e2;
}

.danger-btn {
  background: #fff5f5;
  color: #b42318;
  border-color: #f3c7c7;
}

.error-message {
  margin: 0.5rem 0 0;
  color: #d64045;
  font-size: 0.72rem;
  white-space: normal;
}
</style>
