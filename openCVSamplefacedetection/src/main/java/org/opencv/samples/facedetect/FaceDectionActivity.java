package org.opencv.samples.facedetect;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.samples.facedetect.views.AnimatedView;
import org.opencv.samples.facedetect.views.StickerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FaceDectionActivity extends Activity implements CvCameraViewListener2, SensorEventListener {

    private static final String TAG = "OCVSample::Activity";
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    public static final int JAVA_DETECTOR = 0;
    public static final int NATIVE_DETECTOR = 1;

    private Mat mRgba;
    private Mat mGray;
    private File mCascadeFile;
    private CascadeClassifier mJavaDetector;
    private CascadeClassifier mEyeDetector;
    private DetectionBasedTracker mNativeDetector;

    private int mDetectorType = JAVA_DETECTOR;
    private String[] mDetectorName;

    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;

    private CameraBridgeViewBase mOpenCvCameraView;

    private long lastUpdate = 0;
    private float last_x, last_y, last_z;
    AnimatedView animatedView;
    int x = 100, y = 100;
    View stickerView;
    long lastTime;
    StickerVisibilityRunnable visibilityRunnable;
    SurfaceViewCallback surfaceViewCallback;
    boolean flag = false;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("detection_based_tracker");

                    //Loading Face cascade
                    initialiseFaceDetector();

                    //Loading Eye cascade
                    initialiseEyeDetector();

                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public FaceDectionActivity() {
        mDetectorName = new String[2];
        mDetectorName[JAVA_DETECTOR] = "Java";
        mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.face_detect_surface_view);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        animatedView = (AnimatedView) findViewById(R.id.animated_view);
        stickerView = findViewById(R.id.sticker_view);
        visibilityRunnable = new StickerVisibilityRunnable();
        surfaceViewCallback = new SurfaceViewCallback();
        mOpenCvCameraView.getHolder().addCallback(surfaceViewCallback);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    /**
     * Receives camera frame
     *
     * @param inputFrame frame
     * @return
     */
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
        Long currentTime = System.currentTimeMillis();
        if (lastTime == 0) {
            lastTime = currentTime;
        }
        if (currentTime - lastTime > 500) {
            if (mAbsoluteFaceSize == 0) {
                int height = mGray.rows();
                if (Math.round(height * mRelativeFaceSize) > 0) {
                    mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
                }
                mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
            }

            MatOfRect faces = new MatOfRect();

            detectFace(faces);

            detectEyes(faces);

        }
        return mRgba;

    }




    private void initialiseFaceDetector() {
        try {
            // load cascade file from application resources
            InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default_2);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            if (mJavaDetector.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                mJavaDetector = null;
            } else
                Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

            mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);
            cascadeDir.delete();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }
    }

    private Bitmap getBitmapFromSticker() {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.but_twitter);
        return bitmap;
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            last_x = event.values[0];
            last_y = event.values[1];
            last_z = event.values[2];

            //Calculate time difference
            long curTime = System.currentTimeMillis();

            if ((curTime - lastUpdate) > 100) {
                lastUpdate = curTime;

            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void detectFace(MatOfRect faces) {
        if (mJavaDetector != null) //CascadeClassifier file
            mJavaDetector.detectMultiScale(mGray, faces, 1.1, 6, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        Rect[] facesArray = faces.toArray();
        UpdateStickerRunnable stickerRunnable = new UpdateStickerRunnable();

        if (facesArray.length > 0) {
            visibilityRunnable.setVisibility(true);
            runOnUiThread(visibilityRunnable);
            Bitmap stickerBitmap = getBitmapFromSticker();
            for (int i = 0; i < facesArray.length; i++) {
                Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);
                surfaceViewCallback.drawOverCanvas(stickerBitmap, facesArray[i].tl(), facesArray[i]);
                //stickerRunnable.setParams(new Double(facesArray[i].tl().x).floatValue(), new Double(facesArray[i].tl().y).floatValue());
                //runOnUiThread(stickerRunnable);
            }
        } else {
            visibilityRunnable.setVisibility(false);
            runOnUiThread(visibilityRunnable);
        }
    }


    public void detectEyes(MatOfRect faces) {
        Point topLeft, bottomRight, centerPoint;
        double x1, x2, y1, y2, xCenter, yCenter;

        if (mEyeDetector != null)
            mEyeDetector.detectMultiScale(mGray, faces, 1.1, 6, 2,
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());

        Rect[] facesArray = faces.toArray();

        for (int i = 0; i < facesArray.length; i++) {

            topLeft = facesArray[i].tl();
            bottomRight = facesArray[i].br();
            x1 = topLeft.x;
            y1 = topLeft.y;
            x2 = bottomRight.x;
            y2 = bottomRight.y;
            xCenter = (x1 + x2) / 2;
            yCenter = (y1 + y2) / 2;
            centerPoint = new Point(xCenter, yCenter);

            //    Imgproc.circle(mRgba, centerPoint, 30, FACE_RECT_COLOR, 5);
        }
    }


    class SurfaceViewCallback implements SurfaceHolder.Callback {

        Canvas canvas;
        SurfaceHolder surfaceHolder;

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            this.surfaceHolder = holder;
            Log.d("@@@@ ", "surfaceCreated");
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            this.surfaceHolder = holder;
            Log.d("@@@@ ", "surfaceChanged");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d("@@@@ ", "surfaceDestroyed");

        }


        public void drawOverCanvas(Bitmap stickerBitmap, Point stickerLocation, Rect rect) {
            //getStickerThread(surfaceHolder).setStickerLocation(location).start();
            Canvas canvas;
            canvas = surfaceHolder.lockCanvas();

            canvas.drawBitmap(stickerBitmap,
                    Double.valueOf(stickerLocation.x).floatValue(), Double.valueOf(stickerLocation.y).floatValue(),
                    null);

            surfaceHolder.unlockCanvasAndPost(canvas);
            mOpenCvCameraView.draw(canvas);
        }

    }






    public StickerThread getStickerThread(SurfaceHolder surfaceHolder) {
        StickerThread stickerThread = new StickerThread(surfaceHolder);
        return stickerThread;
    }

    private void initialiseEyeDetector() {
        System.loadLibrary("detection_based_tracker");

        try {
            // load cascade file from application resources
            InputStream is = getResources().openRawResource(R.raw.haarcascade_lefteye_2splits);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            mCascadeFile = new File(cascadeDir, "haarcascade_lefteye_2splits.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            mEyeDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            if (mEyeDetector.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                mEyeDetector = null;
            } else
                Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

            mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);
            cascadeDir.delete();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }
    }


    class StickerThread extends Thread {
        SurfaceHolder surfaceHolder;
        Point stickerLocation;

        public StickerThread(SurfaceHolder holder) {
            surfaceHolder = holder;
        }

        @Override
        public void run() {
            super.run();
            Canvas canvas = surfaceHolder.lockCanvas();
            canvas.drawBitmap(getBitmapFromSticker(),
                    new Double(stickerLocation.x).floatValue() + 100f, new Double(stickerLocation.y).floatValue() + 100f,
                    null);

            //mOpenCvCameraView.draw(canvas);
            surfaceHolder.unlockCanvasAndPost(canvas);
        }

        public StickerThread setStickerLocation(Point location) {
            stickerLocation = location;
            return this;
        }
    }

    /*public void SaveImage (Mat mat) {
        Mat mIntermediateMat = new Mat();

        Imgproc.cvtColor(mRgba, mIntermediateMat, Imgproc.COLOR_RGBA2BGR, 3);

        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        String filename = "barry.png";
        File file = new File(path, filename);

        Boolean bool = null;
        filename = file.toString();

        bool = Highgui.imwrite(filename, mIntermediateMat);

        if (bool == true)
            Log.d(TAG, "SUCCESS writing image to external storage");
        else
            Log.d(TAG, "Fail writing image to external storage");
    }*/

    class UpdateStickerRunnable implements Runnable {
        float x, y;

        @Override
        public void run() {
            //animatedView.invalidateView();
            stickerView.setX(x);
            stickerView.setY(y);
            stickerView.invalidate();
        }

        public void setParams(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    class StickerVisibilityRunnable implements Runnable {
        boolean showView;

        @Override
        public void run() {
            //    stickerView.setVisibility(showView ? View.VISIBLE : View.GONE);
        }

        void setVisibility(boolean showView) {
            this.showView = showView;
        }

    }

    private void setMinFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }

    private void setDetectorType(int type) {
        if (mDetectorType != type) {
            mDetectorType = type;

            if (type == NATIVE_DETECTOR) {
                Log.i(TAG, "Detection Based Tracker enabled");
                mNativeDetector.start();
            } else {
                Log.i(TAG, "Cascade detector enabled");
                mNativeDetector.stop();
            }
        }
    }
}
