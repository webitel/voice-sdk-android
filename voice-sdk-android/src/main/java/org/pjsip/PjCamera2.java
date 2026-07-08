/*
 * Copyright (C) 2021 Teluu Inc. (http://www.teluu.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.pjsip;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.SurfaceHolder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.pjsip.PjCameraInfo2;
public class PjCamera2 {
    private final String TAG = "PjCamera2";

    private CameraDevice camera = null;
    private CameraCaptureSession previewSession = null;

    private volatile boolean isRunning = false;
    private boolean start_with_fps = true;
    private int camIdx;
    private final long userData;
    private final int fps;
    private final int w;
    private final int h;
    private final int fmt;

    private ImageReader imageReader;
    private HandlerThread handlerThread = null;
    private Handler handler;

    /* For debugging purpose only */
    private final SurfaceView surfaceView;

    private byte[] rowTempBuffer = null;
    private byte[] planeTempBuffer = null;

    private static volatile int sDeviceRotationDegrees = 0;

    public static void SetDeviceRotationDegrees(int degrees) {
        sDeviceRotationDegrees = ((degrees % 360) + 360) % 360;
        Log.d("PjCamera2", "SetDeviceRotationDegrees: input=" + degrees + " stored=" + sDeviceRotationDegrees);
    }

    native void PushFrame2(long userData_,
                           ByteBuffer plane0, int rowStride0, int pixStride0,
                           ByteBuffer plane1, int rowStride1, int pixStride1,
                           ByteBuffer plane2, int rowStride2, int pixStride2);

    private final ImageReader.OnImageAvailableListener imageAvailListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            if (!isRunning) return;

            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;

                Image.Plane[] planes = image.getPlanes();
                if (planes == null || planes.length == 0) {
                    image.close();
                    return;
                }

                ByteBuffer plane0 = planes[0].getBuffer();
                ByteBuffer plane1 = planes.length > 1 ? planes[1].getBuffer() : null;
                ByteBuffer plane2 = planes.length > 2 ? planes[2].getBuffer() : null;

                PjCameraInfo2 ci = PjCameraInfo2.GetCameraInfo(camIdx);
                boolean isFrontCamera = (ci != null && ci.facing == 1);

                if (isFrontCamera) {
                    // 1. Перевертаємо яскравість (Y)
                    flipYUVVerticalInPlace(plane0, planes[0].getRowStride(), planes[0].getPixelStride(), w, h);

                    // 2. Перевертаємо кольори (U / V)
                    if (plane1 != null && plane2 != null) {
                        // Перевірка на NV12/NV21 (спільна пам'ять або кроки пікселів рівні 2)
                        if (planes[1].getPixelStride() == 2 || planes[2].getPixelStride() == 2) {
                            // Для NV12/NV21 достатньо перевернути один буфер, бо вони зазвичай інтерлейснуті
                            // Проте, для безпеки перевіряємо, чи це не один і той самий набір даних
                            flipYUVVerticalInPlace(plane1, planes[1].getRowStride(), planes[1].getPixelStride(), w / 2, h / 2);
                        } else {
                            // Чесний YUV420P (Planar), де U і V повністю окремо
                            flipYUVVerticalInPlace(plane1, planes[1].getRowStride(), planes[1].getPixelStride(), w / 2, h / 2);
                            flipYUVVerticalInPlace(plane2, planes[2].getRowStride(), planes[2].getPixelStride(), w / 2, h / 2);
                        }
                    } else {
                        if (plane1 != null) flipYUVVerticalInPlace(plane1, planes[1].getRowStride(), planes[1].getPixelStride(), w / 2, h / 2);
                        if (plane2 != null) flipYUVVerticalInPlace(plane2, planes[2].getRowStride(), planes[2].getPixelStride(), w / 2, h / 2);
                    }
                } else if (ci != null) {
                    // Задня камера: компенсуємо лише точний 180°
                    int requiredRotation = ((ci.orient + sDeviceRotationDegrees) % 360 + 360) % 360;
                    if (requiredRotation == 180) {
                        rotate180YUVPlaneInPlace(plane0, planes[0].getRowStride(), planes[0].getPixelStride(), w, h);
                        if (plane1 != null && plane2 != null) {
                            if (planes[1].getPixelStride() == 2 || planes[2].getPixelStride() == 2) {
                                rotate180YUVPlaneInPlace(plane1, planes[1].getRowStride(), planes[1].getPixelStride(), w / 2, h / 2);
                            } else {
                                rotate180YUVPlaneInPlace(plane1, planes[1].getRowStride(), planes[1].getPixelStride(), w / 2, h / 2);
                                rotate180YUVPlaneInPlace(plane2, planes[2].getRowStride(), planes[2].getPixelStride(), w / 2, h / 2);
                            }
                        } else {
                            if (plane1 != null) rotate180YUVPlaneInPlace(plane1, planes[1].getRowStride(), planes[1].getPixelStride(), w / 2, h / 2);
                            if (plane2 != null) rotate180YUVPlaneInPlace(plane2, planes[2].getRowStride(), planes[2].getPixelStride(), w / 2, h / 2);
                        }
                    }
                }

                // Передаємо модифіковані Direct Буфери в натив
                PushFrame2(userData,
                        plane0, planes[0].getRowStride(), planes[0].getPixelStride(),
                        plane1, plane1 != null ? planes[1].getRowStride() : 0, plane1 != null ? planes[1].getPixelStride() : 0,
                        plane2, plane2 != null ? planes[2].getRowStride() : 0, plane2 != null ? planes[2].getPixelStride() : 0);

            } catch (Exception e) {
                Log.e(TAG, "Помилка обробки кадру: " + e.getMessage(), e);
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    };

    private final CameraDevice.StateCallback camStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice c) {
            Log.i(TAG, "CameraDevice.StateCallback.onOpened");
            camera = c;
            if (isRunning) {
                StartPreview();
            }
        }
        @Override
        public void onClosed(CameraDevice c) {
            Log.i(TAG, "CameraDevice.StateCallback.onClosed");
        }
        @Override
        public void onDisconnected(CameraDevice c) {
            Log.i(TAG, "CameraDevice.StateCallback.onDisconnected");
            Stop();
        }
        @Override
        public void onError(CameraDevice c, int error) {
            Log.e(TAG, "CameraDevice.StateCallback.onError: " + error);
            boolean was_with_fps = start_with_fps;
            Stop();

            if ((error == CameraDevice.StateCallback.ERROR_CAMERA_DEVICE ||
                    error == CameraDevice.StateCallback.ERROR_CAMERA_SERVICE) && was_with_fps) {
                Log.i(TAG, "Retrying without enforcing frame rate..");
                start_with_fps = false;
                Start();
            }
        }
    };

    private final SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "SurfaceHolder.Callback.surfaceCreated");
            if (camera != null) {
                StartPreview();
            }
        }
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "SurfaceHolder.Callback.surfaceChanged");
        }
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "SurfaceHolder.Callback.surfaceDestroyed");
        }
    };

    public PjCamera2(int idx, int w_, int h_, int fmt_, int fps_, long userData_, SurfaceView surface) {
        camIdx = idx;
        w = w_;
        h = h_;
        fmt = fmt_;
        userData = userData_;
        fps = fps_;
        surfaceView = surface;
    }

    public int SwitchDevice(int idx) {
        boolean isCaptureRunning = isRunning;
        int oldIdx = camIdx;

        if (isCaptureRunning) Stop();
        camIdx = idx;

        if (isCaptureRunning) {
            int ret = Start();
            if (ret != 0) {
                camIdx = oldIdx;
                Start();
                return ret;
            }
        }
        return 0;
    }

    private void StartPreview() {
        if (camera == null || imageReader == null) return;
        try {
            List<Surface> surfaceList = new ArrayList<>();
            surfaceList.add(imageReader.getSurface());
            if (surfaceView != null) {
                surfaceList.add(surfaceView.getHolder().getSurface());
            }

            camera.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (!isRunning || camera == null) return;
                    try {
                        CaptureRequest.Builder previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        previewBuilder.addTarget(imageReader.getSurface());
                        if (surfaceView != null) {
                            previewBuilder.addTarget(surfaceView.getHolder().getSurface());
                        }
                        previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                        if (start_with_fps) {
                            Range<Integer> fpsRange = new Range<>(fps, fps);
                            previewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
                        }
                        session.setRepeatingRequest(previewBuilder.build(), null, handler);
                        previewSession = session;
                    } catch (Exception e) {
                        Stop();
                    }
                    if (surfaceView != null) {
                        surfaceView.getHolder().addCallback(surfaceHolderCallback);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "CameraCaptureSession.StateCallback.onConfigureFailed");
                    Stop();
                }
            }, handler);
        } catch (Exception e) {
            Stop();
        }
    }

    public int Start() {
        PjCameraInfo2 ci = PjCameraInfo2.GetCameraInfo(camIdx);
        if (ci == null) {
            Log.e(TAG, "Invalid device index: " + camIdx);
            return -1;
        }

        CameraManager cm = PjCameraInfo2.GetCameraManager();
        if (cm == null) return -2;

        handlerThread = new HandlerThread("Cam2HandlerThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        int maxEstimatedPlaneSize = w * h * 2;
        rowTempBuffer = new byte[w * 4];
        planeTempBuffer = new byte[maxEstimatedPlaneSize];

        imageReader = ImageReader.newInstance(w, h, fmt, 3);
        imageReader.setOnImageAvailableListener(imageAvailListener, handler);
        isRunning = true;

        try {
            cm.openCamera(ci.id, camStateCallback, handler);
        } catch (Exception e) {
            Stop();
            return -10;
        }

        return 0;
    }

    public synchronized void Stop() {
        if (!isRunning) return;
        isRunning = false;
        Log.d(TAG, "Stopping camera component..");

        if (previewSession != null) {
            try {
                previewSession.stopRepeating();
            } catch (Exception e) {
                Log.e(TAG, "Помилка зупинки сесії: " + e.getMessage());
            }
            previewSession.close();
            previewSession = null;
        }

        if (camera != null) {
            camera.close();
            camera = null;
        }

        if (surfaceView != null) {
            surfaceView.getHolder().removeCallback(surfaceHolderCallback);
        }

        if (handlerThread != null) {
            handlerThread.quitSafely();
            try {
                handlerThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            handlerThread = null;
            handler = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        rowTempBuffer = null;
        planeTempBuffer = null;
        start_with_fps = true;

        Log.d(TAG, "Stopped successfully.");
    }


    private void flipYUVVerticalInPlace(ByteBuffer buffer, int rowStride, int pixelStride, int width, int height) {
        if (buffer == null || rowTempBuffer == null) return;
        buffer.rewind();

        int bytesToCopy = width * pixelStride;
        if (rowTempBuffer.length < bytesToCopy) {
            rowTempBuffer = new byte[bytesToCopy * 2];
        }

        for (int y = 0; y < height / 2; y++) {
            int topRowStart = y * rowStride;
            int bottomRowStart = (height - 1 - y) * rowStride;

            if (topRowStart + bytesToCopy > buffer.capacity() || bottomRowStart + bytesToCopy > buffer.capacity()) {
                continue;
            }

            buffer.position(topRowStart);
            buffer.get(rowTempBuffer, 0, bytesToCopy);

            buffer.position(bottomRowStart);
            buffer.limit(bottomRowStart + bytesToCopy);
            ByteBuffer bottomView = buffer.slice();

            buffer.limit(buffer.capacity());
            buffer.position(topRowStart);
            buffer.put(bottomView);

            buffer.position(bottomRowStart);
            buffer.put(rowTempBuffer, 0, bytesToCopy);
        }
        buffer.rewind();
    }


    private void mirrorYUVPlaneInPlace(ByteBuffer src, int rowStride, int pixelStride, int width, int height) {
        if (src == null || rowTempBuffer == null) return;
        src.rewind();

        if (rowTempBuffer.length < rowStride) {
            rowTempBuffer = new byte[rowStride * 2];
        }

        for (int y = 0; y < height; y++) {
            int rowStart = y * rowStride;
            int currentBytes = Math.min(rowStride, src.remaining());
            if (currentBytes <= 0) break;

            src.position(rowStart);
            src.get(rowTempBuffer, 0, currentBytes);

            if (pixelStride == 1) {
                for (int x = 0; x < width / 2; x++) {
                    int leftIdx = x;
                    int rightIdx = width - 1 - x;
                    byte temp = rowTempBuffer[leftIdx];
                    rowTempBuffer[leftIdx] = rowTempBuffer[rightIdx];
                    rowTempBuffer[rightIdx] = temp;
                }
            } else if (pixelStride == 2) {
                int pairsCount = width;
                for (int x = 0; x < pairsCount / 2; x++) {
                    int leftByteIdx = x * 2;
                    int rightByteIdx = (pairsCount - 1 - x) * 2;

                    byte tempU = rowTempBuffer[leftByteIdx];
                    byte tempV = rowTempBuffer[leftByteIdx + 1];

                    rowTempBuffer[leftByteIdx] = rowTempBuffer[rightByteIdx];
                    rowTempBuffer[leftByteIdx + 1] = rowTempBuffer[rightByteIdx + 1];

                    rowTempBuffer[rightByteIdx] = tempU;
                    rowTempBuffer[rightByteIdx + 1] = tempV;
                }
            }

            src.position(rowStart);
            src.put(rowTempBuffer, 0, currentBytes);
        }
        src.rewind();
    }


    private void rotate180YUVPlaneInPlace(ByteBuffer buf, int rowStride, int pixelStride, int width, int height) {
        if (buf == null) return;
        flipYUVVerticalInPlace(buf, rowStride, pixelStride, width, height);
        mirrorYUVPlaneInPlace(buf, rowStride, pixelStride, width, height);
    }
}