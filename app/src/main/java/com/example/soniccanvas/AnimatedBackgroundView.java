package com.example.soniccanvas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnimatedBackgroundView extends View {
    private Paint backgroundPaint;
    private List<Bubble> bubbles;
    private List<FloatingParticle> particles;
    private Random random;
    private int width, height;
    private final int MAX_BUBBLES = 12;
    private final int MAX_PARTICLES = 30;
    private float time = 0;
    private ValueAnimator animator;

    private int[] gradientColors = {
            Color.parseColor("#121212"),  // Dark background
            Color.parseColor("#1A1A2E"),  // Slight blue hint
            Color.parseColor("#16213E")   // Deeper blue
    };

    public AnimatedBackgroundView(Context context) {
        super(context);
        init();
    }

    public AnimatedBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnimatedBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        backgroundPaint = new Paint();
        bubbles = new ArrayList<>();
        particles = new ArrayList<>();
        random = new Random();

        // Start animation
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(10000); // 10 seconds per cycle
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            time = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();

        // Schedule particle creation
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (particles.size() < MAX_PARTICLES && width > 0 && height > 0) {
                    addParticle();
                }
                handler.postDelayed(this, 300);
            }
        }, 300);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;

        // Create background gradient shader
        backgroundPaint.setShader(new LinearGradient(
                0, 0, 0, height,
                gradientColors,
                null,
                Shader.TileMode.CLAMP));

        // Initialize bubbles and particles
        initializeElements();
    }

    private void initializeElements() {
        bubbles.clear();
        particles.clear();

        // Create initial bubbles
        for (int i = 0; i < MAX_BUBBLES; i++) {
            addBubble();
        }

        // Create initial particles
        for (int i = 0; i < MAX_PARTICLES / 2; i++) {
            addParticle();
        }
    }

    private void addBubble() {
        float size = 50 + random.nextFloat() * 150;
        float x = random.nextFloat() * width;
        float y = height + size;
        float speed = 0.5f + random.nextFloat() * 1.5f;
        int alpha = 50 + random.nextInt(100); // semi-transparent bubbles

        int colorIndex = random.nextInt(3);
        int color;
        switch (colorIndex) {
            case 0:
                color = Color.parseColor("#BB86FC"); // Purple
                break;
            case 1:
                color = Color.parseColor("#03DAC6"); // Teal
                break;
            case 2:
            default:
                color = Color.parseColor("#CF6679"); // Pink
                break;
        }

        bubbles.add(new Bubble(x, y, size, speed, color, alpha));
    }

    private void addParticle() {
        float size = 2 + random.nextFloat() * 4;
        float x = random.nextFloat() * width;
        float y = random.nextFloat() * height;
        float speedX = -0.5f + random.nextFloat();
        float speedY = -0.5f + random.nextFloat();
        int alpha = 50 + random.nextInt(100);
        int color = Color.parseColor("#FFFFFF");

        particles.add(new FloatingParticle(x, y, size, speedX, speedY, color, alpha));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background gradient
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        // Update and draw bubbles
        for (int i = bubbles.size() - 1; i >= 0; i--) {
            Bubble bubble = bubbles.get(i);
            bubble.update();
            bubble.draw(canvas);

            // Remove bubbles that have left the screen
            if (bubble.y + bubble.size < 0) {
                bubbles.remove(i);
                // Add a new bubble to replace it
                if (bubbles.size() < MAX_BUBBLES) {
                    addBubble();
                }
            }
        }

        // Update and draw particles
        for (int i = particles.size() - 1; i >= 0; i--) {
            FloatingParticle particle = particles.get(i);
            particle.update();
            particle.draw(canvas);

            // Remove particles that have left the screen or expired
            if (particle.x < -particle.size || particle.x > width + particle.size ||
                    particle.y < -particle.size || particle.y > height + particle.size ||
                    particle.alpha <= 10) {
                particles.remove(i);
            }
        }

        // Add bubbles if needed
        if (bubbles.size() < MAX_BUBBLES / 2) {
            addBubble();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }

    // Bubble class for background animation
    private class Bubble {
        float x, y;
        float size;
        float speed;
        int color;
        int alpha;
        Paint paint;
        float wobbleOffset;
        float wobbleSpeed;

        Bubble(float x, float y, float size, float speed, int color, int alpha) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.speed = speed;
            this.color = color;
            this.alpha = alpha;

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setAlpha(alpha);
            paint.setStyle(Paint.Style.FILL);

            wobbleOffset = random.nextFloat() * 6.28f; // 2π
            wobbleSpeed = 0.01f + random.nextFloat() * 0.03f;
        }

        void update() {
            y -= speed; // Move upward

            // Add slight horizontal wobble
            x += Math.sin(time * 6.28f + wobbleOffset) * wobbleSpeed * size;

            // Gradually fade out as it reaches the top
            if (y < height / 3) {
                alpha = Math.max(10, alpha - 1);
                paint.setAlpha(alpha);
            }
        }

        void draw(Canvas canvas) {
            // Create a radial gradient for a more interesting bubble look
            RadialGradient gradient = new RadialGradient(
                    x, y, size,
                    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                    Shader.TileMode.CLAMP);

            paint.setShader(gradient);
            canvas.drawCircle(x, y, size, paint);
            paint.setShader(null);

            // Draw a slight highlight
            paint.setAlpha(alpha / 3);
            canvas.drawCircle(x - size/4, y - size/4, size/4, paint);
        }
    }

    // Floating particle class
    private class FloatingParticle {
        float x, y;
        float size;
        float speedX, speedY;
        int color;
        int alpha;
        Paint paint;

        FloatingParticle(float x, float y, float size, float speedX, float speedY, int color, int alpha) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.speedX = speedX;
            this.speedY = speedY;
            this.color = color;
            this.alpha = alpha;

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setAlpha(alpha);
            paint.setStyle(Paint.Style.FILL);
        }

        void update() {
            x += speedX;
            y += speedY;

            // Gradually fade
            alpha -= 0.2f;
            if (alpha < 0) alpha = 0;
            paint.setAlpha(alpha);
        }

        void draw(Canvas canvas) {
            canvas.drawCircle(x, y, size, paint);
        }
    }
}
