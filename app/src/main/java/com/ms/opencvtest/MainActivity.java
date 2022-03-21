package com.ms.opencvtest;

import static android.media.MediaCodec.MetricsConstants.MIME_TYPE;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.jcodec.api.SequenceEncoder;
import org.jcodec.api.android.AndroidSequenceEncoder;
import org.jcodec.common.io.SeekableByteChannel;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final java.lang.String TAG = "CameraActivity";
    private JavaCameraView cameraView;
    private Mat matrix;
    private Button btnRecord;
    private String path;
    private boolean isRecord = false;
    private MediaCodec mediaCodec;
    private MediaFormat mediaFormat;
    private MediaMuxer mediaMuxer;
    private MediaCodec.BufferInfo bufferInfo;
    private FileOutputStream fos;
    private int BPP = 8;
    private int FRAME_RATE = 30;
    private BlockingQueue<Bitmap> bitmaps;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCVLoader.initDebug();
        setContentView(R.layout.activity_main);
        cameraView = findViewById(R.id.surfaceView);
        btnRecord = findViewById(R.id.record);
        path = getExternalFilesDir("/").getAbsolutePath();
        Log.d(TAG, path);
        openCamera();

        btnRecord.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (view == btnRecord) {
                    //copy frames from circular buffer
                    CircularFifoQueue<byte[]> fifo = cameraView.frames;
                    if (!isRecord) {
                        isRecord = true;
                        // add frames to MediaCodec buffer
                        // start adding new byte[] frames from Camera to MediaCodec buffer
                    } else {
                        isRecord = false;
                        //stop adding frames
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

    private static byte[] YUV420toNV21(Image image) {
        android.graphics.Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    channelOffset = width * height + 1;
                    outputStride = 2;
                    break;
                case 2:
                    channelOffset = width * height;
                    outputStride = 2;
                    break;
            }

            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }

    public static Bitmap convertYUV(byte[] data, int width, int height, Rect crop) {
        if (crop == null) {
            crop = new Rect(0, 0, width, height);
        }
        Bitmap image = Bitmap.createBitmap(crop.width(), crop.height(), Bitmap.Config.ARGB_8888);
        int yv = 0, uv = 0, vv = 0;

        for (int y = crop.top; y < crop.bottom; y += 1) {
            for (int x = crop.left; x < crop.right; x += 1) {
                yv = data[y * width + x] & 0xff;
                uv = (data[width * height + (x / 2) * 2 + (y / 2) * width + 1] & 0xff) - 128;
                vv = (data[width * height + (x / 2) * 2 + (y / 2) * width] & 0xff) - 128;
                image.setPixel(x, y, convertPixel(yv, uv, vv));
            }
        }
        return image;
    }

    public static int convertPixel(int y, int u, int v) {
        int r = (int) (y + 1.13983f * v);
        int g = (int) (y - .39485f * u - .58060f * v);
        int b = (int) (y + 2.03211f * u);
        r = (r > 255) ? 255 : (r < 0) ? 0 : r;
        g = (g > 255) ? 255 : (g < 0) ? 0 : g;
        b = (b > 255) ? 255 : (b < 0) ? 0 : b;

        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
}




