package nice.com.jzs.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import org.androidannotations.annotations.App;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import nice.com.jzs.R;
import nice.com.jzs.background.AppInfo;
import nice.com.jzs.background.RequestAPI;
import nice.com.jzs.core.AbstractActivity;
import nice.com.jzs.ui.UploadImgBean;
import nice.com.jzs.ui.zicha.ActivityZichaResult;
import nice.com.jzs.ui.zicha.ActivityZichaResult_;
import nice.com.nice_library.bean.BaseBean;
import nice.com.nice_library.util.DisplayUtil;
import nice.com.nice_library.util.ToastUtil;


@SuppressLint("NewApi")
//默认的相机为横平，所以Activity设置为横屏，拍出的照片才正确
public class ActivityCapture extends AbstractActivity implements
        View.OnClickListener, CaptureSensorsObserver.RefocuseListener {
    private ImageView bnCapture;
    private TextView save;
    private TextView notSave;
    private ImageView showImage;
    private boolean isOpenLight = false;

    private FrameLayout framelayoutPreview;
    private CameraPreview preview;
    private Camera camera;
    private PictureCallback pictureCallBack;
    private Camera.AutoFocusCallback focusCallback;
    private CaptureSensorsObserver observer;
    private View focuseView;

    private int currentCameraId;
    private int frontCameraId;
    private boolean _isCapturing;

    private TextView degree;
    private TimeCircleSelector degreeBar;
    private View line;
    private float degreeValue;

    CaptureOrientationEventListener _orientationEventListener;
    private int _rotation;

    public static final int kPhotoMaxSaveSideLen = 1600;
    public static final String kPhotoPath = "photo_path";
    private Bitmap finalBitmap;
    private File photoFile;

    private int cropWidth = 750;
    private int cropHeight = 750;

    private boolean makeWaterMark;

    public static final int kBeforeCameraCode = 1024;

    private Camera.ShutterCallback _shutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
        }
    };

    final static String TAG = "capture";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isTemplate = false;
        observer = new CaptureSensorsObserver(this);
        _orientationEventListener = new CaptureOrientationEventListener(this);
        cropWidth = AppInfo.width;
        cropHeight = AppInfo.height;
        setContentView(R.layout.activity_capture);
        getViews();
        initViews();
        setListeners();
        setupDevice();
    }

    @Override
    protected void onClickBack() {
        finish();
    }


    protected void getViews() {
        save = (TextView) findViewById(R.id.save);
        notSave = (TextView) findViewById(R.id.notSave);
        showImage = (ImageView) findViewById(R.id.showImage);
        bnCapture = (ImageView) findViewById(R.id.bnCapture);
        framelayoutPreview = (FrameLayout) findViewById(R.id.cameraPreview);
        focuseView = findViewById(R.id.viewFocuse);
        degree = (TextView) findViewById(R.id.degree);
        degreeBar = (TimeCircleSelector) findViewById(R.id.degreeBar);
        line = findViewById(R.id.line);
        degreeBar.setAdapter(new TimeCircleSelector.TimeAdapter() {
            @Override
            public int getCount() {
                return 100;
            }

            @Override
            public String getNameByPosition(int position) {
                return null;
            }

            @Override
            public void tempDegree(float degree) {
                degreeValue = degree;
                ActivityCapture.this.degree.setText("当前角度：" + degree);
            }
        });

    }

    protected void initViews() {

    }

    protected void setListeners() {
        save.setOnClickListener(this);
        notSave.setOnClickListener(this);
        bnCapture.setOnClickListener(this);
        observer.setRefocuseListener(this);
        pictureCallBack = new PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                _isCapturing = false;
                Bitmap bitmap = null;
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    options.inJustDecodeBounds = false;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    //此处就把图片压缩了
                    options.inSampleSize = Math.max(options.outWidth / kPhotoMaxSaveSideLen, options.outHeight / kPhotoMaxSaveSideLen);
                    bitmap = BitmapUtil.decodeByteArrayUnthrow(data, options);

                    if (null == bitmap) {
                        options.inSampleSize = Math.max(2, options.inSampleSize * 2);
                        bitmap = BitmapUtil.decodeByteArrayUnthrow(data, options);
                    }

                } catch (Throwable e) {
                    e.printStackTrace();
                }
                if (null == bitmap) {
                    Toast.makeText(ActivityCapture.this, "内存不足，保存照片失败！", Toast.LENGTH_SHORT).show();
                    return;
                }
                Bitmap addBitmap = BitmapUtil.rotateAndScale(bitmap, _rotation, kPhotoMaxSaveSideLen);

                finalBitmap = BitmapUtil.cropPhotoImage(addBitmap, cropWidth, cropHeight);

//                if (makeWaterMark) {
//                    Bitmap waterMarkBitmap = BitmapUtil.loadFromAssets(ActivityCapture.this, "higo_water_mark.png", 1, Bitmap.Config.ARGB_8888);
//                    finalBitmap = BitmapUtil.makeWaterMark(finalBitmap, waterMarkBitmap);
//                }

                photoFile = getOutputMediaFile();

                if (photoFile == null) {
                    return;
                }
                save.setVisibility(View.VISIBLE);
                boolean successful = BitmapUtil.saveBitmap2file(finalBitmap, photoFile, Bitmap.CompressFormat.JPEG, 100);

                while (!successful) {
                    successful = BitmapUtil.saveBitmap2file(finalBitmap, photoFile, Bitmap.CompressFormat.JPEG, 100);
                }
                displayCropImage();
            }
        };
        focusCallback = new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean successed, Camera camera) {
                focuseView.setVisibility(View.INVISIBLE);
            }
        };
    }


    private static File getOutputMediaFile() {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "com.nice.jzs");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.
        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("com.nice.jzs", "failed to create directory");
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "IMG_" + timeStamp + ".jpg");
        return mediaFile;
    }

    @Override
    protected void onDestroy() {
        if (null != observer) {
            observer.setRefocuseListener(null);
            observer = null;
        }
        _orientationEventListener = null;
        if (finalBitmap != null && !finalBitmap.isRecycled()) {
            finalBitmap.recycle();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera(); // release the camera immediately on pause event

        observer.stop();
        _orientationEventListener.disable();
    }

    @Override
    protected void onResume() {
        super.onResume();
        openCamera();
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.release(); // release the camera for other applications
            camera = null;
        }

        if (null != preview) {
            framelayoutPreview.removeAllViews();
            preview = null;
        }
    }

//    private void openCameraLight() {
//        //直接开启
//        if (camera != null) {
//            Camera.Parameters parameters = camera.getParameters();
//            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);//开启
//            camera.setParameters(parameters);
//        }
//    }

//    private void closeCameraLight() {
//        //直接关闭
//        if (camera != null) {
//            Camera.Parameters parameters = camera.getParameters();
//            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);//开启
//            camera.setParameters(parameters);
//        }
//    }

    private void displayCropImage() {
        if (finalBitmap != null) {
            showImage.setImageBitmap(finalBitmap);
            showImage.setVisibility(View.VISIBLE);
            bnCapture.setVisibility(View.INVISIBLE);
            save.setVisibility(View.VISIBLE);
            notSave.setVisibility(View.VISIBLE);
            degreeBar.setVisibility(View.INVISIBLE);
            line.setVisibility(View.INVISIBLE);
        }
    }

    private void hideDisplayCropImage() {
        save.setVisibility(View.INVISIBLE);
        showImage.setVisibility(View.INVISIBLE);
        bnCapture.setVisibility(View.VISIBLE);
        save.setVisibility(View.INVISIBLE);
        notSave.setVisibility(View.INVISIBLE);
        degreeBar.setVisibility(View.VISIBLE);
        line.setVisibility(View.VISIBLE);
        if (finalBitmap != null && !finalBitmap.isRecycled()) {
            finalBitmap.recycle();
        }
        camera.startPreview();
    }


    private void setupDevice() {
        if (android.os.Build.VERSION.SDK_INT > 8) {
            int cameraCount = Camera.getNumberOfCameras();

            if (cameraCount < 1) {
                Toast.makeText(this, "你的设备木有摄像头。。。", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            currentCameraId = 0;
            frontCameraId = findFrontFacingCamera();
        }
    }

    private void openCamera() {
        if (android.os.Build.VERSION.SDK_INT > 8) {
            try {
                camera = Camera.open(currentCameraId);
            } catch (Exception e) {
                Toast.makeText(this, "摄像头打开失败", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            setCameraDisplayOrientation(this, 0, camera);
        } else {
            try {
                camera = Camera.open();
            } catch (Exception e) {
                Toast.makeText(this, "摄像头打开失败", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        Camera.Parameters camParmeters = camera.getParameters();
        List<Size> sizes = camParmeters.getSupportedPreviewSizes();
        for (Size size : sizes) {
            Log.v(TAG, "w:" + size.width + ",h:" + size.height);
        }
        preview = new CameraPreview(this, camera);
        FrameLayout.LayoutParams params1 = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        framelayoutPreview.addView(preview, params1);
        observer.start();
        _orientationEventListener.enable();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bnCapture:
                bnCaptureClicked();
                break;
            case R.id.save: {
                onClickSave();
            }
            break;
            case R.id.notSave: {
                hideDisplayCropImage();
            }
            break;
        }
    }

    private void onClickSave() {
        if (photoFile == null) {
            return;
        }

        uploadPhotoPath = photoFile.getPath();
        new NiceAsyncTask(false) {

            @Override
            public void loadSuccess(BaseBean bean) {
                UploadImgBean imgBean = (UploadImgBean) bean;
                if (imgBean.getData() != null && !TextUtils.isEmpty(imgBean.getData().getUrl())) {
                    ActivityZichaResult_.intent(ActivityCapture.this).degree(String.valueOf(degreeValue)).url(imgBean.getData().getUrl()).start();
                    finish();
                } else {
                    ToastUtil.showToastMessage(ActivityCapture.this, "上传图片失败");
                }
            }

            @Override
            public void exception() {

            }
        }.updaloadImage(uploadPhotoPath, "file", UploadImgBean.class);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    //横竖屏切换的时候
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

//    private void switchCamera() {
//        if (currentCameraId == 0) {
//            currentCameraId = frontCameraId;
//        } else {
//            currentCameraId = 0;
//        }
//        releaseCamera();
//        openCamera();
//    }

    private void bnCaptureClicked() {
        if (_isCapturing) {
            return;
        }
        _isCapturing = true;
        focuseView.setVisibility(View.INVISIBLE);

        try {
            camera.takePicture(null, null, pictureCallBack);
        } catch (RuntimeException e) {
            e.printStackTrace();
            _isCapturing = false;
        }
    }


    /**
     * A basic Camera preview class
     */
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera mCamera;

        @SuppressWarnings("deprecation")
        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
            // deprecated setting, but required on Android versions prior to 3.0
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw
            // the preview.
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (Exception e) {
                Log.d(TAG, "Error setting camera preview: " + e.getMessage());
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // empty. Take care of releasing the Camera preview in your
            // activity.
            if (camera != null) {
                camera.setPreviewCallback(null);
                camera.stopPreview();// 停止预览
                camera.release(); // 释放摄像头资源
                camera = null;
            }
        }

        private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
            final double ASPECT_TOLERANCE = 0.05;
            double targetRatio = (double) w / h;
            if (sizes == null)
                return null;

            Size optimalSize = null;
            double minDiff = Double.MAX_VALUE;

            int targetHeight = h;

            // Try to find an size match aspect ratio and size
            for (Size size : sizes) {
                double ratio = (double) size.width / size.height;
                if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                    continue;
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }

            // Cannot find the one match the aspect ratio, ignore the
            // requirement
            if (optimalSize == null) {
                minDiff = Double.MAX_VALUE;
                for (Size size : sizes) {
                    if (Math.abs(size.height - targetHeight) < minDiff) {
                        optimalSize = size;
                        minDiff = Math.abs(size.height - targetHeight);
                    }
                }
            }

            return optimalSize;
        }

        private Size getOptimalPictureSize(List<Size> sizes, double targetRatio) {
            final double ASPECT_TOLERANCE = 0.05;

            if (sizes == null)
                return null;

            Size optimalSize = null;
            int optimalSideLen = 0;
            double optimalDiffRatio = Double.MAX_VALUE;

            for (Size size : sizes) {

                int sideLen = Math.max(size.width, size.height);
                //LogEx.i("size.width: " + size.width + ", size.height: " + size.height);
                boolean select = false;
                if (sideLen < kPhotoMaxSaveSideLen) {
                    if (0 == optimalSideLen || sideLen > optimalSideLen) {
                        select = true;
                    }
                } else {
                    if (kPhotoMaxSaveSideLen > optimalSideLen) {
                        select = true;
                    } else {
                        double diffRatio = Math.abs((double) size.width / size.height - targetRatio);
                        if (diffRatio + ASPECT_TOLERANCE < optimalDiffRatio) {
                            select = true;
                        } else if (diffRatio < optimalDiffRatio + ASPECT_TOLERANCE && sideLen < optimalSideLen) {
                            select = true;
                        }
                    }
                }

                if (select) {
                    optimalSize = size;
                    optimalSideLen = sideLen;
                    optimalDiffRatio = Math.abs((double) size.width / size.height - targetRatio);
                }
            }

            return optimalSize;
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // If your preview can change or rotate, take care of those events
            // here.
            // Make sure to stop the preview before resizing or reformatting it.

            Debug.debug("surfaceChanged format:" + format + ", w:" + w + ", h:" + h);
            if (mHolder.getSurface() == null) {
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                mCamera.stopPreview();
            } catch (Exception e) {
                // ignore: tried to stop a non-existent preview
            }

            try {
                Camera.Parameters parameters = mCamera.getParameters();

                List<Size> sizes = parameters.getSupportedPreviewSizes();
                Size optimalSize = getOptimalPreviewSize(sizes, w, h);
                parameters.setPreviewSize(optimalSize.width, optimalSize.height);
                parameters.setPictureSize(AppInfo.height, AppInfo.width);
                parameters.setRotation(0);
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                Debug.debug(e.toString());
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here

            // start preview with new settings
            try {
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();
            } catch (Exception e) {
                Debug.debug("Error starting camera preview: " + e.getMessage());
            }
        }
    }

    private int findFrontFacingCamera() {
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                Log.d(TAG, "Camera found");
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    private static void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        CameraInfo info = new CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        //LogEx.i("result: " + result);
        camera.setDisplayOrientation(result);
    }

    @Override
    public void needFocuse() {

        //LogEx.i("_isCapturing: " + _isCapturing);
        if (null == camera || _isCapturing) {
            return;
        }

        if (showImage.getVisibility() == View.VISIBLE) {
            return;
        }

        //LogEx.i("autoFocus");
        camera.cancelAutoFocus();
        try {
            camera.autoFocus(focusCallback);
        } catch (Exception e) {
            Debug.debug(e.toString());
            return;
        }

        if (View.INVISIBLE == focuseView.getVisibility()) {
            focuseView.setVisibility(View.VISIBLE);
            focuseView.getParent().requestTransparentRegion(preview);
        }
    }

    //相机旋转监听的类，最后保存图片时用到
    private class CaptureOrientationEventListener extends OrientationEventListener {
        public CaptureOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (null == camera)
                return;
            if (orientation == ORIENTATION_UNKNOWN)
                return;

            orientation = (orientation + 45) / 90 * 90;
            if (android.os.Build.VERSION.SDK_INT <= 8) {
                _rotation = (90 + orientation) % 360;
                return;
            }

            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(currentCameraId, info);

            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                _rotation = (info.orientation - orientation + 360) % 360;
            } else { // back-facing camera
                _rotation = (info.orientation + orientation) % 360;
            }
        }
    }

}
