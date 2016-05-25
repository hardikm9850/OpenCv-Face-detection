package org.opencv.samples.facedetect.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Created by hardik on 20/05/16.
 */
public class AnimatedView extends ImageView {

    static final int width = 350;
    static final int height = 350;
    ShapeDrawable mDrawable = new ShapeDrawable();
    public static int x;
    public static int y;

    public AnimatedView(Context context) {
        super(context);

        mDrawable = new ShapeDrawable(new OvalShape());
        mDrawable.getPaint().setColor(0xffffAC23);
        mDrawable.setBounds(x, y, x + width, y + height);
    }

    public AnimatedView(Context context, AttributeSet attrs) {
        super(context, attrs);

    }

    public AnimatedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

    }

    public void setBounds(int x,int y){
        this.x = x;
        this.y = y;
    }

    @Override
    protected void onDraw(Canvas canvas) {

        mDrawable.setBounds(x, y, x + width, y + height);
        mDrawable.draw(canvas);
        //invalidate();
    }

    public void invalidateView(){
        invalidate();
    }
}