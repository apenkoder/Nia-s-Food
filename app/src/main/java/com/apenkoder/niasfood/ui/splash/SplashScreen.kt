package com.apenkoder.niasfood.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.apenkoder.niasfood.R
import kotlinx.coroutines.delay

/** Apenkoder Software splash: 600 ms fade in, 1.8 s hold, 600 ms fade out. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
  val alpha = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    alpha.animateTo(1f, animationSpec = tween(durationMillis = 600))
    delay(1800)
    alpha.animateTo(0f, animationSpec = tween(durationMillis = 600))
    onFinished()
  }

  Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A1628)) {
    Image(
      painter = painterResource(id = R.drawable.apenkoder_splasher),
      contentDescription = "Apenkoder Software",
      contentScale = ContentScale.Crop,
      modifier =
        Modifier
          .fillMaxSize()
          .alpha(alpha.value),
    )
  }
}
