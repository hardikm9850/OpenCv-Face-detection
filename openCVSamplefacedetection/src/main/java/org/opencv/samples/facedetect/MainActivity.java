package org.opencv.samples.facedetect;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import org.opencv.samples.facedetect.views.StickerView;

/**
 * Created by hardik on 19/05/16.
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StickerView stickerView = (StickerView) findViewById(R.id.sticker);
        stickerView.setWaterMark(BitmapFactory.decodeResource(getResources(), R.drawable.icon));
    }
}
