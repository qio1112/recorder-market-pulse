<template>
  <div class="chart-wrap">
    <v-chart v-if="option" :option="option" autoresize class="chart" />
    <p v-else class="muted">Select options to show chart data.</p>
  </div>
</template>

<script>
import { defineComponent } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  TooltipComponent,
  GridComponent,
  LegendComponent,
  TitleComponent
} from 'echarts/components'
import VChart from 'vue-echarts'

use([
  CanvasRenderer,
  LineChart,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  TitleComponent
])

export default defineComponent({
  name: 'OptionHistoryLineChart',
  components: { VChart },
  props: {
    option: {
      type: Object,
      default: null
    },
    height: {
      type: String,
      default: '320px'
    }
  }
})
</script>

<style scoped>
.chart-wrap {
  width: 100%;
  min-height: v-bind(height);
}

.chart {
  width: 100%;
  height: v-bind(height);
}

.muted {
  color: #627d98;
  font-size: 0.82rem;
  padding: 0.75rem 0;
}
</style>
