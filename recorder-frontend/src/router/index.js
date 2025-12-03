import { createRouter, createWebHashHistory } from 'vue-router'
import LoginPage from '../views/LoginPage.vue'
import RecordsPage from '../views/RecordsPage.vue'
import RecordDetail from '../views/RecordDetail.vue'
import UserAccountInfo from '../views/UserAccountInfo.vue'
import AddNewRecord from '../views/AddNewRecord.vue'
import EditRecord from '../views/EditRecord.vue'
import PortfolioPage from '../views/PortfolioPage.vue'
import store from '../store/index.js'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/records', meta: { requiresAuth: true } },
    { path: '/records', component: RecordsPage, meta: { requiresAuth: true } },
    { path: '/records/calendar', component: null, redirect: '/records', meta: { requiresAuth: true } },
    { path: '/records/:recordID', component: RecordDetail, props: true, meta: { requiresAuth: true } },
    { path: '/add-record', component: AddNewRecord, meta: { requiresAuth: true } },
    { path: '/edit-record/:recordID', component: EditRecord, props: true, meta: { requiresAuth: true } },
    { path: '/portfolio', component: PortfolioPage, meta: { requiresAuth: true } },
    { path: '/account', component: UserAccountInfo, meta: { requiresAuth: true } },
    { path: '/login', component: LoginPage, meta: { requiresUnauth: true }},
    { path: '/signup', component: null, meta: { requiresUnauth: true }},
    { path: '/:notFound(.*)', component: null }
  ]
});

router.beforeEach(function(to, _, next) {
  const isAuthed = store.getters['user/isUserAuthenticated'];
  if (to.meta.requiresAuth && !isAuthed) {
    next('/login');
  } else if (to.meta.requiresUnauth && isAuthed) {
    next('/');
  } else {
    next();
  }
});

export default router
