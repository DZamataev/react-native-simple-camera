import { NavigationContainer } from '@react-navigation/native';
import React, { useEffect, useState } from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import type { Routes } from './Routes';
import {
  CameraModule,
  CameraPermissionStatus,
} from 'react-native-simple-camera';
import { View } from 'react-native';
import { PermissionsPage } from './PermissionsPage';
import { MediaPage } from './MediaPage';
import { CameraPage } from './CameraPage';

const Stack = createNativeStackNavigator<Routes>();

export default function App() {
  const [cameraPermission, setCameraPermission] =
    useState<CameraPermissionStatus>();
  const [microphonePermission, setMicrophonePermission] =
    useState<CameraPermissionStatus>();

  useEffect(() => {
    CameraModule.getCameraPermissionStatus().then(setCameraPermission);
    CameraModule.getMicrophonePermissionStatus().then(setMicrophonePermission);
  }, []);

  console.log(
    `Re-rendering Navigator. Camera: ${cameraPermission} | Microphone: ${microphonePermission}`,
    CameraModule.isEmulator
  );

  if (cameraPermission == null || microphonePermission == null) {
    // still loading
    return null;
  }

  const showPermissionsPage =
    cameraPermission !== 'authorized' ||
    microphonePermission === 'not-determined';
  return (
    <NavigationContainer>
      <View style={{ flex: 1 }}>
        <Stack.Navigator
          screenOptions={{
            headerShown: false,
            animationTypeForReplace: 'push',
          }}
          initialRouteName={
            showPermissionsPage ? 'PermissionsPage' : 'CameraPage'
          }
        >
          <Stack.Screen name="PermissionsPage" component={PermissionsPage} />
          <Stack.Screen name="CameraPage" component={CameraPage} />
          <Stack.Screen
            name="MediaPage"
            component={MediaPage}
            options={{
              animation: 'none',
              presentation: 'transparentModal',
            }}
          />
        </Stack.Navigator>
      </View>
    </NavigationContainer>
  );
}
