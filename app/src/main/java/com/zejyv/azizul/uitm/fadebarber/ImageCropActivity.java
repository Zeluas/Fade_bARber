package com.zejyv.azizul.uitm.fadebarber;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageCropActivity extends AppCompatActivity {

    private ImageView ivCropImage;
    private CropOverlayView overlayView;
    private Bitmap originalBitmap;
    
    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();
    
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;
    
    private PointF start = new PointF();
    private float oldDist = 1f;
    
    private ScaleGestureDetector scaleDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_crop);

        ivCropImage = findViewById(R.id.iv_crop_image);
        overlayView = findViewById(R.id.overlay_view);
        
        Uri imageUri = getIntent().getData();
        if (imageUri == null) {
            finish();
            return;
        }

        loadBitmap(imageUri);
        
        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());
        
        ivCropImage.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    savedMatrix.set(matrix);
                    start.set(event.getX(), event.getY());
                    mode = DRAG;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    oldDist = spacing(event);
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix);
                        mode = ZOOM;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    mode = NONE;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mode == DRAG) {
                        matrix.set(savedMatrix);
                        matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                    }
                    break;
            }
            fixMatrix();
            ivCropImage.setImageMatrix(matrix);
            return true;
        });

        findViewById(R.id.btn_crop_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_crop_done).setOnClickListener(v -> performCrop());
    }

    private void fixMatrix() {
        if (originalBitmap == null || overlayView == null) return;

        RectF cropRect = overlayView.getCropRect();
        if (cropRect.width() == 0) return;

        float[] values = new float[9];
        matrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];

        // 1. Ensure scale is not too small (image must at least cover crop area)
        float minScale = Math.max(cropRect.width() / originalBitmap.getWidth(),
                cropRect.height() / originalBitmap.getHeight());
        
        if (scale < minScale) {
            matrix.postScale(minScale / scale, minScale / scale, cropRect.centerX(), cropRect.centerY());
        }

        // 2. Ensure image bounds cover the crop rectangle
        RectF currentImageRect = new RectF(0, 0, originalBitmap.getWidth(), originalBitmap.getHeight());
        matrix.mapRect(currentImageRect);

        float dx = 0, dy = 0;
        if (currentImageRect.left > cropRect.left) dx = cropRect.left - currentImageRect.left;
        if (currentImageRect.top > cropRect.top) dy = cropRect.top - currentImageRect.top;
        if (currentImageRect.right < cropRect.right) dx = cropRect.right - currentImageRect.right;
        if (currentImageRect.bottom < cropRect.bottom) dy = cropRect.bottom - currentImageRect.bottom;

        matrix.postTranslate(dx, dy);
    }

    private void loadBitmap(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            originalBitmap = BitmapFactory.decodeStream(is);
            if (originalBitmap == null) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            ivCropImage.setImageBitmap(originalBitmap);
            
            // Initial matrix to center inside overlay
            ivCropImage.post(() -> {
                RectF cropRect = overlayView.getCropRect();
                float scale;
                float dx = 0, dy = 0;

                float bitmapWidth = originalBitmap.getWidth();
                float bitmapHeight = originalBitmap.getHeight();

                if (bitmapWidth > bitmapHeight) {
                    scale = cropRect.height() / bitmapHeight;
                    dx = cropRect.centerX() - (bitmapWidth * scale) / 2f;
                    dy = cropRect.top;
                } else {
                    scale = cropRect.width() / bitmapWidth;
                    dx = cropRect.left;
                    dy = cropRect.centerY() - (bitmapHeight * scale) / 2f;
                }

                matrix.setScale(scale, scale);
                matrix.postTranslate(dx, dy);
                ivCropImage.setImageMatrix(matrix);
            });
            
        } catch (IOException e) {
            e.printStackTrace();
            finish();
        }
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            fixMatrix();
            ivCropImage.setImageMatrix(matrix);
            return true;
        }
    }

    private void performCrop() {
        if (originalBitmap == null) return;

        RectF cropRect = overlayView.getCropRect();
        
        // We create a bitmap of size equal to the crop overlay
        Bitmap croppedBitmap = Bitmap.createBitmap((int) cropRect.width(), (int) cropRect.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(croppedBitmap);
        
        Matrix drawMatrix = new Matrix(matrix);
        drawMatrix.postTranslate(-cropRect.left, -cropRect.top);
        
        canvas.drawBitmap(originalBitmap, drawMatrix, null);
        
        // Save to file
        try {
            File cacheFile = new File(getCacheDir(), "cropped_profile.jpg");
            try (FileOutputStream out = new FileOutputStream(cacheFile)) {
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            
            Intent resultIntent = new Intent();
            resultIntent.setData(Uri.fromFile(cacheFile));
            setResult(RESULT_OK, resultIntent);
            finish();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save cropped image", Toast.LENGTH_SHORT).show();
        }
    }
}
