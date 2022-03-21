package com.ms.opencvtest;

import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

import static android.media.MediaCodec.MetricsConstants.MIME_TYPE;
import static android.media.MediaPlayer.MetricsConstants.MIME_TYPE_AUDIO;

public class H264Encoder implements Runnable
{
    private int width=640;
    private int height=480;
    private int frameRate=25;
    private String TAG="H264Encoder";
    private long frameIndex=0;
    private int vTrackIndex;
    /**************************************************************************************/
    private BlockingQueue<Bitmap> dataBuffer;
    private boolean finish=false;
    private MediaCodec.BufferInfo mBufferInfo;
    private MediaCodec mediaCodec;
    private MediaFormat mediaFormat;
    private MediaMuxer mediaMuxer;

    public H264Encoder(BlockingQueue<Bitmap> dataBuffer)
    {
        this.dataBuffer = dataBuffer;
    }
    public void Stop()
    {
        this.finish = true;
    }
    private int CalcBitRate(double  coeff/*bit/pixel coefficient*/)
    {
        return (int)(this.height*this.width*this.frameRate*coeff);
    }
    private long computePresentationTime(long frameIndex)
    {
        return 132 + frameIndex * 1000000/this.frameRate;
    }
    private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ( (  66 * R + 129 * G +  25 * B + 128) >> 8) +  16;
                U = ( ( -38 * R -  74 * G + 112 * B + 128) >> 8) + 128;
                V = ( ( 112 * R -  94 * G -  18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte)((V<0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte)((U<0) ? 0 : ((U > 255) ? 255 : U));
                }

                index ++;
            }
        }
    }
    private byte [] getNV21(int inputWidth, int inputHeight, Bitmap scaled) {

        int [] argb = new int[inputWidth * inputHeight];

        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte [] yuv = new byte[inputWidth*inputHeight*3/2];
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);
        scaled.recycle();

        return yuv;
    }
    private void Prepare()
    {
        try {
            mBufferInfo = new MediaCodec.BufferInfo();

            mediaCodec = MediaCodec.createEncoderByType("video/avc");
            mediaFormat = MediaFormat.createVideoFormat("video/avc", this.width, this.height);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, CalcBitRate(0.109)/*16000000*/);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, this.frameRate);

            String colors="";
            for(int i=0;i<mediaCodec.getCodecInfo().getCapabilitiesForType("video/avc").colorFormats.length;i++)
            {
                colors=colors+ String.valueOf(mediaCodec.getCodecInfo().getCapabilitiesForType("video/avc").colorFormats[i])+"\r\n";
            }
            Log.d(TAG, "colors: "+colors);
            /*2130706944
            2130708361
            2130706944
            19
            6
            11
            16*/
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                //mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities./*COLOR_FormatYUV420SemiPlanar*/COLOR_FormatYUV420Planar);
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888);
            }else{
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            }
            //2130708361, 2135033992, 21

            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, /*IFRAME_INTERVAL*/1);

            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();

            try {
                String outputPath = new File(Environment.getExternalStorageDirectory(),
                        "test.mp4").toString();
                mediaMuxer = new MediaMuxer("/storage/emulated/0/Android/data/opencv.org/files/vid.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                /*vTrackIndex = mediaMuxer.addTrack(mediaFormat);
                mediaMuxer.start();*/
            } catch (IOException e)
            {
                Log.d(TAG, "MediaMuxer file error: "+ e.getMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void Encode(Bitmap frame)
    {
        int inputBufIndex = mediaCodec.dequeueInputBuffer(55000);
        if (inputBufIndex >= 0)
        {
            //byte[] input = getNV21(this.width, this.height, frame);
            final ByteBuffer inputBuffer = mediaCodec.getInputBuffers()[inputBufIndex];
            inputBuffer.clear();
            int[] argb=new int[this.width*this.height];
            frame.getPixels(argb, 0, this.width, 0, 0, this.width, this.height);
            inputBuffer.asIntBuffer().put(argb);
            //inputBuffer.put(input);
            mediaCodec.queueInputBuffer(inputBufIndex, 0, /*input.length*/this.width*this.height*4,
                    computePresentationTime(this.frameIndex), 0);
            this.frameIndex++;
        }

        MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
        int encoderStatus = mediaCodec.dequeueOutputBuffer(mBufferInfo, 55000);
        switch(encoderStatus)
        {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                MediaFormat newFormat = mediaCodec.getOutputFormat();
                if(newFormat!=null) {
                    vTrackIndex = mediaMuxer.addTrack(newFormat);
                    mediaMuxer.start();
                }
                break;
            default:
        }
        if(encoderStatus>-1) {
            ByteBuffer encodedData = mediaCodec.getOutputBuffers()[encoderStatus];
            if(encodedData!=null)
            {
                encodedData.position(mBufferInfo.offset);
                encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                mediaMuxer.writeSampleData(vTrackIndex, encodedData, mBufferInfo);
                mediaCodec.releaseOutputBuffer(encoderStatus, false);
            }
        }
    }
    private void FinishRecord()
    {
        if (mediaCodec != null) {

            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
        }
        Log.d(TAG, "stop encoding");
    }
    @Override
    public void run()
    {
        Bitmap frame=null;
        Prepare();
        for(;;)
        {
            if(finish==true)
            {
                FinishRecord();
                return;
            }
            try {
                frame=this.dataBuffer.take();
            } catch (InterruptedException e) {
                Log.d(TAG, "BlockingQueue::take error: "+ e.getMessage());
                finish=true;
            }
            if(frame!=null)
            {
                this.Encode(frame);
                Log.d("frame_size: ", String.valueOf(frame.getByteCount()));
                this.dataBuffer.remove(frame);
                frame.recycle();
            }
        }
    }
}