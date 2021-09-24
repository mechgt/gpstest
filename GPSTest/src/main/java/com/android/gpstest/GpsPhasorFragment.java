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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GpsPhasorFragment extends Fragment implements GpsTestListener {

    public final static String TAG = "GpsPhasorFragment";

    @SuppressLint("SimpleDateFormat") // See #117
    SimpleDateFormat mTimeFormat = new SimpleDateFormat(
            DateFormat.is24HourFormat(Application.get().getApplicationContext())
                    ? "HH:mm:ss.SSS" : "hh:mm:ss.SSS a");

    SimpleDateFormat mTimeAndDateFormat = new SimpleDateFormat(
            DateFormat.is24HourFormat(Application.get().getApplicationContext())
                    ? "HH:mm:ss.SSS MMM d, yyyy z" : "hh:mm:ss.SSS a MMM d, yyyy z");

    private Resources mRes;

    private TextView mVoltsaView, mAmpsaView,
            mVoltsbView, mAmpsbView, mVoltscView, mAmpscView,
            mFixTimeView, mFixTimeErrorView, mDeltaTimeView;

    private Location mLocation;

    private int svCount;

    private long mFixTime;
    private double mFracOfSec;

    private PowerSample mPowerSample;

    private boolean mNavigating;

    DeviceInfoViewModel mViewModel;

    ImageView lock;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        mRes = getResources();

        View v = inflater.inflate(R.layout.gps_phasor, container,false);

        mVoltsaView = v.findViewById(R.id.voltsa);
        mAmpsaView = v.findViewById(R.id.ampsa);
        mVoltsbView = v.findViewById(R.id.voltsb);
        mAmpsbView = v.findViewById(R.id.ampsb);
        mVoltscView = v.findViewById(R.id.voltsc);
        mAmpscView = v.findViewById(R.id.ampsc);
        mFixTimeView = v.findViewById(R.id.fix_time);
        mDeltaTimeView = v.findViewById(R.id.delta_time);
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

                mVoltsaView.setText("0 V");
                mAmpsaView.setText("0 A");

                mDeltaTimeView.setText("0ms");

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

    private void readPower() {
        long start = System.currentTimeMillis();
        // System.nanoTime()
        Call<PowerSample> call = Relecs.getInstance().getRelecsAPI().getSample(1, 10);
        call.enqueue(new Callback<PowerSample>() {
            @Override
            public void onResponse(Call<PowerSample> call, Response<PowerSample> response) {
                long delta = System.currentTimeMillis() - start;

                mPowerSample = response.body();
                if (mPowerSample != null) {
                    Phase pA = mPowerSample.data.sample.A;
                    Phase pB = mPowerSample.data.sample.B;
                    Phase pC = mPowerSample.data.sample.C;

                    String phasor_fmt = "%.2f∠%.2f";
                    mVoltsaView.setText(String.format(phasor_fmt, pA.volts, pA.volts_ang));
                    mAmpsaView.setText(String.format(phasor_fmt, pA.amps, pA.amps_ang));
                    mVoltsbView.setText(String.format(phasor_fmt, pB.volts, pB.volts_ang));
                    mAmpsbView.setText(String.format(phasor_fmt, pB.amps, pB.amps_ang));
                    mVoltscView.setText(String.format(phasor_fmt, pC.volts, pC.volts_ang));
                    mAmpscView.setText(String.format(phasor_fmt, pC.amps, pC.amps_ang));

                    // Display request latency time for debugging
                    mDeltaTimeView.setText(String.valueOf(delta) + " ms");
                }
            }

            @Override
            public void onFailure(Call<PowerSample> call, Throwable t) {
                Toast.makeText(getContext(), "An error has occurred", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void readConfig() {
        readConfig(false);
    }

    private void readConfig(boolean force) {
        if (force || true) {
            Call<Config> call = Relecs.getInstance().getRelecsAPI().getConfig(1);
            call.enqueue(new Callback<Config>() {
                @Override
                public void onResponse(Call<Config> call, Response<Config> response) {
                    Toast.makeText(getContext(), "Config read: " + String.valueOf(response.body().soc), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onFailure(Call<Config> call, Throwable t) {
                    Toast.makeText(getContext(), "An error occurred retrieving Config", Toast.LENGTH_LONG).show();
                }
            });
        }
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
        readConfig();
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
        mFracOfSec = System.nanoTime(); // TODO: Is this required?  Calibrate nanoseconds to a boundary

        updateFixTime();
        readConfig();
        readPower();
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
