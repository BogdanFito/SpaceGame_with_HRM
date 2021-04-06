package com.Comany.SpaceGame;



import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;

import android.content.Context;

import android.content.res.Configuration;

import android.hardware.Camera;

import android.hardware.Camera.PreviewCallback;

import android.os.Build;
import android.os.Bundle;

import android.os.PowerManager;

import android.os.PowerManager.WakeLock;

import android.util.Log;

import android.view.SurfaceHolder;

import android.view.SurfaceView;

import android.view.View;

import android.widget.TextView;

import com.unity3d.player.UnityPlayer;


/**

 * This class extends Activity to handle a picture preview, process the preview

 * for a red values and determine a heart beat.

 *

 * @author Justin Wetherell <phishman3579@gmail.com>

 */

public class HeartRateMonitor extends Activity {



    private static final String TAG = "HeartRateMonitor";

    private static final AtomicBoolean processing = new AtomicBoolean(false);



    private static SurfaceView preview = null;

    private static SurfaceHolder previewHolder = null;

    private static Camera camera = null;

    private static View image = null;

    private static TextView text = null;



    private static WakeLock wakeLock = null;





    private static int averageIndex = 0;

    private static final int averageArraySize = 4;

    private static final int[] averageArray = new int[averageArraySize];



    public static enum TYPE {

        GREEN, RED

    };



    private static TYPE currentType = TYPE.GREEN;



    public static TYPE getCurrent() {

        return currentType;

    }



    private static int beatsIndex = 0;

    private static final int beatsArraySize = 3;

    private static final int[] beatsArray = new int[beatsArraySize];

    private static double beats = 0;

    private static long startTime = 0;

    private static boolean transiction = false;

    private static int beats_amount = 0;
    private static double stress_index = 0;
    private static ArrayList<Double> time_delay = new ArrayList<>();
    private static long start;



    /**

     * {@inheritDoc}

     */

    @SuppressLint("InvalidWakeLockTag")
    @Override

    public void onCreate(Bundle savedInstanceState) {

       // Log.d("hello", "create");
        start = System.currentTimeMillis();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);


        preview = (SurfaceView) findViewById(R.id.preview);

        previewHolder = preview.getHolder();

        previewHolder.addCallback(surfaceCallback);

        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);


        image = findViewById(R.id.image);

        text = (TextView) findViewById(R.id.text);


        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");

        new Thread(new Runnable() {

            public void run() {
                while (!transiction) {
                }

                if (beats_amount < 70) {
                    UnityPlayer.UnitySendMessage("Enemy", "maxspeed", "");
                    Log.d("pulse", beats_amount + " max speed");

                    transiction = false;
                } else if (beats_amount <= 90) {
                    UnityPlayer.UnitySendMessage("Enemy", "midspeed", "");
                    Log.d("pulse", beats_amount + " mid speed");

                    transiction = false;
                } else {
                    UnityPlayer.UnitySendMessage("Enemy", "minspeed", "");
                    Log.d("pulse", beats_amount + " min speed");

                    transiction = false;
                }
                double sum =0;
                for (double d : time_delay) {
                    sum+=d; //Log.d("intervals", d + "");
                }
                double MO = sum/time_delay.size();
                //Log.d("MO", MO + "");

                final double threshold = 0.1;
                int count = 0;
                for (double d : time_delay) {
                    if (Math.abs(d -  MO) < threshold) count++;
                }
                double AMO = (double) count/time_delay.size()*100; //Log.d("AMO", AMO + "");
                double VR = Collections.max(time_delay) - Collections.min(time_delay); //Log.d("VR", VR + "");

                stress_index = AMO / (2*VR * MO); //Рассчет индекса стресса
                Log.d("stress", stress_index + "");
                time_delay.clear();

                if (stress_index>150) finishAffinity(); //Закрываем приложение, если человек взволнован

                finish();
            }

        }).start();

    }



    /**

     * {@inheritDoc}

     */

    @Override

    public void onConfigurationChanged(Configuration newConfig) {

        super.onConfigurationChanged(newConfig);


    }



    /**

     * {@inheritDoc}

     */

    @Override

    public void onResume() {

        super.onResume();


        wakeLock.acquire();



        camera = Camera.open();



        startTime = System.currentTimeMillis();


    }



    /**

     * {@inheritDoc}

     */

    @Override

    public void onPause() {

        super.onPause();



        wakeLock.release();



        camera.setPreviewCallback(null);

        camera.stopPreview();

        camera.release();

        camera = null;

    }



    private static PreviewCallback previewCallback = new PreviewCallback() {



        /**

         * {@inheritDoc}

         */

        @TargetApi(Build.VERSION_CODES.N)
        @Override

        public void onPreviewFrame(byte[] data, Camera cam) {


            if (data == null) throw new NullPointerException();

            Camera.Size size = cam.getParameters().getPreviewSize();

            if (size == null) throw new NullPointerException();



            if (!processing.compareAndSet(false, true)) return;



            int width = size.width;

            int height = size.height;



            int imgAvg = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(), height, width);

            // Log.i(TAG, "imgAvg="+imgAvg);

            if (imgAvg == 0 || imgAvg == 255) {

                processing.set(false);

                return;

            }



            int averageArrayAvg = 0;

            int averageArrayCnt = 0;

            for (int i = 0; i < averageArray.length; i++) {

                if (averageArray[i] > 0) {

                    averageArrayAvg += averageArray[i];

                    averageArrayCnt++;

                }

            }



            int rollingAverage = (averageArrayCnt > 0) ? (averageArrayAvg / averageArrayCnt) : 0;


            TYPE newType = currentType;

            if (imgAvg < rollingAverage) {

                newType = TYPE.RED;

                if (newType != currentType) {

                    beats++;
                    time_delay.add((double)(System.currentTimeMillis() - start)/1000);
                    start = System.currentTimeMillis();

                    // Log.d(TAG, "BEAT!! beats="+beats);

                }

            } else if (imgAvg > rollingAverage) {

                newType = TYPE.GREEN;

            }



            if (averageIndex == averageArraySize) averageIndex = 0;

            averageArray[averageIndex] = imgAvg;

            averageIndex++;



            // Transitioned from one state to another to the same

            if (newType != currentType) {

                currentType = newType;

                image.postInvalidate();

            }



            long endTime = System.currentTimeMillis();

            double totalTimeInSecs = (endTime - startTime) / 1000d;

            if (totalTimeInSecs >= 60) {

                double bps = (beats / totalTimeInSecs);

                int dpm = (int) (bps * 60d);

                if (dpm < 30 || dpm > 180) {

                    startTime = System.currentTimeMillis();

                    beats = 0;

                    processing.set(false);

                    return;

                }



                // Log.d(TAG,

                // "totalTimeInSecs="+totalTimeInSecs+" beats="+beats);



                if (beatsIndex == beatsArraySize) beatsIndex = 0;

                beatsArray[beatsIndex] = dpm;

                beatsIndex++;



                int beatsArrayAvg = 0;

                int beatsArrayCnt = 0;

                for (int i = 0; i < beatsArray.length; i++) {

                    if (beatsArray[i] > 0) {

                        beatsArrayAvg += beatsArray[i];

                        beatsArrayCnt++;

                    }

                }

                int beatsAvg = (beatsArrayAvg / beatsArrayCnt);

                beats_amount = beatsAvg;



                text.setText(String.valueOf(beatsAvg));
                transiction = true;


                startTime = System.currentTimeMillis();

                beats = 0;



            }

            processing.set(false);

        }

    };



    private static SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {



        /**

         * {@inheritDoc}

         */

        @SuppressLint("LongLogTag")
        @Override

        public void surfaceCreated(SurfaceHolder holder) {

            try {

                camera.setPreviewDisplay(previewHolder);

                camera.setPreviewCallback(previewCallback);

            } catch (Throwable t) {

                Log.e("PreviewDemo-surfaceCallback", "Exception in setPreviewDisplay()", t);

            }

        }



        /**

         * {@inheritDoc}

         */

        @Override

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            Camera.Parameters parameters = camera.getParameters();

            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);

            Camera.Size size = getSmallestPreviewSize(width, height, parameters);

            if (size != null) {

                parameters.setPreviewSize(size.width, size.height);

                Log.d(TAG, "Using width=" + size.width + " height=" + size.height);

            }

            camera.setParameters(parameters);

            camera.startPreview();

        }



        /**

         * {@inheritDoc}

         */

        @Override

        public void surfaceDestroyed(SurfaceHolder holder) {

            // Ignore

        }

    };



    private static Camera.Size getSmallestPreviewSize(int width, int height, Camera.Parameters parameters) {

        Camera.Size result = null;



        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {

            if (size.width <= width && size.height <= height) {

                if (result == null) {

                    result = size;

                } else {

                    int resultArea = result.width * result.height;

                    int newArea = size.width * size.height;



                    if (newArea < resultArea) result = size;

                }

            }

        }



        return result;

    }

}