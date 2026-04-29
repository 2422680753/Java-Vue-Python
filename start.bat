@echo off
chcp 65001
echo ========================================
echo     智能停车系统 - 一键启动脚本
echo ========================================
echo.

set PROJECT_ROOT=%~dp0
set BACKEND_DIR=%PROJECT_ROOT%backend
set FRONTEND_DIR=%PROJECT_ROOT%frontend
set AI_LP_DIR=%PROJECT_ROOT%ai\license_plate
set AI_PRED_DIR=%PROJECT_ROOT%ai\prediction

echo [1/4] 检查环境...
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Java，请先安装 JDK 11+
    pause
    exit /b 1
)

where node >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Node.js，请先安装 Node.js 16+
    pause
    exit /b 1
)

where python >nul 2>&1
if %errorlevel% neq 0 (
    echo [警告] 未找到 Python，AI模块将无法启动
    set AI_AVAILABLE=false
) else (
    set AI_AVAILABLE=true
)

echo.
echo [2/4] 启动后端服务 (端口: 8080)...
cd /d "%BACKEND_DIR%"
start "Parking-Backend" cmd /c "mvn spring-boot:run"
echo 后端服务正在启动中...

echo.
echo [3/4] 启动前端服务 (端口: 3000)...
cd /d "%FRONTEND_DIR%"
if not exist "node_modules" (
    echo 安装前端依赖...
    call npm install
)
start "Parking-Frontend" cmd /c "npm run dev"
echo 前端服务正在启动中...

if "%AI_AVAILABLE%"=="true" (
    echo.
    echo [4/4] 启动AI服务...
    
    echo   - 车牌识别服务 (端口: 5000)...
    cd /d "%AI_LP_DIR%"
    if not exist "venv" (
        echo 创建Python虚拟环境...
        python -m venv venv
    )
    call venv\Scripts\activate
    pip install -q flask flask-cors opencv-python numpy pillow
    start "Parking-AI-LicensePlate" cmd /c "python app.py"
    echo   车牌识别服务正在启动中...
    
    echo.
    echo   - 车位预测服务 (端口: 5001)...
    cd /d "%AI_PRED_DIR%"
    if not exist "venv" (
        echo 创建Python虚拟环境...
        python -m venv venv
    )
    call venv\Scripts\activate
    pip install -q flask flask-cors numpy pandas
    start "Parking-AI-Prediction" cmd /c "python app.py"
    echo   车位预测服务正在启动中...
) else (
    echo.
    echo [4/4] 跳过AI服务启动 (未安装Python)
)

echo.
echo ========================================
echo     服务启动完成！
echo ========================================
echo.
echo 服务地址:
echo   - 后端API:      http://localhost:8080/api
echo   - 前端页面:      http://localhost:3000
echo   - 车牌识别API:   http://localhost:5000
echo   - 车位预测API:   http://localhost:5001
echo.
echo 访问地址:
echo   - 实时大屏:      http://localhost:3000/dashboard
echo   - 管理后台:      http://localhost:3000/admin
echo   - 用户端:        http://localhost:3000/user
echo.
echo 注意事项:
echo   1. 请确保 MySQL 数据库已启动
echo   2. 首次启动需要初始化数据库
echo   3. AI服务需要额外安装依赖包
echo.
echo 按任意键打开浏览器访问实时大屏...
pause >nul
start http://localhost:3000/dashboard
echo.
echo 所有服务已启动完成！
pause
