<template>
  <section class="portfolio">
    <header class="head">
      <h1>Portfolio Overview</h1>
      <p class="muted">Trades aggregated from records metadata</p>
    </header>

    <div
      class="chart-grid"
      v-for="metric in metrics"
      :key="metric"
    >
      <dashboard-item :title="metric">
        <v-chart v-if="chartOptions[metric]" :option="chartOptions[metric]" autoresize class="chart" />
        <p v-else class="muted small">Load data to see chart</p>
      </dashboard-item>
    </div>

    <button @click="getSourceData()">Load Trades</button>
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
import { validateTrade, recordGroupBySymbolAndSort, enrichAccumulativeTradeData, calculateTotalPortfolioData } from '../utils/portfolioUtils.js'

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
      aggregatedTotal: { dates: [], totalPortfolio: [] },
      selectedSymbols: []
    }
  },
  mounted() {
    this.getSourceData();
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
    buildCharts() {
      const symbols = this.selectedSymbols.length
        ? this.selectedSymbols
        : Object.keys(this.enrichedPortfolioTradeData);
      const nextOptions = {};
      const tooltipFormatter = (params) => {
        if (!params || !params.length) return '';
        const label = params[0].axisValueLabel || params[0].name || '';
        const lines = params.map((p) => {
          const val = Array.isArray(p.data) ? p.data[1] : p.data;
          const num = Number(val);
          const display = Number.isFinite(num) ? num.toFixed(2) : val;
          return `${p.seriesName}: ${display}`;
        });
        return `${label}<br/>${lines.join('<br/>')}`;
      };
      this.metrics.forEach((metric) => {
        const series = symbols
          .map((sym) => {
            const data = this.enrichedPortfolioTradeData[sym];
            if (!data?.dates || !data[metric]) return null;
            const points = data.dates.map((d, idx) => [d, data[metric][idx]]);
            return { name: sym, type: 'line', smooth: true, data: points };
          })
          .filter(Boolean);
        // add aggregated total for totalPortfolio
        if (metric === 'totalPortfolio' && this.aggregatedTotal?.dates?.length) {
          const aggPoints = this.aggregatedTotal.dates.map((d, idx) => [
            d,
            this.aggregatedTotal.totalPortfolio[idx]
          ]);
          series.push({
            name: 'Total',
            type: 'line',
            smooth: true,
            data: aggPoints,
            lineStyle: { width: 3 }
          });
        }
        if (!series.length) return;
        // union dates for x-axis
        const datesSet = new Set();
        series.forEach((s) => s.data.forEach(([d]) => datesSet.add(d)));
        const allDates = Array.from(datesSet).sort();
        nextOptions[metric] = {
          tooltip: { trigger: 'axis', formatter: tooltipFormatter },
          legend: { top: 0 },
          xAxis: { type: 'category', data: allDates },
          yAxis: { type: 'value' },
          series,
          grid: { left: '8%', right: '4%', top: '14%', bottom: '10%' }
        };
      });
      this.chartOptions = nextOptions;
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
      const enriched = enrichAccumulativeTradeData(tradeDataGroupBySymbolAndSort, stockHistoricalData);
      this.aggregatedTotal = calculateTotalPortfolioData(enriched);
      // this.$store.dispatch('portfolio/updatePortfolioData', enriched);
      this.enrichedPortfolioTradeData = enriched || {};
      console.log('enrichd data: ', enriched);
      this.selectedSymbols = Object.keys(this.enrichedPortfolioTradeData);
      this.buildCharts();
    },
    async getStockHistoryData(symbols) {
      let stockData = this.$store.getters['portfolio/getStockDailyHistoryData'];
      if (!stockData || Object.keys(stockData).length === 0) {
        await this.$store.dispatch('portfolio/updateStockHistoryData', symbols);
      }
      stockData = this.$store.getters['portfolio/getStockDailyHistoryData'];
      return stockData;
    }
  }
})
</script>

<style scoped>
.portfolio {
  max-width: 1200px;
  margin: 1.5rem auto;
  padding: 1rem;
}

.head {
  margin-bottom: 1rem;
}

.muted {
  color: #52606d;
}

.muted.small {
  font-size: 0.9rem;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 1rem;
}

.chart-card {
  border: 1px solid #e5e8ed;
  border-radius: 10px;
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
  height: 340px;
}
</style>
