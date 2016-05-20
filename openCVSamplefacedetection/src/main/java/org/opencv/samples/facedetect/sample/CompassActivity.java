package org.opencv.samples.facedetect.sample;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Bundle;

import org.opencv.samples.facedetect.utils.SensorHelper;
import org.opencv.samples.facedetect.views.CompassView;

/**
 * Created by hardik on 20/05/16.
 */
public class CompassActivity extends Activity implements SensorEventListener {
    CompassView compassView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        compassView = new CompassView(this);
        setContentView(compassView);
        SensorHelper sensorHelper = new SensorHelper(Sensor.TYPE_ORIENTATION);
        sensorHelper.initialiseSensorManager(this, this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // angle between the magnetic north direction
        // 0=North, 90=East, 180=South, 270=West
        float angle = event.values[0];
        compassView.updateData(angle);

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
