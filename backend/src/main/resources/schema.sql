-- 智能停车系统数据库初始化脚本
-- 数据库: smart_parking

CREATE DATABASE IF NOT EXISTS smart_parking 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_parking;

-- 停车场表
CREATE TABLE IF NOT EXISTS parking_lot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '停车场名称',
    address VARCHAR(255) NOT NULL COMMENT '地址',
    total_spots INT NOT NULL COMMENT '总车位数',
    occupied_spots INT DEFAULT 0 COMMENT '已占用车位数',
    hourly_rate DECIMAL(10, 2) DEFAULT 5.00 COMMENT '每小时费用',
    daily_max_rate DECIMAL(10, 2) DEFAULT 50.00 COMMENT '每日最高费用',
    description TEXT COMMENT '描述',
    latitude DOUBLE COMMENT '纬度',
    longitude DOUBLE COMMENT '经度',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_location (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='停车场表';

-- 车位表
CREATE TABLE IF NOT EXISTS parking_spot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    spot_number VARCHAR(20) NOT NULL COMMENT '车位编号',
    zone VARCHAR(20) NOT NULL COMMENT '区域(A/B/C/D)',
    floor INT DEFAULT 1 COMMENT '楼层',
    type VARCHAR(20) DEFAULT 'STANDARD' COMMENT '类型(STANDARD/HANDICAPPED/ELECTRIC/VIP)',
    status VARCHAR(20) DEFAULT 'AVAILABLE' COMMENT '状态(AVAILABLE/OCCUPIED/RESERVED/MAINTENANCE)',
    current_plate_number VARCHAR(20) COMMENT '当前车牌号',
    occupied_since DATETIME COMMENT '占用开始时间',
    estimated_exit_time DATETIME COMMENT '预计离场时间',
    confidence DOUBLE DEFAULT 0.0 COMMENT '预测置信度',
    parking_lot_id BIGINT NOT NULL COMMENT '停车场ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_spot_lot (spot_number, parking_lot_id),
    INDEX idx_parking_lot_id (parking_lot_id),
    INDEX idx_status (status),
    INDEX idx_plate (current_plate_number),
    INDEX idx_estimated_exit (estimated_exit_time),
    FOREIGN KEY (parking_lot_id) REFERENCES parking_lot(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车位表';

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    name VARCHAR(50) COMMENT '姓名',
    avatar VARCHAR(255) COMMENT '头像',
    role VARCHAR(20) DEFAULT 'USER' COMMENT '角色(USER/ADMIN/OPERATOR/MANAGER)',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    balance DECIMAL(10, 2) DEFAULT 0.00 COMMENT '余额',
    last_login_time DATETIME COMMENT '最后登录时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_phone (phone),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 车辆表
CREATE TABLE IF NOT EXISTS vehicle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plate_number VARCHAR(20) NOT NULL UNIQUE COMMENT '车牌号',
    vehicle_type VARCHAR(50) COMMENT '车型',
    brand VARCHAR(50) COMMENT '品牌',
    model VARCHAR(50) COMMENT '型号',
    color VARCHAR(20) COMMENT '颜色',
    year INT COMMENT '年份',
    owner_name VARCHAR(50) COMMENT '车主姓名',
    owner_phone VARCHAR(20) COMMENT '车主电话',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    user_id BIGINT COMMENT '用户ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_plate_number (plate_number),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆表';

-- 停车记录表
CREATE TABLE IF NOT EXISTS parking_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_number VARCHAR(50) NOT NULL UNIQUE COMMENT '记录编号',
    plate_number VARCHAR(20) NOT NULL COMMENT '车牌号',
    vehicle_type VARCHAR(50) COMMENT '车型',
    vehicle_color VARCHAR(20) COMMENT '车辆颜色',
    entry_image_url VARCHAR(255) COMMENT '入场图片URL',
    exit_image_url VARCHAR(255) COMMENT '出场图片URL',
    entry_time DATETIME NOT NULL COMMENT '入场时间',
    exit_time DATETIME COMMENT '出场时间',
    duration_minutes BIGINT COMMENT '停车时长(分钟)',
    amount DECIMAL(10, 2) DEFAULT 0.00 COMMENT '金额',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/COMPLETED/CANCELLED/PAID)',
    payment_method VARCHAR(20) COMMENT '支付方式',
    payment_transaction_id VARCHAR(100) COMMENT '支付交易ID',
    payment_time DATETIME COMMENT '支付时间',
    discount_amount DECIMAL(10, 2) DEFAULT 0.00 COMMENT '优惠金额',
    discount_reason VARCHAR(100) COMMENT '优惠原因',
    entry_gate_id VARCHAR(20) COMMENT '入口闸机ID',
    exit_gate_id VARCHAR(20) COMMENT '出口闸机ID',
    operator_entry VARCHAR(50) COMMENT '入场操作员',
    operator_exit VARCHAR(50) COMMENT '出场操作员',
    parking_lot_id BIGINT COMMENT '停车场ID',
    parking_spot_id BIGINT COMMENT '车位ID',
    user_id BIGINT COMMENT '用户ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_record_number (record_number),
    INDEX idx_plate_number (plate_number),
    INDEX idx_status (status),
    INDEX idx_entry_time (entry_time),
    INDEX idx_exit_time (exit_time),
    INDEX idx_parking_lot_id (parking_lot_id),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (parking_lot_id) REFERENCES parking_lot(id) ON DELETE SET NULL,
    FOREIGN KEY (parking_spot_id) REFERENCES parking_spot(id) ON DELETE SET NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='停车记录表';

-- 支付记录表
CREATE TABLE IF NOT EXISTS payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_number VARCHAR(50) NOT NULL UNIQUE COMMENT '支付单号',
    plate_number VARCHAR(20) NOT NULL COMMENT '车牌号',
    amount DECIMAL(10, 2) NOT NULL COMMENT '金额',
    discount_amount DECIMAL(10, 2) DEFAULT 0.00 COMMENT '优惠金额',
    paid_amount DECIMAL(10, 2) NOT NULL COMMENT '实付金额',
    payment_method VARCHAR(20) COMMENT '支付方式(WECHAT/ALIPAY/BALANCE/CASH/CARD)',
    qr_code_url VARCHAR(255) COMMENT '二维码URL',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态(PENDING/PAID/EXPIRED/CANCELLED/REFUNDED)',
    transaction_id VARCHAR(100) COMMENT '第三方交易ID',
    paid_time DATETIME COMMENT '支付时间',
    expired_time DATETIME COMMENT '过期时间',
    payment_provider VARCHAR(50) COMMENT '支付提供商',
    note TEXT COMMENT '备注',
    parking_record_id BIGINT COMMENT '停车记录ID',
    user_id BIGINT COMMENT '用户ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_payment_number (payment_number),
    INDEX idx_plate_number (plate_number),
    INDEX idx_status (status),
    INDEX idx_transaction_id (transaction_id),
    INDEX idx_parking_record_id (parking_record_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (parking_record_id) REFERENCES parking_record(id) ON DELETE SET NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

-- 车位预测表
CREATE TABLE IF NOT EXISTS spot_prediction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    spot_number VARCHAR(20) NOT NULL COMMENT '车位编号',
    parking_lot_id BIGINT NOT NULL COMMENT '停车场ID',
    plate_number VARCHAR(20) NOT NULL COMMENT '车牌号',
    occupied_since DATETIME NOT NULL COMMENT '占用开始时间',
    predicted_exit_time DATETIME NOT NULL COMMENT '预测离场时间',
    confidence DOUBLE NOT NULL COMMENT '置信度',
    actual_exit_time DATETIME COMMENT '实际离场时间',
    prediction_status VARCHAR(20) DEFAULT 'PREDICTING' COMMENT '预测状态(PREDICTING/PENDING/COMPLETED/ACCURATE/INACCURATE)',
    prediction_model VARCHAR(50) COMMENT '预测模型',
    historical_patterns TEXT COMMENT '历史模式(JSON)',
    parking_record_id BIGINT COMMENT '停车记录ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parking_lot_id (parking_lot_id),
    INDEX idx_spot_number (spot_number),
    INDEX idx_predicted_exit (predicted_exit_time),
    INDEX idx_status (prediction_status),
    INDEX idx_parking_record_id (parking_record_id),
    FOREIGN KEY (parking_record_id) REFERENCES parking_record(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车位预测表';

-- 通知表
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL COMMENT '标题',
    content TEXT NOT NULL COMMENT '内容',
    type VARCHAR(50) NOT NULL COMMENT '类型(PEAK_WARNING/SPOT_AVAILABLE/PAYMENT_REMINDER/...)',
    priority VARCHAR(20) DEFAULT 'NORMAL' COMMENT '优先级(LOW/NORMAL/HIGH/URGENT)',
    target_user_id BIGINT COMMENT '目标用户ID',
    target_phone VARCHAR(20) COMMENT '目标手机号',
    plate_number VARCHAR(20) COMMENT '车牌号',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态(PENDING/SENT/READ/FAILED/CANCELLED)',
    sent_time DATETIME COMMENT '发送时间',
    read_time DATETIME COMMENT '阅读时间',
    channel VARCHAR(20) COMMENT '发送渠道(SMS/WECHAT/APP/EMAIL/SCREEN)',
    error_message TEXT COMMENT '错误信息',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_target_user (target_user_id),
    INDEX idx_plate_number (plate_number),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知表';

-- 初始化默认数据
INSERT INTO parking_lot (name, address, total_spots, hourly_rate, daily_max_rate, description, status) VALUES
('智能停车场-总店', '北京市朝阳区建国路88号', 200, 5.00, 50.00, '市中心核心区域，24小时营业', 'ACTIVE'),
('智能停车场-分店A', '北京市海淀区中关村大街100号', 150, 4.00, 40.00, '科技园区附近', 'ACTIVE'),
('智能停车场-分店B', '北京市西城区金融街50号', 100, 6.00, 60.00, '金融商业区', 'ACTIVE');

-- 创建管理员用户
INSERT INTO users (username, password, email, phone, name, role, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E', 'admin@parking.com', '13800138000', '系统管理员', 'ADMIN', 'ACTIVE');

-- 事务日志表（分布式事务补偿机制）
CREATE TABLE IF NOT EXISTS transaction_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id VARCHAR(50) NOT NULL UNIQUE COMMENT '全局事务ID',
    transaction_type VARCHAR(50) NOT NULL COMMENT '事务类型:PAYMENT_CREATE/PAYMENT_PROCESS/VEHICLE_ENTRY/VEHICLE_EXIT等',
    business_key VARCHAR(100) COMMENT '业务键(支付单号/车牌号等)',
    plate_number VARCHAR(20) COMMENT '车牌号',
    idempotent_key VARCHAR(100) COMMENT '幂等键',
    state VARCHAR(30) DEFAULT 'PENDING' COMMENT '事务状态',
    request_data TEXT COMMENT '请求数据(JSON)',
    response_data TEXT COMMENT '响应数据(JSON)',
    error_message TEXT COMMENT '错误信息',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    max_retries INT DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    timeout_at DATETIME COMMENT '超时时间',
    compensating_at DATETIME COMMENT '补偿开始时间',
    compensated_at DATETIME COMMENT '补偿完成时间',
    failed_at DATETIME COMMENT '失败时间',
    completed_at DATETIME COMMENT '完成时间',
    version INT DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_transaction_id (transaction_id),
    INDEX idx_transaction_type (transaction_type),
    INDEX idx_state (state),
    INDEX idx_business_key (business_key),
    INDEX idx_plate_number (plate_number),
    INDEX idx_idempotent_key (idempotent_key),
    INDEX idx_next_retry_time (next_retry_time),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务日志表';

-- 初始化车位数据（默认第一个停车场）
-- 注意：车位数据将通过应用程序自动初始化

-- 设备表（设备监控和故障切换）
CREATE TABLE IF NOT EXISTS device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL UNIQUE COMMENT '设备唯一标识',
    name VARCHAR(100) NOT NULL COMMENT '设备名称',
    type VARCHAR(50) NOT NULL COMMENT '设备类型:CAMERA/INFRARED_CAMERA/BARRIER_GATE等',
    location VARCHAR(255) COMMENT '设备位置描述',
    parking_lot_id BIGINT COMMENT '所属停车场ID',
    gate_id VARCHAR(20) COMMENT '关联闸机ID',
    status VARCHAR(20) DEFAULT 'OFFLINE' COMMENT '状态:ONLINE/OFFLINE/MAINTENANCE/ERROR/DISABLED',
    health VARCHAR(20) DEFAULT 'UNKNOWN' COMMENT '健康状态:HEALTHY/DEGRADED/UNHEALTHY/CRITICAL/UNKNOWN',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    mac_address VARCHAR(50) COMMENT 'MAC地址',
    serial_number VARCHAR(100) COMMENT '序列号',
    model VARCHAR(100) COMMENT '型号',
    firmware_version VARCHAR(50) COMMENT '固件版本',
    last_heartbeat_time DATETIME COMMENT '最后心跳时间',
    heartbeat_interval INT DEFAULT 5 COMMENT '心跳间隔(秒)',
    last_status_change_time DATETIME COMMENT '最后状态变更时间',
    last_error TEXT COMMENT '最后错误信息',
    error_count INT DEFAULT 0 COMMENT '累计错误次数',
    max_retries INT DEFAULT 3 COMMENT '最大重试次数',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    backup_device_id VARCHAR(50) COMMENT '备用设备ID',
    priority INT DEFAULT 0 COMMENT '优先级(高优先级先被选中)',
    role VARCHAR(20) DEFAULT 'PRIMARY' COMMENT '角色:PRIMARY/BACKUP/REDUNDANT',
    config_json TEXT COMMENT '配置(JSON)',
    description TEXT COMMENT '描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_device_id (device_id),
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_health (health),
    INDEX idx_parking_lot_id (parking_lot_id),
    INDEX idx_gate_id (gate_id),
    INDEX idx_enabled (enabled),
    INDEX idx_backup_device_id (backup_device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- 设备心跳日志表
CREATE TABLE IF NOT EXISTS device_heartbeat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL COMMENT '设备ID',
    heartbeat_time DATETIME NOT NULL COMMENT '心跳时间',
    ip_address VARCHAR(50) COMMENT '心跳来源IP',
    cpu_usage INT COMMENT 'CPU使用率(%)',
    memory_usage INT COMMENT '内存使用率(%)',
    disk_usage INT COMMENT '磁盘使用率(%)',
    network_latency INT COMMENT '网络延迟(ms)',
    temperature DOUBLE COMMENT '温度(°C)',
    battery_level INT COMMENT '电池电量(%)',
    status VARCHAR(20) COMMENT '设备状态',
    health VARCHAR(20) COMMENT '设备健康状态',
    status_message VARCHAR(255) COMMENT '状态消息',
    metrics_json TEXT COMMENT '完整指标(JSON)',
    success BOOLEAN DEFAULT TRUE COMMENT '心跳是否成功',
    error_message TEXT COMMENT '错误信息',
    response_time_ms BIGINT COMMENT '响应时间(ms)',
    version_info VARCHAR(100) COMMENT '版本信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_id (device_id),
    INDEX idx_heartbeat_time (heartbeat_time),
    INDEX idx_success (success),
    INDEX idx_device_time (device_id, heartbeat_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备心跳日志表';

-- 初始化默认设备数据（示例）
INSERT INTO device (device_id, name, type, location, parking_lot_id, gate_id, status, health, priority, role, description) VALUES
('CAM-ENT-001', '入口摄像头-主路', 'CAMERA', '停车场主入口闸机', 1, 'GATE-ENT-001', 'ONLINE', 'HEALTHY', 10, 'PRIMARY', '主入口车牌识别摄像头'),
('CAM-ENT-002', '入口摄像头-备用', 'CAMERA', '停车场主入口备用', 1, 'GATE-ENT-001', 'ONLINE', 'HEALTHY', 5, 'BACKUP', '主入口备用车牌识别摄像头'),
('CAM-EXT-001', '出口摄像头-主路', 'CAMERA', '停车场主出口闸机', 1, 'GATE-EXT-001', 'ONLINE', 'HEALTHY', 10, 'PRIMARY', '主出口车牌识别摄像头'),
('CAM-EXT-002', '出口摄像头-备用', 'CAMERA', '停车场主出口备用', 1, 'GATE-EXT-001', 'ONLINE', 'HEALTHY', 5, 'BACKUP', '主出口备用车牌识别摄像头'),
('IR-CAM-ENT-001', '入口红外摄像头', 'INFRARED_CAMERA', '主入口红外辅助', 1, 'GATE-ENT-001', 'ONLINE', 'HEALTHY', 10, 'PRIMARY', '入口红外辅助摄像头，用于夜间/雨雾天气'),
('IR-CAM-EXT-001', '出口红外摄像头', 'INFRARED_CAMERA', '主出口红外辅助', 1, 'GATE-EXT-001', 'ONLINE', 'HEALTHY', 10, 'PRIMARY', '出口红外辅助摄像头'),
('GATE-ENT-001', '入口道闸', 'BARRIER_GATE', '主入口道闸控制', 1, 'GATE-ENT-001', 'ONLINE', 'HEALTHY', 10, 'PRIMARY', '主入口道闸控制器'),
('GATE-EXT-001', '出口道闸', 'BARRIER_GATE', '主出口道闸控制', 1, 'GATE-EXT-001', 'ONLINE', 'HEALTHY', 10, 'PRIMARY', '主出口道闸控制器'),
('DISPLAY-ENT-001', '入口显示屏', 'DISPLAY', '主入口LED大屏', 1, 'GATE-ENT-001', 'ONLINE', 'HEALTHY', 10, 'PRIMARY', '入口车位状态显示屏'),
('QR-SCAN-001', '支付扫码器', 'QR_SCANNER', '出口支付扫码', 1, 'GATE-EXT-001', 'ONLINE', 'HEALTHY', 10, 'PRIMARY', '出口支付二维码扫描器');

-- 节假日表
CREATE TABLE IF NOT EXISTS holiday (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    holiday_date DATE NOT NULL COMMENT '日期',
    name VARCHAR(100) NOT NULL COMMENT '节假日名称',
    type VARCHAR(20) NOT NULL COMMENT '类型:NATIONAL_HOLIDAY/WEEKEND/SPECIAL_EVENT',
    is_peak_day BOOLEAN DEFAULT TRUE COMMENT '是否为高峰期',
    traffic_factor DOUBLE DEFAULT 1.5 COMMENT '车流影响系数(1.0=正常)',
    description TEXT COMMENT '描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_holiday_date (holiday_date),
    INDEX idx_date (holiday_date),
    INDEX idx_type (type),
    INDEX idx_is_peak_day (is_peak_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节假日表';

-- 天气数据表
CREATE TABLE IF NOT EXISTS weather_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    location VARCHAR(100) NOT NULL COMMENT '位置',
    record_date DATE NOT NULL COMMENT '日期',
    record_hour INT DEFAULT 0 COMMENT '小时(0-23)',
    temperature DOUBLE COMMENT '温度(°C)',
    humidity INT COMMENT '湿度(%)',
    precipitation DOUBLE COMMENT '降水量(mm)',
    wind_speed DOUBLE COMMENT '风速(m/s)',
    visibility DOUBLE COMMENT '能见度(km)',
    weather_condition VARCHAR(50) COMMENT '天气状况:CLEAR/RAIN/FOG/SNOW等',
    uv_index INT COMMENT '紫外线指数',
    pressure INT COMMENT '气压(hPa)',
    description VARCHAR(255) COMMENT '描述',
    data_source VARCHAR(50) DEFAULT 'LOCAL' COMMENT '数据来源',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_location_date (location, record_date),
    INDEX idx_date (record_date),
    INDEX idx_weather_condition (weather_condition)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='天气数据表';

-- 活动/事件表
CREATE TABLE IF NOT EXISTS special_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_name VARCHAR(200) NOT NULL COMMENT '活动名称',
    event_type VARCHAR(50) COMMENT '活动类型:CONCERT/SPORTS/MARKET/EXHIBITION等',
    location VARCHAR(200) COMMENT '活动地点',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NOT NULL COMMENT '结束时间',
    expected_visitors INT COMMENT '预计访客人数',
    parking_lot_id BIGINT COMMENT '影响的停车场ID',
    traffic_factor DOUBLE DEFAULT 1.3 COMMENT '车流影响系数',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    description TEXT COMMENT '描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_start_time (start_time),
    INDEX idx_end_time (end_time),
    INDEX idx_parking_lot_id (parking_lot_id),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动/事件表';

-- 车流预测历史表
CREATE TABLE IF NOT EXISTS traffic_prediction_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parking_lot_id BIGINT NOT NULL COMMENT '停车场ID',
    prediction_time DATETIME NOT NULL COMMENT '预测时间',
    target_hour INT NOT NULL COMMENT '预测小时',
    target_date DATE NOT NULL COMMENT '预测日期',
    predicted_traffic INT COMMENT '预测车流量',
    actual_traffic INT COMMENT '实际车流量',
    prediction_error DOUBLE COMMENT '预测误差(%)',
    model_version VARCHAR(50) COMMENT '模型版本',
    weather_condition VARCHAR(50) COMMENT '天气状况',
    is_holiday BOOLEAN COMMENT '是否节假日',
    has_special_event BOOLEAN COMMENT '是否有特殊活动',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parking_lot_id (parking_lot_id),
    INDEX idx_prediction_time (prediction_time),
    INDEX idx_target_date (target_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车流预测历史表';
