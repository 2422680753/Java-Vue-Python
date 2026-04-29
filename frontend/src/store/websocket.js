import { defineStore } from 'pinia'
import { ref } from 'vue'
import SockJS from 'sockjs-client'
import Stomp from 'stompjs'

export const useWebSocketStore = defineStore('websocket', () => {
  const isConnected = ref(false)
  const stompClient = ref(null)
  const subscriptions = ref(new Map())
  const parkingLotStatus = ref(null)
  const spotUpdates = ref([])
  const predictions = ref([])
  const events = ref([])
  const warnings = ref([])

  function connect() {
    if (isConnected.value) {
      console.log('WebSocket already connected')
      return
    }

    try {
      const socket = new SockJS('/ws')
      stompClient.value = Stomp.over(socket)
      
      stompClient.value.debug = () => {}

      stompClient.value.connect(
        {},
        () => {
          console.log('WebSocket connected')
          isConnected.value = true
        },
        (error) => {
          console.error('WebSocket connection error:', error)
          isConnected.value = false
          setTimeout(() => connect(), 5000)
        }
      )
    } catch (error) {
      console.error('Failed to create WebSocket connection:', error)
      setTimeout(() => connect(), 5000)
    }
  }

  function disconnect() {
    if (stompClient.value && stompClient.value.connected) {
      stompClient.value.disconnect()
      isConnected.value = false
      subscriptions.value.clear()
      console.log('WebSocket disconnected')
    }
  }

  function subscribe(topic, callback) {
    if (!stompClient.value || !stompClient.value.connected) {
      console.warn('Not connected, cannot subscribe')
      return
    }

    if (subscriptions.value.has(topic)) {
      console.warn(`Already subscribed to ${topic}`)
      return
    }

    const subscription = stompClient.value.subscribe(topic, (message) => {
      try {
        const body = JSON.parse(message.body)
        callback(body)
      } catch (error) {
        console.error('Failed to parse message:', error)
      }
    })

    subscriptions.value.set(topic, subscription)
    console.log(`Subscribed to ${topic}`)
  }

  function unsubscribe(topic) {
    if (subscriptions.value.has(topic)) {
      subscriptions.value.get(topic).unsubscribe()
      subscriptions.value.delete(topic)
      console.log(`Unsubscribed from ${topic}`)
    }
  }

  function subscribeToParkingLot(parkingLotId) {
    const baseTopic = `/topic/parking-lot/${parkingLotId}`

    subscribe(`${baseTopic}/status`, (data) => {
      parkingLotStatus.value = data
    })

    subscribe(`${baseTopic}/spots`, (data) => {
      spotUpdates.value.unshift({
        ...data,
        receivedAt: new Date()
      })
      if (spotUpdates.value.length > 100) {
        spotUpdates.value.pop()
      }
    })

    subscribe(`${baseTopic}/predictions`, (data) => {
      predictions.value = data.predictions || []
    })

    subscribe(`${baseTopic}/events`, (data) => {
      events.value.unshift({
        ...data,
        receivedAt: new Date()
      })
      if (events.value.length > 50) {
        events.value.pop()
      }
    })

    subscribe(`${baseTopic}/warnings`, (data) => {
      warnings.value.unshift({
        ...data,
        receivedAt: new Date()
      })
      if (warnings.value.length > 20) {
        warnings.value.pop()
      }
    })

    console.log(`Subscribed to all topics for parking lot ${parkingLotId}`)
  }

  function unsubscribeFromParkingLot(parkingLotId) {
    const baseTopic = `/topic/parking-lot/${parkingLotId}`
    unsubscribe(`${baseTopic}/status`)
    unsubscribe(`${baseTopic}/spots`)
    unsubscribe(`${baseTopic}/predictions`)
    unsubscribe(`${baseTopic}/events`)
    unsubscribe(`${baseTopic}/warnings`)
    console.log(`Unsubscribed from all topics for parking lot ${parkingLotId}`)
  }

  function clearAll() {
    spotUpdates.value = []
    predictions.value = []
    events.value = []
    warnings.value = []
  }

  return {
    isConnected,
    stompClient,
    subscriptions,
    parkingLotStatus,
    spotUpdates,
    predictions,
    events,
    warnings,
    connect,
    disconnect,
    subscribe,
    unsubscribe,
    subscribeToParkingLot,
    unsubscribeFromParkingLot,
    clearAll
  }
})
