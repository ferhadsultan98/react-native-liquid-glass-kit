package com.liquidglasskit

// Ported from @uginy/react-native-liquid-glass (MIT license)
// android/src/main/java/com/liquidglass/LiquidGlassView.kt — same AGSL shader
// and shared-backdrop-capture strategy, rehosted on ReactViewGroup instead of
// Expo's ExpoView since this project has no Expo runtime.
//
// ReactViewGroup, not FrameLayout: FrameLayout.onLayout re-positions children
// to fill the parent, which overwrites the bounds Fabric already computed and
// collapses every child to full-screen.
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.ImageView
import com.facebook.react.views.view.ReactViewGroup
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

class LiquidGlassView(context: Context) : ReactViewGroup(context) {

  private val density = resources.displayMetrics.density
  private var interactionEnabled = true

  var shaderBlurRadius: Float = 8f * density
    set(value) { field = value.coerceIn(0f, 100f * density); propsDirty = true; invalidate() }
  var refractionStrength: Float = 0.16f
    set(value) { field = value.coerceIn(0f, 1f); propsDirty = true; invalidate() }
  var chromaticAberration: Float = 0.04f
    set(value) { field = value.coerceIn(0f, 1f); propsDirty = true; invalidate() }
  var edgeGlowIntensity: Float = 0.0f
    set(value) { field = value.coerceIn(0f, 2f); propsDirty = true; invalidate() }
  var glassOpacity: Float = 0.12f
    set(value) { field = value.coerceIn(0f, 1f); propsDirty = true; invalidate() }
  var tintR: Float = 1.0f
  var tintG: Float = 1.0f
  var tintB: Float = 1.0f
  var fresnelPower: Float = 4.0f
    set(value) { field = value.coerceIn(0.5f, 16f); propsDirty = true; invalidate() }
  var cornerRadiusPx: Float = 32f * density
    set(value) { field = value.coerceAtLeast(0f); propsDirty = true; invalidate() }
  var glareIntensity: Float = 0.16f
    set(value) { field = value.coerceIn(0f, 2f); propsDirty = true; invalidate() }
  var borderIntensity: Float = 0.12f
    set(value) { field = value.coerceIn(0f, 1f); propsDirty = true; invalidate() }
  var plainBorder: Boolean = false
    set(value) { field = value; propsDirty = true; invalidate() }
  var edgeWidth: Float = 0.4f
    set(value) { field = value.coerceIn(0.1f, 4f); propsDirty = true; invalidate() }
  var refractionHeightFraction: Float = -1f
    set(value) { field = value.coerceIn(-1f, 0.5f); propsDirty = true; invalidate() }
  var depthEffect: Boolean = false
    set(value) { field = value; propsDirty = true; invalidate() }
  var liquidPower: Float = 1.5f
    set(value) { field = value.coerceIn(0.1f, 8f); propsDirty = true; invalidate() }
  var lightAngle: Float = 0.8f
    set(value) { field = value; propsDirty = true; invalidate() }
  var shaderSaturation: Float = 1.0f
    set(value) { field = value.coerceIn(0f, 3f); propsDirty = true; invalidate() }
  var brightness: Float = 1.0f
    set(value) { field = value.coerceIn(0f, 3f); propsDirty = true; invalidate() }
  var noiseIntensity: Float = 0.01f
    set(value) { field = value.coerceIn(0f, 0.25f); propsDirty = true; invalidate() }
  var iridescence: Float = 0.0f
    set(value) { field = value.coerceIn(0f, 1f); propsDirty = true; invalidate() }

  private var usesHueTint = false
  private var pressX = 0f
  private var pressY = 0f
  private var downRawX = 0f
  private var downRawY = 0f
  private var downLocalX = 0f
  private var downLocalY = 0f
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

  private class SpringValue(
    var value: Float = 0f,
    var velocity: Float = 0f,
    var target: Float = 0f,
  )

  private val pressSpring = SpringValue()
  private val dragXSpring = SpringValue()
  private val dragYSpring = SpringValue()
  private var interactionFrameNanos = 0L
  private var interactionAnimationPosted = false

  private val interactionAnimation = object : Runnable {
    override fun run() {
      interactionAnimationPosted = false
      if (!isAttachedToWindow) return

      val now = System.nanoTime()
      val dt =
        if (interactionFrameNanos == 0L) 1f / 60f
        else ((now - interactionFrameNanos) / 1_000_000_000f).coerceIn(1f / 120f, 1f / 30f)
      interactionFrameNanos = now

      val pressMoving = advanceSpring(pressSpring, dt, 0.001f)
      val xMoving = advanceSpring(dragXSpring, dt, 0.05f * density)
      val yMoving = advanceSpring(dragYSpring, dt, 0.05f * density)
      applyInteractiveTransform()
      invalidate()

      if (pressMoving || xMoving || yMoving) {
        postInteractionFrame()
      } else {
        interactionFrameNanos = 0L
      }
    }
  }

  fun setBlurRadius(radiusDp: Float) {
    shaderBlurRadius = finiteOr(radiusDp, 8f) * density
  }

  fun setCornerRadius(radiusDp: Float) {
    cornerRadiusPx = finiteOr(radiusDp, 32f) * density
  }

  fun setSaturation(value: Float) { shaderSaturation = finiteOr(value, 1f) }

  fun setInteractionEnabled(enabled: Boolean) {
    if (interactionEnabled == enabled) return
    interactionEnabled = enabled
    isClickable = enabled

    if (!enabled) {
      removeCallbacks(interactionAnimation)
      interactionAnimationPosted = false
      interactionFrameNanos = 0L
      pressSpring.value = 0f
      pressSpring.velocity = 0f
      pressSpring.target = 0f
      dragXSpring.value = 0f
      dragXSpring.velocity = 0f
      dragXSpring.target = 0f
      dragYSpring.value = 0f
      dragYSpring.velocity = 0f
      dragYSpring.target = 0f
      isPressed = false
      translationX = 0f
      translationY = 0f
      scaleX = 1f
      scaleY = 1f
      parent?.requestDisallowInterceptTouchEvent(false)
      invalidate()
    }
  }

  fun setTintColor(color: Int) {
    tintR = Color.red(color) / 255f
    tintG = Color.green(color) / 255f
    tintB = Color.blue(color) / 255f
    glassOpacity = Color.alpha(color) / 255f
    usesHueTint =
      glassOpacity > 0f &&
        (abs(tintR - tintG) + abs(tintG - tintB) + abs(tintB - tintR)) > 0.01f
    propsDirty = true
    invalidate()
  }

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
  private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
  private val clipPath = Path()
  private val clipRect = RectF()
  private var fallbackRenderNode: RenderNode? = null
  private var runtimeShader: RuntimeShader? = null
  private var localBitmapShader: BitmapShader? = null
  private var localBitmapVersion = -1
  private var shaderFailed = false

  private var offsetX = 0f
  private var offsetY = 0f
  private var propsDirty = true
  private var attached = false
  private var lastKnownBgWidth = 0
  private var lastKnownBgHeight = 0

  private var startupRetryCount = 0
  private var retryCapturePosted = false

  private val retryCaptureRunnable = Runnable {
    retryCapturePosted = false
    val bmp = sharedBgBitmap
    val bg = sharedBgView
    val sizeChanged =
      bg != null && (bmp == null || bmp.width != bg.width || bmp.height != bg.height)
    if (sizeChanged && isAttachedToWindow) {
      requestSharedCapture(force = true)
    } else if (bmp != null && !bmp.isRecycled && localBitmapShader == null && isAttachedToWindow) {
      buildLocalShader(bmp)
      post { syncOffset() }
    } else if (bmp == null && width > 0 && height > 0 && isAttachedToWindow) {
      requestSharedCapture()
    }
  }

  private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
    updateOffset()
    true
  }

  companion object {
    private var sharedBgBitmap: Bitmap? = null
    private var sharedBgView: View? = null
    private var sharedBgW = 0f
    private var sharedBgH = 0f
    private var isSharedCapturing = false
    private var sharedBitmapVersion = 0

    private const val MAX_STARTUP_RETRIES = 80
    private const val MIN_VALID_ALPHA = 10
    private var activeInstances = 0
    private val allInstances = mutableSetOf<LiquidGlassView>()
    private const val TAG = "RNLiquidGlassView"

    private fun finiteOr(value: Float, fallback: Float): Float =
      if (value.isFinite()) value else fallback

    private const val SHADER_SRC = """
            uniform shader backdrop;
            uniform float2 resolution;
            uniform float2 viewOffset;
            uniform float2 bgSize;
            uniform float blurRadius;
            uniform float refractionStrength;
            uniform float chromaticAberration;
            uniform float edgeGlow;
            uniform float glassOpacity;
            uniform float3 tintColor;
            uniform float fresnelPower;
            uniform float cornerRadius;
            uniform float glareIntensity;
            uniform float borderIntensity;
            uniform float plainBorder;
            uniform float edgeWidth;
            uniform float refractionHeightFraction;
            uniform float depthEffect;
            uniform float liquidPower;
            uniform float lightAngle;
            uniform float saturation;
            uniform float brightness;
            uniform float noiseIntensity;
            uniform float iridescence;
            uniform float density;
            uniform float useHueTint;
            uniform float pressProgress;
            uniform float2 pressPosition;

            float roundedBoxSDF(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + float2(r);
                return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
            }

            half4 sampleBg(float2 uv) {
                return backdrop.eval(clamp(uv + viewOffset, float2(0.0), bgSize));
            }

            half4 sampleBlurred(float2 uv, float radius) {
                if (radius < 0.5) return sampleBg(uv);
                float s = radius * 0.35;
                half4 c = sampleBg(uv) * half(0.20);
                c += sampleBg(uv + float2(-s, 0.0)) * half(0.10);
                c += sampleBg(uv + float2( s, 0.0)) * half(0.10);
                c += sampleBg(uv + float2(0.0,-s)) * half(0.10);
                c += sampleBg(uv + float2(0.0, s)) * half(0.10);
                c += sampleBg(uv + float2(-s,-s)) * half(0.06);
                c += sampleBg(uv + float2( s,-s)) * half(0.06);
                c += sampleBg(uv + float2(-s, s)) * half(0.06);
                c += sampleBg(uv + float2( s, s)) * half(0.06);
                c += sampleBg(uv + float2(-2.0*s, 0.0)) * half(0.04);
                c += sampleBg(uv + float2( 2.0*s, 0.0)) * half(0.04);
                c += sampleBg(uv + float2(0.0,-2.0*s)) * half(0.04);
                c += sampleBg(uv + float2(0.0, 2.0*s)) * half(0.04);
                return c;
            }

            float hash21(float2 p) {
                return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
            }

            half3 applySaturation(half3 color, float sat) {
                half luma = dot(color, half3(half(0.2126), half(0.7152), half(0.0722)));
                return mix(half3(luma), color, half(sat));
            }

            float blendLum(float3 color) {
                return dot(color, float3(0.30, 0.59, 0.11));
            }

            float blendSat(float3 color) {
                return max(max(color.r, color.g), color.b) - min(min(color.r, color.g), color.b);
            }

            float3 clipBlendColor(float3 color) {
                float l = blendLum(color);
                float n = min(min(color.r, color.g), color.b);
                float x = max(max(color.r, color.g), color.b);
                if (n < 0.0) color = float3(l) + (color - float3(l)) * l / max(l - n, 0.0001);
                if (x > 1.0) color = float3(l) + (color - float3(l)) * (1.0 - l) / max(x - l, 0.0001);
                return color;
            }

            float3 setBlendLum(float3 color, float l) {
                return clipBlendColor(color + float3(l - blendLum(color)));
            }

            float3 setBlendSat(float3 color, float s) {
                float n = min(min(color.r, color.g), color.b);
                float x = max(max(color.r, color.g), color.b);
                if (x <= n) return float3(0.0);
                return (color - float3(n)) * s / (x - n);
            }

            float3 hueBlend(float3 backdropColor, float3 sourceColor) {
                return setBlendLum(
                    setBlendSat(sourceColor, blendSat(backdropColor)),
                    blendLum(backdropColor)
                );
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord;
                float2 center = resolution * 0.5;
                float safeRadius = min(cornerRadius, min(resolution.x, resolution.y) * 0.5);
                float sdf = roundedBoxSDF(uv - center, resolution * 0.5, safeRadius);
                float mask = 1.0 - smoothstep(-1.0, 1.5, sdf);
                if (mask < 0.01) return half4(0.0);

                float minDimension = min(resolution.x, resolution.y);
                float refractionHeight =
                    refractionHeightFraction >= 0.0
                        ? refractionHeightFraction * minDimension
                        : safeRadius * edgeWidth;
                refractionHeight = min(refractionHeight, minDimension * 0.5);
                float effectEnabled =
                    step(0.0001, refractionHeight) * step(0.0001, refractionStrength);
                float safeRefractionHeight = max(refractionHeight, 0.0001);
                float edgeDepth = clamp(-sdf / safeRefractionHeight, 0.0, 1.0);
                float circleInput = 1.0 - edgeDepth;
                float edgeProfile =
                    (1.0 - sqrt(max(0.0, 1.0 - circleInput * circleInput))) * effectEnabled;
                float edgeAmt =
                    (1.0 - smoothstep(0.0, safeRefractionHeight, -sdf)) * effectEnabled;
                float liquidFx = pow(max(edgeAmt, 0.0), liquidPower);

                float eps = 1.0;
                float2 sdfGrad = float2(
                    roundedBoxSDF(uv + float2(eps,0) - center, resolution*0.5, safeRadius) -
                    roundedBoxSDF(uv - float2(eps,0) - center, resolution*0.5, safeRadius),
                    roundedBoxSDF(uv + float2(0,eps) - center, resolution*0.5, safeRadius) -
                    roundedBoxSDF(uv - float2(0,eps) - center, resolution*0.5, safeRadius)
                );
                float sdfLen = length(sdfGrad);
                float2 edgeNormal = sdfLen > 0.001 ? sdfGrad / sdfLen
                    : normalize((uv - center) / resolution + float2(0.0001));
                float2 radialNormal = normalize(uv - center + float2(0.0001));
                float2 normal = normalize(edgeNormal + depthEffect * radialNormal);

                float refr = refractionStrength * pow(edgeProfile, liquidPower)
                    * minDimension;
                // A convex glass lens samples toward its centre. This matches
                // Backdrop's negative refractionAmount convention.
                float2 uvRefracted = uv - normal * refr;

                half4 res = sampleBlurred(uvRefracted, blurRadius);

                if (chromaticAberration > 0.001) {
                    float2 centeredCoord = uv - center;
                    float2 halfSize = resolution * 0.5;
                    float dispersionIntensity =
                        chromaticAberration *
                        ((centeredCoord.x * centeredCoord.y) /
                            max(halfSize.x * halfSize.y, 0.0001));
                    float2 dispersedCoord = -normal * refr * dispersionIntensity;
                    half4 undisplaced = sampleBg(uvRefracted);
                    half4 red = sampleBg(uvRefracted + dispersedCoord);
                    half4 purple = sampleBg(uvRefracted - dispersedCoord);
                    // Keep the same signed dispersion coordinate as Backdrop,
                    // but sample only the two outer spectral channels. This
                    // avoids long GPU stalls seen with seven nested samples on
                    // some MediaTek Android drivers.
                    res.r = clamp(res.r + red.r - undisplaced.r, half(0.0), half(1.0));
                    res.b = clamp(res.b + purple.b - undisplaced.b, half(0.0), half(1.0));
                }

                res.rgb = applySaturation(res.rgb, saturation);
                res.rgb *= half(brightness);

                if (useHueTint > 0.5) {
                    res.rgb = half3(hueBlend(float3(res.rgb), tintColor));
                }
                res.rgb = mix(res.rgb, half3(tintColor), half(glassOpacity));

                float2 lightDir = normalize(float2(cos(lightAngle), -sin(lightAngle)));
                float glareAmt = pow(max(dot(normal, lightDir), 0.0), 15.0) * liquidFx * glareIntensity;
                res.rgb += half3(1.0) * half(glareAmt);

                res.rgb += half3(1.0) * half(pow(edgeAmt, fresnelPower) * edgeGlow);

                // Backdrop's default Highlight: 0.5dp wide, 0.25dp blur,
                // white at 0.5 alpha, with a 45-degree directional falloff.
                float borderWidth = ceil(0.5 * density);
                float borderBlur = max(0.25 * density, 0.5);
                float insideDistance = max(-sdf, 0.0);
                float borderBand =
                    1.0 - smoothstep(borderWidth, borderWidth + borderBlur, insideDistance);
                float directionalBorder =
                    abs(dot(normal, normalize(float2(0.70710678, 0.70710678))));
                float borderDirection = mix(directionalBorder, 1.0, plainBorder);
                res.rgb +=
                    half3(1.0) * half(borderBand * borderDirection * borderIntensity);

                // InteractiveHighlight from the reference: a subtle full
                // surface lift plus a stronger radial light at the finger.
                float pressRadius = min(resolution.x, resolution.y) * 1.5;
                float pressDistance = distance(uv, pressPosition);
                float pressSpot = smoothstep(pressRadius, pressRadius * 0.5, pressDistance);
                float pressLight = pressProgress * (0.08 + 0.15 * pressSpot);
                res.rgb += half3(1.0) * half(pressLight);

                float2 toCenter = uv - center;
                float iridAngle = atan(toCenter.y, toCenter.x);
                float iridPhase = iridAngle * 2.5;
                half3 irid = half3(
                    half(0.5 + 0.5 * cos(iridPhase)),
                    half(0.5 + 0.5 * cos(iridPhase + 2.094)),
                    half(0.5 + 0.5 * cos(iridPhase + 4.189))
                );
                float iridWide = liquidFx;
                float iridMask = iridWide * iridescence;
                res.rgb = mix(res.rgb, irid, half(clamp(iridMask, 0.0, 1.0)));

                float grain = (hash21(fragCoord) - 0.5) * noiseIntensity;
                res.rgb += half3(half(grain));

                res.a = 1.0;
                return res * half(mask);
            }
        """

    fun findBackgroundInTree(root: View, exclude: View): View? {
      var bestImageArea = 0L
      var bestImageView: ImageView? = null
      var bestBgArea = 0L
      var bestBgView: View? = null
      fun search(v: View) {
        if (!v.isShown || v.width <= 0 || v.height <= 0 || v === exclude || v is LiquidGlassView) return
        val area = v.width.toLong() * v.height.toLong()
        if (v is ImageView && v.drawable != null) {
          if (area > bestImageArea) { bestImageArea = area; bestImageView = v }
        } else if (v.background != null && area > bestBgArea) {
          bestBgArea = area; bestBgView = v
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) search(v.getChildAt(i))
      }
      search(root)
      return bestImageView ?: bestBgView
    }

    fun isBitmapValid(bitmap: Bitmap): Boolean {
      val w = bitmap.width; val h = bitmap.height
      if (w <= 0 || h <= 0) return false
      val xs = intArrayOf((w * 0.2f).roundToInt(), (w * 0.5f).roundToInt(), (w * 0.8f).roundToInt())
      val ys = intArrayOf((h * 0.2f).roundToInt(), (h * 0.5f).roundToInt(), (h * 0.8f).roundToInt())
      var opaque = 0
      for (x in xs) for (y in ys) {
        if (Color.alpha(bitmap.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))) >= MIN_VALID_ALPHA) opaque++
      }
      return opaque >= 6
    }

    private fun clearSharedBackdrop() {
      sharedBgBitmap?.takeUnless { it.isRecycled }?.recycle()
      sharedBgBitmap = null
      sharedBgView = null
      sharedBgW = 0f
      sharedBgH = 0f
      sharedBitmapVersion++
      for (view in allInstances) {
        view.localBitmapShader = null
        view.localBitmapVersion = -1
        view.propsDirty = true
      }
    }
  }

  init {
    setLayerType(LAYER_TYPE_HARDWARE, null)
    clipChildren = true
    clipToPadding = true
    isClickable = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      try {
        runtimeShader = RuntimeShader(SHADER_SRC)
      } catch (error: RuntimeException) {
        shaderFailed = true
        Log.e(TAG, "Unable to compile the liquid-glass shader; using fallback", error)
      }
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (!attached) {
      attached = true
      activeInstances++
    }
    allInstances.add(this)
    if (runtimeShader == null && !shaderFailed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      try {
        runtimeShader = RuntimeShader(SHADER_SRC)
      } catch (error: RuntimeException) {
        shaderFailed = true
        Log.e(TAG, "Unable to compile the liquid-glass shader; using fallback", error)
      }
    }
    propsDirty = true
    viewTreeObserver.addOnPreDrawListener(preDrawListener)
    val bmp = sharedBgBitmap
    val bg = sharedBgView
    if (
      bmp != null &&
      !bmp.isRecycled &&
      sharedBgW > 0f &&
      bg != null &&
      bg.isAttachedToWindow &&
      bg.rootView === rootView
    ) {
      buildLocalShader(bmp)
      post { syncOffset() }
    } else {
      clearSharedBackdrop()
      startupRetryCount = 0
      retryCapturePosted = false
      post { requestSharedCapture() }
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    if (changed && width > 0 && height > 0) {
      val bmp = sharedBgBitmap
      if (bmp == null || bmp.isRecycled) post { requestSharedCapture() }
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    allInstances.remove(this)
    viewTreeObserver.removeOnPreDrawListener(preDrawListener)
    removeCallbacks(retryCaptureRunnable)
    retryCapturePosted = false
    localBitmapShader = null
    localBitmapVersion = -1
    propsDirty = true
    removeCallbacks(interactionAnimation)
    interactionAnimationPosted = false
    interactionFrameNanos = 0L
    if (attached) {
      attached = false
      activeInstances = maxOf(0, activeInstances - 1)
    }
    if (activeInstances == 0) {
      clearSharedBackdrop()
    }
  }

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
    if (interactionEnabled) true else super.onInterceptTouchEvent(event)

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!interactionEnabled) return super.onTouchEvent(event)

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        parent?.requestDisallowInterceptTouchEvent(true)
        downRawX = event.rawX
        downRawY = event.rawY
        downLocalX = event.x
        downLocalY = event.y
        pressX = downLocalX
        pressY = downLocalY

        dragXSpring.value = 0f
        dragXSpring.velocity = 0f
        dragXSpring.target = 0f
        dragYSpring.value = 0f
        dragYSpring.velocity = 0f
        dragYSpring.target = 0f
        pressSpring.target = 1f
        isPressed = true
        postInteractionFrame()
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        val dx = event.rawX - downRawX
        val dy = event.rawY - downRawY
        dragXSpring.value = dx
        dragXSpring.velocity = 0f
        dragXSpring.target = dx
        dragYSpring.value = dy
        dragYSpring.velocity = 0f
        dragYSpring.target = dy
        // Raw coordinates remain stable while this view itself is translated
        // and scaled; derive the highlight from the original local down point.
        pressX = (downLocalX + dx).coerceIn(0f, width.toFloat())
        pressY = (downLocalY + dy).coerceIn(0f, height.toFloat())
        applyInteractiveTransform()
        invalidate()
        return true
      }

      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        val dx = event.rawX - downRawX
        val dy = event.rawY - downRawY
        pressSpring.target = 0f
        dragXSpring.target = 0f
        dragYSpring.target = 0f
        isPressed = false
        parent?.requestDisallowInterceptTouchEvent(false)
        postInteractionFrame()
        if (
          event.actionMasked == MotionEvent.ACTION_UP &&
          abs(dx) <= touchSlop &&
          abs(dy) <= touchSlop
        ) {
          performClick()
        }
        return true
      }
    }
    return super.onTouchEvent(event)
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  private fun postInteractionFrame() {
    if (interactionAnimationPosted) return
    interactionAnimationPosted = true
    postOnAnimation(interactionAnimation)
  }

  private fun advanceSpring(
    spring: SpringValue,
    dt: Float,
    threshold: Float,
  ): Boolean {
    val stiffness = 300f
    val damping = 2f * 0.5f * sqrt(stiffness)
    val acceleration =
      -stiffness * (spring.value - spring.target) - damping * spring.velocity
    spring.velocity += acceleration * dt
    spring.value += spring.velocity * dt
    if (
      abs(spring.value - spring.target) <= threshold &&
      abs(spring.velocity) <= threshold * 10f
    ) {
      spring.value = spring.target
      spring.velocity = 0f
      return false
    }
    return true
  }

  private fun applyInteractiveTransform() {
    if (width <= 0 || height <= 0) return
    val w = width.toFloat()
    val h = height.toFloat()
    val minDimension = min(w, h)
    val maxDimension = max(w, h)
    val dx = dragXSpring.value
    val dy = dragYSpring.value

    translationX = minDimension * tanh(0.05f * dx / minDimension)
    translationY = minDimension * tanh(0.05f * dy / minDimension)

    val baseScale = 1f + (4f * density / h) * pressSpring.value
    val dragScale = 4f * density / h
    val angle = atan2(dy, dx)
    scaleX =
      baseScale +
        dragScale * abs(cos(angle) * dx / maxDimension) * min(w / h, 1f)
    scaleY =
      baseScale +
        dragScale * abs(sin(angle) * dy / maxDimension) * min(h / w, 1f)
  }

  private fun buildLocalShader(bmp: Bitmap) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || shaderFailed) return
    try {
      val bs = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      val s = runtimeShader ?: RuntimeShader(SHADER_SRC).also { runtimeShader = it }
      s.setInputShader("backdrop", bs)
      localBitmapShader = bs
      localBitmapVersion = sharedBitmapVersion
      propsDirty = true
      paint.shader = s
      invalidate()
    } catch (error: RuntimeException) {
      shaderFailed = true
      runtimeShader = null
      localBitmapShader = null
      localBitmapVersion = -1
      paint.shader = null
      Log.e(TAG, "Unable to initialize the liquid-glass shader; using fallback", error)
      invalidate()
    }
  }

  private fun syncOffset() {
    val bgView = sharedBgView ?: return
    if (!bgView.isAttachedToWindow || bgView.rootView !== rootView) {
      requestSharedCapture()
      return
    }
    val bgLoc = IntArray(2); val viewLoc = IntArray(2)
    bgView.getLocationOnScreen(bgLoc); getLocationOnScreen(viewLoc)
    offsetX = (viewLoc[0] - bgLoc[0]).toFloat()
    offsetY = (viewLoc[1] - bgLoc[1]).toFloat()
    lastKnownBgWidth = bgView.width
    lastKnownBgHeight = bgView.height
    invalidate()
  }

  private fun updateOffset() {
    if (width <= 0 || height <= 0) return
    val bgView = sharedBgView ?: return
    val bmp = sharedBgBitmap
    if (
      bmp == null ||
      bmp.isRecycled ||
      !bgView.isAttachedToWindow ||
      bgView.rootView !== rootView
    ) {
      if (!isSharedCapturing) requestSharedCapture()
      return
    }
    if (
      bgView.width != lastKnownBgWidth ||
      bgView.height != lastKnownBgHeight ||
      bmp.width != bgView.width ||
      bmp.height != bgView.height
    ) {
      if (!isSharedCapturing) requestSharedCapture(force = true)
      return
    }
    val bgLoc = IntArray(2); val viewLoc = IntArray(2)
    bgView.getLocationOnScreen(bgLoc); getLocationOnScreen(viewLoc)
    val newX = (viewLoc[0] - bgLoc[0]).toFloat()
    val newY = (viewLoc[1] - bgLoc[1]).toFloat()
    if (
      (localBitmapShader == null || localBitmapVersion != sharedBitmapVersion) &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
      offsetX = newX; offsetY = newY
      buildLocalShader(bmp)
      return
    }
    val densityDelta = 0.5f * resources.displayMetrics.density
    if (abs(newX - offsetX) >= densityDelta || abs(newY - offsetY) >= densityDelta) {
      offsetX = newX; offsetY = newY
      invalidate()
    }
  }

  private fun scheduleRetry() {
    if (retryCapturePosted || startupRetryCount >= MAX_STARTUP_RETRIES) return
    retryCapturePosted = true
    startupRetryCount += 1
    val delayMs = when {
      startupRetryCount < 12 -> 16L
      startupRetryCount < 36 -> 50L
      else -> 125L
    }
    postDelayed(retryCaptureRunnable, delayMs)
  }

  private fun requestSharedCapture(force: Boolean = false) {
    if (isSharedCapturing || width <= 0 || height <= 0) return
    val bgView = findBackgroundInTree(rootView, this)
    if (bgView == null || bgView.width <= 0 || bgView.height <= 0) { scheduleRetry(); return }
    val existing = sharedBgBitmap
    if (
      !force &&
      existing != null &&
      !existing.isRecycled &&
      sharedBgView === bgView &&
      existing.width == bgView.width &&
      existing.height == bgView.height
    ) {
      buildLocalShader(existing)
      syncOffset()
      return
    }

    isSharedCapturing = true
    try {
      val bmp = Bitmap.createBitmap(bgView.width, bgView.height, Bitmap.Config.ARGB_8888)
      Canvas(bmp).also { c -> bgView.draw(c) }

      if (!isBitmapValid(bmp)) { bmp.recycle(); scheduleRetry(); return }

      val previousBitmap = sharedBgBitmap
      sharedBgBitmap = bmp
      sharedBgView = bgView
      sharedBgW = bgView.width.toFloat()
      sharedBgH = bgView.height.toFloat()
      sharedBitmapVersion++
      lastKnownBgWidth = bgView.width
      lastKnownBgHeight = bgView.height

      val bgLoc = IntArray(2); val viewLoc = IntArray(2)
      bgView.getLocationOnScreen(bgLoc); getLocationOnScreen(viewLoc)
      offsetX = (viewLoc[0] - bgLoc[0]).toFloat()
      offsetY = (viewLoc[1] - bgLoc[1]).toFloat()

      startupRetryCount = 0
      retryCapturePosted = false

      for (other in allInstances) {
        if (other.isAttachedToWindow && other.rootView === rootView) {
          other.buildLocalShader(bmp)
          other.post { other.syncOffset() }
        }
      }

      previousBitmap?.takeUnless { it === bmp || it.isRecycled }?.recycle()
      invalidate()
    } catch (error: Exception) {
      Log.w(TAG, "Backdrop capture failed; retrying", error)
      scheduleRetry()
    } finally {
      isSharedCapturing = false
    }
  }

  /**
   * The glass is painted here rather than in onDraw: ReactViewGroup never
   * routes through onDraw, so an override there is simply never called.
   * Drawing before super.dispatchDraw puts the glass behind this view's
   * React children, which is where a backdrop belongs.
   */
  override fun dispatchDraw(canvas: Canvas) {
    if (width <= 0 || height <= 0) {
      super.dispatchDraw(canvas)
      return
    }
    val radius = min(cornerRadiusPx, min(width, height) * 0.5f)
    clipRect.set(0f, 0f, width.toFloat(), height.toFloat())
    clipPath.rewind()
    clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW)
    val checkpoint = canvas.save()
    canvas.clipPath(clipPath)
    drawGlass(canvas)
    super.dispatchDraw(canvas)
    canvas.restoreToCount(checkpoint)
  }

  private fun drawGlass(canvas: Canvas) {
    val bmp = sharedBgBitmap
    if (bmp == null || bmp.isRecycled || sharedBgW <= 0f) {
      if (!isSharedCapturing && width > 0 && height > 0) post { requestSharedCapture() }
      return
    }
    if (
      canvas.isHardwareAccelerated &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      !shaderFailed
    ) {
      var bs = localBitmapShader
      if (bs == null || localBitmapVersion != sharedBitmapVersion) {
        buildLocalShader(bmp)
        bs = localBitmapShader
        if (bs == null) {
          drawFallbackGlass(canvas, bmp)
          return
        }
      }
      try {
        val s = runtimeShader ?: RuntimeShader(SHADER_SRC).also { runtimeShader = it; it.setInputShader("backdrop", bs) }
        if (propsDirty) {
          s.setFloatUniform("bgSize", sharedBgW, sharedBgH)
          s.setFloatUniform("blurRadius", shaderBlurRadius)
          s.setFloatUniform("refractionStrength", refractionStrength)
          s.setFloatUniform("chromaticAberration", chromaticAberration)
          s.setFloatUniform("edgeGlow", edgeGlowIntensity)
          s.setFloatUniform("glassOpacity", glassOpacity)
          s.setFloatUniform("tintColor", tintR, tintG, tintB)
          s.setFloatUniform("fresnelPower", fresnelPower)
          s.setFloatUniform("cornerRadius", cornerRadiusPx)
          s.setFloatUniform("glareIntensity", glareIntensity)
          s.setFloatUniform("borderIntensity", borderIntensity)
          s.setFloatUniform("plainBorder", if (plainBorder) 1f else 0f)
          s.setFloatUniform("edgeWidth", edgeWidth)
          s.setFloatUniform("refractionHeightFraction", refractionHeightFraction)
          s.setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
          s.setFloatUniform("liquidPower", liquidPower)
          s.setFloatUniform("lightAngle", lightAngle)
          s.setFloatUniform("saturation", shaderSaturation)
          s.setFloatUniform("brightness", brightness)
          s.setFloatUniform("noiseIntensity", noiseIntensity)
          s.setFloatUniform("iridescence", iridescence)
          s.setFloatUniform("density", density)
          s.setFloatUniform("useHueTint", if (usesHueTint) 1f else 0f)
          propsDirty = false
        }
        s.setFloatUniform("resolution", width.toFloat(), height.toFloat())
        s.setFloatUniform("viewOffset", offsetX, offsetY)
        s.setFloatUniform("pressProgress", pressSpring.value)
        s.setFloatUniform(
          "pressPosition",
          pressX.coerceIn(0f, width.toFloat()),
          pressY.coerceIn(0f, height.toFloat()),
        )
        paint.shader = s
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return
      } catch (error: RuntimeException) {
        shaderFailed = true
        runtimeShader = null
        localBitmapShader = null
        localBitmapVersion = -1
        paint.shader = null
        Log.e(TAG, "Liquid-glass rendering failed; using fallback", error)
      }
    }
    drawFallbackGlass(canvas, bmp)
  }

  private fun drawFallbackGlass(canvas: Canvas, bitmap: Bitmap) {
    val colorMatrix = ColorMatrix().apply {
      setSaturation(shaderSaturation)
      postConcat(
        ColorMatrix(
          floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
          ),
        ),
      )
    }
    fallbackPaint.shader = null
    fallbackPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)

    if (canvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      fallbackPaint.alpha = 255
      val renderNode = fallbackRenderNode ?: RenderNode("LiquidGlassBackdrop").also {
        fallbackRenderNode = it
      }
      renderNode.setPosition(0, 0, width, height)
      val recordingCanvas = renderNode.beginRecording(width, height)
      recordingCanvas.drawBitmap(bitmap, -offsetX, -offsetY, fallbackPaint)
      renderNode.endRecording()
      renderNode.setRenderEffect(if (shaderBlurRadius >= 0.5f) {
        RenderEffect.createBlurEffect(
          shaderBlurRadius,
          shaderBlurRadius,
          Shader.TileMode.CLAMP,
        )
      } else {
        null
      })
      canvas.drawRenderNode(renderNode)
    } else {
      // API 24-30: nine weighted samples keep the component visible and
      // approximate a frosted blur without RenderScript or extra allocations.
      val spread = (shaderBlurRadius * 0.35f).coerceAtMost(18f * density)
      val offsets = floatArrayOf(
        0f, 0f,
        -spread, 0f,
        spread, 0f,
        0f, -spread,
        0f, spread,
        -spread, -spread,
        spread, -spread,
        -spread, spread,
        spread, spread,
      )
      fallbackPaint.alpha = 32
      var index = 0
      while (index < offsets.size) {
        canvas.drawBitmap(
          bitmap,
          -offsetX + offsets[index],
          -offsetY + offsets[index + 1],
          fallbackPaint,
        )
        index += 2
      }
    }

    fallbackPaint.alpha = 255
    fallbackPaint.colorFilter = null
    drawFallbackSurface(canvas)
  }

  private fun drawFallbackSurface(canvas: Canvas) {
    val radius = min(cornerRadiusPx, min(width, height) * 0.5f)

    overlayPaint.shader = null
    overlayPaint.style = Paint.Style.FILL
    if (usesHueTint && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      overlayPaint.color = Color.rgb(
        (tintR * 255f).roundToInt().coerceIn(0, 255),
        (tintG * 255f).roundToInt().coerceIn(0, 255),
        (tintB * 255f).roundToInt().coerceIn(0, 255),
      )
      overlayPaint.blendMode = BlendMode.HUE
      canvas.drawRoundRect(clipRect, radius, radius, overlayPaint)
      overlayPaint.blendMode = null
    }
    overlayPaint.color = Color.argb(
      (glassOpacity * 255f).roundToInt().coerceIn(0, 255),
      (tintR * 255f).roundToInt().coerceIn(0, 255),
      (tintG * 255f).roundToInt().coerceIn(0, 255),
      (tintB * 255f).roundToInt().coerceIn(0, 255),
    )
    canvas.drawRoundRect(clipRect, radius, radius, overlayPaint)

    val highlightAlpha =
      ((glareIntensity * 0.22f + edgeGlowIntensity * 0.14f) * 255f)
        .roundToInt()
        .coerceIn(0, 180)
    if (highlightAlpha > 0) {
      overlayPaint.shader = LinearGradient(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        intArrayOf(
          Color.argb(highlightAlpha, 255, 255, 255),
          Color.TRANSPARENT,
          Color.argb(highlightAlpha / 4, 255, 255, 255),
        ),
        floatArrayOf(0f, 0.48f, 1f),
        Shader.TileMode.CLAMP,
      )
      canvas.drawRoundRect(clipRect, radius, radius, overlayPaint)
      overlayPaint.shader = null
    }

    val borderAlpha = (borderIntensity * 255f).roundToInt().coerceIn(0, 255)
    if (borderAlpha > 0) {
      borderPaint.strokeWidth = density
      borderPaint.color = Color.argb(borderAlpha, 255, 255, 255)
      val inset = density * 0.5f
      canvas.drawRoundRect(
        inset,
        inset,
        width - inset,
        height - inset,
        (radius - inset).coerceAtLeast(0f),
        (radius - inset).coerceAtLeast(0f),
        borderPaint,
      )
    }

    if (pressSpring.value > 0f) {
      val progress = pressSpring.value.coerceIn(0f, 1f)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        overlayPaint.blendMode = BlendMode.PLUS
      } else {
        @Suppress("DEPRECATION")
        overlayPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
      }

      overlayPaint.color = Color.argb(
        (0.08f * progress * 255f).roundToInt().coerceIn(0, 255),
        255,
        255,
        255,
      )
      canvas.drawRoundRect(clipRect, radius, radius, overlayPaint)

      val spotAlpha = (0.15f * progress * 255f).roundToInt().coerceIn(0, 255)
      overlayPaint.shader = RadialGradient(
        pressX.coerceIn(0f, width.toFloat()),
        pressY.coerceIn(0f, height.toFloat()),
        min(width, height) * 1.5f,
        intArrayOf(
          Color.argb(spotAlpha, 255, 255, 255),
          Color.argb(spotAlpha, 255, 255, 255),
          Color.TRANSPARENT,
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
      )
      canvas.drawRoundRect(clipRect, radius, radius, overlayPaint)
      overlayPaint.shader = null
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        overlayPaint.blendMode = null
      } else {
        @Suppress("DEPRECATION")
        overlayPaint.xfermode = null
      }
    }
  }
}
