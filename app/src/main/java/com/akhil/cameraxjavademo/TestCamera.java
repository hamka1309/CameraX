package com.akhil.cameraxjavademo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Display;
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
    private int REQUEST_CODE_PERMISSIONS = 1001;

    private final String FILENAME = "yyyyMMddHHmmss";
    private final String PHOTO_EXTENSION = ".jpg";
    @BindView(R.id.previewView)
    PreviewView previewView;
    Preview preview;
    ProcessCameraProvider cameraProvider;
    @BindView(R.id.iv_image_preview)
    ImageView ivImagePreview;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalyzer;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private File outputDirectory;
    private int ratio = AspectRatio.RATIO_4_3;
    private PreviewConfig previewConfig;
    private int turnOnSplash = ImageCapture.FLASH_MODE_OFF;
    private boolean isTime = false;

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
            new CountDownTimer(5000, 1000) {
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

        if (ratio == AspectRatio.RATIO_4_3) {
            ratio = AspectRatio.RATIO_16_9;
        } else {
            ratio = AspectRatio.RATIO_4_3;
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
        preview = new Preview.Builder()
                .setTargetAspectRatio(ratio)
                .build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;
        Log.i("hadtt", "bindPreview: "+width+"----"+height);


        imageCapture = new ImageCapture.Builder()
                .setFlashMode(turnOnSplash)
                .build();
        imageAnalyzer = new ImageAnalysis.Builder().build();
        cameraProvider.unbindAll();

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            preview.setSurfaceProvider(previewView.createSurfaceProvider());
            Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer);

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


}
