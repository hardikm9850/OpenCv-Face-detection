package org.opencv.samples.facedetect.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Created by hardik on 20/05/16.
 */
public class SensorHelper {

    private SensorManager sensorManager;
    private Sensor sensorAccelerometer;
    private SensorEventListener sensorEventListener;
    private int sensorType = Sensor.TYPE_ACCELEROMETER;

    public void initialiseSensorManager(Context context, SensorEventListener eventListener) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorAccelerometer = sensorManager.getDefaultSensor(sensorType);
        sensorEventListener = eventListener;
        registerListener();
    }

    public SensorHelper(int sensorType) {
        this.sensorType = sensorType;
    }

    public void unregisterListener() {
        sensorManager.unregisterListener(sensorEventListener);
    }

    public void registerListener() {
        sensorManager.registerListener(sensorEventListener, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }
}
