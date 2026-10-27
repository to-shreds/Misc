package com.localfolderslideshow.app;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class ZoomImageView extends ImageView {
    public interface OnSingleTapListener {
        void onSingleTap();
    }

    public interface OnZoomStateChangedListener {
        void onZoomStateChanged(boolean zoomed);
    }

    private final Matrix zoomMatrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private OnSingleTapListener singleTapListener;
    private OnZoomStateChangedListener zoomStateChangedListener;
    private boolean cropMode = false;
    private float userScale = 1f;
    private boolean lastZoomed = false;

    public ZoomImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                Drawable drawable = getDrawable();
                if (drawable == null) return false;

                float desired = clamp(userScale * detector.getScaleFactor(), 1f, 5f);
                float delta = desired / userScale;
                zoomMatrix.postScale(delta, delta, detector.getFocusX(), detector.getFocusY());
                userScale = desired;
                fixBounds();
                setImageMatrix(zoomMatrix);
                notifyZoomState();
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                performClick();
                if (singleTapListener != null) singleTapListener.onSingleTap();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (getDrawable() == null) return true;
                if (userScale > 1.05f) {
                    resetZoom();
                } else {
                    float target = 2.5f;
                    float delta = target / userScale;
                    zoomMatrix.postScale(delta, delta, e.getX(), e.getY());
                    userScale = target;
                    fixBounds();
                    setImageMatrix(zoomMatrix);
                    notifyZoomState();
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (userScale <= 1.001f || getDrawable() == null) return false;
                zoomMatrix.postTranslate(-distanceX, -distanceY);
                fixBounds();
                setImageMatrix(zoomMatrix);
                return true;
            }
        });
    }

    public void setCropMode(boolean cropMode) {
        this.cropMode = cropMode;
        resetZoom();
    }

    public void setOnSingleTapListener(OnSingleTapListener listener) {
        this.singleTapListener = listener;
    }

    public void setOnZoomStateChangedListener(OnZoomStateChangedListener listener) {
        this.zoomStateChangedListener = listener;
    }

    public boolean isZoomed() {
        return userScale > 1.001f;
    }

    public void resetZoom() {
        userScale = 1f;
        applyBaseMatrix();
        notifyZoomState();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        post(this::resetZoom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetZoom();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void applyBaseMatrix() {
        Drawable drawable = getDrawable();
        int viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int viewHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        if (drawable == null || viewWidth <= 0 || viewHeight <= 0) return;

        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        if (drawableWidth <= 0 || drawableHeight <= 0) return;

        float scaleX = (float) viewWidth / drawableWidth;
        float scaleY = (float) viewHeight / drawableHeight;
        float baseScale = cropMode ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY);

        float scaledWidth = drawableWidth * baseScale;
        float scaledHeight = drawableHeight * baseScale;
        float dx = getPaddingLeft() + (viewWidth - scaledWidth) / 2f;
        float dy = getPaddingTop() + (viewHeight - scaledHeight) / 2f;

        zoomMatrix.reset();
        zoomMatrix.postScale(baseScale, baseScale);
        zoomMatrix.postTranslate(dx, dy);
        setImageMatrix(zoomMatrix);
    }

    private void fixBounds() {
        Drawable drawable = getDrawable();
        if (drawable == null) return;

        RectF rect = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        zoomMatrix.mapRect(rect);

        float leftBound = getPaddingLeft();
        float topBound = getPaddingTop();
        float rightBound = getWidth() - getPaddingRight();
        float bottomBound = getHeight() - getPaddingBottom();
        float viewWidth = rightBound - leftBound;
        float viewHeight = bottomBound - topBound;

        float dx = 0f;
        float dy = 0f;

        if (rect.width() <= viewWidth) {
            dx = leftBound + (viewWidth - rect.width()) / 2f - rect.left;
        } else if (rect.left > leftBound) {
            dx = leftBound - rect.left;
        } else if (rect.right < rightBound) {
            dx = rightBound - rect.right;
        }

        if (rect.height() <= viewHeight) {
            dy = topBound + (viewHeight - rect.height()) / 2f - rect.top;
        } else if (rect.top > topBound) {
            dy = topBound - rect.top;
        } else if (rect.bottom < bottomBound) {
            dy = bottomBound - rect.bottom;
        }

        if (dx != 0f || dy != 0f) zoomMatrix.postTranslate(dx, dy);
    }

    private void notifyZoomState() {
        boolean zoomed = isZoomed();
        if (zoomed != lastZoomed) {
            lastZoomed = zoomed;
            if (zoomStateChangedListener != null) zoomStateChangedListener.onZoomStateChanged(zoomed);
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
