import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/events' },
    {
      path: '/events',
      component: () => import('@/views/EventListView.vue'),
      props: { mode: 1 },
    },
    {
      path: '/seat',
      component: () => import('@/views/EventListView.vue'),
      props: { mode: 2 },
    },
    { path: '/events/:id', component: () => import('@/views/EventDetailView.vue') },
    {
      path: '/orders',
      component: () => import('@/views/OrdersView.vue'),
      meta: { requiresAuth: true },
    },
    { path: '/login', component: () => import('@/views/LoginView.vue') },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue') },
  ],
})

// Vue Router 5 已把守卫的 next(value) 标记为 @deprecated，一律用返回值。
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && auth.isLoggedIn) {
    return '/events'
  }
  return true
})
