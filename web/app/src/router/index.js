import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue')
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue')
      },
      {
        path: 'agents',
        name: 'Agents',
        component: () => import('../views/Agents.vue')
      },
      {
        path: 'workflows',
        name: 'Workflows',
        component: () => import('../views/Workflows.vue')
      },
      {
        path: 'workflow/:id',
        name: 'WorkflowDetail',
        component: () => import('../views/WorkflowDetail.vue')
      },
      {
        path: 'preview/:taskId',
        name: 'ArtifactPreview',
        component: () => import('../views/ArtifactPreview.vue')
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('../views/Logs.vue')
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/Profile.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.token) {
    next('/login')
  } else {
    next()
  }
})

export default router
