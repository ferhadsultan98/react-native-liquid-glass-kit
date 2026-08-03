import React, { forwardRef } from 'react';
import {
  Pressable,
  StyleSheet,
  View,
  type PressableProps,
  type PressableStateCallbackType,
  type StyleProp,
  type ViewStyle,
} from 'react-native';

import { LiquidGlassView } from './LiquidGlassView';
import type { LiquidGlassViewProps } from './NativeLiquidGlassView';

export interface LiquidGlassButtonProps
  extends Omit<PressableProps, 'children' | 'style'> {
  /** Button content, or a render function receiving the Pressable state. */
  children?:
    | React.ReactNode
    | ((state: PressableStateCallbackType) => React.ReactNode);
  /** Layout style applied to the accessible Pressable root. */
  style?: PressableProps['style'];
  /** Padding and alignment for the button content. */
  contentStyle?: StyleProp<ViewStyle>;
  /** Optical configuration for the non-intercepting glass background. */
  glassProps?: Omit<
    LiquidGlassViewProps,
    'children' | 'interactive' | 'pointerEvents' | 'style'
  >;
  /** Scale applied while pressed. Set to 1 to disable this feedback. */
  pressedScale?: number;
}

/**
 * Accessible Pressable with a liquid-glass background.
 *
 * The glass layer runs in non-interactive mode so the Pressable remains the
 * single touch owner. This avoids responder conflicts inside lists, dialogs,
 * forms, and other composed React Native controls.
 */
export const LiquidGlassButton = forwardRef<
  React.ElementRef<typeof Pressable>,
  LiquidGlassButtonProps
>(
  (
    {
      accessibilityRole,
      children,
      contentStyle,
      disabled,
      glassProps,
      pressedScale = 0.98,
      style,
      ...pressableProps
    },
    ref,
  ) =>
    React.createElement(
      Pressable,
      {
        ...pressableProps,
        accessibilityRole: accessibilityRole ?? 'button',
        children: (state: PressableStateCallbackType) =>
          React.createElement(
            View,
            {
              pointerEvents: 'none',
              style: [
                styles.surface,
                state.pressed && !disabled
                  ? { transform: [{ scale: pressedScale }] }
                  : null,
              ],
            },
            React.createElement(LiquidGlassView, {
              cornerRadius: 24,
              blurRadius: 2,
              refractionStrength: 0.5,
              edgeWidth: 0.5,
              borderIntensity: 0.5,
              saturation: 1.5,
              glareIntensity: 0,
              edgeGlowIntensity: 0,
              noiseIntensity: 0,
              tintColor: 'rgba(255,255,255,0.24)',
              ...glassProps,
              interactive: false,
              pointerEvents: 'none',
              style: StyleSheet.absoluteFill,
            }),
            React.createElement(
              View,
              { style: [styles.content, contentStyle] },
              typeof children === 'function' ? children(state) : children,
            ),
          ),
        disabled,
        ref,
        style: (state: PressableStateCallbackType) => [
          styles.root,
          typeof style === 'function' ? style(state) : style,
          disabled ? styles.disabled : null,
        ],
      },
    ),
);

LiquidGlassButton.displayName = 'LiquidGlassButton';

const styles = StyleSheet.create({
  root: {
    borderRadius: 24,
  },
  surface: {
    borderRadius: 24,
  },
  content: {
    minHeight: 48,
    paddingHorizontal: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  disabled: {
    opacity: 0.5,
  },
});
