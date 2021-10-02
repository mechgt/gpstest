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
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
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
import com.android.gpstest.util.PreferenceUtils;
import com.android.gpstest.util.SatelliteUtils;
import com.android.gpstest.util.UIUtils;

import com.android.gpstest.view.GpsPhasorView;

import java.text.DecimalFormat;
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
    private int mGpsId;

    private TextView mVoltsaView, mAmpsaView,
            mVoltsbView, mAmpsbView, mVoltscView, mAmpscView,
            mFixTimeView, mFixTimeErrorView, mDeltaTimeView,
            mGpsOffsetView, mCfgFreqView, mCfgTimeBaseView,
            mSpoofTime;

    private Switch mSpoofSim;

    private GpsPhasorView mPhasorView;

    private Location mLocation;

    private int svCount;

    private long mFixTime;
    private long mFracOfSec;

    private PowerSample mPowerSample;

    private boolean mNavigating;
    private final DecimalFormat mCycleFormat = new DecimalFormat("0E0 cycles/sec");

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
        mCfgFreqView = v.findViewById(R.id.freq);
        mCfgTimeBaseView = v.findViewById(R.id.time_base);
        mSpoofSim = v.findViewById(R.id.gps_spoof_sim);
        mSpoofSim.setChecked(PreferenceUtils.getInt(R.string.pref_key_sim_spoof, 0) > 0);
        mSpoofSim.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    PreferenceUtils.saveInt(R.string.pref_key_sim_spoof, 0);
                } else {
                    PreferenceUtils.saveInt(R.string.pref_key_sim_spoof, 1);
                }
            }
        });
        mSpoofTime = v.findViewById(R.id.spoof_time);
        mSpoofTime.setText(getTimeString(0));
        mPhasorView = v.findViewById(R.id.phasor_view);
        mGpsOffsetView = v.findViewById(R.id.gps_diff);
        v.findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Phase.resetSpoof();
                mSpoofTime.setText(getTimeString(0));
            }
        });

        mGpsId = Integer.parseInt(PreferenceUtils.getString(R.string.pref_key_id));

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
        long start = System.nanoTime();
        // System.nanoTime()
        Call<PowerSample> call = Relecs.getInstance().getRelecsAPI().getSample(mGpsId, 10);
        call.enqueue(new Callback<PowerSample>() {
            @SuppressLint("DefaultLocale")
            @Override
            public void onResponse(Call<PowerSample> call, Response<PowerSample> response) {
                long delta = System.nanoTime() - start;

                mPowerSample = response.body();
                if (mPowerSample != null) {
                    Phase pA = mPowerSample.data.sample.A;
                    Phase pB = mPowerSample.data.sample.B;
                    Phase pC = mPowerSample.data.sample.C;

                    if (mSpoofSim.isChecked()) {
                        double spoof = Phase.adjSpoofing(1e7);
                        mSpoofTime.setText(getTimeString(spoof));
                    }

                    pA.applyTimestamp(mFixTime/1e3, mFracOfSec);
                    pB.applyTimestamp(mFixTime/1e3, mFracOfSec);
                    pC.applyTimestamp(mFixTime/1e3, mFracOfSec);

                    mGpsOffsetView.setText(String.format("%s, %.1f°", getTimeString(pA.gpsoffset_ns), pA.gpsoffset_deg));

                    String phasor_fmt = "%.2f∠%.1f°";
                    mVoltsaView.setText(String.format(phasor_fmt, pA.volts/1e3, pA.volts_ang));
                    mAmpsaView.setText(String.format(phasor_fmt, pA.amps, pA.amps_ang));
                    mVoltsbView.setText(String.format(phasor_fmt, pB.volts/1e3, pB.volts_ang));
                    mAmpsbView.setText(String.format(phasor_fmt, pB.amps, pB.amps_ang));
                    mVoltscView.setText(String.format(phasor_fmt, pC.volts/1e3, pC.volts_ang));
                    mAmpscView.setText(String.format(phasor_fmt, pC.amps, pC.amps_ang));

                    // Display request latency time for debugging
                    mDeltaTimeView.setText(getTimeString(delta));

                    mPhasorView.setPhasors(new Phase[]{pA, pB, pC});
                }
            }

            @Override
            public void onFailure(Call<PowerSample> call, Throwable t) {
                Toast.makeText(getContext(), "A power read error has occurred", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void submitSample(PowerSample sample) {
        Call<PRStatus> call = Relecs.getInstance().getRelecsAPI().submit(mGpsId, sample);
        call.enqueue(new Callback<PRStatus>() {
            @SuppressLint("DefaultLocale")
            @Override
            public void onResponse(Call<PRStatus> call, Response<PRStatus> response) {
                PRStatus relayStatus = response.body();
                if (relayStatus != null) {
                    for (int zid = 0; zid < relayStatus.zones.size() ; zid++) {
                        mPhasorView.setRelay(relayStatus.zones.get(zid).status);
                    }
                }
            }

            @Override
            public void onFailure(Call<PRStatus> call, Throwable t) {
                Toast.makeText(getContext(), "A submission error occurred.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void readConfig() {
        readConfig(false);
    }

    private void readConfig(boolean force) {
        if (force || true) {
            Call<Config> call = Relecs.getInstance().getRelecsAPI().getConfig(mGpsId);
            call.enqueue(new Callback<Config>() {
                @SuppressLint("DefaultLocale")
                @Override
                public void onResponse(Call<Config> call, Response<Config> response) {
                    Config config = response.body();
                    if (config != null) {
                        Toast.makeText(getContext(), "Config read: " + String.valueOf(config.soc), Toast.LENGTH_LONG).show();
                        PreferenceUtils.saveInt(Application.get().getString(R.string.pref_key_gps_time_base), config.time_base);
                        PreferenceUtils.saveInt(Application.get().getString(R.string.pref_key_gps_freqHz), GpsPhasorUtils.fnomToHz(config.fnom));

                        mCfgTimeBaseView.setText(mCycleFormat.format(config.time_base));
                        mCfgFreqView.setText(String.format("%d Hz", GpsPhasorUtils.fnomToHz(config.fnom)));
                    }
                }

                @Override
                public void onFailure(Call<Config> call, Throwable t) {
                    Toast.makeText(getContext(), "An error occurred retrieving config", Toast.LENGTH_LONG).show();
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

        // Update preferences in case they've changed
        Relecs.refreshPrefs();
        mGpsId = Integer.parseInt(PreferenceUtils.getString(R.string.pref_key_id));
        readConfig();

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
        mFracOfSec = (long) (System.nanoTime() % 1e9);

        updateFixTime();

        readPower();
        submitSample(mPowerSample);
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
        mPhasorView.setStarted();
        setStarted(true);
    }

    @Override
    public void onGnssStopped() {
        mPhasorView.setStopped();
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

    /**
     * Gets a string of elapsed time adjusted to the nearest time unit.
     *
     * @param ns    Elapsed time to be displayed, in nanoseconds.
     * @return      A formatted string such as 300 ms, 100 ns, or 1000 sec.
     */
    private String getTimeString(double ns) {
        double scale;
        String units;
        if (Math.abs(ns) > 60e9) {
            scale = 1e-9 / 60;
            units = "%1.0f mins.";
        } else if (Math.abs(ns) > 1e9) {
            scale = 1e-9;
            units = "%1.0f sec.";
        } else if (Math.abs(ns) > 1e6) {
            scale = 1e-6;
            units = "%1.0f ms";
        } else if (Math.abs(ns) > 1e3) {
            scale = 1e-3;
            units = "%1.0f us";
        } else if (ns == 0) {
            return "0 ms";
        } else {
            scale = 1;
            units = "%1.0f ns";
        }

        return String.format(units, ns * scale);
    }
}
