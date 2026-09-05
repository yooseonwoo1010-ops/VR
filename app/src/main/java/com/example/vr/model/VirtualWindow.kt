package com.example.vr.model

import kotlin.math.cos
import kotlin.math.sin

/**
 * VirtualWindow
 * 
 * 역할:
 * - 3D 월드 공간에 단단히 고정된 가상 창 데이터 모델
 * - HeadTracker를 직접 참조하지 않음 (완전 분리)
 * - 한 번 월드에 배치된 후에는 사용자가 머리를 움직여도 월드 좌표 및 회전이 변하지 않음
 */
data class VirtualWindow(
    // 3D 월드 변환 (기본값: 정면 2.0m)
    var position: Vector3 = Vector3(0f, 0f, 2.0f),
    var rotationY: Float = 0f, // radians (Y축 기준 회전)
    var rotationX: Float = 0f, // radians (X축 기준 회전)
    
    // 창 크기 (기본 요구 사양: 1.2m x 0.7m, 거리 2.0m)
    var width: Float = 1.2f,
    var height: Float = 0.7f,
    var distance: Float = 2.0f,
    
    // 텍스트 및 콘텐츠
    var title: String = "VR TEST WINDOW",
    var subtitle: String = "Head Tracking Test",
    var textContent: String = "이 창은 월드 공간에 고정되어 있으며 머리를 움직여도 사용자를 따라오지 않습니다.",
    var subContent: String = "• 고개를 돌려 시야에서 벗어나도 창의 월드 위치는 그대로 유지됩니다.\n• [RECENTER] 버튼을 누르면 현재 시선 정면에 창을 다시 정렬합니다.",
    
    // 상태
    var isVisible: Boolean = true,
    var hoveredButtonId: String? = null,
    var gazeDwellProgress: Float = 0f,
    var isPassthrough: Boolean = true
) {
    val rightVector: Vector3
        get() {
            val cosY = cos(rotationY.toDouble()).toFloat()
            val sinY = sin(rotationY.toDouble()).toFloat()
            return Vector3(cosY, 0f, -sinY)
        }

    val upVector: Vector3
        get() = Vector3(0f, 1f, 0f)

    val normalVector: Vector3
        get() {
            val cosY = cos(rotationY.toDouble()).toFloat()
            val sinY = sin(rotationY.toDouble()).toFloat()
            return Vector3(sinY, 0f, cosY)
        }

    /**
     * 창 평면 표면 상의 정규화 좌표 (u in 0..1, v in 0..1)를 3D 월드 공간 좌표로 변환
     * u = 0 (좌측) .. 1 (우측)
     * v = 0 (상단) .. 1 (하단)
     */
    fun get3DPoint(u: Float, v: Float): Vector3 {
        val r = rightVector
        val uVec = upVector
        return position + r * ((u - 0.5f) * width) + uVec * ((0.5f - v) * height)
    }

    /**
     * 3D 월드 공간에서의 4개 꼭짓점(TL, TR, BR, BL) 반환
     */
    fun getCorners(): List<Vector3> {
        return listOf(
            get3DPoint(0f, 0f), // Top-Left
            get3DPoint(1f, 0f), // Top-Right
            get3DPoint(1f, 1f), // Bottom-Right
            get3DPoint(0f, 1f)  // Bottom-Left
        )
    }
}
