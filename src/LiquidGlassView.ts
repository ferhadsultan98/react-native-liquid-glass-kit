import React, { forwardRef } from 'react';
import { processColor, type ColorValue } from 'react-native';

import NativeLiquidGlassView, {
  type LiquidGlassViewProps,
} from './NativeLiquidGlassView';

/**
 * Public wrapper around the generated Fabric component.
 *
 * Published libraries are consumed from precompiled JavaScript, so React
 * Native's Babel colour transform does not run for their props. Normalising
 * the tint here keeps the public API consistent with built-in RN components.
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

export default LiquidGlassView;

