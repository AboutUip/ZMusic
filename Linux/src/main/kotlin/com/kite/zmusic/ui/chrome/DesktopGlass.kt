package com.kite.zmusic.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.kite.zmusic.data.GlassStyle
import com.kite.zmusic.ui.theme.MainPalette
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * 桌面液体玻璃：Skia RuntimeEffect（折射率 / 模糊）画在面板上，不糊文字。
 * 这是 Linux 终局玻璃引擎，设置项直接绑定这两档参数。
 */
private const val GLASS_SKSL = """
uniform float2 uResolution;
uniform float uRefraction;
uniform float uBlur;
uniform float uDark;
half4 main(float2 fragCoord) {
  float2 uv = fragCoord / uResolution;
  float ripple = sin((uv.x * 17.0 + uv.y * 11.0) * (8.0 + uRefraction * 36.0))
    * 0.045 * uRefraction;
  float frost = mix(0.10, 0.28, clamp(uBlur, 0.0, 1.0));
  float3 light = float3(0.97, 0.98, 1.00);
  float3 dark = float3(0.16, 0.16, 0.18);
  float3 base = mix(light, dark, uDark);
  float highlight = smoothstep(0.0, 0.35, 1.0 - uv.y) * 0.18 * (0.4 + uRefraction);
  return half4(half3(base + highlight + ripple), half(frost + 0.08));
}
"""

@Composable
fun Modifier.liquidGlass(shape: Shape, style: GlassStyle): Modifier {
    val dark = if (MainPalette.isDark) 1f else 0f
    val effect = remember {
        runCatching { RuntimeEffect.makeForShader(GLASS_SKSL) }.getOrNull()
    }
    val fill = MainPalette.snapshot.glassFill(0.18f + style.refraction * 0.28f + style.blur * 0.08f)
    return this
        .clip(shape)
        .then(
            if (effect != null) {
                Modifier.drawBehind {
                    val builder = RuntimeShaderBuilder(effect)
                    builder.uniform("uResolution", size.width, size.height)
                    builder.uniform("uRefraction", style.refraction)
                    builder.uniform("uBlur", style.blur)
                    builder.uniform("uDark", dark)
                    val paint = Paint()
                    paint.shader = builder.makeShader()
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(
                            Rect.makeWH(size.width, size.height),
                            paint,
                        )
                    }
                    paint.close()
                }
            } else {
                Modifier.background(
                    Brush.linearGradient(
                        colors = listOf(
                            fill,
                            fill.copy(alpha = (fill.alpha + style.blur * 0.08f).coerceIn(0.06f, 0.32f)),
                        ),
                    ),
                )
            },
        )
        .border(0.5.dp, MainPalette.Hairline, shape)
}

@Composable
fun Modifier.itemChrome(shape: Shape): Modifier =
    clip(shape).background(MainPalette.Card).border(0.5.dp, MainPalette.Hairline, shape)
