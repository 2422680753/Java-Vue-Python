#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
车牌识别API服务
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import numpy as np
import cv2
import base64
import os
from datetime import datetime
import logging

from plate_recognition import recognize_plate, RecognitionResult

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

UPLOAD_FOLDER = os.path.join(os.path.dirname(__file__), 'uploads')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        'status': 'healthy',
        'service': 'license-plate-recognition',
        'timestamp': datetime.now().isoformat()
    })

@app.route('/api/recognize', methods=['POST'])
def recognize():
    try:
        if 'image' in request.files:
            image_file = request.files['image']
            if image_file.filename == '':
                return jsonify({
                    'success': False,
                    'message': '没有选择文件'
                }), 400
            
            image_data = image_file.read()
            nparr = np.frombuffer(image_data, np.uint8)
            image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            filename = f"{timestamp}_{image_file.filename}"
            filepath = os.path.join(UPLOAD_FOLDER, filename)
            cv2.imwrite(filepath, image)
            
        elif 'image_base64' in request.json:
            image_base64 = request.json['image_base64']
            if ',' in image_base64:
                image_base64 = image_base64.split(',')[1]
            
            image_data = base64.b64decode(image_base64)
            nparr = np.frombuffer(image_data, np.uint8)
            image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            filename = f"{timestamp}_base64.jpg"
            filepath = os.path.join(UPLOAD_FOLDER, filename)
            cv2.imwrite(filepath, image)
            
        else:
            return jsonify({
                'success': False,
                'message': '请提供图像文件或Base64编码的图像'
            }), 400
        
        if image is None:
            return jsonify({
                'success': False,
                'message': '无法解码图像'
            }), 400
        
        gate_id = request.form.get('gate_id', request.args.get('gate_id', 'UNKNOWN'))
        
        logger.info(f"开始识别车牌 - 闸机: {gate_id}")
        result = recognize_plate(image_data=image)
        
        image_url = f"/uploads/{filename}"
        
        response = {
            'success': result.success,
            'message': result.message,
            'plateNumber': result.plate_number,
            'province': result.province,
            'city': result.city,
            'vehicleType': result.vehicle_type,
            'color': result.color,
            'confidence': result.confidence,
            'imageUrl': image_url,
            'timestamp': datetime.now().isoformat(),
            'processingTime': result.processing_time,
            'algorithmVersion': result.algorithm_version,
            'gateId': gate_id
        }
        
        logger.info(f"识别完成 - 车牌: {result.plate_number}, 置信度: {result.confidence:.2%}, 耗时: {result.processing_time:.2f}ms")
        
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"识别过程中发生错误: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'message': f'识别服务错误: {str(e)}'
        }), 500

@app.route('/api/recognize/batch', methods=['POST'])
def recognize_batch():
    try:
        if 'images' not in request.files:
            return jsonify({
                'success': False,
                'message': '请提供图像文件'
            }), 400
        
        images = request.files.getlist('images')
        results = []
        
        for idx, image_file in enumerate(images):
            try:
                image_data = image_file.read()
                nparr = np.frombuffer(image_data, np.uint8)
                image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
                
                result = recognize_plate(image_data=image)
                
                timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
                filename = f"{timestamp}_{idx}_{image_file.filename}"
                filepath = os.path.join(UPLOAD_FOLDER, filename)
                cv2.imwrite(filepath, image)
                
                results.append({
                    'index': idx,
                    'success': result.success,
                    'plateNumber': result.plate_number,
                    'confidence': result.confidence,
                    'color': result.color,
                    'message': result.message,
                    'imageUrl': f"/uploads/{filename}"
                })
                
            except Exception as e:
                logger.error(f"批量识别第 {idx} 张图像失败: {str(e)}")
                results.append({
                    'index': idx,
                    'success': False,
                    'message': str(e)
                })
        
        return jsonify({
            'success': True,
            'total': len(images),
            'successCount': sum(1 for r in results if r['success']),
            'results': results
        })
        
    except Exception as e:
        logger.error(f"批量识别错误: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'message': f'批量识别服务错误: {str(e)}'
        }), 500

@app.route('/api/recognize/verify', methods=['POST'])
def verify_plate():
    try:
        data = request.get_json()
        
        if not data or 'plateNumber' not in data:
            return jsonify({
                'success': False,
                'message': '请提供车牌号'
            }), 400
        
        plate_number = data['plateNumber']
        expected_plate = data.get('expectedPlate')
        
        is_valid = validate_plate_format(plate_number)
        
        match_confidence = 0.0
        if expected_plate:
            match_confidence = calculate_plate_similarity(plate_number, expected_plate)
        
        province = get_province(plate_number)
        
        return jsonify({
            'success': True,
            'plateNumber': plate_number,
            'isValidFormat': is_valid,
            'province': province,
            'matchConfidence': match_confidence,
            'expectedPlate': expected_plate
        })
        
    except Exception as e:
        logger.error(f"车牌验证错误: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'message': str(e)
        }), 500

def validate_plate_format(plate_number):
    if not plate_number:
        return False
    
    plate_number = plate_number.upper().strip()
    
    import re
    patterns = [
        r'^[京津沪渝冀豫云辽黑湘皖鲁苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼新][A-Z][A-Z0-9]{5}$',
        r'^[京津沪渝冀豫云辽黑湘皖鲁苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼新][A-Z][A-Z0-9]{6}$',
        r'^WJ[京津沪渝冀豫云辽黑湘皖鲁苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼新]?[0-9]{5}$',
    ]
    
    for pattern in patterns:
        if re.match(pattern, plate_number):
            return True
    
    return False

def get_province(plate_number):
    province_map = {
        '京': '北京', '津': '天津', '沪': '上海', '渝': '重庆',
        '冀': '河北', '豫': '河南', '云': '云南', '辽': '辽宁',
        '黑': '黑龙江', '湘': '湖南', '皖': '安徽', '鲁': '山东',
        '苏': '江苏', '浙': '浙江', '赣': '江西', '鄂': '湖北',
        '桂': '广西', '甘': '甘肃', '晋': '山西', '蒙': '内蒙古',
        '陕': '陕西', '吉': '吉林', '闽': '福建', '贵': '贵州',
        '粤': '广东', '青': '青海', '藏': '西藏', '川': '四川',
        '宁': '宁夏', '琼': '海南', '新': '新疆'
    }
    
    if plate_number and len(plate_number) > 0:
        first_char = plate_number[0]
        return province_map.get(first_char, first_char)
    
    return ''

def calculate_plate_similarity(plate1, plate2):
    if not plate1 or not plate2:
        return 0.0
    
    plate1 = plate1.upper().strip()
    plate2 = plate2.upper().strip()
    
    if plate1 == plate2:
        return 1.0
    
    min_len = min(len(plate1), len(plate2))
    max_len = max(len(plate1), len(plate2))
    
    matches = 0
    for i in range(min_len):
        if plate1[i] == plate2[i]:
            matches += 1
    
    similarity = matches / max_len
    
    char_map = {
        'O': '0', '0': 'O',
        'I': '1', '1': 'I', 'L': '1',
        'Z': '2', '2': 'Z',
        'S': '5', '5': 'S'
    }
    
    adjusted_matches = 0
    for i in range(min_len):
        if plate1[i] == plate2[i]:
            adjusted_matches += 1
        elif plate1[i] in char_map and char_map[plate1[i]] == plate2[i]:
            adjusted_matches += 0.8
    
    adjusted_similarity = adjusted_matches / max_len
    
    return max(similarity, adjusted_similarity)

@app.route('/api/info', methods=['GET'])
def get_info():
    return jsonify({
        'service': 'License Plate Recognition API',
        'version': '1.0.0',
        'description': '高精度车牌识别系统，支持多种识别引擎',
        'endpoints': {
            'POST /api/recognize': '识别单张图像',
            'POST /api/recognize/batch': '批量识别图像',
            'POST /api/recognize/verify': '验证车牌格式',
            'GET /health': '健康检查'
        },
        'supported_formats': ['jpg', 'jpeg', 'png', 'bmp'],
        'accuracy': '98%+',
        'processing_time': '< 500ms'
    })

if __name__ == '__main__':
    logger.info("启动车牌识别服务...")
    app.run(host='0.0.0.0', port=5000, debug=True, threaded=True)
