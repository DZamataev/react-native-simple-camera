import React, { useCallback, useMemo, useState } from 'react';
import type { ImageLoadEventData, NativeSyntheticEvent } from 'react-native';
import { Image, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import Video, { LoadError, OnLoadData } from 'react-native-video';
import { SAFE_AREA_PADDING } from './Constants';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { Routes } from './Routes';
import { useIsFocused } from '@react-navigation/core';

const isVideoOnLoadEvent = (
  event: OnLoadData | NativeSyntheticEvent<ImageLoadEventData>
): event is OnLoadData => 'duration' in event && 'naturalSize' in event;

type Props = NativeStackScreenProps<Routes, 'MediaPage'>;
export function MediaPage({ navigation, route }: Props): React.ReactElement {
  const { path, type } = route.params;
  const [hasMediaLoaded, setHasMediaLoaded] = useState(false);
  const isScreenFocused = useIsFocused();
  const isVideoPaused = !isScreenFocused;

  const onMediaLoad = useCallback(
    (event: OnLoadData | NativeSyntheticEvent<ImageLoadEventData>) => {
      if (isVideoOnLoadEvent(event)) {
        console.log(
          `Video loaded. Size: ${event.naturalSize.width}x${event.naturalSize.height} (${event.naturalSize.orientation}, ${event.duration} seconds)`
        );
      } else {
        console.log(
          `Image loaded. Size: ${event.nativeEvent.source.width}x${event.nativeEvent.source.height}`
        );
      }
    },
    []
  );
  const onMediaLoadEnd = useCallback(() => {
    console.log('media has loaded.');
    setHasMediaLoaded(true);
  }, []);
  const onMediaLoadError = useCallback((error: LoadError) => {
    console.log(`failed to load media: ${JSON.stringify(error)}`);
  }, []);

  const source = useMemo(() => ({ uri: `file://${path}` }), [path]);

  const screenStyle = useMemo(
    () => ({ opacity: hasMediaLoaded ? 1 : 0 }),
    [hasMediaLoaded]
  );

  return (
    <View style={[styles.container, screenStyle]}>
      {type === 'photo' && (
        <Image
          source={source}
          style={StyleSheet.absoluteFill}
          resizeMode="contain"
          onLoadEnd={onMediaLoadEnd}
          onLoad={onMediaLoad}
        />
      )}
      {type === 'video' && (
        <Video
          source={source}
          style={StyleSheet.absoluteFill}
          paused={isVideoPaused}
          resizeMode="cover"
          posterResizeMode="cover"
          allowsExternalPlayback={false}
          automaticallyWaitsToMinimizeStalling={false}
          disableFocus={true}
          repeat={true}
          useTextureView={false}
          controls={false}
          playWhenInactive={true}
          ignoreSilentSwitch="ignore"
          onReadyForDisplay={onMediaLoadEnd}
          onLoad={onMediaLoad}
          onError={onMediaLoadError}
        />
      )}

      <TouchableOpacity style={styles.closeButton} onPress={navigation.goBack}>
        <Text children={'Close'} />
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'white',
  },
  closeButton: {
    position: 'absolute',
    top: SAFE_AREA_PADDING.paddingTop,
    left: SAFE_AREA_PADDING.paddingLeft,
    width: 40,
    height: 40,
  },
  saveButton: {
    position: 'absolute',
    bottom: SAFE_AREA_PADDING.paddingBottom,
    left: SAFE_AREA_PADDING.paddingLeft,
    width: 40,
    height: 40,
  },
  icon: {
    textShadowColor: 'black',
    textShadowOffset: {
      height: 0,
      width: 0,
    },
    textShadowRadius: 1,
  },
});
