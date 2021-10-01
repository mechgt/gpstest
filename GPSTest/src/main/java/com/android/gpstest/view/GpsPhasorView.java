package com.android.gpstest.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.GpsStatus;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.android.gpstest.Application;
import com.android.gpstest.GpsPhasorUtils;
import com.android.gpstest.GpsTestListener;
import com.android.gpstest.PRStatus;
import com.android.gpstest.Phase;
import com.android.gpstest.R;
import com.android.gpstest.util.UIUtils;
import com.fasterxml.jackson.databind.JsonSerializer;


/**
 * View that shows satellite positions on a circle representing the sky
 */

public class GpsPhasorView extends View implements GpsTestListener {

    // View dimensions, to draw the compass with the correct width and height
    private static int mHeight;

    private static int mWidth;

    private static int SAT_RADIUS;

    Context mContext;

    WindowManager mWindowManager;

    private Paint mHorizonActiveFillPaint, mHorizonInactiveFillPaint, mHorizonStrokePaint,
            mGridStrokePaint, mNorthPaint, mNorthFillPaint,
            mStatusFaultPaint, mStatusOkPaint, mStatusWarnPaint;

    private int textColor, satStrokeColorUsed;

    private double mOrientation = 0.0;

    private boolean mStarted;

    private int mStatus;
    private Phasor[] mPhasors;
    private double mMag;
    private int mPhaseCount;

    public GpsPhasorView(Context context) {
        super(context);
        init(context);
    }

    public GpsPhasorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        SAT_RADIUS = UIUtils.dpToPixels(context, 5);

        int backgroundColor;
        if (Application.getPrefs().getBoolean(mContext.getString(R.string.pref_key_dark_theme), false)) {
            // Dark theme
            textColor = getResources().getColor(android.R.color.secondary_text_dark);
            backgroundColor = ContextCompat.getColor(context, R.color.navdrawer_background_dark);
            satStrokeColorUsed = getResources().getColor(android.R.color.darker_gray);
        } else {
            // Light theme
            textColor = getResources().getColor(R.color.body_text_2_light);
            backgroundColor = Color.WHITE;
            satStrokeColorUsed = Color.BLACK;
        }

        mHorizonActiveFillPaint = new Paint();
        mHorizonActiveFillPaint.setColor(backgroundColor);
        mHorizonActiveFillPaint.setStyle(Paint.Style.FILL);
        mHorizonActiveFillPaint.setAntiAlias(true);

        mHorizonInactiveFillPaint = new Paint();
        mHorizonInactiveFillPaint.setColor(Color.LTGRAY);
        mHorizonInactiveFillPaint.setStyle(Paint.Style.FILL);
        mHorizonInactiveFillPaint.setAntiAlias(true);

        mHorizonStrokePaint = new Paint();
        mHorizonStrokePaint.setColor(Color.BLACK);
        mHorizonStrokePaint.setStyle(Paint.Style.STROKE);
        mHorizonStrokePaint.setStrokeWidth(2.0f);
        mHorizonStrokePaint.setAntiAlias(true);

        mGridStrokePaint = new Paint();
        mGridStrokePaint.setColor(ContextCompat.getColor(mContext, R.color.gray));
        mGridStrokePaint.setStyle(Paint.Style.STROKE);
        mGridStrokePaint.setAntiAlias(true);

        mNorthPaint = new Paint();
        mNorthPaint.setColor(Color.BLACK);
        mNorthPaint.setStyle(Paint.Style.STROKE);
        mNorthPaint.setStrokeWidth(4.0f);
        mNorthPaint.setAntiAlias(true);

        mNorthFillPaint = new Paint();
        mNorthFillPaint.setColor(Color.GRAY);
        mNorthFillPaint.setStyle(Paint.Style.FILL);
        mNorthFillPaint.setStrokeWidth(4.0f);
        mNorthFillPaint.setAntiAlias(true);

        mStatusFaultPaint = new Paint();
        mStatusFaultPaint.setColor(Color.RED);
        mStatusFaultPaint.setStyle(Paint.Style.FILL);
        mStatusFaultPaint.setAntiAlias(true);

        mStatusOkPaint = new Paint();
        mStatusOkPaint.setColor(Color.GREEN);
        mStatusOkPaint.setStyle(Paint.Style.FILL);
        mStatusOkPaint.setAntiAlias(true);


        mPhasors = new Phasor[5];
        mStatus = 0;
        for (int i = 0; i < 5; i++) {
            mPhasors[i] = new Phasor(0, 0, GpsPhasorUtils.Line.values()[i]);
        }

        setFocusable(true);

        // Get the proper height and width of view before drawing
        getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        mHeight = getHeight();
                        mWidth = getWidth();
                        return true;
                    }
                }
        );
    }

    public void setStarted() {
        mStarted = true;
        invalidate();
    }

    public void setStopped() {
        mStarted = false;
        invalidate();
    }

    public synchronized void setPhasors(Phase[] phases) {
        mPhaseCount = phases.length;
        mMag = 0;

        for (int i = 0; i < phases.length; i++) {
            mPhasors[i].magnitude = phases[i].getVolts();
            mPhasors[i].angle = phases[i].getVolts_ang();

            // Find largest phasor for display scaling
            mMag = Math.max(mMag, Math.abs(mPhasors[i].magnitude));
        }

        mStarted = true;
        invalidate();
    }

    public synchronized void setRelay(int status) {
        mStatus = status;
        mStarted = true;
        invalidate();
    }

    private void drawLine(Canvas c, float x1, float y1, float x2, float y2) {
        drawLine(c, x1, y1, x2, y2, mGridStrokePaint);
    }

    private void drawLine(Canvas c, float x1, float y1, float x2, float y2, Paint paint) {
        // rotate the line based on orientation
        double angle = Math.toRadians(-mOrientation);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float centerX = (x1 + x2) / 2.0f;
        float centerY = (y1 + y2) / 2.0f;
        x1 -= centerX;
        y1 = centerY - y1;
        x2 -= centerX;
        y2 = centerY - y2;

        float X1 = cos * x1 + sin * y1 + centerX;
        float Y1 = -(-sin * x1 + cos * y1) + centerY;
        float X2 = cos * x2 + sin * y2 + centerX;
        float Y2 = -(-sin * x2 + cos * y2) + centerY;

        c.drawLine(X1, Y1, X2, Y2, paint);
    }

    private void drawHorizon(Canvas c, int s) {
        float radius = s / 2;
        // sin 30 == cos 60 = 0.5
        // sin 60 == cos 30 = 0.8660254
        float x30 = (float) (radius * Math.cos(Math.PI / 6));
        float y30 = (float) (radius * Math.sin(Math.PI / 6));
        float x60 = (float) (radius * Math.cos(Math.PI / 3));
        float y60 = (float) (radius * Math.sin(Math.PI / 3));

        c.drawCircle(radius, radius, radius,
                mStarted ? mHorizonActiveFillPaint : mHorizonInactiveFillPaint);
        drawLine(c, 0, radius, 2 * radius, radius);
        drawLine(c, radius, 0, radius, 2 * radius);
        drawLine(c, radius - x30, radius + y30, radius + x30, radius - y30);
        drawLine(c, radius - x60, radius + y60, radius + x60, radius - y60);
        drawLine(c, radius - x30, radius - y30, radius + x30, radius + y30);
        drawLine(c, radius - x60, radius - y60, radius + x60, radius + y60);

        c.drawCircle(radius, radius, elevationToRadius(s, 60.0f), mGridStrokePaint);
        c.drawCircle(radius, radius, elevationToRadius(s, 30.0f), mGridStrokePaint);
        c.drawCircle(radius, radius, elevationToRadius(s, 0.0f), mGridStrokePaint);
        c.drawCircle(radius, radius, radius, mHorizonStrokePaint);
    }

    private void drawFaultIndicator(Canvas c, int s) {
        float radius = s / 2;
        Paint paint;

        if (mStatus == 0)
            paint = mStatusOkPaint;
        else if (mStatus == 1)
            paint = mStatusFaultPaint;
        else
            paint = mStatusWarnPaint;

        c.drawCircle(radius, radius, elevationToRadius(s, 85.0f), paint);
    }

    private void drawNorthIndicator(Canvas c, int s) {
        float radius = s / 2;
        double angle = Math.toRadians(-mOrientation);
        final float ARROW_HEIGHT_SCALE = 0.05f;
        final float ARROW_WIDTH_SCALE = 0.1f;

        float x1, y1;  // Tip of arrow
        x1 = radius;
        y1 = elevationToRadius(s, 90.0f);

        float x2, y2;
        x2 = x1 + radius * ARROW_HEIGHT_SCALE;
        y2 = y1 + radius * ARROW_WIDTH_SCALE;

        float x3, y3;
        x3 = x1 - radius * ARROW_HEIGHT_SCALE;
        y3 = y1 + radius * ARROW_WIDTH_SCALE;

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x1, y1);
        path.close();

        // Rotate arrow around center point
        Matrix matrix = new Matrix();
        matrix.postRotate((float) -mOrientation, radius, radius);
        path.transform(matrix);

        c.drawPath(path, mNorthPaint);
        c.drawPath(path, mNorthFillPaint);
    }

    private float elevationToRadius(int s, float elev) {
        return ((s / 2) - SAT_RADIUS) * (1.0f - (elev / 90.0f));
    }

    private void drawTriangle(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint) {
        float x1, y1;  // Top
        x1 = x;
        y1 = y - SAT_RADIUS;

        float x2, y2; // Lower left
        x2 = x - SAT_RADIUS;
        y2 = y + SAT_RADIUS;

        float x3, y3; // Lower right
        x3 = x + SAT_RADIUS;
        y3 = y + SAT_RADIUS;

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x1, y1);
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }

    private void drawDiamond(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint) {
        Path path = new Path();
        path.moveTo(x, y - SAT_RADIUS);
        path.lineTo(x - SAT_RADIUS * 1.5f, y);
        path.lineTo(x, y + SAT_RADIUS);
        path.lineTo(x + SAT_RADIUS * 1.5f, y);
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }

    private void drawPentagon(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint) {
        Path path = new Path();
        path.moveTo(x, y - SAT_RADIUS);
        path.lineTo(x - SAT_RADIUS, y - (SAT_RADIUS / 3));
        path.lineTo(x - 2 * (SAT_RADIUS / 3), y + SAT_RADIUS);
        path.lineTo(x + 2 * (SAT_RADIUS / 3), y + SAT_RADIUS);
        path.lineTo(x + SAT_RADIUS, y - (SAT_RADIUS / 3));
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }

    private void drawHexagon(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint) {
        final float MULTIPLIER = 0.6f;
        final float SIDE_MULTIPLIER = 1.4f;
        Path path = new Path();
        // Top-left
        path.moveTo(x - SAT_RADIUS * MULTIPLIER, y - SAT_RADIUS);
        // Left
        path.lineTo(x - SAT_RADIUS * SIDE_MULTIPLIER, y);
        // Bottom
        path.lineTo(x - SAT_RADIUS * MULTIPLIER, y + SAT_RADIUS);
        path.lineTo(x + SAT_RADIUS * MULTIPLIER, y + SAT_RADIUS);
        // Right
        path.lineTo(x + SAT_RADIUS * SIDE_MULTIPLIER, y);
        // Top-right
        path.lineTo(x + SAT_RADIUS * MULTIPLIER, y - SAT_RADIUS);
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }

    private void drawOval(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint) {
        RectF rect = new RectF(x - SAT_RADIUS * 1.5f, y - SAT_RADIUS, x + SAT_RADIUS * 1.5f, y + SAT_RADIUS);

        c.drawOval(rect, fillPaint);
        c.drawOval(rect, strokePaint);
    }

    private void drawPhasor(Canvas c, Phasor p, int radius) {
        double scale = radius / mMag * p.magnitude;
        float px = (float) (scale * Math.cos(p.radians()));
        float py = (float) (scale * Math.sin(p.radians()));

        drawLine(c, radius, radius, radius + px, radius - py, p.paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int minScreenDimen;

        minScreenDimen = (mWidth < mHeight) ? mWidth : mHeight;

        drawHorizon(canvas, minScreenDimen);

        drawNorthIndicator(canvas, minScreenDimen);

        if (mPhasors != null) {
            for (int i = 0; i < mPhaseCount; i++) {
                if (mPhasors[i] != null) {
                    drawPhasor(canvas, mPhasors[i], minScreenDimen / 2);
                }
            }
        }

        drawFaultIndicator(canvas, minScreenDimen);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Use the width of the screen as the measured dimension for width and height of view
        // This allows other views in the same layout to be visible on the screen (#124)
        int specSize = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(specSize, specSize);
    }

    @Override
    public void onOrientationChanged(double orientation, double tilt) {
        mOrientation = orientation;
        invalidate();
    }

    @Override
    public void gpsStart() {
    }

    @Override
    public void gpsStop() {
    }

    @Override
    public void onGnssFirstFix(int ttffMillis) {
    }

    @Override
    public void onGnssFixAcquired() {
    }

    @Override
    public void onGnssFixLost() {
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onSatelliteStatusChanged(GnssStatus status) {
    }

    @Override
    public void onGnssStarted() {
    }

    @Override
    public void onGnssStopped() {
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
    }

    @Override
    public void onNmeaMessage(String message, long timestamp) {
    }

    @Deprecated
    @Override
    public void onGpsStatusChanged(int event, GpsStatus status) {
    }

    @Override
    public void onLocationChanged(Location location) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}

class Phasor {
    double magnitude;
    double angle;
    Paint paint;

    Phasor(double mag, double ang, GpsPhasorUtils.Line line) {
        this.angle = ang;
        this.magnitude = mag;
        this.paint = new Paint();
        paint.setColor(getColor(line));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10.0f);
        paint.setAntiAlias(true);

    }

    static int getColor(GpsPhasorUtils.Line line) {
        switch (line) {
            case A:
                return Color.RED;
            case B:
                return Color.BLUE;
            case C:
                return Color.GREEN;
            default:
                return Color.GRAY;
        }
    }

    double radians() {
        return Math.toRadians(angle);
    }
}