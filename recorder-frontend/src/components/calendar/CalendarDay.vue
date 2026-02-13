<template>
  <div
    class="day-card"
    :class="{ 'is-other-month': isOtherMonth, 'is-weekend': isWeekend }"
    @click="goToRecords"
  >
    <header class="day-card__header">
      <span class="day-card__date">{{ monthDayLabel }}</span>
      <span class="day-card__weekday">{{ weekdayLabel }}</span>
    </header>
    <section class="day-card__body">
      <div class="day-card__count" :class="{ 'has-records': recordCount > 0 }">
        <span class="day-card__count-number">{{ recordCount }}</span>
        <span class="day-card__count-label">records</span>
      </div>
    </section>
  </div>
</template>

<script>
import router from '../../router';

const formatDateLabel = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export default {
  name: 'CalendarDay',
  props: {
    date: {
      type: Date,
      required: true
    },
    isOtherMonth: {
      type: Boolean,
      default: false
    }
  },
  inject: {
    recordCounts: {
      from: 'recordCounts',
      default: () => null
    }
  },
  computed: {
    dayNumber() {
      return this.date.getDate();
    },
    monthDayLabel() {
      const month = this.date.toLocaleDateString(undefined, { month: 'short' });
      return `${month} ${this.dayNumber}`;
    },
    weekdayLabel() {
      return this.date.toLocaleDateString(undefined, { weekday: 'short' });
    },
    isWeekend() {
      const day = this.date.getDay();
      return day === 0 || day === 6;
    },
    dateLabel() {
      return formatDateLabel(this.date);
    },
    recordCount() {
      const map = this.recordCounts?.value ?? this.recordCounts ?? {};
      const value = map ? map[this.dateLabel] : 0;
      return typeof value === 'number' ? value : 0;
    }
  },
  methods: {
    goToRecords() {
      router.push({
        path: '/records',
        query: {
          labels: this.dateLabel,
          isCreatedByUserOnly: 'true'
        }
      });
    }
  }
};
</script>

<style scoped>
.day-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 8px 10px;
  min-height: 80px;
  height: 100%;
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: linear-gradient(180deg, #ffffff 0%, #f7fafc 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
  transition: transform 0.12s ease, box-shadow 0.12s ease;
  cursor: pointer;
}

.day-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(31, 41, 55, 0.08);
}

.day-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.day-card__date {
  font-size: 18px;
  font-weight: 700;
  color: #415571;
}

.day-card__weekday {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.day-card__body {
  flex: 1;
  display: flex;
  align-items: center;
  color: #9ca3af;
  font-size: 13px;
}

.day-card__count {
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: #cbd5e1;
}

.day-card__count-number {
  font-size: 20px;
  font-weight: 700;
  color: #d2dbd8;
}

.day-card__count-label {
  font-size: 12px;
  letter-spacing: 0.02em;
}

.has-records .day-card__count-number {
  color: #1d4ed8;
}

.is-weekend {
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

.is-weekend .day-card__date {
  color: #6b7280;
}

.is-other-month {
  background: #f8fafc;
  color: #9ca3af;
  border-style: dashed;
}
</style>
