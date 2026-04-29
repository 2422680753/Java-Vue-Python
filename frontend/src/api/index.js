import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

api.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export const parkingLotAPI = {
  getAll: () => api.get('/parking/lots'),
  getById: (id) => api.get(`/parking/lots/${id}`),
  create: (data) => api.post('/parking/lots', data),
  getStatus: (id) => api.get(`/parking/lots/${id}/status`),
  getSpots: (id) => api.get(`/parking/lots/${id}/spots`),
  getAvailableSpots: (id) => api.get(`/parking/lots/${id}/spots/available`),
  getSpotCounts: (id) => api.get(`/parking/lots/${id}/spots/count`)
}

export const licensePlateAPI = {
  recognize: (formData) => api.post('/license-plate/recognize', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  processEntry: (data, parkingLotId, gateId) => api.post('/license-plate/entry', data, {
    params: { parkingLotId, gateId }
  }),
  processExit: (data, parkingLotId, gateId) => api.post('/license-plate/exit', data, {
    params: { parkingLotId, gateId }
  }),
  entryWithImage: (formData, parkingLotId, gateId) => api.post('/license-plate/entry-with-image', formData, {
    params: { parkingLotId, gateId },
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  exitWithImage: (formData, parkingLotId, gateId) => api.post('/license-plate/exit-with-image', formData, {
    params: { parkingLotId, gateId },
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const paymentAPI = {
  create: (parkingRecordId, data) => api.post('/payment/create', data, {
    params: { parkingRecordId }
  }),
  process: (paymentNumber) => api.post(`/payment/pay/${paymentNumber}`),
  getStatus: (paymentNumber) => api.get(`/payment/status/${paymentNumber}`),
  getDetail: (paymentNumber) => api.get(`/payment/detail/${paymentNumber}`),
  simulatePay: (paymentNumber, paymentMethod) => api.post('/payment/simulate-pay', null, {
    params: { paymentNumber, paymentMethod }
  })
}

export const predictionAPI = {
  getUpcoming: (parkingLotId, hoursAhead) => api.get(`/prediction/lots/${parkingLotId}/upcoming`, {
    params: { hoursAhead }
  }),
  checkPeak: (parkingLotId) => api.get(`/prediction/lots/${parkingLotId}/peak-status`),
  triggerUpdate: (parkingLotId) => api.post(`/prediction/lots/${parkingLotId}/trigger-update`),
  getNextAvailable: (parkingLotId) => api.get(`/prediction/lots/${parkingLotId}/next-available`),
  getAvailableSoon: (parkingLotId) => api.get(`/prediction/lots/${parkingLotId}/available-soon`)
}

export default api
