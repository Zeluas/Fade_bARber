package com.zejyv.azizul.uitm.fadebarber;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FloatingBubblesView: A custom decorative view that renders animated floating circles.
 * Used as a background effect in headers throughout the app.
 */
public class FloatingBubblesView extends View {

    private final List<Bubble> bubbles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    public FloatingBubblesView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(ContextCompat.getColor(context, R.color.white));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Initialize bubbles only once or reposition if bounds change
        if (bubbles.isEmpty()) {
            int bubbleCount = 5;
            for (int i = 0; i < bubbleCount; i++) {
                bubbles.add(new Bubble(w, h));
            }
        } else {
            // Coordinate adjustment to prevent bubbles from getting stuck off-screen
            for (Bubble bubble : bubbles) {
                if (bubble.x > w) bubble.x = random.nextInt(w);
                if (bubble.y > h) bubble.y = h;
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        // Core Animation Loop
        for (Bubble bubble : bubbles) {
            bubble.update(getWidth(), getHeight());
            canvas.drawCircle(bubble.x, bubble.y, bubble.radius, paint);
        }
        // Force a redraw on the next frame for smooth animation
        invalidate();
    }

    /**
     * Inner class representing a single animated bubble.
     */
    private class Bubble {
        float x, y, radius, speed;

        Bubble(int w, int h) {
            reset(w, h, true);
        }

        /**
         * Resets the bubble to the bottom of the view with randomized properties.
         * @param randomY If true, starts at a random height (for initialization).
         */
        void reset(int w, int h, boolean randomY) {
            radius = 10 + random.nextInt(40);
            x = random.nextInt(w);
            y = randomY ? random.nextInt(h) : h + radius;
            speed = 1 + random.nextFloat() * 3;
        }

        /**
         * Moves the bubble upward and resets it if it leaves the top bound.
         */
        void update(int w, int h) {
            y -= speed;
            if (y + radius < 0) {
                reset(w, h, false);
            }
        }
    }
}
