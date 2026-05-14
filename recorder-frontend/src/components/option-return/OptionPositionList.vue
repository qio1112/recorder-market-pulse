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
        <select v-model="sharedFields.symbol" @focus="loadSymbols" @change="onSymbolChanged(sharedFields.symbol)">
          <option value="" disabled>Select symbol</option>
          <option v-for="symbol in symbols" :key="symbol" :value="symbol">{{ symbol }}</option>
        </select>
      </label>

      <label class="field">
        <span>Expiry</span>
        <select v-model="sharedFields.expiry" :disabled="!sharedFields.symbol" @change="loadOptionHistory">
          <option value="" disabled>Select expiry</option>
          <option v-for="expiry in expiries" :key="expiry.expiry" :value="expiry.expiry">
            {{ expiry.expiry }}{{ expiry.expired ? '' : ' (Unexpired)' }}
          </option>
        </select>
      </label>
    </div>
    <p v-if="loadingExpiries" class="muted small">Loading expiries...</p>
    <p v-if="loadingHistory" class="muted small">Loading option history...</p>

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
          <select v-model="draft.strike" :disabled="draft.positionType === 'stock' || !currentHistory">
            <option value="" disabled>Select strike</option>
            <option v-for="strike in availableStrikes" :key="strike" :value="String(strike)">
              {{ formatStrike(strike) }}
            </option>
          </select>
        </label>

        <label class="field">
          <span>Shares</span>
          <input v-model="draft.shares" type="number" step="1" placeholder="e.g. 1 or -1 for short" />
        </label>

        <label class="field">
          <span>Price</span>
          <input v-model="draft.price" type="number" step="0.01" min="0" placeholder="optional" />
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
            <th>Show</th>
            <th>Type</th>
            <th>Strike</th>
            <th>Shares</th>
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
            @toggle-show="togglePositionShow"
          />
        </tbody>
      </table>
    </div>

    <div v-else class="empty-state">
      No positions added yet. Use the shared inputs and add row above to create the first one.
    </div>

    <option-return-chart :positions="positions" :strategy-history="strategyHistory" />
  </section>
</template>

<script>
import OptionPositionRow from './OptionPositionRow.vue'
import OptionReturnChart from './OptionReturnChart.vue'
import { getOptionExpiries, getOptionSymbols } from '../../api/OptionHistoryService.js'

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
      symbols: [],
      expiries: [],
      symbolsLoaded: false,
      loadingExpiries: false,
      loadingHistory: false,
      draft: {
        positionType: 'stock',
        strike: '',
        shares: '',
        price: ''
      },
      formError: '',
      nextId: 1,
      positions: [],
      stockHistoryBySymbol: {}
    }
  },
  computed: {
    cacheKey() {
      if (!this.sharedFields.symbol || !this.sharedFields.expiry || !this.draft.positionType || this.draft.positionType === 'stock') {
        return ''
      }
      return this.buildCacheKey(this.sharedFields.symbol, this.sharedFields.expiry, this.draft.positionType)
    },
    currentHistory() {
      return this.cacheKey
        ? this.$store.getters['optionHistory/getOptionHistoryByKey'](this.cacheKey)
        : null
    },
    availableStrikes() {
      return (this.currentHistory?.strikes || []).map((item) => item.strike)
    },
    strategyHistory() {
      return this.buildStrategyHistory()
    }
  },
  mounted() {
    this.loadPersistedState()
    if (this.sharedFields.symbol) {
      this.loadStockHistory(this.sharedFields.symbol)
      this.onSymbolChanged(this.sharedFields.symbol, false)
      this.loadPersistedPositionHistory()
    }
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
        if (this.sharedFields.symbol) {
          this.symbols = [this.sharedFields.symbol]
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
          expiry: position.expiry,
          show: position.show
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
      const rawPrice = rawPosition.price
      const priceBlank = rawPrice === null || rawPrice === undefined || String(rawPrice).trim() === ''
      const price = priceBlank ? null : Number.parseFloat(rawPrice)
      const expiry = String(rawPosition.expiry || '').trim()
      const show = rawPosition.show !== false

      if (
        !symbol ||
        !['call', 'put', 'stock'].includes(positionType) ||
        (positionType !== 'stock' && (!Number.isFinite(strike) || strike < 0)) ||
        !Number.isInteger(shares) ||
        (!priceBlank && (!Number.isFinite(price) || price < 0)) ||
        (positionType !== 'stock' && !expiry)
      ) {
        return null
      }

      return {
        symbol,
        positionType,
        strike: positionType === 'stock' ? null : Number(strike.toFixed(1)),
        shares,
        price: priceBlank ? null : Number(price.toFixed(2)),
        expiry: positionType === 'stock' ? '' : expiry,
        show
      }
    },
    addPosition() {
      const normalized = this.normalizePosition({
        symbol: this.sharedFields.symbol,
        expiry: this.sharedFields.expiry,
        ...this.draft
      })

      if (!normalized) {
        this.formError = 'Enter shared symbol and integer shares. Expiry and strike are required for call/put. Price is optional but must be non-negative.'
        return
      }
      if (!this.canAddToCurrentStrategy(normalized)) {
        this.formError = 'Use one symbol and one option expiry for a strategy.'
        return
      }

      const newPosition = {
        id: this.nextId++,
        ...normalized,
        isEditing: false
      }
      this.positions.unshift(newPosition)
      this.loadHistoryForPosition(newPosition).catch(() => null)
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
    togglePositionShow(id) {
      this.positions = this.positions.map((position) => (
        position.id === id ? { ...position, show: !position.show } : position
      ))
    },
    canAddToCurrentStrategy(position) {
      const existingSymbols = new Set(this.positions.map((item) => item.symbol).filter(Boolean))
      if (existingSymbols.size && !existingSymbols.has(position.symbol)) return false
      if (position.positionType === 'stock') return true

      const existingExpiries = new Set(
        this.positions
          .filter((item) => item.positionType !== 'stock')
          .map((item) => item.expiry)
          .filter(Boolean)
      )
      return !existingExpiries.size || existingExpiries.has(position.expiry)
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
    },
    async loadSymbols() {
      if (this.symbolsLoaded) return
      try {
        this.symbols = await getOptionSymbols()
        this.symbolsLoaded = true
      } catch {
        this.formError = 'Failed to load option symbols.'
      }
    },
    async onSymbolChanged(symbol, resetExpiry = true) {
      this.expiries = []
      if (resetExpiry) {
        this.sharedFields.expiry = ''
      }
      this.draft.strike = ''
      if (!symbol) return
      await this.loadStockHistory(symbol)
      this.loadingExpiries = true
      try {
        const expiries = await getOptionExpiries(symbol)
        this.expiries = [...expiries].sort((a, b) => String(b.expiry).localeCompare(String(a.expiry)))
      } catch {
        this.formError = `Failed to load expiries for ${symbol}.`
      } finally {
        this.loadingExpiries = false
      }
    },
    buildCacheKey(symbol, expiry, optionType) {
      return `${symbol}|${expiry}|${optionType}`
    },
    async loadOptionHistory() {
      this.draft.strike = ''
      if (!this.sharedFields.symbol || !this.sharedFields.expiry || this.draft.positionType === 'stock') return
      const key = this.cacheKey
      if (this.$store.getters['optionHistory/getOptionHistoryByKey'](key)) return
      this.loadingHistory = true
      try {
        await this.$store.dispatch('optionHistory/loadOptionHistory', {
          symbol: this.sharedFields.symbol,
          expiry: this.sharedFields.expiry,
          optionType: this.draft.positionType
        })
      } catch {
        this.formError = `Failed to load ${this.sharedFields.symbol} ${this.sharedFields.expiry} ${this.draft.positionType} history.`
      } finally {
        this.loadingHistory = false
      }
    },
    async loadHistoryForPosition(position) {
      if (position.positionType === 'stock') {
        await this.loadStockHistory(position.symbol)
        return
      }
      const key = this.buildCacheKey(position.symbol, position.expiry, position.positionType)
      if (this.$store.getters['optionHistory/getOptionHistoryByKey'](key)) return
      await this.$store.dispatch('optionHistory/loadOptionHistory', {
        symbol: position.symbol,
        expiry: position.expiry,
        optionType: position.positionType
      })
    },
    async loadPersistedPositionHistory() {
      const uniquePositions = Array.from(
        new Map(this.positions.map((position) => [
          `${position.symbol}|${position.expiry}|${position.positionType}`,
          position
        ])).values()
      )
      await Promise.all(uniquePositions.map((position) => this.loadHistoryForPosition(position).catch(() => null)))
    },
    async loadStockHistory(symbol) {
      if (!symbol || this.stockHistoryBySymbol[symbol]) return
      let stockData = this.$store.getters['portfolio/getStockDailyHistoryData'] || {}
      if (!stockData[symbol]) {
        await this.$store.dispatch('portfolio/updateStockHistoryData', [symbol])
        stockData = this.$store.getters['portfolio/getStockDailyHistoryData'] || {}
      }
      if (stockData[symbol]) {
        this.stockHistoryBySymbol = { ...this.stockHistoryBySymbol, [symbol]: stockData[symbol] }
      }
    },
    formatStrike(value) {
      const number = Number(value)
      if (!Number.isFinite(number)) return value
      return Number.isInteger(number) ? String(number) : number.toFixed(1)
    },
    getOptionStrikeData(position) {
      const key = this.buildCacheKey(position.symbol, position.expiry, position.positionType)
      const history = this.$store.getters['optionHistory/getOptionHistoryByKey'](key)
      return (history?.strikes || []).find((item) => Number(item.strike) === Number(position.strike)) || null
    },
    getHistoryArray(history, field) {
      return Array.isArray(history?.[field]) ? history[field] : []
    },
    getOptionMidArray(strikeData) {
      const history = strikeData?.history || {}
      const mid = this.getHistoryArray(history, 'mid')
      if (mid.length) return mid
      const bid = this.getHistoryArray(history, 'bid')
      const ask = this.getHistoryArray(history, 'ask')
      return bid.map((value, index) => {
        const bidValue = Number(value)
        const askValue = Number(ask[index])
        return Number.isFinite(bidValue) && Number.isFinite(askValue) ? (bidValue + askValue) / 2 : null
      })
    },
    getStockDateArray(stockHistory) {
      return stockHistory?.Datetime || stockHistory?.Date || stockHistory?.date || stockHistory?.dates || []
    },
    getStockCloseArray(stockHistory) {
      return stockHistory?.Close || stockHistory?.close || stockHistory?.closePrice || []
    },
    buildStrategyHistory() {
      const enabledPositions = this.positions.filter((position) => position.show !== false)
      if (!enabledPositions.length) return null
      const optionPositions = enabledPositions.filter((position) => position.positionType !== 'stock')
      if (!optionPositions.length) return null

      const optionHistoryByPosition = optionPositions
        .map((position) => {
          const strikeData = this.getOptionStrikeData(position)
          const history = strikeData?.history || {}
          const dates = this.getHistoryArray(history, 'date')
          const mids = this.getOptionMidArray(strikeData)
          const priceByDate = new Map()
          dates.forEach((date, index) => {
            const mid = Number(mids[index])
            if (date && Number.isFinite(mid)) {
              priceByDate.set(date, mid)
            }
          })
          return { position, priceByDate }
        })
        .filter((item) => item.priceByDate.size)
      if (optionHistoryByPosition.length !== optionPositions.length) return null

      const validDates = optionHistoryByPosition
        .slice(1)
        .reduce((dates, item) => dates.filter((date) => item.priceByDate.has(date)), Array.from(optionHistoryByPosition[0].priceByDate.keys()))
        .sort((a, b) => String(a).localeCompare(String(b)))
      if (!validDates.length) return null
      const validDateSet = new Set(validDates)

      const strategyByDate = new Map()
      optionHistoryByPosition.forEach(({ position, priceByDate }) => {
        validDates.forEach((date) => {
          const mid = priceByDate.get(date)
          const contribution = Number(position.shares) * 100 * mid
          this.addStrategyPoint(strategyByDate, date, contribution, {
            label: this.buildOptionLegLabel(position),
            price: mid,
            shares: position.shares
          })
        })
      })

      enabledPositions.filter((position) => position.positionType === 'stock').forEach((position) => {
        const stockHistory = this.stockHistoryBySymbol[position.symbol]
        const dates = this.getStockDateArray(stockHistory)
        const closes = this.getStockCloseArray(stockHistory)
        dates.forEach((date, index) => {
          const close = Number(closes[index])
          if (!date || !validDateSet.has(date) || !Number.isFinite(close)) return
          const contribution = Number(position.shares) * close
          this.addStrategyPoint(strategyByDate, date, contribution, {
            label: `${position.symbol} Stock`,
            price: close,
            shares: position.shares
          })
        })
      })

      const expiry = enabledPositions.find((position) => position.positionType !== 'stock')?.expiry || this.sharedFields.expiry || ''
      const data = validDates
        .map((date) => [date, strategyByDate.get(date)])
        .filter(([, item]) => item)
        .map(([date, item]) => ({
          value: [date, Number(item.total.toFixed(2))],
          expiry,
          legs: item.legs
        }))
      return data.length ? data : null
    },
    addStrategyPoint(strategyByDate, date, contribution, leg) {
      const current = strategyByDate.get(date) || { total: 0, legs: [] }
      current.total += contribution
      current.legs.push({
        ...leg,
        price: Number(leg.price.toFixed(3))
      })
      strategyByDate.set(date, current)
    },
    buildOptionLegLabel(position) {
      const type = position.positionType === 'call' ? 'Call' : 'Put'
      return `${position.symbol} ${type} ${this.formatStrike(position.strike)}`
    }
  },
  watch: {
    'draft.positionType'() {
      this.resetDraftStrikeIfStock()
      this.loadOptionHistory()
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
  border-radius: 4px;
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

.muted.small {
  margin: -0.35rem 0 0.55rem;
  color: #52606d;
  font-size: 0.76rem;
}

.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.25rem 0.6rem;
  border-radius: 4px;
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
  border-radius: 4px;
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
  border-radius: 4px;
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
  border-radius: 4px;
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
  border-radius: 4px;
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
  min-width: 980px;
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
  border-radius: 4px;
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
