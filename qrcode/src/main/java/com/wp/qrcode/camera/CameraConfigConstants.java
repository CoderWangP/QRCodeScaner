package com.wp.qrcode.camera;

/**
 * Created by wp on 2020/9/16.
 * <p>
 * Description:camera 配置参数常量
 */
public interface CameraConfigConstants {
    /**
     * 单次自动对焦
     */
    boolean AUTO_FOCUS = true;
    /**
     * 连续自动对焦
     */
    boolean DISABLE_CONTINUOUS_FOCUS = false;
    /**
     * 相机滤镜模式
     */
    boolean INVERT_SCAN = false;
    /**
     * 条形码场景匹配
     */
    boolean BARCODE_SCENE_MODE = false;
    /**
     * 测光区域,对焦区域
     */
    boolean METERING = false;

    /**
     * 曝光补偿
     */
    boolean BEST_EXPOSURE = false;
}
