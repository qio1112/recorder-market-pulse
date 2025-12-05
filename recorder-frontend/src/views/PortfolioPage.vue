<template>
  <section class="portfolio">
    <header class="head">
      <h1>Portfolio Overview</h1>
      <p class="muted">Trades aggregated from records metadata</p>
    </header>

    <div class="chart-grid single">
      <dashboard-item title="totalPortfolio">
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
      selectedSymbols: []
    }
  },
  computed: {
    otherMetrics() {
      return this.metrics.filter((m) => m !== 'totalPortfolio');
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
        const lines = params.map((p) => {
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
          const points = data.dates.map((d, idx) => [d, data[metric][idx]]);
          return { name: sym, type: 'line', smooth: true, data: points };
        })
        .filter(Boolean);
    },
    maybeAddAggregateSeries(metric, series) {
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
      const agg = this.aggregateMetric(metric);
      if (!agg.dates.length) return series;
      const aggPoints = agg.dates.map((d, idx) => [d, agg.values[idx]]);
      return [
        ...series,
        {
          name: 'Total',
          type: 'line',
          smooth: true,
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
        tooltip: { trigger: 'axis', formatter },
        legend: { top: 0 },
        xAxis: { type: 'category', data: allDates },
        yAxis: { type: 'value' },
        series,
        grid: { left: '8%', right: '4%', top: '14%', bottom: '10%' }
      };
    },
    buildCharts() {
      const symbols = this.selectedSymbols.length
        ? this.selectedSymbols
        : Object.keys(this.enrichedPortfolioTradeData);
      this.aggregatedMetrics = {};
      const nextOptions = {};
      const baseFormatter = this.baseTooltipFormatter;
      const totalFormatter = this.buildTotalTooltip();
      this.metrics.forEach((metric) => {
        const baseSeries = this.buildSeriesForMetric(metric, symbols);
        const series = this.maybeAddAggregateSeries(metric, baseSeries);
        const option = this.buildOption(
          metric,
          series,
          metric === 'totalPortfolio' ? totalFormatter : baseFormatter
        );
        if (option) nextOptions[metric] = option;
      });
      this.chartOptions = nextOptions;
      this.buildDonut(symbols);
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
        tooltip: { trigger: 'item', formatter: '{b}: {d}% ({c})' },
        legend: { orient: 'vertical', right: 10, top: 'middle' },
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
    aggregateMetric(metric) {
      if (this.aggregatedMetrics[metric]) return this.aggregatedMetrics[metric];
      const result = aggregateMetricAcrossSymbols(this.enrichedPortfolioTradeData, metric);
      this.aggregatedMetrics[metric] = result;
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
      const enriched = enrichAccumulativeTradeData(tradeDataGroupBySymbolAndSort, stockHistoricalData);
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
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: 1rem;
}

.chart-grid.single {
  grid-template-columns: 1fr;
  margin-bottom: 1rem;
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
