import type { TemporaryFile } from './TemporaryFile';

export interface TakePhotoOptions {
  flash?: 'on' | 'off' | 'auto';
}

export type CameraPosition = 'front' | 'back';

/**
 * Represents a Photo taken by the Camera written to the local filesystem.
 *
 * Related: {@linkcode Camera.takePhoto | Camera.takePhoto()}, {@linkcode Camera.takeSnapshot | Camera.takeSnapshot()}
 */
export interface PhotoFile extends TemporaryFile {
  width: number;
  height: number;
  thumbnail?: Record<string, unknown>;
}
