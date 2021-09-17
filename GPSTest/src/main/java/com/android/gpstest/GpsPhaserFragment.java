/*
 * Copyright (C) 2008-2018 The Android Open Source Project,
 * Sean J. Barbeau (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gpstest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.GpsStatus;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.android.gpstest.model.SatelliteStatus;
import com.android.gpstest.util.DateTimeUtils;
import com.android.gpstest.util.SatelliteUtils;
import com.android.gpstest.util.UIUtils;

import java.text.SimpleDateFormat;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GpsPhaserFragment extends Fragment implements GpsTestListener {

    public final static String TAG = "GpsPhaserFragment";

    @SuppressLint("SimpleDateFormat") // See #117
    SimpleDateFormat mTimeFormat = new SimpleDateFormat(
            DateFormat.is24HourFormat(Application.get().getApplicationContext())
                    ? "HH:mm:ss.SSS" : "hh:mm:ss.SSS a");

    SimpleDateFormat mTimeAndDateFormat = new SimpleDateFormat(
            DateFormat.is24HourFormat(Application.get().getApplicationContext())
                    ? "HH:mm:ss.SSS MMM d, yyyy z" : "hh:mm:ss.SSS a MMM d, yyyy z");

    private Resources mRes;

    private TextView mVoltageView, mVoltageAngView, mCurrentView, mCurrentAngView, mFixTimeView, mFixTimeErrorView;

    private Location mLocation;

    private int svCount;

    private String mSnrCn0Title;

    private long mFixTime;

    private boolean mNavigating;

    DeviceInfoViewModel mViewModel;

    ImageView lock;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        mRes = getResources();

        View v = inflater.inflate(R.layout.gps_phaser, container,false);

        mVoltageView = v.findViewById(R.id.voltage);
        mVoltageAngView = v.findViewById(R.id.volt_ang);
        mCurrentView = v.findViewById(R.id.current);
        mCurrentAngView = v.findViewById(R.id.current_ang);
        mFixTimeView = v.findViewById(R.id.fix_time);
        mFixTimeErrorView = v.findViewById(R.id.fix_time_error);
        mFixTimeErrorView.setOnClickListener(view -> showTimeErrorDialog(mFixTime));

        GpsTestActivity.getInstance().addListener(this);

        mViewModel = ViewModelProviders.of(getActivity()).get(DeviceInfoViewModel.class);

        return v;
    }

    private void setStarted(boolean navigating) {
        if (navigating != mNavigating) {
            if (!navigating) {
                mViewModel.reset();
                mVoltageView.setText("0 V");
                mVoltageAngView.setText("0°");
                mCurrentView.setText("0 A");
                mCurrentAngView.setText("0°");

                mFixTime = 0;
                updateFixTime();

                if (lock != null) {
                    lock.setVisibility(View.GONE);
                }

                svCount = 0;
            }
            mNavigating = navigating;
        }
    }

    private void updateFixTime() {
        if (mFixTime == 0 || (GpsTestActivity.getInstance() != null && !GpsTestActivity.getInstance().mStarted)) {
            mFixTimeView.setText("");
            mFixTimeErrorView.setText("");
            mFixTimeErrorView.setVisibility(View.GONE);
        } else {
            if (DateTimeUtils.Companion.isTimeValid(mFixTime)) {
                mFixTimeErrorView.setVisibility(View.GONE);
                mFixTimeView.setVisibility(View.VISIBLE);
                mFixTimeView.setText(formatFixTimeDate(mFixTime));
            } else {
                // Error in fix time
                mFixTimeErrorView.setVisibility(View.VISIBLE);
                mFixTimeView.setVisibility(View.GONE);
                mFixTimeErrorView.setText(formatFixTimeDate(mFixTime));
            }
        }
    }

    private void updatePower() {
        Call<List<PowerSample>> call = Relecs.getInstance().getRelecsAPI().getSample(1);

        call.enqueue(new Callback<List<PowerSample>>() {
            @Override
            public void onResponse(Call<List<PowerSample>> call, Response<List<PowerSample>> response) {
                Object r = response.body();
                // List<PowerSample> samples = response.body();
            }

            @Override
            public void onFailure(Call<List<PowerSample>> call, Throwable t) {
                Toast.makeText(getContext(), "An error has occured", Toast.LENGTH_LONG).show();
            }

        });
    }

    /**
     * Returns a formatted version of the provided fixTime based on the width of the current display
     * @return a formatted version of the provided fixTime based on the width of the current display
     */
    private String formatFixTimeDate(long fixTime) {
        Context context = getContext();
        if (context == null) {
            return "";
        }
        return UIUtils.isWideEnoughForDate(context) ? mTimeAndDateFormat.format(fixTime) : mTimeFormat.format(fixTime);
    }

    @Override
    public void onResume() {
        super.onResume();
        GpsTestActivity gta = GpsTestActivity.getInstance();
        setStarted(gta.mStarted);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void gpsStart() {

    }

    @Override
    public void gpsStop() {

    }

    @Override
    public void onGpsStatusChanged(int event, GpsStatus status) {

    }

    public void onLocationChanged(Location location) {
        if (!UIUtils.isFragmentAttached(this)) {
            // Fragment isn't visible, so return to avoid IllegalStateException (see #85)
            return;
        }

        // Cache location for copy to clipboard operation
        mLocation = location;

        mFixTime = location.getTime();

        updateFixTime();
        updatePower();
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    public void onProviderEnabled(String provider) {
    }

    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onGnssFirstFix(int ttffMillis) {
        if (mViewModel != null) {
            mViewModel.setGotFirstFix(true);
        }
    }

    @Override
    public void onGnssFixAcquired() {
        showHaveFix();
    }

    @Override
    public void onGnssFixLost() {
        showLostFix();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onSatelliteStatusChanged(GnssStatus status) {
        updateGnssStatus(status);
    }

    @Override
    public void onGnssStarted() {
        setStarted(true);
    }

    @Override
    public void onGnssStopped() {
        setStarted(false);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
        // No-op
    }

    @Override
    public void onOrientationChanged(double orientation, double tilt) {
    }

    @Override
    public void onNmeaMessage(String message, long timestamp) {
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void updateGnssStatus(GnssStatus status) {
        setStarted(true);
        updateFixTime();

        if (!UIUtils.isFragmentAttached(this)) {
            // Fragment isn't visible, so return to avoid IllegalStateException (see #85)
            return;
        }

        final int length = status.getSatelliteCount();
        svCount = 0;
        mViewModel.reset();

        while (svCount < length) {
            SatelliteStatus satStatus = new SatelliteStatus(status.getSvid(svCount), SatelliteUtils.getGnssConstellationType(status.getConstellationType(svCount)),
                    status.getCn0DbHz(svCount),
                    status.hasAlmanacData(svCount),
                    status.hasEphemerisData(svCount),
                    status.usedInFix(svCount),
                    status.getElevationDegrees(svCount),
                    status.getAzimuthDegrees(svCount));
            if (SatelliteUtils.isGnssCarrierFrequenciesSupported()) {
                if (status.hasCarrierFrequencyHz(svCount)) {
                    satStatus.setHasCarrierFrequency(true);
                    satStatus.setCarrierFrequencyHz(status.getCarrierFrequencyHz(svCount));
                }
            }

            svCount++;
        }
    }

    private void showTimeErrorDialog(long time) {
        java.text.DateFormat format = SimpleDateFormat.getDateTimeInstance(java.text.DateFormat.LONG, java.text.DateFormat.LONG);

        TextView textView = (TextView) getLayoutInflater().inflate(R.layout.error_text_dialog, null);
        textView.setText(getString(R.string.error_time_message, format.format(time), DateTimeUtils.Companion.getNUM_DAYS_TIME_VALID()));

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.error_time_title);
        builder.setView(textView);
        Drawable drawable = getResources().getDrawable(android.R.drawable.ic_dialog_alert);
        DrawableCompat.setTint(drawable, getResources().getColor(R.color.colorPrimary));
        builder.setIcon(drawable);
        builder.setNeutralButton(R.string.main_help_close,
                (dialog, which) -> dialog.dismiss()
        );
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(getActivity());
        dialog.show();
    }

    private void showHaveFix() {
        if (lock != null) {
            UIUtils.showViewWithAnimation(lock, UIUtils.ANIMATION_DURATION_SHORT_MS);
        }
    }

    private void showLostFix() {
        if (lock != null) {
            UIUtils.hideViewWithAnimation(lock, UIUtils.ANIMATION_DURATION_SHORT_MS);
        }
    }
}
