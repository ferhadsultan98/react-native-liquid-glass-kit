import React, { forwardRef } from 'react';
import {
  NativeModules,
  Platform,
  processColor,
  type ColorValue,
} from 'react-native';

import NativeLiquidGlassView, {
  type LiquidGlassViewProps,
} from './NativeLiquidGlassView';

/**
 * Public wrapper around the generated Fabric component.
 *
 * Published libraries are consumed from precompiled JavaScript, so React
 * Native's Babel colour transform does not run for their props. Normalising
 * the tint here keeps the public API consistent with built-in RN components:
 * callers may safely pass named, hex, rgb(a), hsl(a), or platform colours.
 */
export const LiquidGlassView = forwardRef<
  React.ElementRef<typeof NativeLiquidGlassView>,
  LiquidGlassViewProps
>(({ tintColor, ...props }, ref) =>
  React.createElement(NativeLiquidGlassView, {
    ...props,
    ref,
    tintColor:
      tintColor == null
        ? undefined
        : (processColor(tintColor) as unknown as ColorValue),
  }),
);

LiquidGlassView.displayName = 'LiquidGlassView';

export type { LiquidGlassViewProps };
export default LiquidGlassView;

type ImagePickerModule = {
  pickImage(): Promise<string | null>;
};

/** Opens Android's privacy-preserving system Photo Picker. */
export async function pickLiquidGlassImage(): Promise<string | null> {
  if (Platform.OS !== 'android') {
    throw new Error('pickLiquidGlassImage is only available on Android.');
  }

  const imagePicker = NativeModules.RNLiquidGlassImagePicker as
    | ImagePickerModule
    | undefined;

  if (imagePicker == null) {
    throw new Error(
      'react-native-sultan-liquid-glass is not linked. Rebuild the Android app after installation.',
    );
  }

  return imagePicker.pickImage();
}
