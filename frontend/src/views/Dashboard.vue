<template>
  <div class="dashboard-container">
    <header class="dashboard-header">
      <div class="title">
        <el-icon class="icon"><Parking /></el-icon>
        <span>{{ parkingLotName }}</span>
      </div>
      <div class="status-bar">
        <span class="time">{{ currentTime }}</span>
        <el-tag :type="isPeakHour ? 'danger' : 'success'" size="large" class="peak-tag">
          {{ isPeakHour ? '高峰期' : '平峰期' }}
        </el-tag>
        <el-tag :type="wsConnected ? 'success' : 'danger'" size="small" class="connection-tag">
          {{ wsConnected ? '已连接' : '断开' }}
        </el-tag>
      </div>
    </header>

    <main class="dashboard-main">
      <div class="stats-row">
        <div class="stat-card total">
          <div class="stat-value">{{ totalSpots }}</div>
          <div class="stat-label">总车位</div>
        </div>
        <div class="stat-card available">
          <div class="stat-value">{{ availableSpots }}</div>
          <div class="stat-label">空余车位</div>
        </div>
        <div class="stat-card occupied">
          <div class="stat-value">{{ occupiedSpots }}</div>
          <div class="stat-label">已占车位</div>
        </div>
        <div class="stat-card occupancy">
          <div class="stat-value">{{ occupancyRate }}%</div>
          <div class="stat-label">使用率</div>
        </div>
      </div>

      <div class="content-row">
        <div class="left-panel">
          <div class="panel-section parking-map">
            <h3>实时车位状态</h3>
            <div class="spot-grid">
              <div 
                v-for="spot in spots" 
                :key="spot.spotNumber"
                :class="['spot-item', spot.status.toLowerCase()]"
                :title="`车位: ${spot.spotNumber}, 状态: ${spot.status}`"
              >
                <span class="spot-number">{{ spot.spotNumber }}</span>
                <span v-if="spot.currentPlateNumber" class="spot-plate">
                  {{ spot.currentPlateNumber }}
                </span>
              </div>
            </div>
            <div class="legend">
              <span class="legend-item available"><span class="dot"></span>空闲</span>
              <span class="legend-item occupied"><span class="dot"></span>占用</span>
              <span class="legend-item reserved"><span class="dot"></span>预留</span>
            </div>
          </div>

          <div class="panel-section upcoming">
            <h3>
              <el-icon><Timer /></el-icon>
              未来2小时即将可用的车位
            </h3>
            <div v-if="upcomingSpots.length === 0" class="no-data">
              暂无即将可用的车位预测
            </div>
            <div v-else class="upcoming-list">
              <div 
                v-for="spot in upcomingSpots" 
                :key="spot.spotNumber"
                class="upcoming-item"
              >
                <div class="spot-info">
                  <span class="spot-num">{{ spot.spotNumber }}</span>
                  <span class="plate">{{ spot.plateNumber }}</span>
                </div>
                <div class="time-info">
                  <span class="countdown">
                    {{ formatMinutes(spot.minutesUntilAvailable) }}后可用
                  </span>
                  <el-progress 
                    :percentage="Math.min(100, spot.confidence * 100)" 
                    :color="spot.confidence > 0.8 ? '#67c23a' : '#e6a23c'"
                    :stroke-width="6"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="right-panel">
          <div class="panel-section events">
            <h3>
              <el-icon><Notification /></el-icon>
              实时事件
            </h3>
            <div class="events-list">
              <div 
                v-for="event in recentEvents" 
                :key="event.timestamp"
                :class="['event-item', event.type.toLowerCase()]"
              >
                <div class="event-icon">
                  <el-icon v-if="event.type === 'ENTRY'"><CaretTop /></el-icon>
                  <el-icon v-else-if="event.type === 'EXIT'"><CaretBottom /></el-icon>
                  <el-icon v-else><Bell /></el-icon>
                </div>
                <div class="event-content">
                  <div class="event-title">
                    {{ event.type === 'ENTRY' ? '车辆入场' : event.type === 'EXIT' ? '车辆出场' : '系统事件' }}
                  </div>
                  <div class="event-detail">
                    车牌: {{ event.plateNumber }}
                    <span v-if="event.spotNumber"> 车位: {{ event.spotNumber }}</span>
                    <span v-if="event.amount"> 金额: ¥{{ event.amount }}</span>
                  </div>
                </div>
                <div class="event-time">
                  {{ formatTime(event.timestamp) }}
                </div>
              </div>
            </div>
          </div>

          <div v-if="warnings.length > 0" class="panel-section warnings">
            <h3 class="warning-title">
              <el-icon><Warning /></el-icon>
              预警信息
            </h3>
            <div class="warnings-list">
              <div 
                v-for="warning in warnings.slice(0, 5)" 
                :key="warning.timestamp"
                class="warning-item"
              >
                <div class="warning-content">
                  <div class="warning-msg">{{ warning.message }}</div>
                  <div class="warning-detail">可用车位: {{ warning.availableSpots }} 个</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, onActivated } from 'vue'
import { useWebSocketStore } from '@/store/websocket'
import { parkingLotAPI, predictionAPI } from '@/api'
import dayjs from 'dayjs'

const wsStore = useWebSocketStore()

const parkingLotName = ref('智能停车场')
const totalSpots = ref(0)
const availableSpots = ref(0)
const occupiedSpots = ref(0)
const occupancyRate = ref(0)
const isPeakHour = ref(false)
const currentTime = ref('')
const spots = ref([])
const upcomingSpots = ref([])
const recentEvents = ref([])
const warnings = ref([])

const wsConnected = computed(() => wsStore.isConnected)

let timeInterval = null
let refreshInterval = null
const parkingLotId = 1

async function loadParkingLotStatus() {
  try {
    const res = await parkingLotAPI.getStatus(parkingLotId)
    if (res.success && res.data) {
      const data = res.data
      parkingLotName.value = data.parkingLotName || '智能停车场'
      totalSpots.value = data.totalSpots || 0
      availableSpots.value = data.availableSpots || 0
      occupiedSpots.value = data.occupiedSpots || 0
      occupancyRate.value = Math.round((data.occupancyRate || 0) * 100)
      isPeakHour.value = data.isPeakHour || false
      
      if (data.upcomingAvailableSpots) {
        upcomingSpots.value = data.upcomingAvailableSpots
      }
    }
  } catch (error) {
    console.error('Failed to load parking lot status:', error)
  }
}

async function loadSpots() {
  try {
    const res = await parkingLotAPI.getSpots(parkingLotId)
    if (res.success && res.data) {
      spots.value = res.data
    }
  } catch (error) {
    console.error('Failed to load spots:', error)
  }
}

async function loadPredictions() {
  try {
    const res = await predictionAPI.getUpcoming(parkingLotId, 2)
    if (res.success && res.data) {
      upcomingSpots.value = res.data
    }
  } catch (error) {
    console.error('Failed to load predictions:', error)
  }
}

function updateCurrentTime() {
  currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
}

function formatMinutes(minutes) {
  if (minutes < 60) {
    return `${minutes}分钟`
  }
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return mins > 0 ? `${hours}小时${mins}分钟` : `${hours}小时`
}

function formatTime(timestamp) {
  return dayjs(timestamp).format('HH:mm:ss')
}

function setupWebSocketListeners() {
  if (wsStore.isConnected) {
    wsStore.subscribeToParkingLot(parkingLotId)
  }
  
  recentEvents.value = wsStore.events.slice(0, 10)
  warnings.value = wsStore.warnings
}

onMounted(() => {
  updateCurrentTime()
  timeInterval = setInterval(updateCurrentTime, 1000)
  
  wsStore.connect()
  
  loadParkingLotStatus()
  loadSpots()
  loadPredictions()
  
  refreshInterval = setInterval(() => {
    loadParkingLotStatus()
    loadSpots()
  }, 5000)
  
  setTimeout(setupWebSocketListeners, 1000)
})

onActivated(() => {
  loadParkingLotStatus()
  loadSpots()
  loadPredictions()
})

onUnmounted(() => {
  if (timeInterval) clearInterval(timeInterval)
  if (refreshInterval) clearInterval(refreshInterval)
  wsStore.unsubscribeFromParkingLot(parkingLotId)
})
</script>

<style scoped>
.dashboard-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  color: #fff;
  font-family: 'Microsoft YaHei', sans-serif;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 30px;
  background: rgba(0, 0, 0, 0.3);
  border-bottom: 2px solid #00d4ff;
}

.title {
  display: flex;
  align-items: center;
  gap: 15px;
  font-size: 28px;
  font-weight: bold;
  background: linear-gradient(90deg, #00d4ff, #00ff88);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.icon {
  font-size: 36px;
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 20px;
}

.time {
  font-size: 18px;
  font-family: 'Digital-7', monospace;
  color: #00ff88;
}

.peak-tag {
  font-size: 16px;
  font-weight: bold;
}

.connection-tag {
  font-size: 12px;
}

.dashboard-main {
  padding: 20px 30px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 25px;
  text-align: center;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  transition: transform 0.3s, box-shadow 0.3s;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
}

.stat-card.total {
  border-left: 4px solid #00d4ff;
}

.stat-card.available {
  border-left: 4px solid #00ff88;
}

.stat-card.occupied {
  border-left: 4px solid #ff6b6b;
}

.stat-card.occupancy {
  border-left: 4px solid #ffd93d;
}

.stat-value {
  font-size: 48px;
  font-weight: bold;
  font-family: 'Digital-7', monospace;
  margin-bottom: 10px;
}

.stat-card.total .stat-value { color: #00d4ff; }
.stat-card.available .stat-value { color: #00ff88; }
.stat-card.occupied .stat-value { color: #ff6b6b; }
.stat-card.occupancy .stat-value { color: #ffd93d; }

.stat-label {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.7);
}

.content-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
}

.left-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.right-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.panel-section {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 20px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.panel-section h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 20px 0;
  font-size: 18px;
  color: #00d4ff;
  border-bottom: 1px solid rgba(0, 212, 255, 0.3);
  padding-bottom: 10px;
}

.spot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.spot-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  min-height: 60px;
}

.spot-item.available {
  background: rgba(0, 255, 136, 0.2);
  border: 1px solid #00ff88;
}

.spot-item.occupied {
  background: rgba(255, 107, 107, 0.2);
  border: 1px solid #ff6b6b;
}

.spot-item.reserved {
  background: rgba(255, 217, 61, 0.2);
  border: 1px solid #ffd93d;
}

.spot-item:hover {
  transform: scale(1.05);
}

.spot-number {
  font-weight: bold;
  font-size: 14px;
}

.spot-plate {
  font-size: 10px;
  opacity: 0.8;
  margin-top: 4px;
}

.legend {
  display: flex;
  justify-content: center;
  gap: 30px;
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.legend-item .dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.legend-item.available .dot { background: #00ff88; }
.legend-item.occupied .dot { background: #ff6b6b; }
.legend-item.reserved .dot { background: #ffd93d; }

.upcoming-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.upcoming-item {
  background: rgba(0, 212, 255, 0.1);
  border-radius: 8px;
  padding: 15px;
  border-left: 3px solid #00d4ff;
}

.spot-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}

.spot-num {
  font-weight: bold;
  font-size: 16px;
  color: #00d4ff;
}

.plate {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
}

.countdown {
  font-size: 14px;
  color: #00ff88;
  margin-bottom: 8px;
  display: block;
}

.events-list {
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.event-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  transition: background 0.3s;
}

.event-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

.event-item.entry {
  border-left: 3px solid #00ff88;
}

.event-item.exit {
  border-left: 3px solid #ff6b6b;
}

.event-icon {
  font-size: 24px;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 50%;
}

.event-item.entry .event-icon { color: #00ff88; }
.event-item.exit .event-icon { color: #ff6b6b; }

.event-content {
  flex: 1;
}

.event-title {
  font-weight: bold;
  font-size: 14px;
  margin-bottom: 4px;
}

.event-detail {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.event-time {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.warning-title {
  color: #ff6b6b !important;
}

.warnings-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.warning-item {
  background: rgba(255, 107, 107, 0.2);
  border: 1px solid #ff6b6b;
  border-radius: 8px;
  padding: 12px;
}

.warning-msg {
  font-weight: bold;
  color: #ff6b6b;
  margin-bottom: 5px;
}

.warning-detail {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.no-data {
  text-align: center;
  padding: 40px;
  color: rgba(255, 255, 255, 0.5);
}
</style>
