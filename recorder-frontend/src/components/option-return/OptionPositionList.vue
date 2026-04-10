<template>
  <section class="position-list-card">
    <div class="section-head">
      <div>
        <h2>Underlying Positions</h2>
        <p>Add stock or option rows for the expiry model input.</p>
      </div>
      <span class="count-badge">{{ positions.length }} saved</span>
    </div>

    <div class="shared-inputs">
      <label class="field">
        <span>Symbol</span>
        <input v-model.trim="sharedFields.symbol" type="text" placeholder="e.g. AAPL" />
      </label>

      <label class="field">
        <span>Expiry</span>
        <input v-model="sharedFields.expiry" type="date" />
      </label>
    </div>

    <form class="composer" @submit.prevent="addPosition">
      <div class="row-grid">
        <label class="field">
          <span>Type</span>
          <select v-model="draft.positionType">
            <option value="call">call</option>
            <option value="put">put</option>
            <option value="stock">stock</option>
          </select>
        </label>

        <label class="field">
          <span>Strike</span>
          <input
            v-model="draft.strike"
            type="number"
            step="0.1"
            min="0"
            placeholder="e.g. 150.0"
            :disabled="draft.positionType === 'stock'"
          />
        </label>

        <label class="field">
          <span>Quantity</span>
          <input v-model="draft.shares" type="number" step="1" placeholder="e.g. 1 or -1 for short" />
        </label>

        <label class="field">
          <span>Price</span>
          <input v-model="draft.price" type="number" step="0.01" min="0" placeholder="e.g. 2.35" />
        </label>
      </div>

      <p class="shared-note">
        New rows will use symbol <strong>{{ sharedFields.symbol || '...' }}</strong> and expiry
        <strong>{{ sharedFields.expiry || '...' }}</strong>.
      </p>

      <div class="composer-actions">
        <button type="submit" class="primary-btn">Add Position</button>
        <button type="button" class="secondary-btn" @click="resetAll">Reset</button>
        <p v-if="formError" class="error-message">{{ formError }}</p>
      </div>
    </form>

    <div v-if="positions.length" class="table-scroll">
      <table class="positions-table">
        <thead>
          <tr>
            <th>Symbol</th>
            <th>Type</th>
            <th>Strike</th>
            <th>Quantity</th>
            <th>Price</th>
            <th>Expiry</th>
            <th class="actions-col">Actions</th>
          </tr>
        </thead>
        <tbody>
          <option-position-row
            v-for="position in positions"
            :key="position.id"
            :item="position"
            @edit="startEditing"
            @save="savePosition"
            @cancel="cancelEditing"
            @remove="removePosition"
          />
        </tbody>
      </table>
    </div>

    <div v-else class="empty-state">
      No positions added yet. Use the shared inputs and add row above to create the first one.
    </div>

    <option-return-chart :positions="positions" />
  </section>
</template>

<script>
import OptionPositionRow from './OptionPositionRow.vue'
import OptionReturnChart from './OptionReturnChart.vue'

const OPTION_RETURN_STORAGE_KEY = 'recorder.optionReturn.positions'

export default {
  name: 'OptionPositionList',
  components: {
    OptionPositionRow,
    OptionReturnChart
  },
  data() {
    return {
      sharedFields: {
        symbol: '',
        expiry: ''
      },
      draft: {
        positionType: 'stock',
        strike: '',
        shares: '',
        price: ''
      },
      formError: '',
      nextId: 1,
      positions: []
    }
  },
  mounted() {
    this.loadPersistedState()
  },
  methods: {
    loadPersistedState() {
      const raw = window.localStorage.getItem(OPTION_RETURN_STORAGE_KEY)
      if (!raw) return

      try {
        const parsed = JSON.parse(raw)
        const persistedPositions = Array.isArray(parsed.positions) ? parsed.positions : []
        const normalizedPositions = persistedPositions
          .map((position, index) => {
            const normalized = this.normalizePosition(position)
            if (!normalized) return null
            return {
              id: Number(position.id) || index + 1,
              ...normalized,
              isEditing: false
            }
          })
          .filter(Boolean)

        this.sharedFields = {
          symbol: String(parsed.sharedFields?.symbol || '').trim().toUpperCase(),
          expiry: String(parsed.sharedFields?.expiry || '').trim()
        }
        this.draft = {
          positionType: ['call', 'put', 'stock'].includes(parsed.draft?.positionType)
            ? parsed.draft.positionType
            : 'stock',
          strike: parsed.draft?.positionType === 'stock' ? '' : String(parsed.draft?.strike ?? ''),
          shares: String(parsed.draft?.shares ?? ''),
          price: String(parsed.draft?.price ?? '')
        }
        this.positions = normalizedPositions
        this.nextId = normalizedPositions.reduce((maxId, position) => Math.max(maxId, Number(position.id) || 0), 0) + 1
      } catch (_) {
        window.localStorage.removeItem(OPTION_RETURN_STORAGE_KEY)
      }
    },
    persistState() {
      const payload = {
        sharedFields: this.sharedFields,
        draft: this.draft,
        positions: this.positions.map((position) => ({
          id: position.id,
          symbol: position.symbol,
          positionType: position.positionType,
          strike: position.strike,
          shares: position.shares,
          price: position.price,
          expiry: position.expiry
        }))
      }

      window.localStorage.setItem(OPTION_RETURN_STORAGE_KEY, JSON.stringify(payload))
    },
    getEmptyDraft() {
      return {
        positionType: 'stock',
        strike: '',
        shares: '',
        price: ''
      }
    },
    normalizePosition(rawPosition) {
      const symbol = String(rawPosition.symbol || '').trim().toUpperCase()
      const positionType = String(rawPosition.positionType || '').trim().toLowerCase()
      const strike = Number.parseFloat(rawPosition.strike)
      const shares = Number.parseInt(rawPosition.shares, 10)
      const price = Number.parseFloat(rawPosition.price)
      const expiry = String(rawPosition.expiry || '').trim()

      if (
        !symbol ||
        !['call', 'put', 'stock'].includes(positionType) ||
        (positionType !== 'stock' && (!Number.isFinite(strike) || strike < 0)) ||
        !Number.isInteger(shares) ||
        !Number.isFinite(price) ||
        price < 0 ||
        !expiry
      ) {
        return null
      }

      return {
        symbol,
        positionType,
        strike: positionType === 'stock' ? null : Number(strike.toFixed(1)),
        shares,
        price: Number(price.toFixed(2)),
        expiry
      }
    },
    addPosition() {
      const normalized = this.normalizePosition({
        symbol: this.sharedFields.symbol,
        expiry: this.sharedFields.expiry,
        ...this.draft
      })

      if (!normalized) {
        this.formError = 'Enter shared symbol, shared expiry, integer quantity, and non-negative price. Strike is required for call/put.'
        return
      }

      this.positions.unshift({
        id: this.nextId++,
        ...normalized,
        isEditing: false
      })
      this.formError = ''
      this.draft = this.getEmptyDraft()
    },
    startEditing(id) {
      this.positions = this.positions.map((position) => ({
        ...position,
        isEditing: position.id === id
      }))
    },
    savePosition(updatedPosition) {
      const current = this.positions.find((position) => position.id === updatedPosition.id)
      if (!current) return

      const normalized = this.normalizePosition({
        symbol: current.symbol,
        expiry: current.expiry,
        ...updatedPosition
      })
      if (!normalized) return

      this.positions = this.positions.map((position) => {
        if (position.id !== updatedPosition.id) {
          return { ...position, isEditing: false }
        }

        return {
          ...position,
          ...normalized,
          isEditing: false
        }
      })
    },
    cancelEditing() {
      this.positions = this.positions.map((position) => ({
        ...position,
        isEditing: false
      }))
    },
    removePosition(id) {
      this.positions = this.positions.filter((position) => position.id !== id)
    },
    resetAll() {
      this.sharedFields = {
        symbol: '',
        expiry: ''
      }
      this.draft = this.getEmptyDraft()
      this.formError = ''
      this.nextId = 1
      this.positions = []
      window.localStorage.removeItem(OPTION_RETURN_STORAGE_KEY)
    },
    resetDraftStrikeIfStock() {
      if (this.draft.positionType === 'stock') {
        this.draft.strike = ''
      }
    }
  },
  watch: {
    'draft.positionType'() {
      this.resetDraftStrikeIfStock()
    },
    sharedFields: {
      deep: true,
      handler() {
        this.persistState()
      }
    },
    draft: {
      deep: true,
      handler() {
        this.persistState()
      }
    },
    positions: {
      deep: true,
      handler() {
        this.persistState()
      }
    }
  }
}
</script>

<style scoped>
.position-list-card {
  border: 1px solid #cfd7e2;
  border-radius: 12px;
  background: #ffffff;
  padding: 1rem;
  box-shadow: 0 6px 18px rgba(15, 76, 129, 0.05);
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 0.8rem;
}

.section-head h2 {
  margin: 0;
  color: #0f4c81;
  font-size: 1.15rem;
}

.section-head p {
  margin: 0.2rem 0 0;
  color: #52606d;
  font-size: 0.82rem;
}

.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.25rem 0.6rem;
  border-radius: 999px;
  background: #e5f3ff;
  color: #0f4c81;
  border: 1px solid #cde7ff;
  font-size: 0.74rem;
  white-space: nowrap;
}

.shared-inputs,
.row-grid {
  display: grid;
  gap: 0.75rem;
}

.shared-inputs {
  grid-template-columns: minmax(140px, 1fr) minmax(180px, 1fr);
  margin-bottom: 0.8rem;
}

.composer {
  padding: 0.85rem;
  border-radius: 10px;
  background: #f7fafc;
  border: 1px solid #d9e2ec;
}

.row-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-items: end;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.field span {
  font-size: 0.7rem;
  font-weight: 600;
  color: #1f2933;
}

input,
select {
  width: 100%;
  padding: 0.48rem 0.6rem;
  border-radius: 7px;
  border: 1px solid #cfd7e2;
  font-size: 0.8rem;
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

.shared-note {
  margin: 0.7rem 0 0;
  color: #52606d;
  font-size: 0.76rem;
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.75rem;
}

.primary-btn {
  padding: 0.5rem 0.85rem;
  border-radius: 7px;
  border: 1px solid #00768d;
  background: #00768d;
  color: #ffffff;
  cursor: pointer;
  font-size: 0.76rem;
}

.primary-btn:hover,
.primary-btn:active {
  background: #0f4c81;
  border-color: #0f4c81;
}

.secondary-btn {
  padding: 0.5rem 0.85rem;
  border-radius: 7px;
  border: 1px solid #cfd7e2;
  background: #f7fafc;
  color: #0f4c81;
  cursor: pointer;
  font-size: 0.76rem;
}

.secondary-btn:hover,
.secondary-btn:active {
  background: #eef4f8;
}

.error-message {
  margin: 0;
  color: #d64045;
  font-size: 0.75rem;
}

.table-scroll {
  margin-top: 0.8rem;
  overflow-x: auto;
}

.positions-table {
  width: 100%;
  min-width: 940px;
  border-collapse: collapse;
}

.positions-table th {
  padding: 0.7rem 0.8rem 0.45rem;
  color: #52606d;
  font-size: 0.7rem;
  font-weight: 700;
  text-align: left;
  border-bottom: 1px solid #d9e2ec;
}

.positions-table th.actions-col {
  text-align: right;
}

.empty-state {
  margin-top: 0.8rem;
  padding: 1rem 0.85rem;
  border: 1px dashed #cfd7e2;
  border-radius: 10px;
  color: #52606d;
  background: #fbfdff;
  text-align: center;
  font-size: 0.82rem;
}

@media (max-width: 860px) {
  .shared-inputs,
  .row-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .position-list-card,
  .composer {
    padding: 0.85rem;
  }

  .section-head,
  .composer-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .shared-inputs,
  .row-grid {
    grid-template-columns: 1fr;
  }
}
</style>
