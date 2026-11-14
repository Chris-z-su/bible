import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('./views/HomeView.vue'),
    meta: { title: '精读圣经' },
  },
  {
    path: '/read/:bookId/:chapter',
    name: 'read',
    component: () => import('./views/ReadView.vue'),
    meta: { title: '阅读' },
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('./views/SearchView.vue'),
    meta: { title: '经文搜索' },
  },
  {
    path: '/logs',
    name: 'logs',
    component: () => import('./views/LogsView.vue'),
    meta: { title: '系统日志' },
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 96, behavior: 'smooth' }
    return { top: 0 }
  },
})

router.afterEach((to) => {
  const base = '精读圣经'
  document.title = to.meta?.title ? `${to.meta.title} · ${base}` : base
})

export default router
