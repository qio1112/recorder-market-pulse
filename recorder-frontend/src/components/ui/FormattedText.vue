<template>
  <div class="formatted-text">
    <div
      v-for="(line, lineIndex) in parsedLines"
      :key="lineIndex"
      class="formatted-line"
      :class="lineClass(line)"
    >
      <template v-for="(span, spanIndex) in line.spans" :key="spanIndex">
        <strong v-if="span.bold">{{ span.text }}</strong>
        <span v-else>{{ span.text }}</span>
      </template>
    </div>
  </div>
</template>

<script>
import { parseFormattedText } from '../../utils/formattedText.js'

export default {
  name: 'FormattedText',
  props: {
    text: {
      type: String,
      required: false,
      default: ''
    }
  },
  computed: {
    parsedLines() {
      return parseFormattedText(this.text);
    }
  },
  methods: {
    lineClass(line) {
      return line.level ? `formatted-line--h${line.level}` : 'formatted-line--body';
    }
  }
}
</script>

<style scoped>
.formatted-text {
  color: #243b53;
  font-size: 0.84rem;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.formatted-line {
  min-height: 1.5em;
  white-space: pre-wrap;
}

.formatted-line + .formatted-line {
  margin-top: 0.28rem;
}

.formatted-line--h1 {
  font-size: 1.28rem;
  line-height: 1.28;
  font-weight: 700;
  color: #102a43;
}

.formatted-line--h2 {
  font-size: 1.08rem;
  line-height: 1.35;
  font-weight: 700;
  color: #102a43;
}

.formatted-line--h3 {
  font-size: 0.95rem;
  line-height: 1.42;
  font-weight: 700;
  color: #102a43;
}

strong {
  font-weight: 700;
}
</style>
