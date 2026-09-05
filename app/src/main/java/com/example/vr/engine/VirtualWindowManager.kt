package com.example.vr.engine

import com.example.vr.model.Vector3
import com.example.vr.model.VirtualWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2

/**
 * VirtualWindowManager
 * 역할:
 * - 3D 가상 창의 생성 및 월드 공간 앵커(배치) 관리
 * - 앱 시작 시 또는 사용자가 명시적으로 'RECENTER' 버튼을 누를 때만 창을 배치함
 * - 절대로 센서 리스너나 매 프레임 업데이트 루프에서 창 위치/회전을 덮어쓰지 않음
 * - HeadTracker를 직접 참조하지 않고, 순수하게 명령 전달을 통해 독립성 유지
 */
class VirtualWindowManager {

    private val _window = MutableStateFlow(VirtualWindow())
    val window: StateFlow<VirtualWindow> = _window.asStateFlow()

    init {
        // 기본 위치: 앱 시작 시 3D 월드 공간의 정면 (0, 0, 2.0m)
        _window.value = VirtualWindow(
            position = Vector3(0f, 0f, 2.0f),
            rotationY = 0f,
            rotationX = 0f,
            width = 1.2f,
            height = 0.7f,
            distance = 2.0f,
            isVisible = true
        )
    }

    /**
     * 월드 공간에 창을 단 한 번 배치합니다.
     * (앱 초기화 시 또는 사용자가 시점 재정렬을 명시적으로 요청했을 때만 호출)
     */
    fun placeVirtualWindowOnce(cameraPos: Vector3, forwardDir: Vector3, distance: Float = 2.0f) {
        val hForward = Vector3(forwardDir.x, 0f, forwardDir.z).normalized()
        val finalDir = if (hForward.length() < 0.05f) Vector3(0f, 0f, 1f) else hForward

        val newPos = cameraPos + finalDir * distance
        
        // 창이 카메라를 정면으로 마주보도록 Y축 회전값 설정
        val yaw = atan2(finalDir.x.toDouble(), finalDir.z.toDouble()).toFloat()

        _window.value = _window.value.copy(
            position = newPos,
            rotationY = yaw,
            rotationX = 0f,
            distance = distance,
            isVisible = true,
            hoveredButtonId = null,
            gazeDwellProgress = 0f
        )
    }

    fun setHoverState(hoveredButtonId: String?, dwellProgress: Float) {
        val curr = _window.value
        if (curr.hoveredButtonId != hoveredButtonId || curr.gazeDwellProgress != dwellProgress) {
            _window.value = curr.copy(
                hoveredButtonId = hoveredButtonId,
                gazeDwellProgress = dwellProgress
            )
        }
    }

    fun closeWindow() {
        _window.value = _window.value.copy(isVisible = false)
    }

    fun openWindow() {
        _window.value = _window.value.copy(isVisible = true)
    }

    fun toggleVisibility() {
        _window.value = _window.value.copy(isVisible = !_window.value.isVisible)
    }

    fun setPassthrough(active: Boolean) {
        _window.value = _window.value.copy(isPassthrough = active)
    }
}
