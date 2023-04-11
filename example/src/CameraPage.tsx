import * as React from 'react';
import { useCallback, useRef, useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import {
  CameraAspectRatio,
  CameraModule,
  CameraPosition,
  CameraRuntimeError,
  CameraView,
  CameraViewRef,
  PhotoFile,
  VideoFile,
} from 'react-native-simple-camera';
import { CONTENT_SPACING, SAFE_AREA_PADDING } from './Constants';
import type { Routes } from './Routes';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useIsFocused } from '@react-navigation/core';

const BUTTON_SIZE = 40;

type Props = NativeStackScreenProps<Routes, 'CameraPage'>;
export function CameraPage({ navigation }: Props): React.ReactElement {
  const camera = useRef<CameraViewRef>(null);
  [, setR] = useState(0);

  // check if camera page is active
  const isActive = useIsFocused();

  const isRec = useRef(false);
  const [cameraPosition, setCameraPosition] = useState<CameraPosition>('back');
  const [flash, setFlash] = useState<'off' | 'on'>('off');
  const [ratio, setRatio] = useState<CameraAspectRatio>('16_9');

  const onError = useCallback((error: CameraRuntimeError) => {
    console.error(error);
  }, []);
  const onMediaCaptured = useCallback(
    (media: PhotoFile | VideoFile, type: 'photo' | 'video') => {
      console.log(`Media captured! ${JSON.stringify(media)}`);
      navigation.navigate('MediaPage', {
        path: media.path,
        type: type,
      });
    },
    [navigation]
  );
  const onFlipCameraPressed = useCallback(() => {
    setCameraPosition((p) => (p === 'back' ? 'front' : 'back'));
  }, []);

  const onFlshCameraPressed = useCallback(() => {
    setFlash((p) => (p === 'off' ? 'on' : 'off'));
  }, []);

  return (
    <View style={styles.container}>
      <CameraView
        ref={camera}
        cameraAspectRatio={ratio}
        style={StyleSheet.absoluteFill}
        position={cameraPosition}
        isActive={isActive}
        onError={onError}
        onInitialized={() => {
          setR((p) => p + 1);
        }}
        onReadCode={(code) => {
          console.log('js on read code:', code);
        }}
        enableZoomGesture={true}
        photo={true}
        video={true}
        audio={true}
        orientation="portrait"
      />

      <View style={styles.buttons}>
        <TouchableOpacity
          style={styles.button2}
          onPress={async () => {
            onMediaCaptured(
              await camera.current!.takePhoto({ flash }),
              'photo'
            );
          }}
        >
          <Text style={{ color: 'black' }} children={'Photo'} />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.button2}
          onPress={() => {
            setRatio(ratio === '16_9' ? '4_3' : '16_9');
          }}
        >
          <Text style={{ color: 'black' }} children={ratio} />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.button2}
          onPress={async () => {
            if (isRec.current) {
              camera.current?.stopRecording();
              isRec.current = false;
              return;
            }
            isRec.current = true;
            console.log(
              '[CameraPage.]',
              camera.current?.startRecording({
                onRecordingFinished: (v) => {
                  onMediaCaptured(v, 'video');
                },
                onRecordingError: () => {},
              })
            );
          }}
        >
          <Text style={{ color: 'black' }} children={'Video'} />
        </TouchableOpacity>
      </View>

      <View style={styles.rightButtonRow}>
        <TouchableOpacity style={styles.button} onPress={onFlipCameraPressed}>
          <Text children={'Flip'} />
        </TouchableOpacity>
        {camera.current?.supportsFlash() && (
          <TouchableOpacity onPress={onFlshCameraPressed}>
            <Text children={`Flash: ${flash}`} />
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'black',
  },
  captureButton: {
    position: 'absolute',
    alignSelf: 'center',
    bottom: SAFE_AREA_PADDING.paddingBottom,
  },
  button: {
    marginBottom: CONTENT_SPACING,
    width: BUTTON_SIZE,
    height: BUTTON_SIZE,
    borderRadius: BUTTON_SIZE / 2,
    backgroundColor: 'rgba(140, 140, 140, 0.3)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  rightButtonRow: {
    position: 'absolute',
    right: SAFE_AREA_PADDING.paddingRight,
    top: SAFE_AREA_PADDING.paddingTop,
  },
  text: {
    color: 'white',
    fontSize: 11,
    fontWeight: 'bold',
    textAlign: 'center',
  },

  button2: {
    width: 48,
    height: 48,
    backgroundColor: 'white',
    bottom: 0,
  },

  buttons: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
  },
});
