<template>
  <section class="portfolio">
    <header class="head">
      <h1>Portfolio Overview</h1>
      <p class="muted">Trades aggregated from records metadata</p>
      <p class="muted" v-if="hasInvalidSymbols">Invalid Symbols: {{ invalidSymbols.join(',') }}</p>
    </header>

    <div class="chart-grid single">
      <dashboard-item title="TotalPortfolio">
        <div class="controls">
          <label class="control-label">
            <span>Range</span>
            <select v-model="selectedDateRange" class="range-select">
              <option value="1m">1 Month</option>
              <option value="ytd">YTD</option>
              <option value="1y">1 Year</option>
              <option value="all">All</option>
            </select>
          </label>
          <label class="control-label">
            <input type="checkbox" v-model="showTotalAggregate" />
            Show total line
          </label>
        </div>
        <v-chart
          v-if="chartOptions.totalPortfolio"
          :option="chartOptions.totalPortfolio"
          autoresize
          class="chart"
        />
        <p v-else class="muted small">Load data to see chart</p>
      </dashboard-item>
    </div>

    <div class="chart-grid multi">
      <dashboard-item title="Allocation (latest)" subtitle="Share of total stock value">
        <v-chart v-if="donutOption" :option="donutOption" autoresize class="chart" />
        <p v-else class="muted small">Load data to see chart</p>
      </dashboard-item>
      <dashboard-item v-for="metric in otherMetrics" :key="metric" :title="metric">
        <v-chart v-if="chartOptions[metric]" :option="chartOptions[metric]" autoresize class="chart" />
        <p v-else class="muted small">Load data to see chart</p>
      </dashboard-item>
    </div>
  </section>
</template>

<script>
import { defineComponent } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DatasetComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import DashboardItem from '../components/portfolio/DashboardItem.vue'
import { getRecords, ListRecordRequest } from '../api/RecordService.js'
import { validateTrade, recordGroupBySymbolAndSort, enrichAccumulativeTradeData, aggregateMetricAcrossSymbols } from '../utils/portfolioUtils.js'

use([
  CanvasRenderer,
  LineChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DatasetComponent
])

export default defineComponent({
  name: 'PortfolioPage',
  components: { VChart, DashboardItem },
  data() {
    return {
      metrics: [
        'totalPortfolio',
        'shares',
        'cashFlow',
        'cash',
        'averageCostPerShare',
        'totalCost',
        'realizedPnL',
        'unrealizedPnL',
        'totalStockValue'
      ],
      chartOptions: {},
      enrichedPortfolioTradeData: {},
      aggregatedMetrics: {},
      donutOption: null,
      showTotalAggregate: true,
      selectedDateRange: '1m',
      selectedSymbols: [],
      invalidSymbols: []
    }
  },
  computed: {
    otherMetrics() {
      return this.metrics.filter((m) => m !== 'totalPortfolio');
    },
    hasInvalidSymbols() {
      return this.invalidSymbols && this.invalidSymbols.length > 0;
    },
    inactiveSymbols() {
      return Object.keys(this.enrichedPortfolioTradeData).filter((sym) => {
        const shares = this.enrichedPortfolioTradeData[sym]?.shares;
        if (!Array.isArray(shares) || !shares.length) return false;
        return Number(shares.at(-1)) === 0;
      });
    }
  },
  mounted() {
    this.getSourceData();
  },
  watch: {
    showTotalAggregate() {
      this.buildCharts();
    },
    selectedDateRange() {
      this.buildCharts();
    }
  },
  methods: {
    parseMetadata(meta) {
      if (!meta) return null;
      let obj = meta;
      if (typeof meta === 'string') {
        try {
          obj = JSON.parse(meta);
        } catch (e) {
          return null;
        }
      }
      return typeof obj === 'object' && obj !== null ? obj : null;
    },
    baseTooltipFormatter(params) {
      if (!params || !params.length) return '';
      const label = params[0].axisValueLabel || params[0].name || '';
      const lines = params.map((p) => {
        const val = Array.isArray(p.data) ? p.data[1] : p.data;
        const num = Number(val);
        const display = Number.isFinite(num) ? num.toFixed(2) : val;
        return `${p.seriesName}: ${display}`;
      });
      return `${label}<br/>${lines.join('<br/>')}`;
    },
    buildTotalTooltip() {
      return (params) => {
        if (!params || !params.length) return '';
        const date = params[0].axisValueLabel || params[0].name || '';
        const sortedParams = [...params].sort((a, b) => {
          if (a.seriesName === 'Total' && b.seriesName !== 'Total') return 1;
          if (b.seriesName === 'Total' && a.seriesName !== 'Total') return -1;
          const aRaw = Array.isArray(a.data) ? a.data[1] : a.data;
          const bRaw = Array.isArray(b.data) ? b.data[1] : b.data;
          const aVal = Number(aRaw);
          const bVal = Number(bRaw);
          if (!Number.isFinite(aVal) && !Number.isFinite(bVal)) return 0;
          if (!Number.isFinite(aVal)) return 1;
          if (!Number.isFinite(bVal)) return -1;
          return bVal - aVal;
        });
        const lines = sortedParams.map((p) => {
          const raw = Array.isArray(p.data) ? p.data[1] : p.data;
          const val = Number(raw);
          const display = Number.isFinite(val) ? val.toFixed(2) : raw;
          if (p.seriesName === 'Total') {
            return `Total: ${display}`;
          }
          const sym = p.seriesName;
          const data = this.enrichedPortfolioTradeData[sym];
          let sharesTxt = '';
          if (data?.dates && data.shares && data.averageCostPerShare) {
            const idx = data.dates.indexOf(date);
            if (idx >= 0) {
              const sh = Number(data.shares[idx]);
              const avg = Number(data.averageCostPerShare[idx]);
              const close = Array.isArray(data.closePrice) ? Number(data.closePrice[idx]) : null;
              const shStr = Number.isFinite(sh) ? sh.toFixed(1) : '';
              const avgStr = Number.isFinite(avg) ? avg.toFixed(2) : '';
              const closeStr = Number.isFinite(close) ? close.toFixed(2) : '';
              if (shStr) {
                sharesTxt = ` (${shStr} shares${avgStr ? `, ${avgStr} avg` : ''}${closeStr ? `, close=${closeStr}` : ''})`;
              }
            }
          }
          return `${sym}: ${display}${sharesTxt}`;
        });
        return `${date}<br/>${lines.join('<br/>')}`;
      };
    },
    buildSeriesForMetric(metric, symbols) {
      return symbols
        .map((sym) => {
          const data = this.enrichedPortfolioTradeData[sym];
          if (!data?.dates || !data[metric]) return null;
          const points = this.filterPointsBySelectedDateRange(data.dates.map((d, idx) => [d, data[metric][idx]]));
          return { name: sym, type: 'line', smooth: true, showSymbol: false, data: points };
        })
        .filter((series) => series && series.data.length);
    },
    maybeAddAggregateSeries(metric, series, symbols) {
      const aggregateKeys = [
        'cashFlow',
        'cash',
        'totalCost',
        'realizedPnL',
        'unrealizedPnL',
        'totalStockValue',
        'totalPortfolio'
      ];
      if (!aggregateKeys.includes(metric)) return series;
      if (metric === 'totalPortfolio' && !this.showTotalAggregate) return series;
      const agg = this.aggregateMetric(metric, symbols);
      if (!agg.dates.length) return series;
      const aggPoints = this.filterPointsBySelectedDateRange(agg.dates.map((d, idx) => [d, agg.values[idx]]));
      if (!aggPoints.length) return series;
      return [
        ...series,
        {
          name: 'Total',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: aggPoints,
          lineStyle: { width: 3 }
        }
      ];
    },
    buildOption(metric, series, formatter) {
      if (!series.length) return null;
      const datesSet = new Set();
      series.forEach((s) => s.data.forEach(([d]) => datesSet.add(d)));
      const allDates = Array.from(datesSet).sort();
      return {
        textStyle: { color: '#52606d', fontSize: 11 },
        tooltip: { trigger: 'axis', formatter },
        legend: { top: 0, textStyle: { color: '#52606d', fontSize: 11 } },
        xAxis: {
          type: 'category',
          data: allDates,
          axisLabel: { color: '#627d98', fontSize: 10 }
        },
        yAxis: {
          type: 'value',
          axisLabel: { color: '#627d98', fontSize: 10 }
        },
        series,
        grid: { left: '8%', right: '4%', top: '14%', bottom: '10%' }
      };
    },
    filterPointsBySelectedDateRange(points) {
      if (this.selectedDateRange === 'all' || !points.length) return points;
      const validDates = points
        .map(([date]) => new Date(`${date}T00:00:00`))
        .filter((date) => !Number.isNaN(date.getTime()));
      if (!validDates.length) return points;

      const maxDate = new Date(Math.max(...validDates.map((date) => date.getTime())));
      let startDate = null;
      if (this.selectedDateRange === '1m') {
        startDate = new Date(maxDate);
        startDate.setMonth(startDate.getMonth() - 1);
      } else if (this.selectedDateRange === '1y') {
        startDate = new Date(maxDate);
        startDate.setFullYear(startDate.getFullYear() - 1);
      } else if (this.selectedDateRange === 'ytd') {
        startDate = new Date(maxDate.getFullYear(), 0, 1);
      }
      if (!startDate) return points;
      return points.filter(([date]) => new Date(`${date}T00:00:00`) >= startDate);
    },
    buildCharts() {
      const symbols = this.selectedSymbols.length
        ? this.selectedSymbols
        : Object.keys(this.enrichedPortfolioTradeData);
      const inactiveSymbolSet = new Set(this.inactiveSymbols);
      this.aggregatedMetrics = {};
      const nextOptions = {};
      const baseFormatter = this.baseTooltipFormatter;
      const totalFormatter = this.buildTotalTooltip();
      this.metrics.forEach((metric) => {
        const metricSymbols = metric === 'realizedPnL'
          ? symbols
          : symbols.filter((sym) => !inactiveSymbolSet.has(sym));
        const baseSeries = this.buildSeriesForMetric(metric, metricSymbols);
        const series = this.maybeAddAggregateSeries(metric, baseSeries, metricSymbols);
        const option = this.buildOption(
          metric,
          series,
          metric === 'totalPortfolio' ? totalFormatter : baseFormatter
        );
        if (option) nextOptions[metric] = option;
      });
      this.chartOptions = nextOptions;
      this.buildDonut(symbols.filter((sym) => !inactiveSymbolSet.has(sym)));
    },
    buildDonut(symbols) {
      const pieData = symbols
        .map((sym) => {
          const data = this.enrichedPortfolioTradeData[sym];
          if (!data || !data.totalStockValue?.length) return null;
          const lastVal = data.totalStockValue[data.totalStockValue.length - 1];
          const valNum = Number(lastVal);
          if (!Number.isFinite(valNum) || valNum <= 0) return null;
          return { name: sym, value: Number(valNum.toFixed(2)) };
        })
        .filter(Boolean);

      const total = pieData.reduce((sum, p) => sum + p.value, 0);
      if (!pieData.length || total <= 0) {
        this.donutOption = null;
        return;
      }

      this.donutOption = {
        textStyle: { color: '#52606d', fontSize: 11 },
        tooltip: { trigger: 'item', formatter: '{b}: {d}% ({c})' },
        legend: {
          orient: 'vertical',
          right: 10,
          top: 'middle',
          textStyle: { color: '#52606d', fontSize: 11 }
        },
        series: [
          {
            name: 'Allocation',
            type: 'pie',
            radius: ['50%', '75%'],
            avoidLabelOverlap: false,
            itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
            label: { show: false },
            emphasis: { label: { show: true, fontWeight: 'bold' } },
            data: pieData
          }
        ]
      };
    },
    aggregateMetric(metric, symbols) {
      const cacheKey = `${metric}:${symbols.join('|')}`;
      if (this.aggregatedMetrics[cacheKey]) return this.aggregatedMetrics[cacheKey];
      const dataForSymbols = Object.fromEntries(
        symbols
          .filter((sym) => this.enrichedPortfolioTradeData[sym])
          .map((sym) => [sym, this.enrichedPortfolioTradeData[sym]])
      );
      const result = aggregateMetricAcrossSymbols(dataForSymbols, metric);
      this.aggregatedMetrics[cacheKey] = result;
      return result;
    },
    async getSourceData() {
      const request = new ListRecordRequest({
        labels: ['INVESTMENT_REC'],
        excludeLabels: ['INVALID_TRADE'],
        pageSize: 10000
      });
      const data = await getRecords(request);
      const tradeDataRaw = (data?.content || [])
        .filter((rec) => validateTrade(rec.metadata))
        .map((rec) => ({ ...rec.metadata, recordID: rec.id }));

      const tradeDataGroupBySymbolAndSort = recordGroupBySymbolAndSort(tradeDataRaw);
      const symbols = Object.keys(tradeDataGroupBySymbolAndSort);
      const stockHistoricalData = await this.getStockHistoryData(symbols);
      const tradeDataWithValidSymbols = Object.fromEntries(
        Object.entries(tradeDataGroupBySymbolAndSort).filter(([key]) =>
          !this.invalidSymbols.includes(key)
        )
      );
      console.log("valid symbols: ", tradeDataWithValidSymbols)
      const enriched = enrichAccumulativeTradeData(tradeDataWithValidSymbols, stockHistoricalData);
      this.enrichedPortfolioTradeData = enriched || {};
      console.log('enrichd data: ', enriched);
      this.selectedSymbols = Object.keys(this.enrichedPortfolioTradeData);
      this.buildCharts();
    },
    async getStockHistoryData(symbols) {
      let stockData = this.$store.getters['portfolio/getStockDailyHistoryData'];
      const existingSymbols = Object.keys(stockData);
      const invalidSymbols = this.$store.getters['portfolio/getInvalidSymbols'];
      const symbolsExcludingInvalid = invalidSymbols ? symbols.filter(sym => !invalidSymbols.includes(sym)) : symbols;
      if (!stockData || Object.keys(stockData).length === 0 || existingSymbols.length < symbolsExcludingInvalid.length) {
        await this.$store.dispatch('portfolio/updateStockHistoryData', symbols);
      }
      stockData = this.$store.getters['portfolio/getStockDailyHistoryData'];
      this.invalidSymbols = this.$store.getters['portfolio/getInvalidSymbols'];
      return stockData;
    }
  }
})
</script>

<style scoped>
.portfolio {
  max-width: 1320px;
  margin: 1rem auto;
  padding: 0 0.6rem 1rem;
}

.head {
  margin-bottom: 0.8rem;
}

.head h1 {
  color: #0f4c81;
  font-size: 1.4rem;
  margin: 0 0 0.25rem;
}

.controls {
  display: flex;
  justify-content: flex-end;
  gap: 0.7rem;
  flex-wrap: wrap;
  margin-bottom: 0.45rem;
}

.control-label {
  color: #52606d;
  font-size: 0.78rem;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.range-select {
  border: 1px solid #d9e2ec;
  border-radius: 4px;
  color: #486581;
  background: #fff;
  padding: 0.42rem 0.55rem;
  font: inherit;
  font-size: 0.8rem;
}

.range-select:focus {
  border-color: #2f80ed;
  outline: none;
}

.muted {
  color: #52606d;
  font-size: 0.88rem;
  margin: 0;
}

.muted.small {
  font-size: 0.78rem;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 0.8rem;
}

.chart-grid.multi {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.chart-grid.single {
  grid-template-columns: 1fr;
  margin-bottom: 0.8rem;
}

@media (max-width: 760px) {
  .chart-grid.multi {
    grid-template-columns: 1fr;
  }
}

:deep(.dashboard-item) {
  border-color: #d9e2ec;
  border-radius: 4px;
  padding: 0.65rem;
  gap: 0.4rem;
}

:deep(.item-head h2) {
  color: #243b53;
  font-size: 0.95rem;
}

:deep(.item-head .muted) {
  color: #627d98;
  font-size: 0.78rem;
}

.chart-card {
  border: 1px solid #e5e8ed;
  border-radius: 4px;
  background: #fff;
  padding: 0.75rem;
  box-shadow: 0 6px 18px rgba(16, 42, 67, 0.05);
}

.chart-card h2 {
  margin: 0 0 0.5rem 0;
  font-size: 1.05rem;
  color: #102a43;
}

.chart {
  width: 100%;
  height: 315px;
}
</style>
