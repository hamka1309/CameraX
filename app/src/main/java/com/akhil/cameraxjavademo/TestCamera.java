package com.akhil.cameraxjavademo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.impl.PreviewConfig;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class TestCamera extends AppCompatActivity {

    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private final String FILENAME = "yyyyMMddHHmmss";
    private final String PHOTO_EXTENSION = ".jpg";
    @BindView(R.id.view_grid)
    View viewGridContainer;
    @BindView(R.id.previewView)
    PreviewView previewView;
    Preview preview;
    ProcessCameraProvider cameraProvider;
    @BindView(R.id.iv_image_preview)
    ImageView ivImagePreview;
    @BindView(R.id.iv_show_image)
    ImageView ivShowImage;
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalyzer;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private File outputDirectory;
    private int ratio = AspectRatio.RATIO_4_3;
    private String ratioNumber = "3:4";
    private int widthPreview = 720;
    private int heightPreview = 960;
    private PreviewConfig previewConfig;
    private int turnOnSplash = ImageCapture.FLASH_MODE_OFF;
    private boolean isTime = false;
    private boolean isGrib = false;
    private CountDownTimer countDownTimer;
    private Camera camera;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

//        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            @Override
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                camera.getCameraControl().setLinearZoom((float) progress / 100);
//            }
//
//            @Override
//            public void onStartTrackingTouch(SeekBar seekBar) {
//
//            }
//
//            @Override
//            public void onStopTrackingTouch(SeekBar seekBar) {
//
//            }
//        });

        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
                float v = camera.getCameraInfo().getZoomState().getValue().getZoomRatio() * detector.getScaleFactor();
                camera.getCameraControl().setZoomRatio(v);
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }
        });


        previewView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleGestureDetector.onTouchEvent(event);
                return true;
            }
        });

    }


    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        outputDirectory = getOutputDirectory(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));

        OrientationEventListener orientationEventListener = new OrientationEventListener((Context) this) {
            @Override
            public void onOrientationChanged(int orientation) {
                int rotation;

                // Monitors orientation values to determine the target rotation value
                if (orientation >= 45 && orientation < 135) {
                    rotation = Surface.ROTATION_270;
                } else if (orientation >= 135 && orientation < 225) {
                    rotation = Surface.ROTATION_180;
                } else if (orientation >= 225 && orientation < 315) {
                    rotation = Surface.ROTATION_90;
                } else {
                    rotation = Surface.ROTATION_0;
                }

                imageCapture.setTargetRotation(rotation);
            }
        };

        orientationEventListener.enable();
    }

    @OnClick(R.id.camera_switch_button)
    public void onViewClicked() {
        if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
            lensFacing = CameraSelector.LENS_FACING_BACK;
        } else {
            lensFacing = CameraSelector.LENS_FACING_FRONT;
        }
        bindPreview();
    }

    @OnClick(R.id.captureImg)
    public void onViewClickedCaptureImg() {
        if (isTime) {

            countDownTimer = new CountDownTimer(5000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {

                }

                @Override
                public void onFinish() {
                    takeAPhoto();
                }
            }.start();
        } else {
            takeAPhoto();
        }

    }

    @OnClick(R.id.iv_splash)
    public void onViewClickedTimer() {
        if (turnOnSplash == ImageCapture.FLASH_MODE_OFF) {
            turnOnSplash = ImageCapture.FLASH_MODE_AUTO;
        } else if (turnOnSplash == ImageCapture.FLASH_MODE_AUTO) {
            turnOnSplash = ImageCapture.FLASH_MODE_ON;
        } else {
            turnOnSplash = ImageCapture.FLASH_MODE_OFF;
        }
        imageCapture.setFlashMode(turnOnSplash);
    }

    @OnClick(R.id.iv_ratio)
    public void onViewClickedRadio() {
        //3:4 720:960
        //9:16 720:1280
        //1:1 720 720


        if (TextUtils.equals(ratioNumber, "1:1")) {
            ratio = AspectRatio.RATIO_4_3;
            ratioNumber = "4:3";
            widthPreview = 720;
            heightPreview = 960;
        } else if (TextUtils.equals(ratioNumber, "4:3")) {
            ratio = AspectRatio.RATIO_16_9;
            ratioNumber = "16:9";
            widthPreview = 720;
            heightPreview = 1280;
        } else {
            ratioNumber = "1:1";
            ratio = AspectRatio.RATIO_4_3;
            widthPreview = 720;
            heightPreview = 720;
        }

        bindPreview();
    }

    @OnClick(R.id.iv_time)
    public void onViewClickedTime() {

        if (isTime) {
            isTime = false;
        } else {
            isTime = true;
        }

    }

    private File getOutputDirectory(Context context) {
        Log.i("hadtt", "getOutputDirectory: " + Environment.getExternalStorageState());
        Log.i("hadtt", "getOutputDirectory: " + Environment.getRootDirectory().toString());
        File[] externalMediaDirs = context.getApplicationContext().getExternalMediaDirs();

        if (externalMediaDirs != null && externalMediaDirs.length > 0) {
            File file = new File(externalMediaDirs[0].getPath(), "CameraXDemo");
            if (!file.exists()) {
                file.mkdirs();
            }
            return file;
        }
        return context.getFilesDir();
//        return Environment.getRootDirectory();
    }

    private void bindPreview() {

        Log.i("hadtt", "bindPreview: " + widthPreview + "---" + heightPreview);
        this.setAspectRatioTextureView(widthPreview, heightPreview);
        int rotation = (int) previewView.getRotation();
        preview = new Preview.Builder()
                .setTargetAspectRatio(ratio)
                .setTargetRotation(rotation)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;
        Log.i("hadtt", "bindPreview: " + width + "----" + height);


        imageCapture = new ImageCapture.Builder()
                .setFlashMode(turnOnSplash)
                .setTargetAspectRatio(ratio)
                .setTargetRotation(rotation)
                .build();

        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(ratio)
                .setTargetRotation(rotation)
                .build();
        cameraProvider.unbindAll();

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            preview.setSurfaceProvider(previewView.createSurfaceProvider());
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private File createFile(File baseFolder, String fomat, String extension) {

        return new File(baseFolder, new SimpleDateFormat(fomat, Locale.US).format(System.currentTimeMillis()) + extension);
    }


    private void takeAPhoto() {
        File file = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION);

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file)
                .build();

        imageCapture.takePicture(outputFileOptions, executor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {

                String saveUri = outputFileOptions.toString();
                Log.i("hadtt", "Photo capture succeeded: $savedUri" + Uri.fromFile(file));
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.i("hadtt", "onError: ");
            }
        });
    }

    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }


    @OnClick(R.id.iv_grib)
    public void onViewClickedGrib() {
        if (isGrib) {
            isGrib = false;
            viewGridContainer.setVisibility(View.GONE);
        } else {
            isGrib = true;
            viewGridContainer.setVisibility(View.VISIBLE);
        }
    }

    private void setAspectRatioTextureView(int ResolutionWidth, int ResolutionHeight) {

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int DSI_height = displayMetrics.heightPixels;
        int DSI_width = displayMetrics.widthPixels;
        Log.i("hadtt", "setAspectRatioTextureView: " + DSI_height + "--" + DSI_width);
        if (ResolutionWidth > ResolutionHeight) {
            int newWidth = DSI_width;
            int newHeight = ((DSI_width * ResolutionWidth) / ResolutionHeight);
            updateTextureViewSize(newWidth, newHeight);

        } else {
            int newWidth = DSI_width;
            int newHeight = ((DSI_width * ResolutionHeight) / ResolutionWidth);
            updateTextureViewSize(newWidth, newHeight);
        }

    }

    private void updateTextureViewSize(int viewWidth, int viewHeight) {
        Log.i("hadtt", "TextureView Width : " + viewWidth + " TextureView Height : " + viewHeight);
        previewView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewHeight));
    }

}
