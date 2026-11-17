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

    private final Paint bidangPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint garisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stikPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titikPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float posisiX;
    private float posisiY;
    private boolean hanyaMendatar;
    private float opasitas = 1f;
    private int pointerAktif = NO_POINTER;
    private OnPositionChangedListener pendengarGerak;

    public Gamepad(Context context, AttributeSet attributes) {
        super(context, attributes);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setOnPositionChangedListener(OnPositionChangedListener pendengar) {
        pendengarGerak = pendengar;
    }

    public void setHorizontalOnly(boolean aktif) {
        hanyaMendatar = aktif;
        if (aktif) {
            posisiY = 0;
            invalidate();
        }
    }

    public void setOpacity(float nilai) {
        opasitas = Math.max(0, Math.min(1, nilai));
        invalidate();
    }

    public void setPosition(float x, float y) {
        posisiX = batasi(x);
        posisiY = hanyaMendatar ? 0 : batasi(y);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float tengahX = getWidth() / 2f;
        float tengahY = getHeight() / 2f;
        float jariBidang = Math.min(getWidth(), getHeight()) / 2f - dp(10);
        float jariStik = jariBidang * 0.26f;
        float jarakStik = jariBidang - jariStik - dp(3);

        bidangPaint.setColor(Color.argb(Math.round(112 * opasitas), 64, 64, 64));
        bidangPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(tengahX, tengahY, jariBidang, bidangPaint);

        garisPaint.setColor(Color.argb(Math.round(190 * opasitas), 180, 180, 180));
        garisPaint.setStyle(Paint.Style.STROKE);
        garisPaint.setStrokeWidth(dp(2));
        canvas.drawCircle(tengahX, tengahY, jariBidang, garisPaint);
        garisPaint.setStrokeWidth(dp(1));
        canvas.drawCircle(tengahX, tengahY, jarakStik * 0.55f, garisPaint);
        canvas.drawLine(
                tengahX - jarakStik,
                tengahY,
                tengahX + jarakStik,
                tengahY,
                garisPaint
        );
        if (!hanyaMendatar) {
            canvas.drawLine(
                    tengahX,
                    tengahY - jarakStik,
                    tengahX,
                    tengahY + jarakStik,
                    garisPaint
            );
        }

        float letakStikX = tengahX + posisiX * jarakStik;
        float letakStikY = tengahY + posisiY * jarakStik;
        stikPaint.setColor(Color.argb(Math.round(230 * opasitas), 255, 255, 255));
        stikPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(letakStikX, letakStikY, jariStik, stikPaint);
        titikPaint.setColor(Color.argb(Math.round(230 * opasitas), 0, 0, 0));
        titikPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(letakStikX, letakStikY, jariStik * 0.22f, titikPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent sentuhan) {
        if (!isEnabled()) {
            return false;
        }

        switch (sentuhan.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pointerAktif = sentuhan.getPointerId(0);
                perbaruiPosisiDariSentuhan(sentuhan, 0);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (pointerAktif == NO_POINTER) {
                    int pointerBaru = sentuhan.getActionIndex();
                    pointerAktif = sentuhan.getPointerId(pointerBaru);
                    perbaruiPosisiDariSentuhan(sentuhan, pointerBaru);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pointerAktif == NO_POINTER) {
                    pointerAktif = sentuhan.getPointerId(0);
                }
                int indeksPointer = sentuhan.findPointerIndex(pointerAktif);
                if (indeksPointer >= 0) {
                    perbaruiPosisiDariSentuhan(sentuhan, indeksPointer);
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                int indeksTerangkat = sentuhan.getActionIndex();
                if (sentuhan.getPointerId(indeksTerangkat) == pointerAktif) {
                    if (sentuhan.getPointerCount() > 1) {
                        int indeksPengganti = indeksTerangkat == 0 ? 1 : 0;
                        pointerAktif = sentuhan.getPointerId(indeksPengganti);
                        perbaruiPosisiDariSentuhan(sentuhan, indeksPengganti);
                    } else {
                        lepaskanStik();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                lepaskanStik();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                lepaskanStik();
                return true;
            default:
                return true;
        }
    }

    private void perbaruiPosisiDariSentuhan(MotionEvent sentuhan, int indeksPointer) {
        float jariBidang = Math.min(getWidth(), getHeight()) / 2f - dp(10);
        float jariStik = jariBidang * 0.26f;
        float jarakStik = jariBidang - jariStik - dp(3);
        float tengahX = getWidth() / 2f;
        float tengahY = getHeight() / 2f;

        float x = batasi((sentuhan.getX(indeksPointer) - tengahX) / jarakStik);
        float y = hanyaMendatar
                ? 0
                : batasi((sentuhan.getY(indeksPointer) - tengahY) / jarakStik);
        float panjang = (float) Math.sqrt(x * x + y * y);
        if (panjang > 1) {
            x /= panjang;
            y /= panjang;
        }

        posisiX = x;
        posisiY = y;
        invalidate();
        kirimPosisiKePendengar();
    }

    private void lepaskanStik() {
        pointerAktif = NO_POINTER;
        setPosition(0, 0);
        kirimPosisiKePendengar();
    }

    private void kirimPosisiKePendengar() {
        if (pendengarGerak != null) {
            pendengarGerak.onPositionChanged(posisiX, posisiY);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float nilai) {
        return nilai * getResources().getDisplayMetrics().density;
    }

    private static float batasi(float nilai) {
        return Math.max(-1, Math.min(1, nilai));
    }
}
