import type { ViewProps } from 'react-native';
import type { VideoStabilizationMode } from './CameraDevice';
import type { CameraRuntimeError } from './CameraError';
import type { CameraPreset } from './CameraPreset';
import type { Orientation } from './Orientation';

export type CameraAspectRatio = '16_9' | '4_3';

export interface CameraProps extends ViewProps {
  isActive: boolean;
  photo?: boolean;
  video?: boolean;
  audio?: boolean;
  torch?: 'off' | 'on';
  zoom?: number;
  enableZoomGesture?: boolean;
  enableReadCode: boolean;
  preset?: CameraPreset;
  cameraAspectRatio?: CameraAspectRatio;
  /**
   * Specifies the video stabilization mode to use for this camera device. Make sure the given `format` contains the given `videoStabilizationMode`.
   *
   * Requires `format` to be set.
   * @platform iOS
   */
  videoStabilizationMode?: VideoStabilizationMode;
  orientation?: Orientation;
  onError?: (error: CameraRuntimeError) => void;
  onInitialized?: () => void;
  onReadCode?: (code: string) => void;
}
