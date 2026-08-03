import {
  codegenNativeComponent,
  type ColorValue,
  type HostComponent,
  type ViewProps,
} from 'react-native';
import type {
  Float,
  WithDefault,
} from 'react-native/Libraries/Types/CodegenTypes';

export interface LiquidGlassViewProps extends ViewProps {
  /** Enables native press, drag, highlight, and spring-return physics. */
  interactive?: WithDefault<boolean, true>;
  /** Backdrop blur radius in density-independent pixels. */
  blurRadius?: WithDefault<Float, 8>;
  /** Shape corner radius in density-independent pixels. */
  cornerRadius?: WithDefault<Float, 32>;
  /** Backdrop colour saturation; 1 leaves it unchanged. */
  saturation?: WithDefault<Float, 1>;
  /** Colour wash. Its alpha controls tint strength. */
  tintColor?: ColorValue;
  /** How far the lens bends backdrop pixels. */
  refractionStrength?: WithDefault<Float, 0.16>;
  /** Real-time RGB fringe strength at the refracting edge. */
  chromaticAberration?: WithDefault<Float, 0.04>;
  /** Fresnel edge-glow strength. */
  edgeGlowIntensity?: WithDefault<Float, 0>;
  /** Fresnel exponent; higher values create a tighter rim. */
  fresnelPower?: WithDefault<Float, 4>;
  /** Directional specular glare strength. */
  glareIntensity?: WithDefault<Float, 0.16>;
  /** Bright border intensity. */
  borderIntensity?: WithDefault<Float, 0.12>;
  /** Uses a uniform border instead of a directional highlight. */
  plainBorder?: WithDefault<boolean, false>;
  /** Refracting edge width as a multiple of the corner radius. */
  edgeWidth?: WithDefault<Float, 0.4>;
  /** Explicit edge height as a fraction of the minimum view dimension. */
  refractionHeightFraction?: WithDefault<Float, -1>;
  /** Enables the radial depth normal used by the playground lens. */
  depthEffect?: WithDefault<boolean, false>;
  /** Refraction falloff exponent. */
  liquidPower?: WithDefault<Float, 1.5>;
  /** Direction of the glare in radians. */
  lightAngle?: WithDefault<Float, 0.8>;
  /** Backdrop brightness multiplier. */
  brightness?: WithDefault<Float, 1>;
  /** Frosted-grain intensity. */
  noiseIntensity?: WithDefault<Float, 0.01>;
  /** Rainbow sheen around the rim. */
  iridescence?: WithDefault<Float, 0>;
}

export default codegenNativeComponent<LiquidGlassViewProps>(
  'RNLiquidGlassView',
) as HostComponent<LiquidGlassViewProps>;
