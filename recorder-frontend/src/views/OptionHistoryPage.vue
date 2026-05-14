<template>
  <section class="option-history-page">
    <header class="head">
      <h1>Option History</h1>
      <p>Review option prices, Greeks, and stock-price context.</p>
      <p v-if="error" class="error">{{ error }}</p>
    </header>

    <section class="selection-panel">
      <div class="field-row">
        <label class="field">
          <span>Symbol</span>
          <select v-model="selectedSymbol" @focus="loadSymbols">
            <option value="" disabled>Select symbol</option>
            <option v-for="sym in symbols" :key="sym" :value="sym">{{ sym }}</option>
          </select>
        </label>
        <label class="field expiry-field">
          <span>Expiry</span>
          <select v-model="selectedExpiry" :disabled="!selectedSymbol" @change="loadOptionHistory">
            <option value="" disabled>Select expiry</option>
            <option v-for="expiry in expiries" :key="expiry.expiry" :value="expiry.expiry">
              {{ expiry.expiry }}{{ expiry.expired ? '' : ' (Unexpired)' }}
            </option>
          </select>
        </label>
        <label class="field compact">
          <span>Type</span>
          <select v-model="selectedOptionType" @change="loadOptionHistory">
            <option value="call">Call</option>
            <option value="put">Put</option>
          </select>
        </label>
        <label class="field">
          <span>Strike</span>
          <select v-model="selectedStrike">
            <option value="" disabled>Select strike</option>
            <option v-for="strike in availableStrikes" :key="strike" :value="String(strike)">
              {{ formatNumber(strike) }}
            </option>
          </select>
        </label>
        <button type="button" class="primary" :disabled="!canAddSelectedOption" @click="addSelectedOption">
          Add Option
        </button>
      </div>
      <p v-if="loadingExpiries" class="muted small">Loading expiries...</p>
      <p v-if="loadingHistory" class="muted small">Loading option history...</p>
    </section>

    <div class="content-layout">
      <aside class="left-panel">
        <selected-option-list
          :options="selectedOptions"
          @toggle-show="toggleSelectedOption"
          @remove="removeSelectedOption"
        />
      </aside>

      <main class="chart-panel">
        <dashboard-item title="Option Mid Price History">
          <option-history-line-chart :option="midPriceChartOption" height="300px" />
        </dashboard-item>

        <dashboard-item title="Implied Volatility History">
          <option-history-line-chart :option="impliedVolatilityChartOption" height="280px" />
        </dashboard-item>

        <section class="section-toolbar">
          <h2>Greeks</h2>
          <button type="button" class="secondary" @click="showGreekCharts = !showGreekCharts">
            {{ showGreekCharts ? 'Hide' : 'Show' }}
          </button>
        </section>
        <div v-if="showGreekCharts" class="chart-grid">
          <dashboard-item v-for="greek in greekFields" :key="greek.key" :title="greek.label">
            <option-history-line-chart :option="buildMetricChartOption(greek.key, greek.label)" height="220px" />
          </dashboard-item>
        </div>

        <section class="section-toolbar">
          <h2>Option vs Stock</h2>
        </section>
        <div class="chart-grid">
          <dashboard-item
            v-for="option in visibleSelectedOptions"
            :key="`stock-${option.id}`"
            :title="option.label"
            subtitle="Option mid price and stock price"
          >
            <option-history-line-chart :option="buildOptionStockChart(option)" height="230px" />
          </dashboard-item>
        </div>
      </main>
    </div>
  </section>
</template>

<script>
import { defineComponent } from 'vue'
import DashboardItem from '../components/portfolio/DashboardItem.vue'
import OptionHistoryLineChart from '../components/option-history/OptionHistoryLineChart.vue'
import SelectedOptionList from '../components/option-history/SelectedOptionList.vue'
import { getOptionExpiries, getOptionSymbols } from '../api/OptionHistoryService.js'

const OPTION_HISTORY_SELECTION_STORAGE_KEY = 'recorder.optionHistory.selectedOptions'

export default defineComponent({
  name: 'OptionHistoryPage',
  components: {
    DashboardItem,
    OptionHistoryLineChart,
    SelectedOptionList
  },
  data() {
    return {
      symbols: [],
      expiries: [],
      selectedSymbol: '',
      selectedExpiry: '',
      selectedOptionType: 'call',
      selectedStrike: '',
      selectedOptions: [],
      symbolsLoaded: false,
      loadingExpiries: false,
      loadingHistory: false,
      showGreekCharts: true,
      error: '',
      greekFields: [
        { key: 'delta', label: 'Delta' },
        { key: 'gamma', label: 'Gamma' },
        { key: 'theta', label: 'Theta' },
        { key: 'vega', label: 'Vega' },
        { key: 'rho', label: 'Rho' }
      ]
    }
  },
  computed: {
    cacheKey() {
      if (!this.selectedSymbol || !this.selectedExpiry || !this.selectedOptionType) return '';
      return this.buildCacheKey(this.selectedSymbol, this.selectedExpiry, this.selectedOptionType);
    },
    currentHistory() {
      return this.cacheKey
        ? this.$store.getters['optionHistory/getOptionHistoryByKey'](this.cacheKey)
        : null;
    },
    availableStrikes() {
      return (this.currentHistory?.strikes || []).map((item) => item.strike);
    },
    canAddSelectedOption() {
      return Boolean(this.currentHistory && this.selectedStrike);
    },
    visibleSelectedOptions() {
      return this.selectedOptions.filter((option) => option.show);
    },
    midPriceChartOption() {
      return this.buildMetricChartOption('mid', 'Mid Price');
    },
    impliedVolatilityChartOption() {
      return this.buildMetricChartOption('impliedVolatility', 'IV');
    }
  },
  watch: {
    selectedSymbol(newSymbol) {
      this.onSymbolChanged(newSymbol);
    }
  },
  mounted() {
    this.loadPersistedSelectedOptions();
  },
  methods: {
    loadPersistedSelectedOptions() {
      const raw = window.localStorage.getItem(OPTION_HISTORY_SELECTION_STORAGE_KEY);
      if (!raw) return;
      let items = [];
      try {
        items = JSON.parse(raw);
      } catch {
        window.localStorage.removeItem(OPTION_HISTORY_SELECTION_STORAGE_KEY);
        return;
      }
      if (!Array.isArray(items)) return;
      items.forEach((item) => {
        this.restoreSelectedOption(item);
      });
    },
    async restoreSelectedOption(item) {
      const normalized = this.normalizePersistedSelectedOption(item);
      if (!normalized) return;
      try {
        const history = await this.$store.dispatch('optionHistory/loadOptionHistory', {
          symbol: normalized.symbol,
          expiry: normalized.expiry,
          optionType: normalized.optionType
        });
        const strikeData = (history?.strikes || []).find(
          (strikeItem) => Number(strikeItem.strike) === Number(normalized.strike)
        );
        if (!strikeData || this.selectedOptions.some((option) => option.id === normalized.id)) return;
        this.selectedOptions.push({
          ...normalized,
          label: this.buildSelectedOptionLabel(normalized.symbol, normalized.expiry, normalized.optionType, normalized.strike),
          strikeData
        });
      } catch {
        this.error = 'Failed to restore saved option selections.';
      }
    },
    normalizePersistedSelectedOption(item) {
      const symbol = String(item?.symbol || '').trim().toUpperCase();
      const expiry = String(item?.expiry || '').trim();
      const optionType = String(item?.optionType || '').trim().toLowerCase();
      const strike = Number(item?.strike);
      const show = item?.show !== false;
      if (!symbol || !expiry || !['call', 'put'].includes(optionType) || !Number.isFinite(strike)) {
        return null;
      }
      return {
        id: this.buildSelectedOptionId(symbol, expiry, optionType, strike),
        symbol,
        expiry,
        optionType,
        strike,
        show
      };
    },
    persistSelectedOptions() {
      const payload = this.selectedOptions.map((option) => ({
        symbol: option.symbol,
        expiry: option.expiry,
        optionType: option.optionType,
        strike: option.strike,
        show: option.show
      }));
      window.localStorage.setItem(OPTION_HISTORY_SELECTION_STORAGE_KEY, JSON.stringify(payload));
    },
    async loadSymbols() {
      if (this.symbolsLoaded) return;
      this.error = '';
      try {
        this.symbols = await getOptionSymbols();
        this.symbolsLoaded = true;
      } catch {
        this.error = 'Failed to load option symbols.';
      }
    },
    async onSymbolChanged(symbol) {
      this.expiries = [];
      this.selectedExpiry = '';
      this.selectedStrike = '';
      if (!symbol) return;
      this.loadingExpiries = true;
      this.error = '';
      try {
        const expiries = await getOptionExpiries(symbol);
        this.expiries = [...expiries].sort((a, b) => String(b.expiry).localeCompare(String(a.expiry)));
      } catch {
        this.error = `Failed to load expiries for ${symbol}.`;
      } finally {
        this.loadingExpiries = false;
      }
    },
    buildCacheKey(symbol, expiry, optionType) {
      return `${symbol}|${expiry}|${optionType}`;
    },
    async loadOptionHistory() {
      this.selectedStrike = '';
      if (!this.selectedSymbol || !this.selectedExpiry || !this.selectedOptionType) return;
      const key = this.cacheKey;
      if (this.$store.getters['optionHistory/getOptionHistoryByKey'](key)) return;
      this.loadingHistory = true;
      this.error = '';
      try {
        await this.$store.dispatch('optionHistory/loadOptionHistory', {
          symbol: this.selectedSymbol,
          expiry: this.selectedExpiry,
          optionType: this.selectedOptionType
        });
      } catch {
        this.error = `Failed to load ${this.selectedSymbol} ${this.selectedExpiry} ${this.selectedOptionType} history.`;
      } finally {
        this.loadingHistory = false;
      }
    },
    addSelectedOption() {
      const strike = Number(this.selectedStrike);
      const strikeData = (this.currentHistory?.strikes || []).find((item) => Number(item.strike) === strike);
      if (!strikeData) return;
      const id = this.buildSelectedOptionId(
        this.selectedSymbol,
        this.selectedExpiry,
        this.selectedOptionType,
        strike
      );
      if (this.selectedOptions.some((option) => option.id === id)) return;
      this.selectedOptions.push({
        id,
        label: this.buildSelectedOptionLabel(this.selectedSymbol, this.selectedExpiry, this.selectedOptionType, strike),
        symbol: this.selectedSymbol,
        expiry: this.selectedExpiry,
        optionType: this.selectedOptionType,
        strike,
        show: true,
        strikeData
      });
      this.persistSelectedOptions();
    },
    buildSelectedOptionLabel(symbol, expiry, optionType, strike) {
      return `${symbol} ${expiry} ${optionType.toUpperCase()} ${this.formatStrikeForTitle(strike)}`;
    },
    buildSelectedOptionId(symbol, expiry, optionType, strike) {
      return `${symbol}|${expiry}|${optionType}|${strike}`;
    },
    toggleSelectedOption(id) {
      const option = this.selectedOptions.find((item) => item.id === id);
      if (option) {
        option.show = !option.show;
        this.persistSelectedOptions();
      }
    },
    removeSelectedOption(id) {
      this.selectedOptions = this.selectedOptions.filter((option) => option.id !== id);
      this.persistSelectedOptions();
    },
    getHistoryArray(option, field) {
      const history = option?.strikeData?.history || {};
      return Array.isArray(history[field]) ? history[field] : [];
    },
    getDateArray(option) {
      return this.getHistoryArray(option, 'date');
    },
    getMidPriceArray(option) {
      const mid = this.getHistoryArray(option, 'mid');
      if (mid.length) return mid;
      const bid = this.getHistoryArray(option, 'bid');
      const ask = this.getHistoryArray(option, 'ask');
      return bid.map((value, index) => {
        const bidValue = Number(value);
        const askValue = Number(ask[index]);
        return Number.isFinite(bidValue) && Number.isFinite(askValue)
          ? this.roundNumber((bidValue + askValue) / 2, 3)
          : null;
      });
    },
    buildMetricPoints(option, field) {
      const dates = this.getDateArray(option);
      const values = field === 'mid' ? this.getMidPriceArray(option) : this.getHistoryArray(option, field);
      const digits = this.getDigitsForField(field);
      return dates
        .map((date, index) => ({
          value: [date, this.toNumberOrNull(values[index], digits)],
          optionExpiry: option.expiry,
          valueDigits: digits
        }))
        .filter((point) => point.value[0] && point.value[1] !== null);
    },
    toNumberOrNull(value, digits = 3) {
      const number = Number(value);
      return Number.isFinite(number) ? this.roundNumber(number, digits) : null;
    },
    roundNumber(value, digits = 3) {
      return Number(Number(value).toFixed(digits));
    },
    formatNumber(value, digits = 3) {
      const number = Number(value);
      return Number.isFinite(number) ? number.toFixed(digits) : value;
    },
    getDigitsForField(field) {
      if (field === 'impliedVolatility') return 4;
      return this.greekFields.some((greek) => greek.key === field) ? 4 : 3;
    },
    formatStrikeForTitle(value) {
      const number = Number(value);
      if (!Number.isFinite(number)) return value;
      return Number.isInteger(number) ? String(number) : number.toFixed(1);
    },
    buildMetricChartOption(field, label) {
      const valueDigits = this.getDigitsForField(field);
      const series = this.visibleSelectedOptions
        .map((option) => ({
          name: option.label,
          type: 'line',
          smooth: true,
          showSymbol: false,
          optionExpiry: option.expiry,
          valueDigits,
          data: this.buildMetricPoints(option, field)
        }))
        .filter((item) => item.data.length);
      return this.buildSharedLineOption(label, series);
    },
    buildSharedLineOption(label, series) {
      if (!series.length) return null;
      const dates = Array.from(new Set(series.flatMap((item) => item.data.map((point) => this.getPointDate(point))))).sort();
      return {
        tooltip: { trigger: 'axis', formatter: this.formatTooltipValues },
        legend: { type: 'scroll', top: 0 },
        grid: { left: '7%', right: '4%', top: '18%', bottom: '10%' },
        xAxis: { type: 'category', data: dates },
        yAxis: { type: 'value', name: label, scale: true },
        series
      };
    },
    buildOptionStockChart(option) {
      const dates = this.getDateArray(option);
      const mid = this.getMidPriceArray(option);
      const stock = this.getHistoryArray(option, 'stockPrice');
      const optionPoints = [];
      const stockPoints = [];
      dates.forEach((date, index) => {
        const midValue = this.toNumberOrNull(mid[index], 3);
        const stockValue = this.toNumberOrNull(stock[index], 3);
        if (date && midValue !== null) {
          optionPoints.push({ value: [date, midValue], optionExpiry: option.expiry, valueDigits: 3 });
        }
        if (date && stockValue !== null) {
          stockPoints.push({ value: [date, stockValue], optionExpiry: option.expiry, valueDigits: 3 });
        }
      });
      if (!optionPoints.length || !stockPoints.length) return null;
      return {
        tooltip: { trigger: 'axis', formatter: this.formatTooltipValues },
        legend: { top: 0 },
        grid: { left: '8%', right: '8%', top: '18%', bottom: '10%' },
        xAxis: { type: 'category', data: dates },
        yAxis: [
          { type: 'value', name: 'Option', scale: true },
          { type: 'value', name: 'Stock', scale: true }
        ],
        series: [
          {
            name: 'Option Mid',
            type: 'line',
            smooth: true,
            showSymbol: false,
            optionExpiry: option.expiry,
            data: optionPoints,
            yAxisIndex: 0
          },
          {
            name: `${option.symbol} Stock`,
            type: 'line',
            smooth: true,
            showSymbol: false,
            optionExpiry: option.expiry,
            data: stockPoints,
            yAxisIndex: 1
          }
        ]
      };
    },
    formatTooltipValues(params) {
      if (!params || !params.length) return '';
      const date = params[0].axisValueLabel || params[0].name || '';
      const expiry = params.find((param) => param.data?.optionExpiry)?.data?.optionExpiry;
      const daysToExpiry = this.daysBetween(date, expiry);
      const dateLabel = daysToExpiry === null ? date : `${date} (${daysToExpiry} days to expiry)`;
      const lines = params.map((param) => {
        const rawValue = this.getPointValue(param.data);
        const digits = param.data?.valueDigits ?? 3;
        return `${param.seriesName}: ${this.formatNumber(rawValue, digits)}`;
      });
      return `${dateLabel}<br/>${lines.join('<br/>')}`;
    },
    getPointDate(point) {
      if (Array.isArray(point)) return point[0];
      if (Array.isArray(point?.value)) return point.value[0];
      return '';
    },
    getPointValue(point) {
      if (Array.isArray(point)) return point[1];
      if (Array.isArray(point?.value)) return point.value[1];
      return point;
    },
    daysBetween(date, expiry) {
      if (!date || !expiry) return null;
      const start = new Date(`${date}T00:00:00`);
      const end = new Date(`${expiry}T00:00:00`);
      if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return null;
      const oneDayMs = 24 * 60 * 60 * 1000;
      return Math.max(0, Math.round((end.getTime() - start.getTime()) / oneDayMs));
    }
  }
})
</script>

<style scoped>
.option-history-page {
  max-width: 1320px;
  margin: 1rem auto;
  padding: 0 0.6rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.head {
  margin-bottom: 0;
}

.head h1 {
  margin: 0 0 0.25rem;
  color: #0f4c81;
  font-size: 1.4rem;
}

.head p,
.muted {
  margin: 0;
  color: #52606d;
  font-size: 0.88rem;
}

.muted.small {
  font-size: 0.82rem;
  margin-top: 0.45rem;
}

.error {
  color: #b42318;
  background: #fff5f5;
  border: 1px solid #f2b8b5;
  border-radius: 4px;
  padding: 0.5rem 0.65rem;
  font-size: 0.84rem;
  margin-top: 0.5rem;
}

.selection-panel {
  border: 1px solid #d9e2ec;
  border-radius: 4px;
  background: #ffffff;
  padding: 0.75rem;
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(210px, 260px) 1fr;
  gap: 0.75rem;
  align-items: start;
}

.left-panel {
  position: sticky;
  top: 0.75rem;
}

.chart-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.field-row {
  display: flex;
  align-items: flex-end;
  gap: 0.55rem;
  flex-wrap: wrap;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.28rem;
  color: #52606d;
  font-weight: 700;
  font-size: 0.78rem;
}

.field.compact select {
  min-width: 8rem;
}

.expiry-field select {
  width: 15.5rem;
  min-width: 15.5rem;
}

select {
  min-width: 10.5rem;
  border: 1px solid #d9e2ec;
  border-radius: 4px;
  padding: 0.42rem 0.55rem;
  background: #ffffff;
  color: #486581;
  font: inherit;
  font-size: 0.8rem;
}

select:focus {
  outline: none;
  border-color: #9fb3c8;
  color: #334e68;
}

select:disabled {
  background: #f8fafc;
  color: #9fb3c8;
}

.primary,
.secondary {
  min-height: 2.15rem;
  border-radius: 4px;
  padding: 0.4rem 0.65rem;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
}

.primary {
  border: 1px solid #0f4c81;
  background: #0f4c81;
  color: #ffffff;
}

.primary:disabled {
  border-color: #bcccdc;
  background: #d9e2ec;
  color: #627d98;
  cursor: not-allowed;
}

.secondary {
  border: 1px solid #bcccdc;
  background: #ffffff;
  color: #243b53;
}

.section-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.55rem;
}

.section-toolbar h2 {
  margin: 0;
  color: #0f4c81;
  font-size: 1rem;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 0.75rem;
}

@media (max-width: 640px) {
  .content-layout {
    grid-template-columns: 1fr;
  }

  .left-panel {
    position: static;
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }

  select {
    min-width: 100%;
  }

  .field,
  .field-row {
    width: 100%;
  }
}
</style>
