package com.example.robotcontrollerbola;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class Gamepad extends View {
    private static final int NO_POINTER = -1;

    public interface OnPositionChangedListener {
        void onPositionChanged(float x, float y);
    }

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float posisiX;
    private float posisiY;
    private boolean horizontal;
    private float opacity = 1f;
    private int activePointer = NO_POINTER;
    private OnPositionChangedListener listener;

    public Gamepad(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setOnPositionChangedListener(OnPositionChangedListener l) {
        this.listener = l;
    }

    public void setHorizontalOnly(boolean enabled) {
        this.horizontal = enabled;
        if (enabled) {
            this.posisiY = 0;
            invalidate();
        }
    }

    public void setOpacity(float val) {
        this.opacity = Math.max(0, Math.min(1, val));
        invalidate();
    }

    public void setPosition(float x, float y) {
        this.posisiX = limit(x);
        this.posisiY = horizontal ? 0 : limit(y);
        invalidate();
    }

    public float getPositionX() {
        return posisiX;
    }

    public float getPositionY() {
        return posisiY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float baseRadius = Math.min(getWidth(), getHeight()) / 2f - dp(10);
        float stickRadius = baseRadius * 0.26f;
        float stickDist = baseRadius - stickRadius - dp(3);

        basePaint.setColor(Color.argb(Math.round(112 * opacity), 64, 64, 64));
        basePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);

        linePaint.setColor(Color.argb(Math.round(190 * opacity), 180, 180, 180));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2));
        canvas.drawCircle(centerX, centerY, baseRadius, linePaint);
        linePaint.setStrokeWidth(dp(1));
        canvas.drawCircle(centerX, centerY, stickDist * 0.55f, linePaint);
        canvas.drawLine(centerX - stickDist, centerY, centerX + stickDist, centerY, linePaint);
        if (!horizontal) {
            canvas.drawLine(centerX, centerY - stickDist, centerX, centerY + stickDist, linePaint);
        }

        float stickX = centerX + posisiX * stickDist;
        float stickY = centerY + posisiY * stickDist;
        stickPaint.setColor(Color.argb(Math.round(230 * opacity), 255, 255, 255));
        stickPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(stickX, stickY, stickRadius, stickPaint);
        dotPaint.setColor(Color.argb(Math.round(230 * opacity), 0, 0, 0));
        dotPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(stickX, stickY, stickRadius * 0.22f, dotPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                activePointer = event.getPointerId(0);
                updatePositionFromTouch(event, 0);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (activePointer == NO_POINTER) {
                    int newPointer = event.getActionIndex();
                    activePointer = event.getPointerId(newPointer);
                    updatePositionFromTouch(event, newPointer);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (activePointer == NO_POINTER) {
                    activePointer = event.getPointerId(0);
                }
                int pointerIdx = event.findPointerIndex(activePointer);
                if (pointerIdx >= 0) {
                    updatePositionFromTouch(event, pointerIdx);
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                int upIdx = event.getActionIndex();
                if (event.getPointerId(upIdx) == activePointer) {
                    if (event.getPointerCount() > 1) {
                        int replacementIdx = upIdx == 0 ? 1 : 0;
                        activePointer = event.getPointerId(replacementIdx);
                        updatePositionFromTouch(event, replacementIdx);
                    } else {
                        lepasStick();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                lepasStick();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                lepasStick();
                return true;
            default:
                return true;
        }
    }

    private void updatePositionFromTouch(MotionEvent event, int pointerIdx) {
        float baseRadius = Math.min(getWidth(), getHeight()) / 2f - dp(10);
        float stickRadius = baseRadius * 0.26f;
        float stickDist = baseRadius - stickRadius - dp(3);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        float x = limit((event.getX(pointerIdx) - centerX) / stickDist);
        float y = horizontal ? 0 : limit((event.getY(pointerIdx) - centerY) / stickDist);
        float len = (float) Math.sqrt(x * x + y * y);
        if (len > 1) {
            x /= len;
            y /= len;
        }

        this.posisiX = x;
        this.posisiY = y;
        invalidate();
        notifyListener();
    }

    private void lepasStick() {
        activePointer = NO_POINTER;
        setPosition(0, 0);
        notifyListener();
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onPositionChanged(posisiX, posisiY);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float val) {
        return val * getResources().getDisplayMetrics().density;
    }

    private static float limit(float val) {
        return Math.max(-1, Math.min(1, val));
    }
}
