import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '实时大屏' }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/Admin.vue'),
    meta: { title: '管理后台' },
    children: [
      {
        path: '',
        redirect: '/admin/parking-lots'
      },
      {
        path: 'parking-lots',
        name: 'ParkingLots',
        component: () => import('@/views/admin/ParkingLots.vue'),
        meta: { title: '停车场管理' }
      },
      {
        path: 'spots',
        name: 'Spots',
        component: () => import('@/views/admin/Spots.vue'),
        meta: { title: '车位管理' }
      },
      {
        path: 'records',
        name: 'Records',
        component: () => import('@/views/admin/Records.vue'),
        meta: { title: '停车记录' }
      },
      {
        path: 'payments',
        name: 'Payments',
        component: () => import('@/views/admin/Payments.vue'),
        meta: { title: '支付管理' }
      },
      {
        path: 'predictions',
        name: 'Predictions',
        component: () => import('@/views/admin/Predictions.vue'),
        meta: { title: '预测管理' }
      }
    ]
  },
  {
    path: '/user',
    name: 'User',
    component: () => import('@/views/User.vue'),
    meta: { title: '用户端' },
    children: [
      {
        path: '',
        redirect: '/user/park'
      },
      {
        path: 'park',
        name: 'UserPark',
        component: () => import('@/views/user/Park.vue'),
        meta: { title: '停车查询' }
      },
      {
        path: 'payment',
        name: 'UserPayment',
        component: () => import('@/views/user/Payment.vue'),
        meta: { title: '扫码支付' }
      },
      {
        path: 'history',
        name: 'UserHistory',
        component: () => import('@/views/user/History.vue'),
        meta: { title: '停车历史' }
      }
    ]
  },
  {
    path: '/pay/:paymentNumber',
    name: 'PaymentPage',
    component: () => import('@/views/PaymentPage.vue'),
    meta: { title: '扫码支付' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 智能停车系统` : '智能停车系统'
  next()
})

export default router
