import { NativeModules, Platform } from 'react-native';

export { LiquidGlassView } from './LiquidGlassView';
export type { LiquidGlassViewProps } from './NativeLiquidGlassView';
export { default } from './LiquidGlassView';

export {
  LiquidGlassButton,
  type LiquidGlassButtonProps,
} from './LiquidGlassButton';

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
      'react-native-liquid-glass-kit is not linked. Rebuild the Android app after installation.',
    );
  }

  return imagePicker.pickImage();
}
