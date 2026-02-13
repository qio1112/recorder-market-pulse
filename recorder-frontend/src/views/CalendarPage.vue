<template>
  <section class="calendar-page">
    <div class="calendar-page__container">
      <header class="calendar-page__header">
        <div>
          <h1 class="calendar-page__title">Monthly Calendar</h1>
          <p class="calendar-page__subtitle">Select a month to browse your timeline.</p>
        </div>
        <div class="calendar-page__controls">
          <button class="calendar-nav" @click="goToPrevMonth">
            ‹ Prev
          </button>
          <input
            class="calendar-month-input"
            type="month"
            :max="monthInputMax"
            v-model="selectedMonth"
            @change="onMonthChange"
          />
          <button class="calendar-nav" @click="goToNextMonth" :disabled="!canGoNext">
            Next ›
          </button>
        </div>
      </header>
      <div class="calendar-page__body">
        <FullCalendar ref="calendarRef" :options="calendarOptions" />
      </div>
    </div>
  </section>
</template>

<script>
import { createApp, computed, ref, watch } from 'vue';
import FullCalendar from '@fullcalendar/vue3';
import dayGridPlugin from '@fullcalendar/daygrid';
import CalendarDay from '../components/calendar/CalendarDay.vue';
import { getRecordCountByDateLabelRange } from '../api/RecordService';
import { useRoute, useRouter } from 'vue-router';

const formatMonthValue = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
const formatDateLabel = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const formatRouteMonth = (date) => `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, '0')}`;

export default {
  name: 'CalendarPage',
  components: { FullCalendar },
  setup() {
    const today = new Date();
    const route = useRoute();
    const router = useRouter();
    const calendarRef = ref(null);
    const calendarPlugins = [dayGridPlugin];
    const clampToCurrentMonth = (date) => {
      const cap = new Date(today.getFullYear(), today.getMonth(), 1);
      return date > cap ? cap : date;
    };
    const parseRouteMonth = (param) => {
      if (typeof param !== 'string' || !/^[0-9]{6}$/.test(param)) return null;
      const year = Number(param.slice(0, 4));
      const month = Number(param.slice(4)) - 1;
      if (Number.isNaN(year) || Number.isNaN(month)) return null;
      const dt = new Date(year, month, 1);
      return Number.isNaN(dt.getTime()) ? null : clampToCurrentMonth(dt);
    };

    const initialMonthDate = parseRouteMonth(route.params.month) || clampToCurrentMonth(today);
    const calendarOptions = ref({
      plugins: calendarPlugins,
      initialView: 'dayGridMonth',
      initialDate: initialMonthDate,
      headerToolbar: false,
      firstDay: 0,
      dayCellDidMount: (info) => handleDayCellMount(info),
      dayCellWillUnmount: (info) => handleDayCellUnmount(info),
      datesSet: (info) => handleDatesSet(info),
      validRange: null,
      fixedWeekCount: false,
      showNonCurrentDates: true,
      expandRows: false,
      height: 'auto'
    });
    const selectedMonth = ref(formatMonthValue(initialMonthDate));
    const monthInputMax = formatMonthValue(today);
    const recordCounts = ref({});
    const lastFetchedMonthKey = ref('');
    const mountedCells = new Map();

    const validRange = computed(() => ({
      end: new Date(today.getFullYear(), today.getMonth() + 1, 1)
    }));
    calendarOptions.value.validRange = validRange.value;

    const canGoNext = computed(() => selectedMonth.value < monthInputMax);

    const goToPrevMonth = () => {
      const [year, month] = selectedMonth.value.split('-').map(Number);
      const newDate = new Date(year, month - 2, 1);
      selectedMonth.value = formatMonthValue(newDate);
      calendarRef.value?.getApi().gotoDate(newDate);
      syncRouteMonth(newDate);
    };

    const goToNextMonth = () => {
      if (!canGoNext.value) return;
      const [year, month] = selectedMonth.value.split('-').map(Number);
      const newDate = new Date(year, month, 1);
      if (newDate > new Date(today.getFullYear(), today.getMonth(), 1)) return;
      selectedMonth.value = formatMonthValue(newDate);
      calendarRef.value?.getApi().gotoDate(newDate);
      syncRouteMonth(newDate);
    };

    const onMonthChange = () => {
      const [year, month] = selectedMonth.value.split('-').map(Number);
      const chosenDate = new Date(year, month - 1, 1);
      const cappedDate = chosenDate > new Date(today.getFullYear(), today.getMonth(), 1)
        ? new Date(today.getFullYear(), today.getMonth(), 1)
        : chosenDate;
      selectedMonth.value = formatMonthValue(cappedDate);
      calendarRef.value?.getApi().gotoDate(cappedDate);
      syncRouteMonth(cappedDate);
    };

    const handleDatesSet = (info) => {
      const currentStart = info.view?.currentStart || info.start;
      selectedMonth.value = formatMonthValue(currentStart);
      calendarOptions.value.validRange = validRange.value;
      syncRouteMonth(currentStart);
      fetchCountsForMonth(currentStart);
    };

    const handleDayCellMount = (info) => {
      const mountTarget = info.el.querySelector('.fc-daygrid-day-frame') || info.el;
      const visibleMonth = info.view?.currentStart?.getMonth();
      const isOtherMonth = typeof info.isOther === 'boolean'
        ? info.isOther
        : visibleMonth != null
          ? info.date.getMonth() !== visibleMonth
          : false;

      mountTarget.innerHTML = '';
      const app = createApp(CalendarDay, {
        date: info.date,
        isOtherMonth
      });
      app.provide('recordCounts', recordCounts);
      app.mount(mountTarget);
      mountedCells.set(info.el, { app, mountTarget });
    };

    const handleDayCellUnmount = (info) => {
      const mounted = mountedCells.get(info.el);
      if (mounted) {
        mounted.app.unmount();
        mounted.mountTarget.innerHTML = '';
        mountedCells.delete(info.el);
      }
    };

    const fetchCountsForMonth = async (anchorDate) => {
      if (!anchorDate) return;
      const monthKey = formatMonthValue(anchorDate);
      if (monthKey === lastFetchedMonthKey.value) return;
      const startDate = formatDateLabel(new Date(anchorDate.getFullYear(), anchorDate.getMonth(), 1));
      const endDate = formatDateLabel(new Date(anchorDate.getFullYear(), anchorDate.getMonth() + 1, 0));
      const response = await getRecordCountByDateLabelRange(startDate, endDate);
      if (response && Array.isArray(response)) {
        const map = {};
        response.forEach(item => {
          if (item && item.dateLabel) {
            map[item.dateLabel] = item.recordCount ?? 0;
          }
        });
        recordCounts.value = map;
      } else {
        recordCounts.value = {};
      }
      lastFetchedMonthKey.value = monthKey;
    };

    const syncRouteMonth = (dateObj) => {
      const targetParam = formatRouteMonth(dateObj);
      if (route.params.month === targetParam) return;
      router.replace({ path: `/calendar/${targetParam}` });
    };

    watch(
      () => route.params.month,
      (val) => {
        const parsed = parseRouteMonth(val);
        if (!parsed) {
          syncRouteMonth(clampToCurrentMonth(today));
          return;
        }
        selectedMonth.value = formatMonthValue(parsed);
        calendarRef.value?.getApi().gotoDate(parsed);
        fetchCountsForMonth(parsed);
      }
    );

    fetchCountsForMonth(initialMonthDate);

    return {
      calendarPlugins,
      calendarRef,
      calendarOptions,
      selectedMonth,
      monthInputMax,
      canGoNext,
      validRange,
      goToPrevMonth,
      goToNextMonth,
      onMonthChange,
      handleDatesSet,
      handleDayCellMount,
      handleDayCellUnmount
    };
  }
};
</script>

<style scoped>
.calendar-page {
  padding: 16px 18px 24px;
  background: #f1f5f9;
  min-height: 100vh;
  display: flex;
  justify-content: center;
}

.calendar-page__container {
  width: 100%;
  max-width: 1180px;
}

.calendar-page__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 16px;
  flex-wrap: wrap;
}

.calendar-page__title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
}

.calendar-page__subtitle {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.calendar-page__controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.calendar-month-input {
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background: #fff;
  font-size: 14px;
  color: #111827;
}

.calendar-nav {
  padding: 8px 12px;
  border-radius: 8px;
  background: #1f2937;
  color: #fff;
  border: none;
  cursor: pointer;
  font-weight: 600;
  transition: background 0.12s ease, transform 0.12s ease;
}

.calendar-nav:disabled {
  background: #9ca3af;
  cursor: not-allowed;
}

.calendar-nav:not(:disabled):hover {
  background: #111827;
  transform: translateY(-1px);
}

.calendar-page__body {
  flex: 1;
  background: #fff;
  border-radius: 14px;
  padding: 8px;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.06);
  min-height: 560px;
}

:deep(.fc) {
  background: transparent;
  padding: 6px;
  border-radius: 10px;
  box-shadow: none;
}

:deep(.fc .fc-toolbar-title) {
  display: none;
}

:deep(.fc-theme-standard td),
:deep(.fc-theme-standard th) {
  border-color: #e2e8f0;
}

:deep(.fc-col-header-cell-cushion) {
  padding: 8px 2px;
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

:deep(.fc-scrollgrid) {
  border: none;
}

:deep(.fc-daygrid-day-number) {
  display: none;
}

:deep(.fc-daygrid-day-top) {
  display: none;
}

:deep(.fc-daygrid-day-events) {
  display: none;
}

:deep(.fc-daygrid-day-frame) {
  padding: 0;
  height: 100%;
}

:deep(.fc-daygrid-day) {
  padding: 4px;
  background: #f8fafc;
  height: 120px;
  min-height: 120px;
}
</style>
