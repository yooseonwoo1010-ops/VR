package com.example.vr.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.vr.handtracking.HandTrackingManager

@Composable
fun CameraPassthroughView(
    handManager: HandTrackingManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(Unit) {
        handManager.setSurfaceProvider(previewView.surfaceProvider, lifecycleOwner)
        onDispose {
            handManager.setSurfaceProvider(null, lifecycleOwner)
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

