package com.example.vr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vr.model.HandGesture

@Composable
fun HandGestureSimulatorPanel(
    onUpdateHand: (isRight: Boolean, normX: Float, normY: Float, gesture: HandGesture, isPinching: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var rightPosX by remember { mutableFloatStateOf(0.4f) }
    var rightPosY by remember { mutableFloatStateOf(0.0f) }
    var rightPinch by remember { mutableStateOf(false) }
    var rightGesture by remember { mutableStateOf(HandGesture.OPEN_PALM) }

    var leftPosX by remember { mutableFloatStateOf(-0.4f) }
    var leftPosY by remember { mutableFloatStateOf(0.0f) }
    var leftPinch by remember { mutableStateOf(false) }
    var leftGesture by remember { mutableStateOf(HandGesture.OPEN_PALM) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hand_gesture_simulator_panel")
    ) {
        // Left Virtual Hand Touch Pad
        VirtualHandTouchPad(
            isRight = false,
            posX = leftPosX,
            posY = leftPosY,
            isPinching = leftPinch,
            activeGesture = leftGesture,
            onPositionChange = { x, y ->
                leftPosX = x
                leftPosY = y
                onUpdateHand(false, x, y, leftGesture, leftPinch)
            },
            onPinchChange = { pinching ->
                leftPinch = pinching
                leftGesture = if (pinching) HandGesture.PINCH else HandGesture.OPEN_PALM
                onUpdateHand(false, leftPosX, leftPosY, leftGesture, pinching)
            },
            onGestureSelect = { g ->
                leftGesture = g
                onUpdateHand(false, leftPosX, leftPosY, g, leftPinch)
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        )

        // Right Virtual Hand Touch Pad
        VirtualHandTouchPad(
            isRight = true,
            posX = rightPosX,
            posY = rightPosY,
            isPinching = rightPinch,
            activeGesture = rightGesture,
            onPositionChange = { x, y ->
                rightPosX = x
                rightPosY = y
                onUpdateHand(true, x, y, rightGesture, rightPinch)
            },
            onPinchChange = { pinching ->
                rightPinch = pinching
                rightGesture = if (pinching) HandGesture.PINCH else HandGesture.OPEN_PALM
                onUpdateHand(true, rightPosX, rightPosY, rightGesture, pinching)
            },
            onGestureSelect = { g ->
                rightGesture = g
                onUpdateHand(true, rightPosX, rightPosY, g, rightPinch)
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        )
    }
}

@Composable
private fun VirtualHandTouchPad(
    isRight: Boolean,
    posX: Float,
    posY: Float,
    isPinching: Boolean,
    activeGesture: HandGesture,
    onPositionChange: (Float, Float) -> Unit,
    onPinchChange: (Boolean) -> Unit,
    onGestureSelect: (HandGesture) -> Unit,
    modifier: Modifier = Modifier
) {
    val handColor = if (isRight) Color(0xFF00E5FF) else Color(0xFFFF0077)
    val handName = if (isRight) "오른손 (R)" else "왼손 (L)"

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC0F172A),
        modifier = modifier
            .border(1.dp, handColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .width(130.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = handName,
                color = handColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 2D Virtual Hand Joystick Pad
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(1.dp, handColor.copy(alpha = 0.4f), CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newX = (posX + dragAmount.x / 50f).coerceIn(-1f, 1f)
                            val newY = (posY - dragAmount.y / 50f).coerceIn(-1f, 1f)
                            onPositionChange(newX, newY)
                        }
                    }
                    .testTag(if (isRight) "right_hand_joystick" else "left_hand_joystick"),
                contentAlignment = Alignment.Center
            ) {
                // Hand Position Indicator
                Box(
                    modifier = Modifier
                        .offset(
                            x = (posX * 35).dp,
                            y = (-posY * 35).dp
                        )
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isPinching) Color.White else handColor)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPinching) "🤏" else "✋",
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Pinch Trigger Button (Click / Grab)
            Button(
                onClick = { onPinchChange(!isPinching) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPinching) Color.White else handColor
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .testTag(if (isRight) "right_pinch_button" else "left_pinch_button")
            ) {
                Text(
                    text = if (isPinching) "🤏 핀치 해제" else "🤏 핀치 클릭",
                    color = if (isPinching) Color.Black else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}
