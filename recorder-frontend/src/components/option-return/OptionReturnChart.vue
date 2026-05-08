<template>
  <section class="chart-stack">
    <section class="chart-card">
      <div class="chart-head">
        <div>
          <h3>Overall Return</h3>
          <p>The combined expiry return of all added positions.</p>
        </div>
      </div>

      <v-chart
        v-if="overallChartOption"
        :option="overallChartOption"
        autoresize
        class="chart"
      />
      <div v-else class="empty-chart">
        Add positions above to render the overall return line.
      </div>
    </section>

    <section class="chart-card">
      <div class="chart-head">
        <div>
          <h3>Position Return Chart</h3>
          <p>Each line shows one position's return at expiry against stock price.</p>
        </div>
      </div>

      <v-chart
        v-if="positionChartOption"
        :option="positionChartOption"
        autoresize
        class="chart"
      />
      <div v-else class="empty-chart">
        Add positions above to render expiry return lines.
      </div>
    </section>
  </section>
</template>

<script>
import { defineComponent } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  MarkLineComponent,
  MarkPointComponent
} from 'echarts/components'
import VChart from 'vue-echarts'

use([
  CanvasRenderer,
  LineChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  MarkLineComponent,
  MarkPointComponent
])

export default defineComponent({
  name: 'OptionReturnChart',
  components: {
    VChart
  },
  props: {
    positions: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    xValues() {
      return this.positions.length ? this.buildStockPriceRange() : []
    },
    xAxisBounds() {
      if (!this.xValues.length) return null
      return {
        min: this.xValues[0],
        max: this.xValues[this.xValues.length - 1]
      }
    },
    overallChartOption() {
      if (!this.positions.length || !this.xValues.length) return null

      const overallData = this.xValues.map((stockPrice) => {
        const total = this.positions.reduce(
          (sum, position) => sum + this.calculateReturn(position, stockPrice),
          0
        )
        return [Number(stockPrice.toFixed(2)), Number(total.toFixed(2))]
      })

      return {
        tooltip: {
          trigger: 'axis',
          formatter: (params) => {
            if (!params || !params.length) return ''
            const stockPrice = Number(params[0].axisValue)
            const total = Array.isArray(params[0].data) ? Number(params[0].data[1]) : Number(params[0].data)
            return `Stock Price: ${stockPrice.toFixed(2)}<br/>Overall: ${total.toFixed(2)}`
          }
        },
        grid: {
          left: '7%',
          right: '4%',
          top: '10%',
          bottom: '11%'
        },
        xAxis: {
          type: 'value',
          name: 'Stock Price',
          nameLocation: 'middle',
          nameGap: 28,
          min: this.xAxisBounds.min,
          max: this.xAxisBounds.max
        },
        yAxis: {
          type: 'value',
          name: 'Overall Return'
        },
        series: [
          {
            name: 'Overall Return',
            type: 'line',
            smooth: false,
            symbol: 'none',
            lineStyle: {
              width: 3
            },
            data: overallData
          }
        ]
      }
    },
    positionChartOption() {
      if (!this.positions.length || !this.xValues.length) return null

      const series = this.positions.map((position, index) => ({
        name: this.buildSeriesName(position, index),
        type: 'line',
        symbol: 'none',
        data: this.xValues.map((stockPrice) => [
          Number(stockPrice.toFixed(2)),
          Number(this.calculateReturn(position, stockPrice).toFixed(2))
        ]),
        markLine: this.buildMarkLine(position),
        markPoint: this.buildMarkPoint(position)
      }))

      return {
        tooltip: {
          trigger: 'axis',
          formatter: (params) => {
            if (!params || !params.length) return ''
            const stockPrice = Number(params[0].axisValue)
            const lines = params.map((param) => {
              const value = Array.isArray(param.data) ? Number(param.data[1]) : Number(param.data)
              return `${param.seriesName}: ${value.toFixed(2)}`
            })
            return `Stock Price: ${stockPrice.toFixed(2)}<br/>${lines.join('<br/>')}`
          }
        },
        legend: {
          top: 0,
          type: 'scroll'
        },
        grid: {
          left: '7%',
          right: '4%',
          top: '16%',
          bottom: '10%'
        },
        xAxis: {
          type: 'value',
          name: 'Stock Price',
          nameLocation: 'middle',
          nameGap: 28,
          min: this.xAxisBounds.min,
          max: this.xAxisBounds.max
        },
        yAxis: {
          type: 'value',
          name: 'Return'
        },
        series
      }
    }
  },
  methods: {
    buildStockPriceRange() {
      const refs = this.positions.flatMap((position) => {
        const values = []
        const strike = Number(position.strike)
        const breakEven = this.getBreakEven(position)

        if (position.positionType === 'stock') {
          if (Number.isFinite(breakEven) && breakEven > 0) values.push(breakEven)
          return values
        }

        if (Number.isFinite(strike) && strike > 0) values.push(strike)
        if (Number.isFinite(breakEven) && breakEven > 0) values.push(breakEven)
        return values
      }).filter((value) => Number.isFinite(value) && value > 0)

      if (!refs.length) {
        return Array.from({ length: 61 }, (_, index) => index + 1)
      }

      const minRef = Math.min(...refs)
      const maxRef = Math.max(...refs)
      const spread = Math.max(maxRef - minRef, maxRef * 0.2, 1)
      const padding = spread * 0.2
      const minPrice = Math.max(0.01, minRef - padding)
      const maxPrice = maxRef + padding
      const step = (maxPrice - minPrice) / 60
      const sampledValues = Array.from({ length: 61 }, (_, index) => minPrice + (index * step))
      const exactKeyValues = refs.filter((value) => value >= minPrice && value <= maxPrice)

      return Array.from(new Set(
        [...sampledValues, ...exactKeyValues].map((value) => Number(value.toFixed(4)))
      )).sort((a, b) => a - b)
    },
    calculateReturn(position, stockPrice) {
      const quantity = Number(position.shares)
      const price = Number(position.price)
      const strike = Number(position.strike)
      const optionContractSize = 100

      if (position.positionType === 'stock') {
        return quantity * (stockPrice - price)
      }
      if (position.positionType === 'call') {
        return quantity * optionContractSize * (Math.max(stockPrice - strike, 0) - price)
      }
      return quantity * optionContractSize * (Math.max(strike - stockPrice, 0) - price)
    },
    buildSeriesName(position, index) {
      const direction = Number(position.shares) >= 0 ? 'Long' : 'Short'
      if (position.positionType === 'stock') {
        return `${index + 1}. ${direction} Stock @ ${Number(position.price).toFixed(2)}`
      }
      return `${index + 1}. ${direction} ${position.positionType} K=${Number(position.strike).toFixed(1)}`
    },
    getBreakEven(position) {
      const premium = Number(position.price)
      const strike = Number(position.strike)

      if (position.positionType === 'stock') {
        return premium
      }
      if (position.positionType === 'call') {
        return strike + premium
      }
      return strike - premium
    },
    buildMarkLine(position) {
      if (position.positionType === 'stock') {
        return {
          symbol: 'none',
          lineStyle: { type: 'dashed' },
          label: { formatter: `Entry ${Number(position.price).toFixed(2)}` },
          data: [{ xAxis: Number(position.price).toFixed(2) }]
        }
      }

      return {
        symbol: 'none',
        lineStyle: { type: 'dashed' },
        label: { formatter: `K ${Number(position.strike).toFixed(1)}` },
        data: [{ xAxis: Number(position.strike).toFixed(1) }]
      }
    },
    buildMarkPoint(position) {
      const breakEven = this.getBreakEven(position)
      if (!Number.isFinite(breakEven) || breakEven < 0) return undefined

      return {
        symbolSize: 24,
        label: {
          formatter: `BE\n${breakEven.toFixed(2)}`,
          fontSize: 10
        },
        data: [
          {
            coord: [Number(breakEven.toFixed(2)), 0],
            value: 0
          }
        ]
      }
    }
  }
})
</script>

<style scoped>
.chart-stack {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.chart-card {
  margin-top: 0.9rem;
  border: 1px solid #cfd7e2;
  border-radius: 4px;
  background: #ffffff;
  padding: 0.9rem;
  box-shadow: 0 6px 18px rgba(15, 76, 129, 0.04);
}

.chart-head {
  margin-bottom: 0.45rem;
}

.chart-head h3 {
  margin: 0;
  color: #0f4c81;
  font-size: 0.98rem;
}

.chart-head p {
  margin: 0.18rem 0 0;
  color: #52606d;
  font-size: 0.74rem;
}

.chart {
  height: 320px;
}

.empty-chart {
  padding: 1rem 0.85rem;
  border-radius: 4px;
  background: #fbfdff;
  border: 1px dashed #d9e2ec;
  color: #52606d;
  font-size: 0.8rem;
  text-align: center;
}
</style>
