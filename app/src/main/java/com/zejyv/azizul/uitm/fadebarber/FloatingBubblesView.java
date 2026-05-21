package com.zejyv.azizul.uitm.fadebarber;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FloatingBubblesView extends View {

    private final List<Bubble> bubbles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final int bubbleCount = 5;

    public FloatingBubblesView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(ContextCompat.getColor(context, R.color.white));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bubbles.clear();
        for (int i = 0; i < bubbleCount; i++) {
            bubbles.add(new Bubble(w, h));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Bubble bubble : bubbles) {
            bubble.update(getWidth(), getHeight());
            canvas.drawCircle(bubble.x, bubble.y, bubble.radius, paint);
        }
        invalidate();
    }

    private class Bubble {
        float x, y, radius, speed;

        Bubble(int w, int h) {
            reset(w, h, true);
        }

        void reset(int w, int h, boolean randomY) {
            radius = 10 + random.nextInt(40);
            x = random.nextInt(w);
            y = randomY ? random.nextInt(h) : h + radius;
            speed = 1 + random.nextFloat() * 3;
        }

        void update(int w, int h) {
            y -= speed;
            if (y + radius < 0) {
                reset(w, h, false);
            }
        }
    }
}
