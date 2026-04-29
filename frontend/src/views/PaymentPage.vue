<template>
  <div class="payment-page">
    <div class="payment-container">
      <div class="payment-header">
        <h2>停车缴费</h2>
        <p>请在15分钟内完成支付</p>
      </div>

      <div v-if="loading" class="loading-section">
        <el-icon class="loading-icon"><Loading /></el-icon>
        <p>正在加载支付信息...</p>
      </div>

      <div v-else-if="error" class="error-section">
        <el-icon class="error-icon"><Warning /></el-icon>
        <p class="error-message">{{ error }}</p>
        <el-button type="primary" @click="loadPaymentInfo">重新加载</el-button>
      </div>

      <div v-else-if="paymentInfo" class="payment-content">
        <div class="vehicle-info">
          <div class="info-row">
            <span class="label">车牌号</span>
            <span class="value highlight">{{ paymentInfo.plateNumber }}</span>
          </div>
          <div class="info-row" v-if="paymentInfo.parkingRecord">
            <span class="label">入场时间</span>
            <span class="value">{{ formatTime(paymentInfo.parkingRecord.entryTime) }}</span>
          </div>
          <div class="info-row" v-if="paymentInfo.parkingRecord && paymentInfo.parkingRecord.exitTime">
            <span class="label">出场时间</span>
            <span class="value">{{ formatTime(paymentInfo.parkingRecord.exitTime) }}</span>
          </div>
          <div class="info-row" v-if="paymentInfo.parkingRecord && paymentInfo.parkingRecord.durationMinutes">
            <span class="label">停车时长</span>
            <span class="value">{{ formatDuration(paymentInfo.parkingRecord.durationMinutes) }}</span>
          </div>
        </div>

        <div class="amount-section">
          <div class="amount-label">应付金额</div>
          <div class="amount-value">
            <span class="currency">¥</span>
            <span class="number">{{ paymentInfo.amount }}</span>
          </div>
          <div v-if="paymentInfo.discountAmount > 0" class="discount-info">
            优惠: -¥{{ paymentInfo.discountAmount }}
          </div>
        </div>

        <div class="payment-methods">
          <h4>选择支付方式</h4>
          <div class="method-list">
            <div 
              v-for="method in paymentMethods" 
              :key="method.value"
              :class="['method-item', { active: selectedMethod === method.value }]"
              @click="selectedMethod = method.value"
            >
              <el-icon class="method-icon" :class="method.iconClass">{{ method.icon }}</el-icon>
              <span class="method-name">{{ method.name }}</span>
              <el-icon v-if="selectedMethod === method.value" class="check-icon"><Check /></el-icon>
            </div>
          </div>
        </div>

        <div v-if="!isPaid" class="qrcode-section" v-show="showQrCode">
          <h4>扫码支付</h4>
          <div class="qrcode-container">
            <img 
              v-if="qrCodeUrl" 
              :src="qrCodeUrl" 
              alt="支付二维码" 
              class="qrcode-image"
            />
            <div v-else class="qrcode-placeholder">
              <el-icon><QrCode /></el-icon>
              <p>生成二维码中...</p>
            </div>
          </div>
          <p class="qrcode-tip">请使用{{ getSelectedMethodName() }}扫描二维码支付</p>
          <div class="countdown">
            <el-icon><Timer /></el-icon>
            <span>支付剩余时间: {{ remainingTime }}</span>
          </div>
        </div>

        <div v-if="isPaid" class="success-section">
          <el-icon class="success-icon"><CircleCheck /></el-icon>
          <h3>支付成功</h3>
          <p class="success-tip">请在15分钟内离场</p>
          <div class="success-details">
            <div class="detail-row">
              <span>支付时间:</span>
              <span>{{ formatTime(paymentInfo.paidTime) }}</span>
            </div>
            <div class="detail-row">
              <span>交易单号:</span>
              <span class="trx-id">{{ paymentInfo.transactionId }}</span>
            </div>
          </div>
        </div>

        <div v-if="!isPaid" class="action-section">
          <el-button 
            type="primary" 
            size="large" 
            @click="simulatePayment"
            :loading="paying"
          >
            模拟支付（测试）
          </el-button>
          <el-button 
            type="success" 
            size="large" 
            @click="refreshQrCode"
          >
            刷新二维码
          </el-button>
        </div>
      </div>

      <div class="payment-footer">
        <p>如有问题请联系客服: 400-123-4567</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, onActivated } from 'vue'
import { useRoute } from 'vue-router'
import { paymentAPI } from '@/api'
import dayjs from 'dayjs'

const route = useRoute()

const loading = ref(true)
const error = ref('')
const paymentInfo = ref(null)
const selectedMethod = ref('WECHAT')
const showQrCode = ref(true)
const qrCodeUrl = ref('')
const isPaid = ref(false)
const paying = ref(false)
const remainingTime = ref('15:00')

let countdownInterval = null
let checkInterval = null

const paymentMethods = [
  { value: 'WECHAT', name: '微信支付', icon: 'ChatDotRound', iconClass: 'wechat' },
  { value: 'ALIPAY', name: '支付宝', icon: 'Wallet', iconClass: 'alipay' },
  { value: 'BALANCE', name: '余额支付', icon: 'Coin', iconClass: 'balance' }
]

async function loadPaymentInfo() {
  loading.value = true
  error.value = ''
  
  try {
    const paymentNumber = route.params.paymentNumber || route.query.paymentNumber
    
    if (!paymentNumber) {
      error.value = '缺少支付单号'
      return
    }
    
    const res = await paymentAPI.getStatus(paymentNumber)
    
    if (res.success) {
      paymentInfo.value = res.data
      isPaid.value = res.data.paid
      
      if (paymentInfo.value.qrCodeUrl) {
        qrCodeUrl.value = paymentInfo.value.qrCodeUrl
      }
      
      if (isPaid.value) {
        stopCountdown()
      } else {
        startCountdown()
        startPaymentCheck()
      }
    } else {
      error.value = res.message || '获取支付信息失败'
    }
  } catch (err) {
    console.error('Load payment info error:', err)
    error.value = '获取支付信息失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function startCountdown() {
  if (!paymentInfo.value || !paymentInfo.value.expiredTime) {
    return
  }
  
  countdownInterval = setInterval(() => {
    const now = dayjs()
    const expired = dayjs(paymentInfo.value.expiredTime)
    const diff = expired.diff(now, 'second')
    
    if (diff <= 0) {
      remainingTime.value = '00:00'
      stopCountdown()
      error.value = '支付已过期，请重新生成支付订单'
    } else {
      const minutes = Math.floor(diff / 60)
      const seconds = diff % 60
      remainingTime.value = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    }
  }, 1000)
}

function stopCountdown() {
  if (countdownInterval) {
    clearInterval(countdownInterval)
    countdownInterval = null
  }
}

function startPaymentCheck() {
  const paymentNumber = route.params.paymentNumber || route.query.paymentNumber
  if (!paymentNumber) return
  
  checkInterval = setInterval(async () => {
    try {
      const res = await paymentAPI.getStatus(paymentNumber)
      if (res.success && res.data.paid) {
        isPaid.value = true
        paymentInfo.value = res.data
        stopCountdown()
        stopPaymentCheck()
      }
    } catch (err) {
      console.error('Check payment status error:', err)
    }
  }, 3000)
}

function stopPaymentCheck() {
  if (checkInterval) {
    clearInterval(checkInterval)
    checkInterval = null
  }
}

async function simulatePayment() {
  if (paying.value || isPaid.value) return
  
  paying.value = true
  
  try {
    const paymentNumber = route.params.paymentNumber || route.query.paymentNumber
    
    const res = await paymentAPI.simulatePay(paymentNumber, selectedMethod.value)
    
    if (res.success) {
      isPaid.value = true
      paymentInfo.value = {
        ...paymentInfo.value,
        status: 'PAID',
        paid: true,
        paidTime: res.data.paidTime || dayjs().format(),
        transactionId: res.data.transactionId
      }
      stopCountdown()
      stopPaymentCheck()
    } else {
      error.value = res.message || '支付失败'
    }
  } catch (err) {
    console.error('Simulate payment error:', err)
    error.value = '支付失败，请稍后重试'
  } finally {
    paying.value = false
  }
}

function refreshQrCode() {
  loadPaymentInfo()
}

function getSelectedMethodName() {
  const method = paymentMethods.find(m => m.value === selectedMethod.value)
  return method ? method.name : ''
}

function formatTime(time) {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

function formatDuration(minutes) {
  if (!minutes) return '-'
  if (minutes < 60) {
    return `${minutes}分钟`
  }
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return mins > 0 ? `${hours}小时${mins}分钟` : `${hours}小时`
}

onMounted(() => {
  loadPaymentInfo()
})

onActivated(() => {
  loadPaymentInfo()
})

onUnmounted(() => {
  stopCountdown()
  stopPaymentCheck()
})
</script>

<style scoped>
.payment-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.payment-container {
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  width: 100%;
  max-width: 480px;
  overflow: hidden;
}

.payment-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  padding: 30px;
  text-align: center;
}

.payment-header h2 {
  margin: 0 0 10px 0;
  font-size: 24px;
}

.payment-header p {
  margin: 0;
  opacity: 0.9;
  font-size: 14px;
}

.loading-section,
.error-section {
  padding: 60px 40px;
  text-align: center;
}

.loading-icon,
.error-icon {
  font-size: 64px;
  margin-bottom: 20px;
}

.loading-icon {
  color: #667eea;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.error-icon {
  color: #f56c6c;
}

.error-message {
  color: #f56c6c;
  margin-bottom: 20px;
}

.payment-content {
  padding: 30px;
}

.vehicle-info {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #eee;
}

.info-row:last-child {
  border-bottom: none;
}

.label {
  color: #666;
  font-size: 14px;
}

.value {
  color: #333;
  font-size: 14px;
  font-weight: 500;
}

.value.highlight {
  color: #667eea;
  font-size: 18px;
  font-weight: bold;
}

.amount-section {
  text-align: center;
  padding: 30px 0;
  border-bottom: 1px solid #eee;
  margin-bottom: 20px;
}

.amount-label {
  color: #666;
  font-size: 14px;
  margin-bottom: 10px;
}

.amount-value {
  display: flex;
  justify-content: center;
  align-items: baseline;
  gap: 5px;
}

.currency {
  font-size: 24px;
  color: #f56c6c;
}

.number {
  font-size: 48px;
  font-weight: bold;
  color: #f56c6c;
  line-height: 1;
}

.discount-info {
  color: #67c23a;
  font-size: 14px;
  margin-top: 10px;
}

.payment-methods h4 {
  margin: 0 0 15px 0;
  color: #333;
  font-size: 16px;
}

.method-list {
  display: flex;
  gap: 15px;
}

.method-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 15px;
  border: 2px solid #eee;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.method-item:hover {
  border-color: #667eea;
}

.method-item.active {
  border-color: #667eea;
  background: #f8f9ff;
}

.method-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.method-icon.wechat { color: #07c160; }
.method-icon.alipay { color: #1677ff; }
.method-icon.balance { color: #e6a23c; }

.method-name {
  font-size: 14px;
  color: #333;
}

.check-icon {
  color: #67c23a;
  margin-top: 5px;
}

.qrcode-section {
  margin-top: 20px;
  text-align: center;
}

.qrcode-section h4 {
  margin: 0 0 20px 0;
  color: #333;
  font-size: 16px;
}

.qrcode-container {
  width: 200px;
  height: 200px;
  margin: 0 auto 15px;
  border: 1px solid #eee;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.qrcode-image {
  width: 180px;
  height: 180px;
}

.qrcode-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: #999;
}

.qrcode-placeholder .el-icon {
  font-size: 48px;
}

.qrcode-tip {
  color: #666;
  font-size: 14px;
  margin-bottom: 15px;
}

.countdown {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #f56c6c;
  font-size: 14px;
}

.success-section {
  text-align: center;
  padding: 20px 0;
}

.success-icon {
  font-size: 64px;
  color: #67c23a;
  margin-bottom: 15px;
}

.success-section h3 {
  margin: 0 0 10px 0;
  color: #67c23a;
  font-size: 20px;
}

.success-tip {
  color: #666;
  font-size: 14px;
  margin-bottom: 20px;
}

.success-details {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 15px;
  text-align: left;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 14px;
}

.detail-row span:first-child {
  color: #666;
}

.detail-row span:last-child {
  color: #333;
  font-weight: 500;
}

.trx-id {
  font-family: monospace;
  font-size: 12px;
}

.action-section {
  display: flex;
  gap: 15px;
  margin-top: 20px;
}

.action-section .el-button {
  flex: 1;
}

.payment-footer {
  padding: 20px 30px;
  background: #f8f9fa;
  text-align: center;
}

.payment-footer p {
  margin: 0;
  color: #999;
  font-size: 12px;
}
</style>
