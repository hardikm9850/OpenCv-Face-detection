package org.opencv.samples.facedetect;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.samples.facedetect.views.AnimatedView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

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
    int stickerId = 100;
    CvCameraViewFrame bufferCameraFrame;
    Bitmap stickerBitmap;
    Mat stickerMat;
    UpdateStickerRunnable stickerRunnable;
    int numberOfDetectedFaces = -1;
    ArrayList<Integer> stickerViewIdList;
    int counter = 0;

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
        stickerView = findViewById(R.id.sticker_view);
        visibilityRunnable = new StickerVisibilityRunnable();
        stickerBitmap = getBitmapFromSticker();
        stickerRunnable = new UpdateStickerRunnable();
        stickerViewIdList = new ArrayList<>();
    }

    private void getMatFromBitmap() {
        Utils.bitmapToMat(stickerBitmap, stickerMat
        );
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
        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
            mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
        }

        if (counter % 5 == 0) {
            MatOfRect faces = new MatOfRect();

            detectFace(faces);

            detectEyes(faces);
        }
        counter++;
        bufferCameraFrame = inputFrame;
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

    private void onImageCaptureClicked() {

        Bitmap mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.but_twitter);
        Mat myMat = new Mat();
        Utils.bitmapToMat(mBitmap, myMat);

        Utils.matToBitmap(myMat, mBitmap);

        Canvas canvas = mOpenCvCameraView.getHolder().lockCanvas();
        canvas.drawBitmap(mBitmap, 0f, 0f, null);
        mOpenCvCameraView.getHolder().unlockCanvasAndPost(canvas);
        //Mat captureMat = bufferCameraFrame.gray();
        /*MatOfRect faces = new MatOfRect();

        if (mJavaDetector != null) //CascadeClassifier file
            mJavaDetector.detectMultiScale(captureMat, faces, 1.1, 6, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());

        Rect[] facesArray = faces.toArray();
        if (facesArray.length > 0) {
            for (int i = 0; i < facesArray.length; i++) {
                Imgproc.rectangle(captureMat, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);
                surfaceViewCallback.drawOverCanvas(stickerBitmap, facesArray[i].tl(), facesArray[i]);
            }
        }
        Canvas canvas = mOpenCvCameraView.getHolder().lockCanvas();
        canvas.drawBitmap(stickerBitmap,0f,0f,null);*/
    }

    @SuppressLint("LongLogTag")
    public void detectFace(MatOfRect faces) {
        if (mJavaDetector != null) //CascadeClassifier file
            mJavaDetector.detectMultiScale(mGray, faces, 1.4, 4, 2,
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        final Rect[] facesArray = faces.toArray();
        stickerRunnable = new UpdateStickerRunnable();
        if (facesArray.length > 0) { //At least 1 face is present
            if (numberOfDetectedFaces == facesArray.length) {
                stickerRunnable.setNewFacesDetected(false);
                Log.d("@@@@ setNewFacesDetected ", "false");
            } else {
                //Draw sticker over face
                stickerRunnable.setNewFacesDetected(true);
                Log.d("@@@@ setNewFacesDetected ", "true");
            }
            for (int i = 0; i < facesArray.length; i++) {
                Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);

                stickerRunnable.setParams(Double.valueOf(facesArray[i].tl().x).floatValue(),
                        Double.valueOf(facesArray[i].tl().y).floatValue());
                runOnUiThread(stickerRunnable);
            }
            numberOfDetectedFaces = facesArray.length;
            Log.d("@@@@ numberOfDetectedFaces" + numberOfDetectedFaces, "facesArray.length " + facesArray.length);
        } else if (numberOfDetectedFaces != -1) {
            stickerRunnable.resetViews(true);
            numberOfDetectedFaces = -1;
            Log.d("@@@@ resetViews ", "");
            runOnUiThread(stickerRunnable);
        }
    }

    @SuppressWarnings("ResourceType")
    class UpdateStickerRunnable implements Runnable {
        private float x, y;
        private boolean resetViews;


        private Rect[] facesArray;

        boolean newFacesDetected = true;

        @Override
        public void run() {
            ViewGroup parentView = (ViewGroup) findViewById(R.id.root_face_detetctor);

            if (resetViews) {
                for (int viewId : stickerViewIdList) {
                    parentView.removeView(findViewById(viewId));
                }
                resetViews = false;
                stickerViewIdList.clear();
                Log.d("@@@@ clear views ", "343");
                return;
            }
            ImageView stickerView;
            if (newFacesDetected) {
                stickerView = new ImageView(getApplicationContext());
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(200, 200);
                stickerView.setLayoutParams(params);
                stickerId = org.opencv.samples.facedetect.utils.Utils.generateViewId() + 1;
                stickerViewIdList.add(stickerId);
                stickerView.setId(stickerId);
                stickerView.setImageDrawable(getResources().getDrawable(R.drawable.but_twitter));
                stickerView.setX(Float.valueOf(x).intValue());
                stickerView.setY(Float.valueOf(y).intValue());
                Log.d("@@@@ add view ", "360");
                parentView.addView(stickerView);
            } else {
                for (int viewId : stickerViewIdList) {
                    stickerView = (ImageView) findViewById(viewId);
                    stickerView.setX(Float.valueOf(x).intValue());
                    stickerView.setY(Float.valueOf(y).intValue());
                    Log.d("#### x "+Float.valueOf(x).intValue()," y "+Float.valueOf(y).intValue());
                }
            }
        }

        public void setParams(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public void resetViews(boolean reset) {
            resetViews = reset;
        }

        public void setNewFacesDetected(boolean newFacesDetected) {
            this.newFacesDetected = newFacesDetected;
        }

        public void setFacesArray(Rect[] facesArray) {
            this.facesArray = facesArray;
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
