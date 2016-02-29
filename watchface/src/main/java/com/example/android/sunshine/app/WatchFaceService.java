package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WatchFaceService extends CanvasWatchFaceService {
    private static String TAG = WatchFaceService.class.getSimpleName();

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private boolean isRound;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

        private static final String WEATHER_PATH = "/weather";
        private static final String WEATHER_DATA_PATH = "/weather_data";

        private static final String KEY_UUID = "uuid";
        private static final String KEY_HIGH = "high";
        private static final String KEY_LOW = "low";
        private static final String KEY_WEATHER_ID = "weatherId";


        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                long now = System.currentTimeMillis();
                mCalendar.setTimeInMillis(now);
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(WatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mAMPMPaint;
        Paint mTextDatePaint;
        Paint mTextTempHighPaint;
        Paint mTextTempLowPaint;
        Paint mTextTempLowAmbientPaint;

        Bitmap mWeatherIcon;

        String mWeatherHigh;
        String mWeatherLow;

        boolean mAmbient;

        //        Time mTime;
        private Calendar mCalendar;

        float mXOffsetTime;
        float mXOffsetDate;
        float mXOffsetTimeAmbient;

        float mTimeYOffset;
        float mAMPMYOffset;
        float mDateYOffset;
        float mDividerYOffset;
        float mIconYOffset;
        float mWeatherYOffset;
        float mIconSize;

        float mLineHeight;

//        float mYOffsetDate;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = WatchFaceService.this.getResources();
            mTimeYOffset = resources.getDimension(R.dimen.digital_time_y_offset);
            mAMPMYOffset = resources.getDimension(R.dimen.digital_ampm_y_offset);
            mDateYOffset = resources.getDimension(R.dimen.digital_date_y_offset);
            mDividerYOffset = resources.getDimension(R.dimen.digital_divider_y_offset);
            mIconYOffset = resources.getDimension(R.dimen.digital_icon_y_offset);
            mWeatherYOffset = resources.getDimension(R.dimen.digital_weather_y_offset);


            mLineHeight = resources.getDimension(R.dimen.digital_line_height);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.primary));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mAMPMPaint = new Paint();
            mAMPMPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTextDatePaint = new Paint();
            mTextDatePaint = createTextPaint(resources.getColor(R.color.primary_light));

            mTextTempHighPaint = createTextPaint(Color.WHITE);
            mTextTempLowPaint = createTextPaint(resources.getColor(R.color.primary_light));
            mTextTempLowAmbientPaint = createTextPaint(Color.WHITE);

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createBoldTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(BOLD_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();

                registerReceiver();

                mCalendar.setTimeZone(TimeZone.getDefault());
                long now = System.currentTimeMillis();
                mCalendar.setTimeInMillis(now);
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WatchFaceService.this.getResources();
            isRound = insets.isRound();
            mXOffsetTime = resources.getDimension(isRound
                    ? R.dimen.digital_time_x_offset_round : R.dimen.digital_time_x_offset);
            mXOffsetDate = resources.getDimension(isRound
                    ? R.dimen.digital_date_x_offset_round : R.dimen.digital_date_x_offset);
            mXOffsetTimeAmbient = resources.getDimension(isRound
                    ? R.dimen.digital_time_x_offset_round_ambient : R.dimen.digital_time_x_offset_ambient);
            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);
            float ampmTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_ampm_text_size_round : R.dimen.digital_ampm_text_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
            float tempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);

            mIconSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_icon_size_round : R.dimen.digital_temp_icon_size);

            mTextPaint.setTextSize(timeTextSize);
            mAMPMPaint.setTextSize(ampmTextSize);
            mTextDatePaint.setTextSize(dateTextSize);
            mTextTempHighPaint.setTextSize(tempTextSize);
            mTextTempLowAmbientPaint.setTextSize(tempTextSize);
            mTextTempLowPaint.setTextSize(tempTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.d(TAG,"Ambient Mode -->"+inAmbientMode);
            if(inAmbientMode){
                mTimeYOffset = mTimeYOffset+11;
                mWeatherYOffset = mWeatherYOffset-31;
            }else{
                mTimeYOffset = mTimeYOffset-11;
                mWeatherYOffset = mWeatherYOffset+31;
            }
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mAMPMPaint.setAntiAlias(!inAmbientMode);
                    mTextTempHighPaint.setAntiAlias(!inAmbientMode);
                    mTextTempLowAmbientPaint.setAntiAlias(!inAmbientMode);
                    mTextTempLowPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (mAmbient) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            boolean is24Hour = DateFormat.is24HourFormat(WatchFaceService.this);

            int minute = mCalendar.get(Calendar.MINUTE);
            int am_pm  = mCalendar.get(Calendar.AM_PM);

            String timeText;
            if (is24Hour) {
                int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
                timeText = String.format("%02d:%02d", hour, minute);
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }

                timeText = String.format("%d:%02d", hour, minute);
            }

            // Decide which paint to user for the next bits dependent on ambient mode.
            if(!mAmbient){

                Paint datePaint = mTextDatePaint;
                // Draw the date
                String dayOfWeekString = getDay(mCalendar.get(Calendar.DAY_OF_WEEK));
                String monthOfYearString = getMonth(mCalendar.get(Calendar.MONTH));

                int dayOfMonth = mCalendar.get(Calendar.DAY_OF_MONTH);
                int year = mCalendar.get(Calendar.YEAR);

                String dateText = String.format("%s, %s %d %d", dayOfWeekString, monthOfYearString, dayOfMonth, year);
                float xOffsetDate = datePaint.measureText(dateText) / 2;
                canvas.drawText(dateText, bounds.centerX() - xOffsetDate, mDateYOffset, datePaint);

                // Draw a line to separate date and time from weather elements
                canvas.drawLine(bounds.centerX() - 20, mDividerYOffset, bounds.centerX() + 20, mDividerYOffset, mAMPMPaint);

            }
            float xOffsetTime = mTextPaint.measureText(timeText) + 5;
            if(!isRound) {
                xOffsetTime = (xOffsetTime/2)-15;
            }
            float xOffsetAMPM = xOffsetTime + mTextPaint.measureText(timeText) + 5;
            Log.d(TAG, "Bound_X->" + bounds.centerX() + "; xOffsetTime->" + xOffsetTime + "; xOffsetAMPM->" + xOffsetAMPM + "; textPaint->" + mTextPaint.measureText(timeText));

            if(!is24Hour) {
                String amPmText = com.example.android.sunshine.app.Util.getAmPmString(getResources(), am_pm);
                canvas.drawText(timeText, xOffsetTime, mTimeYOffset, mTextPaint);
                canvas.drawText(amPmText, xOffsetAMPM, mTimeYOffset, mAMPMPaint);
            }

            // Draw high and low temp if we have it
            if (mWeatherHigh != null && mWeatherLow != null) {
//                // Draw a line to separate date and time from weather elements
                float highTextLen = mTextTempHighPaint.measureText(mWeatherHigh);

                if (mAmbient) {
                    float lowTextLen = mTextTempLowAmbientPaint.measureText(mWeatherLow);
                    float xOffset = bounds.centerX() - ((highTextLen + lowTextLen + 20) / 2);
                    canvas.drawText(mWeatherHigh, xOffset+7, mWeatherYOffset, mTextTempHighPaint);
                    canvas.drawText(mWeatherLow, xOffset + highTextLen + 21, mWeatherYOffset, mTextTempLowAmbientPaint);
                } else {
                    float xOffset = bounds.centerX() - (highTextLen / 2);
                    canvas.drawText(mWeatherHigh, xOffset+7, mWeatherYOffset, mTextTempHighPaint);
                    canvas.drawText(mWeatherLow, bounds.centerX() + (highTextLen / 2) + 21, mWeatherYOffset, mTextTempLowPaint);
                    float iconXOffset = bounds.centerX() - ((highTextLen / 2) + mWeatherIcon.getWidth() + 21);
                    canvas.drawBitmap(mWeatherIcon, iconXOffset + 9, (mIconYOffset - ((11f/10)*mWeatherIcon.getHeight())), null);
                }
            }
        }

        @NonNull
        private String getMonth(int monthOfYear) {
            String monthOfYearString;
            switch(monthOfYear) {
                case Calendar.JANUARY:
                    monthOfYearString = getResources().getString(R.string.january);
                    break;
                case Calendar.FEBRUARY:
                    monthOfYearString = getResources().getString(R.string.february);
                    break;
                case Calendar.MARCH:
                    monthOfYearString = getResources().getString(R.string.march);
                    break;
                case Calendar.APRIL:
                    monthOfYearString = getResources().getString(R.string.april);
                    break;
                case Calendar.MAY:
                    monthOfYearString = getResources().getString(R.string.may);
                    break;
                case Calendar.JUNE:
                    monthOfYearString = getResources().getString(R.string.june);
                    break;
                case Calendar.JULY:
                    monthOfYearString = getResources().getString(R.string.july);
                    break;
                case Calendar.AUGUST:
                    monthOfYearString = getResources().getString(R.string.august);
                    break;
                case Calendar.SEPTEMBER:
                    monthOfYearString = getResources().getString(R.string.september);
                    break;
                case Calendar.OCTOBER:
                    monthOfYearString = getResources().getString(R.string.october);
                    break;
                case Calendar.NOVEMBER:
                    monthOfYearString = getResources().getString(R.string.november);
                    break;
                case Calendar.DECEMBER:
                    monthOfYearString = getResources().getString(R.string.december);
                    break;
                default:
                    monthOfYearString = "";
            }
            return monthOfYearString;
        }

        @NonNull
        private String getDay(int day) {
            String dayOfWeekString;
            switch (day) {
                case Calendar.SUNDAY:
                    dayOfWeekString = getResources().getString(R.string.sunday);
                    break;
                case Calendar.MONDAY:
                    dayOfWeekString = getResources().getString(R.string.monday);
                    break;
                case Calendar.TUESDAY:
                    dayOfWeekString = getResources().getString(R.string.tuesday);
                    break;
                case Calendar.WEDNESDAY:
                    dayOfWeekString = getResources().getString(R.string.wednesday);
                    break;
                case Calendar.THURSDAY:
                    dayOfWeekString = getResources().getString(R.string.thursday);
                    break;
                case Calendar.FRIDAY:
                    dayOfWeekString = getResources().getString(R.string.friday);
                    break;
                case Calendar.SATURDAY:
                    dayOfWeekString = getResources().getString(R.string.saturday);
                    break;
                default:
                    dayOfWeekString = "";
            }
            return dayOfWeekString;
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(TAG, "Connected...");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            requestWeatherInfo();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "Connection Suspended");
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(TAG, "on Data Changed");
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    Log.d(TAG, "on Data Changed..."+path);
                    if (path.equals(WEATHER_DATA_PATH)) {
                        if (dataMap.containsKey(KEY_HIGH)) {
                            mWeatherHigh = dataMap.getString(KEY_HIGH);
                            Log.d(TAG, "High->" + mWeatherHigh);
                        }
                        if (dataMap.containsKey(KEY_LOW)) {
                            mWeatherLow = dataMap.getString(KEY_LOW);
                            Log.d(TAG, "Low->" + mWeatherLow);
                        }
                        if (dataMap.containsKey(KEY_WEATHER_ID)) {
                            int weatherId = dataMap.getInt(KEY_WEATHER_ID);
                            Drawable b = getResources().getDrawable(com.example.android.sunshine.app.Util.getIconResourceForWeatherCondition(weatherId));
                            Bitmap icon = ((BitmapDrawable) b).getBitmap();
                            float scaledWidth = (mTextTempHighPaint.getTextSize() / icon.getHeight()) * icon.getWidth();
                            mWeatherIcon = Bitmap.createScaledBitmap(icon, (int) scaledWidth, (int) mTextTempHighPaint.getTextSize(), true);
                        }
                        invalidate();
                    }
                    //for testing UI
//                    mWeatherHigh = "16°";
//                    mWeatherLow = "7°";
//
//                    int weatherId = 800;
//                    Drawable b = getResources().getDrawable(Util.getIconResourceForWeatherCondition(weatherId));
//                    Bitmap icon = ((BitmapDrawable) b).getBitmap();
//                    float scaledWidth = (mIconSize/ icon.getHeight()) * icon.getWidth();
//                    mWeatherIcon = Bitmap.createScaledBitmap(icon, (int) scaledWidth, (int) mIconSize, true);
//
//                    invalidate();

                    //testing finish
                }
            }
            Log.d(TAG,"on Data Changed... finish");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d(TAG, "Connection Failed");
        }

        public void requestWeatherInfo() {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_PATH);
            putDataMapRequest.getDataMap().putString(KEY_UUID, UUID.randomUUID().toString());
            PutDataRequest request = putDataMapRequest.asPutDataRequest();

            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {
                                Log.d(TAG, "Failed");
                            } else {
                                Log.d(TAG, "Success");
                            }
                        }
                    });
        }
    }



    private static class EngineHandler extends Handler {
        private final WeakReference<WatchFaceService.Engine> mWeakReference;

        public EngineHandler(WatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
