package com.zejyv.azizul.uitm.fadebarber;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CropOverlayView extends View {
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cropRect = new RectF();
    private final Path path = new Path();

    public CropOverlayView(Context context) {
        this(context, null);
    }

    public CropOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        backgroundPaint.setColor(0x99000000); // 60% black
        borderPaint.setColor(0xFFFFFFFF); // White
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float size = Math.min(w, h) * 0.8f;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        cropRect.set(left, top, left + size, top + size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        path.reset();
        path.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        path.addRect(cropRect, Path.Direction.CCW);
        canvas.drawPath(path, backgroundPaint);
        
        canvas.drawRect(cropRect, borderPaint);
    }

    public RectF getCropRect() {
        return cropRect;
    }
}
