import React, {
  forwardRef,
  memo,
  RefObject,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from 'react';
import {
  requireNativeComponent,
  NativeModules,
  NativeSyntheticEvent,
  findNodeHandle,
} from 'react-native';
import type { CameraDevice } from './CameraDevice';
import type { ErrorWithCause } from './CameraError';
import {
  CameraCaptureError,
  CameraRuntimeError,
  tryParseNativeCameraError,
  isErrorWithCause,
} from './CameraError';
import type { CameraProps } from './CameraProps';
import type { CameraPosition, PhotoFile, TakePhotoOptions } from './PhotoFile';
import type { Point } from './Point';
import type { RecordVideoOptions, VideoFile } from './VideoFile';
import { CameraDevices, getAvailableCameras } from './hooks/useCameraDevices';
import { useIsForeground } from './hooks/useIsForeground';

export type CameraPermissionStatus =
  | 'authorized'
  | 'not-determined'
  | 'denied'
  | 'restricted';
export type CameraPermissionRequestResult = 'authorized' | 'denied';

interface OnErrorEvent {
  code: string;
  message: string;
  cause?: ErrorWithCause;
}
type NativeCameraViewProps = Omit<
  CameraProps,
  'device' | 'onInitialized' | 'onError'
> & {
  cameraId: string;
  onInitialized?: (event: NativeSyntheticEvent<void>) => void;
  onError?: (event: NativeSyntheticEvent<OnErrorEvent>) => void;
  onViewReady: () => void;
};

const _CameraModule = NativeModules.CameraView;
if (_CameraModule == null)
  console.error(
    "Camera: Native Module 'CameraView' was null! Did you run pod install?"
  );

function handle(ref: RefObject<any>): number | null {
  const nodeHandle = findNodeHandle(ref.current);
  if (nodeHandle == null || nodeHandle === -1) {
    return null;
  }

  return nodeHandle;
}

export interface CameraViewRef {
  takePhoto(options?: TakePhotoOptions): Promise<PhotoFile>;
  startRecording(options: RecordVideoOptions): void;
  stopRecording(): void;
  focus(point: Point): Promise<void>;
  supportsFlash: () => boolean;
  supportFlip: () => boolean;
}

export const CameraView = memo(
  forwardRef<CameraViewRef, CameraProps & { position: CameraPosition }>(
    (props, ___ref) => {
      const _ref = useRef<any>(null);
      const [device, setDevice] = useState<CameraDevices | undefined>();

      const isForeground = useIsForeground();

      useEffect(() => {
        getAvailableCameras().then(setDevice);
      }, [props.position]);

      useImperativeHandle(
        ___ref,
        () => ({
          supportsFlash: () => device?.[props.position]?.hasFlash ?? false,
          supportFlip: () => !!device?.back && !!device?.front,

          async takePhoto(options?: TakePhotoOptions): Promise<PhotoFile> {
            try {
              return await _CameraModule.takePhoto(
                handle(_ref),
                options ? { ...options, fixOrientation: true } : {}
              );
            } catch (e) {
              throw tryParseNativeCameraError(e);
            }
          },

          startRecording(options: RecordVideoOptions) {
            const {
              onRecordingError,
              onRecordingFinished,
              ...passThroughOptions
            } = options;
            if (
              typeof onRecordingError !== 'function' ||
              typeof onRecordingFinished !== 'function'
            )
              throw new CameraRuntimeError(
                'parameter/invalid-parameter',
                'The onRecordingError or onRecordingFinished functions were not set!'
              );

            const onRecordCallback = (
              video?: VideoFile,
              error?: CameraCaptureError
            ) => {
              if (error != null) return onRecordingError(error);
              if (video != null) return onRecordingFinished(video);
            };
            // TODO: Use TurboModules to either make this a sync invokation, or make it async.
            try {
              _CameraModule.startRecording(
                handle(_ref),
                passThroughOptions,
                onRecordCallback
              );
            } catch (e) {
              throw tryParseNativeCameraError(e);
            }
          },

          async stopRecording() {
            try {
              return await _CameraModule.stopRecording(handle(_ref));
            } catch (e) {
              throw tryParseNativeCameraError(e);
            }
          },

          async focus(point: Point) {
            try {
              return await _CameraModule.focus(handle(_ref), point);
            } catch (e) {
              throw tryParseNativeCameraError(e);
            }
          },
        }),
        [device, props.position]
      );

      function onError(event: NativeSyntheticEvent<OnErrorEvent>) {
        if (props.onError != null) {
          const error = event.nativeEvent;
          const cause = isErrorWithCause(error.cause) ? error.cause : undefined;
          props.onError(
            // @ts-expect-error We're casting from unknown bridge types to TS unions, I expect it to hopefully work
            new CameraRuntimeError(error.code, error.message, cause)
          );
        }
      }

      function onViewReady(): void {}

      if (!device) return null;

      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { position: _p, ...pr } = props;

      return (
        <NativeCameraView
          {...pr}
          cameraAspectRatio={pr.cameraAspectRatio ?? '16_9'}
          cameraId={device![props.position]!.id}
          isActive={isForeground && props.isActive}
          ref={_ref}
          zoom={pr.zoom ?? device![props.position]!.neutralZoom}
          onInitialized={props.onInitialized}
          onError={onError}
          onViewReady={onViewReady}
        />
      );
    }
  )
);
CameraView.displayName = 'CameraView';

export const CameraModule = {
  get isEmulator(): boolean {
    return _CameraModule.isEmulator() === 'true';
  },
  async getAvailableCameraDevices(): Promise<CameraDevice[]> {
    try {
      return await _CameraModule.getAvailableCameraDevices();
    } catch (e) {
      throw tryParseNativeCameraError(e);
    }
  },
  async getCameraPermissionStatus(): Promise<CameraPermissionStatus> {
    try {
      return await _CameraModule.getCameraPermissionStatus();
    } catch (e) {
      throw tryParseNativeCameraError(e);
    }
  },

  async getMicrophonePermissionStatus(): Promise<CameraPermissionStatus> {
    try {
      return await _CameraModule.getMicrophonePermissionStatus();
    } catch (e) {
      throw tryParseNativeCameraError(e);
    }
  },

  async requestCameraPermission(): Promise<CameraPermissionRequestResult> {
    try {
      return await _CameraModule.requestCameraPermission();
    } catch (e) {
      throw tryParseNativeCameraError(e);
    }
  },

  async requestMicrophonePermission(): Promise<CameraPermissionRequestResult> {
    try {
      return await _CameraModule.requestMicrophonePermission();
    } catch (e) {
      throw tryParseNativeCameraError(e);
    }
  },
};

// requireNativeComponent automatically resolves 'CameraView' to 'CameraViewManager'
const NativeCameraView = requireNativeComponent<NativeCameraViewProps>(
  'CameraView',
  // @ts-expect-error because the type declarations are kinda wrong, no?
  CameraView
);
