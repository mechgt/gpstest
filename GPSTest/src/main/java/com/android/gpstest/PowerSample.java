package com.android.gpstest;

import com.android.gpstest.util.PreferenceUtils;
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

