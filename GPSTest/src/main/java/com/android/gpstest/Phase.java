package com.android.gpstest;

import com.android.gpstest.util.PreferenceUtils;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Phase {
    @JsonProperty("volts")
    double volts;
    @JsonProperty("amps")
    double amps;

    @JsonProperty("volts_ang")
    double volts_ang;
    @JsonProperty("amps_ang")
    double amps_ang;

    @JsonProperty("soc")
    double srcsoc;
    @JsonProperty("fracsec")
    double srcfracsec;

    double gpsoffset_deg = 0;
    double gpsoffset_ns = 0;

    Phase() {
    }

    public double getVolts() {
        return volts;
    }

    public double getAmps() {
        return amps;
    }

    public double getVolts_ang() {
        return volts_ang;
    }

    public double getAmps_ang() {
        return amps_ang;
    }

    public void applyTimestamp(double gpssoc, long gpsfracsec) {
        /*
         This function will adjust the original phasor according to the GPS timestamp.

         A timestamp is delivered from the source along with the phase angle which was derived
         from that timestamp. The original time source is assumed to be absolutely correct/stable
         in order to evaluate the GPS signal. If this timestamp and the GPS signal are the same
         then no changes will be applied.  If there is a difference, then the phase angle will be
         adjusted according to the offset.
        */

        int time_base = PreferenceUtils.getInt(Application.get().getString(R.string.pref_key_gps_time_base), 1000000000);
        int freqHz = PreferenceUtils.getInt(Application.get().getString(R.string.pref_key_gps_freqHz), 60);

        gpsoffset_ns = ((gpssoc - srcsoc) * time_base) + gpsfracsec - srcfracsec;    // number of fracs (e.g. nano seconds)

        double cycle = time_base / freqHz;          // fractions per 360 deg.
        gpsoffset_deg = (360 * gpsoffset_ns / cycle) % 360;

        amps_ang = (amps_ang + gpsoffset_deg) % 360;
        volts_ang = (volts_ang + gpsoffset_deg) % 360;

        // Apply revised timestamps
        srcsoc = gpssoc;
        srcfracsec = gpsfracsec;
    }
}
