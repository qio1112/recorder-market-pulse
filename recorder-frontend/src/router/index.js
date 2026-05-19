import { createRouter, createWebHashHistory } from 'vue-router'
import LoginPage from '../views/LoginPage.vue'
import ForgotPasswordPage from '../views/ForgotPasswordPage.vue'
import ResetPasswordPage from '../views/ResetPasswordPage.vue'
import RecordsPage from '../views/RecordsPage.vue'
import RecordDetail from '../views/RecordDetail.vue'
import UserAccountInfo from '../views/UserAccountInfo.vue'
import AddNewRecord from '../views/AddNewRecord.vue'
import EditRecord from '../views/EditRecord.vue'
import PortfolioPage from '../views/PortfolioPage.vue'
import OptionReturn from '../views/OptionReturn.vue'
import OptionHistoryPage from '../views/OptionHistoryPage.vue'
import AdminLlmChatPage from '../views/AdminLlmChatPage.vue'
import AdminToolsPage from '../views/AdminToolsPage.vue'
import PageNotFound from '../views/PageNotFound.vue'
import CalendarPage from '../views/CalendarPage.vue'
import store from '../store/index.js'

const currentMonthParam = () => {
  const now = new Date();
  return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`;
};

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/records', meta: { requiresAuth: true } },
    { path: '/records', component: RecordsPage, meta: { requiresAuth: true } },
    { path: '/calendar', redirect: () => `/calendar/${currentMonthParam()}`, meta: { requiresAuth: true } },
    { path: '/calendar/:month(\\d{6})', component: CalendarPage, meta: { requiresAuth: true } },
    { path: '/records/:recordID', component: RecordDetail, props: true, meta: { requiresAuth: true } },
    { path: '/add-record', component: AddNewRecord, meta: { requiresAuth: true } },
    { path: '/edit-record/:recordID', component: EditRecord, props: true, meta: { requiresAuth: true } },
    { path: '/portfolio', redirect: '/tools/portfolio', meta: { requiresAuth: true } },
    { path: '/option-return', redirect: '/tools/option-return', meta: { requiresAuth: true } },
    { path: '/option-history', redirect: '/tools/option-history', meta: { requiresAuth: true } },
    { path: '/tools/portfolio', component: PortfolioPage, meta: { requiresAuth: true } },
    { path: '/tools/option-return', component: OptionReturn, meta: { requiresAuth: true } },
    { path: '/tools/option-history', component: OptionHistoryPage, meta: { requiresAuth: true } },
    { path: '/tools/admin', component: AdminToolsPage, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/tools/llm-chat', component: AdminLlmChatPage, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/account', component: UserAccountInfo, meta: { requiresAuth: true } },
    { path: '/login', component: LoginPage, meta: { requiresUnauth: true }},
    { path: '/forgot-password', component: ForgotPasswordPage, meta: { requiresUnauth: true }},
    { path: '/reset-password', component: ResetPasswordPage, meta: { requiresUnauth: true }},
    { path: '/signup', component: null, meta: { requiresUnauth: true }},
    { path: '/:notFound(.*)', component: PageNotFound }
  ]
});

router.beforeEach(async function(to, _, next) {
  const isAuthed = store.getters['user/isUserAuthenticated'];
  if (to.meta.requiresAuth && !isAuthed) {
    next('/login');
  } else if (to.meta.requiresAdmin) {
    if (!store.getters['user/isUserInfoLoaded']) {
      await store.dispatch('user/loadUserInfo');
    }
    if (store.getters['user/isAdmin']) {
      next();
    } else {
      next('/records');
    }
  } else if (to.meta.requiresUnauth && isAuthed) {
    next('/');
  } else {
    next();
  }
});

export default router
