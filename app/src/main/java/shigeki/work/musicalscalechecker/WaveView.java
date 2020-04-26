package shigeki.work.musicalscalechecker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.nio.FloatBuffer;

public class WaveView extends View{
    Paint paint;

    float[] data;

    double sense = 0;

    public WaveView(Context context) {
        super(context);
    }

    public WaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WaveView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        paint = new Paint();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(data != null) {
            int pointDis = 16;//荒さ
            float baseLine = getHeight() * 3/4f;

            FloatBuffer fb = FloatBuffer.allocate(data.length*4 / pointDis);
            for(int i=pointDis; i<data.length; i+=pointDis){
                //始点
                fb.put(getWidth() * (i-pointDis)/data.length);
                fb.put(data[i-pointDis]*1000 * (float)(1-sense*3)  + baseLine);
                //終点
                fb.put(getWidth() * i/data.length);
                fb.put(data[i]*1000 * (float)(1-sense*3) + baseLine);
            }
            paint.setStrokeWidth(3.0f);
            paint.setColor(Color.parseColor("#CF6969"));
            canvas.drawLines(fb.array(), paint);
        }
    }

    public void update(float[] data){
        this.data = data;
        invalidate();
    }

    public void setSense(double sense){
        this.sense = sense;
    }

    void print(Object message){
        Log.d("aaaa", message + "");
    }
}
