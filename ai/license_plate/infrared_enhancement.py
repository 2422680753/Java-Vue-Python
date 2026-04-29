#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
红外辅助图像增强模块
支持复杂天气（雨、雾、夜间）下的车牌识别增强
"""

import cv2
import numpy as np
from typing import Tuple, Optional, Dict, List
from dataclasses import dataclass
from enum import Enum
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class WeatherCondition(Enum):
    CLEAR = "clear"
    RAIN = "rain"
    FOG = "fog"
    SNOW = "snow"
    NIGHT = "night"
    RAIN_NIGHT = "rain_night"
    FOG_NIGHT = "fog_night"


@dataclass
class WeatherAnalysis:
    condition: WeatherCondition
    confidence: float
    visibility_score: float
    brightness_score: float
    contrast_score: float
    details: Dict[str, float]


@dataclass
class FusionResult:
    enhanced_image: np.ndarray
    fusion_method: str
    quality_score: float
    weather_condition: WeatherCondition
    enhancement_params: Dict[str, float]


class InfraredEnhancer:
    """红外图像增强器"""
    
    def __init__(self):
        self.kernel_size = 5
        self.clip_limit = 2.0
        self.tile_grid_size = (8, 8)
    
    def enhance_infrared(self, infrared_image: np.ndarray) -> np.ndarray:
        if len(infrared_image.shape) == 3:
            gray = cv2.cvtColor(infrared_image, cv2.COLOR_BGR2GRAY)
        else:
            gray = infrared_image.copy()
        
        clahe = cv2.createCLAHE(
            clipLimit=self.clip_limit,
            tileGridSize=self.tile_grid_size
        )
        enhanced = clahe.apply(gray)
        
        enhanced = self._adaptive_contrast_enhancement(enhanced)
        
        enhanced = self._sharpen_infrared(enhanced)
        
        return enhanced
    
    def _adaptive_contrast_enhancement(self, image: np.ndarray) -> np.ndarray:
        lab = cv2.cvtColor(cv2.cvtColor(image, cv2.COLOR_GRAY2BGR), cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        
        mean_l = np.mean(l)
        std_l = np.std(l)
        
        if mean_l < 80:
            l = cv2.add(l, 50)
        elif mean_l > 170:
            l = cv2.subtract(l, 30)
        
        if std_l < 40:
            clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(4, 4))
            l = clahe.apply(l)
        
        lab = cv2.merge((l, a, b))
        enhanced_bgr = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
        
        return cv2.cvtColor(enhanced_bgr, cv2.COLOR_BGR2GRAY)
    
    def _sharpen_infrared(self, image: np.ndarray) -> np.ndarray:
        kernel = np.array([
            [-1, -1, -1],
            [-1, 9, -1],
            [-1, -1, -1]
        ])
        
        sharpened = cv2.filter2D(image, -1, kernel)
        
        return cv2.addWeighted(image, 0.7, sharpened, 0.3, 0)


class VisibleInfraredFusion:
    """可见光-红外图像融合器"""
    
    def __init__(self):
        self.infrared_enhancer = InfraredEnhancer()
    
    def fuse_images(self, visible_image: np.ndarray, 
                    infrared_image: Optional[np.ndarray] = None,
                    weather_condition: Optional[WeatherCondition] = None) -> FusionResult:
        
        if infrared_image is None:
            enhanced = self._enhance_single_image(visible_image, weather_condition)
            return FusionResult(
                enhanced_image=enhanced,
                fusion_method="single_enhancement",
                quality_score=self._calculate_quality_score(enhanced),
                weather_condition=weather_condition or WeatherCondition.CLEAR,
                enhancement_params={"method": "adaptive"}
            )
        
        enhanced_visible = self._enhance_visible(visible_image, weather_condition)
        enhanced_infrared = self.infrared_enhancer.enhance_infrared(infrared_image)
        
        if len(enhanced_visible.shape) == 3:
            enhanced_visible_gray = cv2.cvtColor(enhanced_visible, cv2.COLOR_BGR2GRAY)
        else:
            enhanced_visible_gray = enhanced_visible
        
        if len(enhanced_infrared.shape) == 3:
            enhanced_infrared_gray = cv2.cvtColor(enhanced_infrared, cv2.COLOR_BGR2GRAY)
        else:
            enhanced_infrared_gray = enhanced_infrared
        
        if enhanced_infrared_gray.shape != enhanced_visible_gray.shape:
            enhanced_infrared_gray = cv2.resize(
                enhanced_infrared_gray, 
                (enhanced_visible_gray.shape[1], enhanced_visible_gray.shape[0])
            )
        
        fused_gray = self._multi_scale_fusion(enhanced_visible_gray, enhanced_infrared_gray, weather_condition)
        
        if len(enhanced_visible.shape) == 3:
            fused = self._colorize_fused_image(enhanced_visible, fused_gray)
        else:
            fused = fused_gray
        
        quality_score = self._calculate_quality_score(fused)
        
        return FusionResult(
            enhanced_image=fused,
            fusion_method="multi_scale_infrared_visible",
            quality_score=quality_score,
            weather_condition=weather_condition or WeatherCondition.CLEAR,
            enhancement_params={
                "visible_weight": 0.6,
                "infrared_weight": 0.4,
                "pyramid_levels": 4
            }
        )
    
    def _enhance_single_image(self, image: np.ndarray, 
                               weather_condition: Optional[WeatherCondition]) -> np.ndarray:
        
        if weather_condition in [WeatherCondition.NIGHT, WeatherCondition.RAIN_NIGHT, 
                                  WeatherCondition.FOG_NIGHT]:
            return self._night_enhancement(image)
        elif weather_condition in [WeatherCondition.FOG, WeatherCondition.SNOW]:
            return self._defog_enhancement(image)
        elif weather_condition == WeatherCondition.RAIN:
            return self._rain_removal_enhancement(image)
        else:
            return self._standard_enhancement(image)
    
    def _enhance_visible(self, image: np.ndarray, 
                         weather_condition: Optional[WeatherCondition]) -> np.ndarray:
        
        enhanced = image.copy()
        
        if len(enhanced.shape) == 2:
            enhanced = cv2.cvtColor(enhanced, cv2.COLOR_GRAY2BGR)
        
        if weather_condition in [WeatherCondition.FOG, WeatherCondition.SNOW, WeatherCondition.FOG_NIGHT]:
            enhanced = self._defog_enhancement(enhanced)
        
        if weather_condition in [WeatherCondition.RAIN, WeatherCondition.RAIN_NIGHT]:
            enhanced = self._rain_removal_enhancement(enhanced)
        
        if weather_condition in [WeatherCondition.NIGHT, WeatherCondition.RAIN_NIGHT, 
                                  WeatherCondition.FOG_NIGHT]:
            enhanced = self._night_enhancement(enhanced)
        
        return enhanced
    
    def _multi_scale_fusion(self, visible_gray: np.ndarray, 
                            infrared_gray: np.ndarray,
                            weather_condition: Optional[WeatherCondition]) -> np.ndarray:
        
        visible_weight = 0.6
        infrared_weight = 0.4
        
        if weather_condition in [WeatherCondition.NIGHT, WeatherCondition.RAIN_NIGHT, 
                                  WeatherCondition.FOG_NIGHT]:
            infrared_weight = 0.6
            visible_weight = 0.4
        elif weather_condition in [WeatherCondition.FOG, WeatherCondition.SNOW]:
            infrared_weight = 0.5
            visible_weight = 0.5
        
        pyramid_levels = 4
        
        visible_pyramid = self._build_gaussian_pyramid(visible_gray, pyramid_levels)
        infrared_pyramid = self._build_gaussian_pyramid(infrared_gray, pyramid_levels)
        
        laplacian_visible = self._build_laplacian_pyramid(visible_pyramid)
        laplacian_infrared = self._build_laplacian_pyramid(infrared_pyramid)
        
        fused_pyramid = []
        for i in range(len(laplacian_visible)):
            if i < 2:
                weight_v = visible_weight * 0.7
                weight_i = infrared_weight * 0.7
            else:
                weight_v = visible_weight
                weight_i = infrared_weight
            
            fused_level = cv2.addWeighted(
                laplacian_visible[i], weight_v,
                laplacian_infrared[i], weight_i,
                0
            )
            fused_pyramid.append(fused_level)
        
        fused = self._reconstruct_from_laplacian(fused_pyramid)
        
        return np.clip(fused, 0, 255).astype(np.uint8)
    
    def _build_gaussian_pyramid(self, image: np.ndarray, levels: int) -> List[np.ndarray]:
        pyramid = [image.astype(np.float32)]
        for _ in range(levels):
            image = cv2.pyrDown(image)
            pyramid.append(image.astype(np.float32))
        return pyramid
    
    def _build_laplacian_pyramid(self, gaussian_pyramid: List[np.ndarray]) -> List[np.ndarray]:
        laplacian = []
        for i in range(len(gaussian_pyramid) - 1):
            size = (gaussian_pyramid[i].shape[1], gaussian_pyramid[i].shape[0])
            gaussian_expanded = cv2.pyrUp(gaussian_pyramid[i + 1], dstsize=size)
            laplacian_level = cv2.subtract(gaussian_pyramid[i], gaussian_expanded)
            laplacian.append(laplacian_level)
        laplacian.append(gaussian_pyramid[-1])
        return laplacian
    
    def _reconstruct_from_laplacian(self, laplacian_pyramid: List[np.ndarray]) -> np.ndarray:
        reconstructed = laplacian_pyramid[-1]
        for i in range(len(laplacian_pyramid) - 2, -1, -1):
            size = (laplacian_pyramid[i].shape[1], laplacian_pyramid[i].shape[0])
            reconstructed = cv2.pyrUp(reconstructed, dstsize=size)
            reconstructed = cv2.add(reconstructed, laplacian_pyramid[i])
        return reconstructed
    
    def _defog_enhancement(self, image: np.ndarray) -> np.ndarray:
        if len(image.shape) == 2:
            image = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
        
        dark_channel = self._get_dark_channel(image)
        
        atmospheric_light = self._estimate_atmospheric_light(image, dark_channel)
        
        transmission = self._estimate_transmission(image, atmospheric_light)
        
        transmission = cv2.max(transmission, 0.1)
        
        scene_radiance = np.empty(image.shape, image.dtype)
        for ind in range(3):
            scene_radiance[:, :, ind] = (
                (image[:, :, ind] - atmospheric_light[ind]) / transmission + 
                atmospheric_light[ind]
            )
        
        scene_radiance = np.clip(scene_radiance, 0, 255).astype(np.uint8)
        
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        lab = cv2.cvtColor(scene_radiance, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        l = clahe.apply(l)
        lab = cv2.merge((l, a, b))
        enhanced = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
        
        return enhanced
    
    def _get_dark_channel(self, image: np.ndarray, patch_size: int = 15) -> np.ndarray:
        min_channel = np.min(image, axis=2)
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (patch_size, patch_size))
        dark_channel = cv2.erode(min_channel, kernel)
        return dark_channel
    
    def _estimate_atmospheric_light(self, image: np.ndarray, 
                                      dark_channel: np.ndarray,
                                      top_percent: float = 0.001) -> np.ndarray:
        flat_dark = dark_channel.flatten()
        flat_image = image.reshape(-1, 3)
        
        num_pixels = dark_channel.size
        num_brightest = int(max(num_pixels * top_percent, 1))
        
        indices = np.argpartition(flat_dark, -num_brightest)[-num_brightest:]
        
        atmospheric_light = np.max(flat_image[indices], axis=0)
        
        return atmospheric_light
    
    def _estimate_transmission(self, image: np.ndarray, 
                                 atmospheric_light: np.ndarray,
                                 omega: float = 0.95,
                                 patch_size: int = 15) -> np.ndarray:
        normalized_image = np.empty(image.shape, dtype=np.float32)
        for ind in range(3):
            normalized_image[:, :, ind] = image[:, :, ind] / atmospheric_light[ind]
        
        dark_channel = self._get_dark_channel((normalized_image * 255).astype(np.uint8), patch_size)
        
        transmission = 1 - omega * (dark_channel.astype(np.float32) / 255)
        
        return transmission
    
    def _night_enhancement(self, image: np.ndarray) -> np.ndarray:
        if len(image.shape) == 2:
            image = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
        
        lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        
        mean_l = np.mean(l)
        
        if mean_l < 50:
            l = cv2.convertScaleAbs(l, alpha=2.5, beta=30)
        elif mean_l < 80:
            l = cv2.convertScaleAbs(l, alpha=2.0, beta=20)
        else:
            l = cv2.convertScaleAbs(l, alpha=1.5, beta=10)
        
        clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(4, 4))
        l = clahe.apply(l)
        
        lab = cv2.merge((l, a, b))
        enhanced = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
        
        denoised = cv2.bilateralFilter(enhanced, 5, 50, 50)
        
        return denoised
    
    def _rain_removal_enhancement(self, image: np.ndarray) -> np.ndarray:
        if len(image.shape) == 2:
            image = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
        
        denoised = cv2.medianBlur(image, 3)
        
        lab = cv2.cvtColor(denoised, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        
        clahe = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8))
        l = clahe.apply(l)
        
        lab = cv2.merge((l, a, b))
        enhanced = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
        
        return enhanced
    
    def _standard_enhancement(self, image: np.ndarray) -> np.ndarray:
        if len(image.shape) == 2:
            image = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
        
        lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        l = clahe.apply(l)
        
        lab = cv2.merge((l, a, b))
        enhanced = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
        
        return enhanced
    
    def _colorize_fused_image(self, color_image: np.ndarray, 
                                gray_fused: np.ndarray) -> np.ndarray:
        if len(color_image.shape) != 3:
            return cv2.cvtColor(gray_fused, cv2.COLOR_GRAY2BGR)
        
        lab_color = cv2.cvtColor(color_image, cv2.COLOR_BGR2LAB)
        l_color, a, b = cv2.split(lab_color)
        
        if gray_fused.shape != l_color.shape:
            gray_fused = cv2.resize(gray_fused, (l_color.shape[1], l_color.shape[0]))
        
        lab_fused = cv2.merge((gray_fused, a, b))
        colorized = cv2.cvtColor(lab_fused, cv2.COLOR_LAB2BGR)
        
        return colorized
    
    def _calculate_quality_score(self, image: np.ndarray) -> float:
        if len(image.shape) == 3:
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        else:
            gray = image
        
        brightness = np.mean(gray) / 255.0
        
        contrast = np.std(gray) / 128.0
        
        edges = cv2.Sobel(gray, cv2.CV_64F, 1, 1, ksize=3)
        edge_strength = np.mean(np.abs(edges)) / 100.0
        
        quality_score = (
            brightness * 0.2 +
            min(contrast, 1.0) * 0.4 +
            min(edge_strength, 1.0) * 0.4
        )
        
        return min(1.0, max(0.0, quality_score))


class WeatherDetector:
    """天气条件检测器"""
    
    def __init__(self):
        self.thresholds = {
            'night_brightness': 60,
            'fog_contrast': 40,
            'rain_noise': 20
        }
    
    def detect_weather(self, image: np.ndarray) -> WeatherAnalysis:
        if len(image.shape) == 3:
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        else:
            gray = image
        
        brightness_score = np.mean(gray) / 255.0
        contrast_score = np.std(gray) / 128.0
        
        edge_score = self._calculate_edge_density(gray)
        noise_score = self._estimate_noise_level(gray)
        
        is_night = brightness_score < 0.25
        is_foggy = contrast_score < 0.3 and edge_score < 0.3
        is_rainy = noise_score > 0.3 and edge_score > 0.2
        
        if is_night:
            if is_foggy:
                condition = WeatherCondition.FOG_NIGHT
            elif is_rainy:
                condition = WeatherCondition.RAIN_NIGHT
            else:
                condition = WeatherCondition.NIGHT
        elif is_foggy:
            condition = WeatherCondition.FOG
        elif is_rainy:
            condition = WeatherCondition.RAIN
        else:
            condition = WeatherCondition.CLEAR
        
        visibility_score = self._calculate_visibility(condition, contrast_score, edge_score)
        
        confidence = self._calculate_detection_confidence(
            condition, brightness_score, contrast_score, edge_score, noise_score
        )
        
        return WeatherAnalysis(
            condition=condition,
            confidence=confidence,
            visibility_score=visibility_score,
            brightness_score=brightness_score,
            contrast_score=contrast_score,
            details={
                'edge_score': edge_score,
                'noise_score': noise_score,
                'is_night': is_night,
                'is_foggy': is_foggy,
                'is_rainy': is_rainy
            }
        )
    
    def _calculate_edge_density(self, gray: np.ndarray) -> float:
        edges = cv2.Canny(gray, 50, 150)
        edge_ratio = np.sum(edges > 0) / edges.size
        return min(1.0, edge_ratio * 10)
    
    def _estimate_noise_level(self, gray: np.ndarray) -> float:
        blurred = cv2.GaussianBlur(gray, (5, 5), 0)
        noise = cv2.absdiff(gray, blurred)
        noise_level = np.mean(noise) / 50.0
        return min(1.0, noise_level)
    
    def _calculate_visibility(self, condition: WeatherCondition, 
                               contrast_score: float,
                               edge_score: float) -> float:
        base_visibility = contrast_score * 0.6 + edge_score * 0.4
        
        if condition in [WeatherCondition.FOG, WeatherCondition.FOG_NIGHT]:
            base_visibility *= 0.5
        elif condition in [WeatherCondition.RAIN, WeatherCondition.RAIN_NIGHT]:
            base_visibility *= 0.7
        elif condition in [WeatherCondition.NIGHT]:
            base_visibility *= 0.85
        
        return min(1.0, max(0.0, base_visibility))
    
    def _calculate_detection_confidence(self, condition: WeatherCondition,
                                          brightness_score: float,
                                          contrast_score: float,
                                          edge_score: float,
                                          noise_score: float) -> float:
        confidence = 0.5
        
        if condition == WeatherCondition.CLEAR:
            if brightness_score > 0.3 and contrast_score > 0.4:
                confidence = 0.9
            else:
                confidence = 0.7
        elif condition in [WeatherCondition.NIGHT, WeatherCondition.RAIN_NIGHT, WeatherCondition.FOG_NIGHT]:
            if brightness_score < 0.3:
                confidence = 0.85
            else:
                confidence = 0.6
        elif condition in [WeatherCondition.FOG, WeatherCondition.FOG_NIGHT]:
            if contrast_score < 0.35:
                confidence = 0.8
            else:
                confidence = 0.6
        elif condition in [WeatherCondition.RAIN, WeatherCondition.RAIN_NIGHT]:
            if noise_score > 0.25:
                confidence = 0.75
            else:
                confidence = 0.55
        
        return min(0.95, max(0.5, confidence))


def enhance_for_complex_weather(visible_image: np.ndarray,
                                 infrared_image: Optional[np.ndarray] = None) -> FusionResult:
    """
    复杂天气下的图像增强主函数
    
    Args:
        visible_image: 可见光图像
        infrared_image: 红外图像（可选）
    
    Returns:
        FusionResult: 融合结果
    """
    weather_detector = WeatherDetector()
    weather_analysis = weather_detector.detect_weather(visible_image)
    
    logger.info(f"检测到天气条件: {weather_analysis.condition.value}, "
                f"置信度: {weather_analysis.confidence:.2%}, "
                f"可见度: {weather_analysis.visibility_score:.2%}")
    
    fusion = VisibleInfraredFusion()
    
    result = fusion.fuse_images(
        visible_image,
        infrared_image,
        weather_analysis.condition
    )
    
    result.weather_condition = weather_analysis.condition
    
    return result


if __name__ == "__main__":
    import sys
    
    if len(sys.argv) > 1:
        image_path = sys.argv[1]
        image = cv2.imread(image_path)
        
        if image is None:
            print(f"无法读取图像: {image_path}")
            sys.exit(1)
        
        result = enhance_for_complex_weather(image)
        
        print(f"天气条件: {result.weather_condition.value}")
        print(f"融合方法: {result.fusion_method}")
        print(f"质量评分: {result.quality_score:.2%}")
        
        output_path = "enhanced_" + sys.argv[1]
        cv2.imwrite(output_path, result.enhanced_image)
        print(f"增强图像已保存到: {output_path}")
    else:
        print("用法: python infrared_enhancement.py <图像路径>")
