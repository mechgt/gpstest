package com.android.gpstest;

import androidx.annotation.RequiresPermission;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PowerSample {
    @JsonProperty("data")
    Data data;
}

class Data {
    @JsonProperty("message")
    String message;

    @JsonProperty("sample")
    Sample sample;

    Data() {
    }
}

class Sample {
    @JsonProperty("0")
    Phase A;

    @JsonProperty("1")
    Phase B;

    @JsonProperty("2")
    Phase C;

    Sample() {
    }
}

class Phase {
    @JsonProperty("volts")
    double volts;
    @JsonProperty("amps")
    double amps;

    @JsonProperty("volts_ang")
    double volts_ang;
    @JsonProperty("amps_ang")
    double amps_ang;

    Phase() {
    }

    public double getVolts() {
        return volts;
    }

    public void setVolts(double volts) {
        this.volts = volts;
    }

    public double getAmps() {
        return amps;
    }

    public void setAmps(double amps) {
        this.amps = amps;
    }

}

