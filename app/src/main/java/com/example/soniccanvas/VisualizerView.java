package com.example.soniccanvas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import java.util.Random;

public class VisualizerView extends View {
    private float magnitude;
    private Paint paint;
    private int color;
    private Random random = new Random();
    private int width;
    private int height;
    private int visualizerType = 0;
    private float[] waveformBuffer;
    private int waveformBufferSize = 256;
    private short[] audioData;
    private int audioDataSize;
    private Path path;
    private boolean isInitialized = false;

    // Sensitivity multiplier - higher value = more responsive visualization
    private float sensitivityMultiplier = 3.5f;

    public VisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VisualizerView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        // Use themed colors
        updateTypeColor();

        waveformBuffer = new float[waveformBufferSize];
        audioData = new short[0];
        audioDataSize = 0;
        path = new Path();
    }

    public void setSensitivityMultiplier(float sensitivity) {
        this.sensitivityMultiplier = sensitivity;
    }

    private void updateTypeColor() {
        switch (visualizerType) {
            case 0: // Waveform
                color = ContextCompat.getColor(getContext(), R.color.visualizer_waveform);
                break;
            case 1: // Bars
                color = ContextCompat.getColor(getContext(), R.color.visualizer_bars);
                break;
            case 2: // Circular
                color = ContextCompat.getColor(getContext(), R.color.visualizer_circular);
                break;
            default:
                color = ContextCompat.getColor(getContext(), R.color.visualizer_waveform);
        }
        paint.setColor(color);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        isInitialized = true;
    }

    public void setVisualizerType(int type) {
        this.visualizerType = type;
        updateTypeColor();
        invalidate();
    }

    public void updateVisualizer(float magnitude, short[] data, int size) {
        this.magnitude = magnitude * sensitivityMultiplier;
        this.audioData = data;
        this.audioDataSize = size;
        updateWaveformBuffer(this.magnitude);
        postInvalidate();
    }

    private void updateWaveformBuffer(float magnitude) {
        for (int i = waveformBufferSize - 1; i > 0; i--) {
            waveformBuffer[i] = waveformBuffer[i - 1];
        }
        waveformBuffer[0] = magnitude;
    }

    public void clear() {
        this.magnitude = 0;
        for (int i = 0; i < waveformBufferSize; i++) {
            waveformBuffer[i] = 0;
        }
        audioDataSize = 0;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isInitialized) return;

        width = getWidth();
        height = getHeight();

        switch (visualizerType) {
            case 0:
                drawWaveform(canvas);
                break;
            case 1:
                drawBars(canvas);
                break;
            case 2:
                drawCircular(canvas);
                break;
            default:
                drawWaveform(canvas);
        }
    }

    private void drawWaveform(Canvas canvas) {
        if (audioDataSize == 0) return;

        path.reset();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);

        float xIncrement = width / (float) Math.min(audioDataSize / 2, 128);
        float yMid = height / 2f;
        float x = 0;

        // Create a smooth path
        path.moveTo(0, yMid);

        for (int i = 0; i < Math.min(audioDataSize / 2, 128); i++) {
            // Apply sensitivity multiplier to make it more responsive
            float amplitude = (audioData[i * 2] / 32768f) * sensitivityMultiplier;
            float y = yMid - (amplitude * height / 2);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }

            x += xIncrement;
        }

        canvas.drawPath(path, paint);

        // Add glow effect
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setAlpha(80); // Semi-transparent
        canvas.drawPath(path, paint);

        // Reset paint
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBars(Canvas canvas) {
        if (audioDataSize == 0) return;

        paint.setStyle(Paint.Style.FILL);

        int numBars = 32;
        float barWidth = (width / (float) numBars) * 0.8f;
        float spacing = (width - (numBars * barWidth)) / (numBars + 1);
        float x = spacing;
        int dataPerBar = audioDataSize / numBars;

        for (int i = 0; i < numBars; i++) {
            float barHeight = 0;
            for (int j = i * dataPerBar; j < (i + 1) * dataPerBar && j < audioDataSize; j++) {
                barHeight += Math.abs(audioData[j]);
            }

            barHeight /= dataPerBar;
            // Increase bar height with sensitivity multiplier
            barHeight = barHeight / 32768f * height * 0.8f * sensitivityMultiplier;

            if (barHeight < 10) barHeight = 10; // Minimum bar height
            if (barHeight > height) barHeight = height;

            float y = height - barHeight;

            // Draw rounded bar
            canvas.drawRoundRect(x, y, x + barWidth, height, 8, 8, paint);

            // Draw bar highlight
            paint.setAlpha(60);
            canvas.drawRect(x, y, x + barWidth * 0.3f, height, paint);
            paint.setAlpha(255);

            x += barWidth + spacing;
        }
    }

    private void drawCircular(Canvas canvas) {
        if (audioDataSize == 0) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);

        float centerX = width / 2f;
        float centerY = height / 2f;
        float baseRadius = Math.min(centerX, centerY) * 0.6f;
        int numPoints = 180;
        float angleIncrement = (float) (2 * Math.PI / numPoints);
        float angle = 0;

        path.reset();

        for (int i = 0; i < numPoints; i++) {
            int dataIndex = (i * audioDataSize / numPoints) % audioDataSize;
            // Apply sensitivity multiplier for more dramatic effect
            float amplitude = Math.abs(audioData[dataIndex]) / 32768f * sensitivityMultiplier;

            // Add some base radius so it's never zero
            float radius = baseRadius + (amplitude * baseRadius * 0.5f);

            float x = centerX + radius * (float) Math.cos(angle);
            float y = centerY + radius * (float) Math.sin(angle);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }

            angle += angleIncrement;
        }

        // Close the path
        path.close();

        // Draw the path with original opacity
        canvas.drawPath(path, paint);

        // Draw inner circle
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(40);
        canvas.drawCircle(centerX, centerY, baseRadius * 0.3f, paint);
        paint.setAlpha(255);
    }
}
