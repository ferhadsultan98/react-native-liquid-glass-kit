package com.sultan.liquidglass

import android.graphics.Color
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.RNLiquidGlassViewManagerDelegate
import com.facebook.react.viewmanagers.RNLiquidGlassViewManagerInterface

@ReactModule(name = LiquidGlassViewManager.NAME)
class LiquidGlassViewManager : ViewGroupManager<LiquidGlassView>(), RNLiquidGlassViewManagerInterface<LiquidGlassView> {

  private val delegate: ViewManagerDelegate<LiquidGlassView> = RNLiquidGlassViewManagerDelegate(this)

  override fun getDelegate(): ViewManagerDelegate<LiquidGlassView> = delegate

  override fun getName(): String = NAME

  override fun createViewInstance(reactContext: ThemedReactContext): LiquidGlassView = LiquidGlassView(reactContext)

  @ReactProp(name = "blurRadius", defaultFloat = 8f)
  override fun setBlurRadius(view: LiquidGlassView, value: Float) {
    view.setBlurRadius(value)
  }

  @ReactProp(name = "cornerRadius", defaultFloat = 32f)
  override fun setCornerRadius(view: LiquidGlassView, value: Float) {
    view.setCornerRadius(value)
  }

  @ReactProp(name = "saturation", defaultFloat = 1f)
  override fun setSaturation(view: LiquidGlassView, value: Float) {
    view.setSaturation(value)
  }

  @ReactProp(name = "tintColor")
  override fun setTintColor(view: LiquidGlassView, value: Int?) {
    view.setTintColor(value ?: Color.argb(31, 255, 255, 255))
  }

  @ReactProp(name = "refractionStrength", defaultFloat = 0.16f)
  override fun setRefractionStrength(view: LiquidGlassView, value: Float) {
    view.refractionStrength = value.finiteOr(0.16f)
  }

  @ReactProp(name = "chromaticAberration", defaultFloat = 0.04f)
  override fun setChromaticAberration(view: LiquidGlassView, value: Float) {
    view.chromaticAberration = value.finiteOr(0.04f)
  }

  @ReactProp(name = "edgeGlowIntensity", defaultFloat = 0f)
  override fun setEdgeGlowIntensity(view: LiquidGlassView, value: Float) {
    view.edgeGlowIntensity = value.finiteOr(0f)
  }

  @ReactProp(name = "fresnelPower", defaultFloat = 4f)
  override fun setFresnelPower(view: LiquidGlassView, value: Float) {
    view.fresnelPower = value.finiteOr(4f)
  }

  @ReactProp(name = "glareIntensity", defaultFloat = 0.16f)
  override fun setGlareIntensity(view: LiquidGlassView, value: Float) {
    view.glareIntensity = value.finiteOr(0.16f)
  }

  @ReactProp(name = "borderIntensity", defaultFloat = 0.12f)
  override fun setBorderIntensity(view: LiquidGlassView, value: Float) {
    view.borderIntensity = value.finiteOr(0.12f)
  }

  @ReactProp(name = "plainBorder", defaultBoolean = false)
  override fun setPlainBorder(view: LiquidGlassView, value: Boolean) {
    view.plainBorder = value
  }

  @ReactProp(name = "edgeWidth", defaultFloat = 0.4f)
  override fun setEdgeWidth(view: LiquidGlassView, value: Float) {
    view.edgeWidth = value.finiteOr(0.4f)
  }

  @ReactProp(name = "refractionHeightFraction", defaultFloat = -1f)
  override fun setRefractionHeightFraction(view: LiquidGlassView, value: Float) {
    view.refractionHeightFraction = value.finiteOr(-1f)
  }

  @ReactProp(name = "depthEffect", defaultBoolean = false)
  override fun setDepthEffect(view: LiquidGlassView, value: Boolean) {
    view.depthEffect = value
  }

  @ReactProp(name = "liquidPower", defaultFloat = 1.5f)
  override fun setLiquidPower(view: LiquidGlassView, value: Float) {
    view.liquidPower = value.finiteOr(1.5f)
  }

  @ReactProp(name = "lightAngle", defaultFloat = 0.8f)
  override fun setLightAngle(view: LiquidGlassView, value: Float) {
    view.lightAngle = value.finiteOr(0.8f)
  }

  @ReactProp(name = "brightness", defaultFloat = 1f)
  override fun setBrightness(view: LiquidGlassView, value: Float) {
    view.brightness = value.finiteOr(1f)
  }

  @ReactProp(name = "noiseIntensity", defaultFloat = 0.01f)
  override fun setNoiseIntensity(view: LiquidGlassView, value: Float) {
    view.noiseIntensity = value.finiteOr(0.01f)
  }

  @ReactProp(name = "iridescence", defaultFloat = 0f)
  override fun setIridescence(view: LiquidGlassView, value: Float) {
    view.iridescence = value.finiteOr(0f)
  }

  private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

  companion object {
    const val NAME = "RNLiquidGlassView"
  }
}
