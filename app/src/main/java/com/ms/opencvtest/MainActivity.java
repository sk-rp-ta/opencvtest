package com.ms.opencvtest;

import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final java.lang.String TAG = "CameraActivity";
    private JavaCameraView cameraView;
    private Mat matrix;
    private Button btnRecord;
    private String path;
    private boolean isRecord = false;
    private int BPP = 8;
    private int FRAME_RATE = 30;
    private int generateIndex = 0;
    private int mHeight = 720;
    private int mWidth = 1280;
    private int mBitRate = 2000000;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCVLoader.initDebug();
        setContentView(R.layout.activity_main);
        cameraView = findViewById(R.id.surfaceView);
        btnRecord = findViewById(R.id.record);
        cameraView.path = getExternalFilesDir("/").getAbsolutePath();

        openCamera();
        btnRecord.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (view == btnRecord) {
                    if(!cameraView.Record){
                        cameraView.setEncoder();
                        cameraView.Record = true;
                    }
                    else{
                        cameraView.Record = false;
                    }
                }
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();

        if (hasFocus) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }


    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        matrix = inputFrame.rgba();
        return matrix;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        matrix = new Mat(width, height, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        matrix.release();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (cameraView != null) {
            cameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (cameraView != null) {
            cameraView.disableView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(getApplicationContext(),
                    "Unable to load OpenCV", Toast.LENGTH_SHORT).show();
        } else if (cameraView != null) {
            cameraView.enableView();
        }
    }

    private void openCamera() {
        cameraView.setMaxFrameSize(mWidth,mHeight);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setCvCameraViewListener(this);
        cameraView.enableView();
    }

    private static byte[] NV21toJPEG(byte[] nv21, int width, int height, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new android.graphics.Rect(0, 0, width, height), quality, out);
        return out.toByteArray();
    }

    public void capturePhoto() {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(path + "/photo.jpg");
            Log.d(TAG, "fos setting");
            fos.write(NV21toJPEG(cameraView.frames.get(0), 1920, 1080, 50));
            Log.d(TAG, "writing");
            fos.close();
            Log.d(TAG, "closing");
            Toast.makeText(getApplicationContext(), "saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "not saved", Toast.LENGTH_SHORT).show();
        }
    }

    private long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }
}




