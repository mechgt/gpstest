package com.android.gpstest;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PRStatus {
    @JsonProperty("rid")
    int rid;

    @JsonProperty("status")
    int status;

    @JsonProperty("message")
    int cfgcnt;

    @JsonProperty("algo")
    int algo;
}
