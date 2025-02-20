/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wp.qrcode.camera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.zxing.PlanarYUVLuminanceSource;
import com.wp.qrcode.camera.open.OpenCamera;
import com.wp.qrcode.camera.open.OpenCameraInterface;

import java.io.IOException;

/**
 * This object wraps the Camera service object and expects to be the only one talking to it. The
 * implementation encapsulates the steps needed to take preview-sized images, which are used for
 * both preview and decoding.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
@SuppressWarnings("deprecation") // camera APIs
public final class CameraManager {

    private static final String TAG = "CameraManager";

    /**
     * 二维码捕获区域，矩形框的宽高范围
     */
    private static final int MIN_FRAME_WIDTH = 240;
    private static final int MIN_FRAME_HEIGHT = 240;

    /**
     * 横屏
     */
/*    private static final int MAX_FRAME_WIDTH = 1200; // = 5/8 * 1920
    private static final int MAX_FRAME_HEIGHT = 675; // = 5/8 * 1080*/

    /**
     * 竖屏
     */
    private static final int MAX_FRAME_WIDTH = 675; // = 5/8 * 1080
    private static final int MAX_FRAME_HEIGHT = 1200; // = 5/8 * 1920

    private final Context context;
    private final CameraConfigurationManager configManager;
    private OpenCamera camera;
    private AutoFocusManager autoFocusManager;

    /**
     * 屏幕中的二维码捕捉矩形框
     */
    private Rect framingRect;
    /**
     * 相机预览图中，二维码捕捉区域矩形框，与屏幕中的二维码捕捉矩形框{@link #framingRect}等比缩放
     */
    private Rect framingRectInPreview;
    private boolean initialized;
    private boolean previewing;
    private int requestedCameraId = OpenCameraInterface.NO_REQUESTED_CAMERA;
    private int requestedFramingRectWidth;
    private int requestedFramingRectHeight;

    public CameraManager(Context context) {
        this.context = context;
        this.configManager = new CameraConfigurationManager(context);
    }

    /**
     * Opens the camera driver and initializes the hardware parameters.
     *
     * @param holder The surface object which the camera will draw preview frames into.
     * @throws IOException Indicates the camera driver failed to open.
     */
    public synchronized void openDriver(SurfaceHolder holder) throws IOException {
        OpenCamera theCamera = camera;
        if (theCamera == null) {
            theCamera = OpenCameraInterface.open(requestedCameraId);
            if (theCamera == null) {
                throw new IOException("Camera.open() failed to return object from driver");
            }
            camera = theCamera;
        }

        if (!initialized) {
            initialized = true;
            configManager.initFromCameraParameters(theCamera);
            if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
                setManualFramingRect(requestedFramingRectWidth, requestedFramingRectHeight);
                requestedFramingRectWidth = 0;
                requestedFramingRectHeight = 0;
            }
        }

        Camera cameraObject = theCamera.getCamera();
        Camera.Parameters parameters = cameraObject.getParameters();
        String parametersFlattened = parameters == null ? null : parameters.flatten(); // Save these, temporarily
        try {
            configManager.setDesiredCameraParameters(theCamera, false);
        } catch (RuntimeException re) {
            // Driver failed
            Log.w(TAG, "Camera rejected parameters. Setting only minimal safe-mode parameters");
            Log.i(TAG, "Resetting to saved camera params: " + parametersFlattened);
            // Reset:
            if (parametersFlattened != null) {
                parameters = cameraObject.getParameters();
                parameters.unflatten(parametersFlattened);
                try {
                    cameraObject.setParameters(parameters);
                    configManager.setDesiredCameraParameters(theCamera, true);
                } catch (RuntimeException re2) {
                    // Well, darn. Give up
                    Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
                }
            }
        }
        cameraObject.setPreviewDisplay(holder);
    }

    public synchronized boolean isOpen() {
        return camera != null;
    }

    public synchronized Point getCameraResolution(){
        return configManager.getCameraResolution();
    }

    /**
     * Closes the camera driver if still in use.
     */
    public synchronized void closeDriver() {
        if (camera != null) {
            camera.getCamera().release();
            camera = null;
            // Make sure to clear these each time we close the camera, so that any scanning rect
            // requested by intent is forgotten.
            framingRect = null;
            framingRectInPreview = null;
        }
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     */
    public synchronized void startPreview() {
        OpenCamera theCamera = camera;
        if (theCamera != null && !previewing) {
            theCamera.getCamera().startPreview();
            previewing = true;
            autoFocusManager = new AutoFocusManager(context, theCamera.getCamera());
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     */
    public synchronized void stopPreview() {
        if (autoFocusManager != null) {
            autoFocusManager.stop();
            autoFocusManager = null;
        }
        if (camera != null && previewing) {
            //清除每帧数据回调
            camera.getCamera().setOneShotPreviewCallback(null);
            camera.getCamera().stopPreview();
            previewing = false;
        }
    }

    /**
     * 打开/关闭闪光灯
     *
     * @param newSetting if {@code true}, light should be turned on if currently off. And vice versa.
     */
    public synchronized void setTorch(boolean newSetting) {
        OpenCamera theCamera = camera;
        if (theCamera != null && newSetting != configManager.getTorchState(theCamera.getCamera())) {
            boolean wasAutoFocusManager = autoFocusManager != null;
            if (wasAutoFocusManager) {
                autoFocusManager.stop();
                autoFocusManager = null;
            }
            configManager.setTorch(theCamera.getCamera(), newSetting);
            if (wasAutoFocusManager) {
                autoFocusManager = new AutoFocusManager(context, theCamera.getCamera());
                autoFocusManager.start();
            }
        }
    }



    /**
     * A single preview frame will be returned to the previewCallback supplied. The data will arrive as byte[]
     * @param previewCallback: Preview frames are delivered here
     */
    public synchronized void requestPreviewFrame(Camera.PreviewCallback previewCallback) {
        OpenCamera theCamera = camera;
        if (theCamera != null && previewing) {
            theCamera.getCamera().setOneShotPreviewCallback(previewCallback);
        }
    }

    /**
     * 获取屏幕中二维码捕捉区域矩形框，不同于相机预览图中的尺寸坐标 {@link #getFramingRectInPreview()}
     * 相机预览的尺寸与屏幕尺寸等比缩放
     * Calculates the framing rect which the UI should draw to show the user where to place the
     * barcode. This target helps with alignment as well as forces the user to hold the device
     * far enough away to ensure the image will be in focus.
     *
     * @return The rectangle to draw on screen in window coordinates.
     */
    public synchronized Rect getFramingRect() {
        if (framingRect == null) {
            if (camera == null) {
                return null;
            }
            Point screenResolution = configManager.getScreenResolution();
            if (screenResolution == null) {
                // Called early, before init even finished
                return null;
            }

            int width = findDesiredDimensionInRange(screenResolution.x, MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
            int height = /*findDesiredDimensionInRange(screenResolution.y, MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);*/width;

            /**
             * 二维码捕捉区域，矩形框，位于屏幕中心
             */
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
        }
        return framingRect;
    }

    private static int findDesiredDimensionInRange(int resolution, int hardMin, int hardMax) {
        int dim = 5 * resolution / 8; // Target 5/8 of each dimension
        if (dim < hardMin) {
            return hardMin;
        }
        return Math.min(dim, hardMax);
    }

    /**
     * 二维码捕捉区域,相对于相机预览，在相机预览区域的位置，非屏幕位置
     * Like {@link #getFramingRect} but coordinates are in terms of the preview frame,
     * not UI / screen.
     *
     * @return {@link Rect} expressing barcode scan area in terms of the preview size
     */
    public synchronized Rect getFramingRectInPreview() {
        if (framingRectInPreview == null) {
            Rect framingRect = getFramingRect();
            if (framingRect == null) {
                return null;
            }
            /**
             * 预览尺寸，二维码捕捉区域矩形框
             */
            Rect rect = new Rect(framingRect);
            /**
             * 相机预览尺寸
             */
            Point cameraResolution = configManager.getCameraResolution();
            /**
             * 屏幕尺寸
             */
            Point screenResolution = configManager.getScreenResolution();
            if (cameraResolution == null || screenResolution == null) {
                // Called early, before init even finished
                return null;
            }
            if (screenResolution.x < screenResolution.y) {
                //portrait 竖屏
                rect.left = rect.left * cameraResolution.y / screenResolution.x;
                rect.right = rect.right * cameraResolution.y / screenResolution.x;
                rect.top = rect.top * cameraResolution.x / screenResolution.y;
                rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
            } else {
                //landscape 横屏
                rect.left = rect.left * cameraResolution.x / screenResolution.x;
                rect.right = rect.right * cameraResolution.x / screenResolution.x;
                rect.top = rect.top * cameraResolution.y / screenResolution.y;
                rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;
            }
            framingRectInPreview = rect;
        }
        return framingRectInPreview;
    }


    /**
     * Allows third party apps to specify the camera ID, rather than determine
     * it automatically based on available cameras and their orientation.
     *
     * @param cameraId camera ID of the camera to use. A negative value means "no preference".
     */
    public synchronized void setManualCameraId(int cameraId) {
        requestedCameraId = cameraId;
    }

    /**
     * Allows third party apps to specify the scanning rectangle dimensions, rather than determine
     * them automatically based on screen resolution.
     *
     * @param width  The width in pixels to scan.
     * @param height The height in pixels to scan.
     */
    public synchronized void setManualFramingRect(int width, int height) {
        if (initialized) {
            Point screenResolution = configManager.getScreenResolution();
            if (width > screenResolution.x) {
                width = screenResolution.x;
            }
            if (height > screenResolution.y) {
                height = screenResolution.y;
            }
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
            Log.d(TAG, "Calculated manual framing rect: " + framingRect);
            framingRectInPreview = null;
        } else {
            requestedFramingRectWidth = width;
            requestedFramingRectHeight = height;
        }
    }

    /**
     * 构建相机预览图的二维码捕捉高亮区域的source(YUV一种颜色编码方式，同样的颜色编码方式还有RGB等)
     * A factory method to build the appropriate LuminanceSource object based on the format
     * of the preview buffers, as described by Camera.Parameters.
     *
     * @param data   A preview frame.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
        Rect rect = getFramingRectInPreview();
        if (rect == null) {
            return null;
        }
        // Go ahead and assume it's YUV rather than die.
        return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
                rect.width(), rect.height(), false);

        /**
         * 不裁剪
         */
/*        return new PlanarYUVLuminanceSource(data, width, height, 0, 0,
                width, height, false);*/
    }

}
