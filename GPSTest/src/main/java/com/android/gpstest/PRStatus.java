package com.android.gpstest;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

// Protection Relay Status
public class PRStatus {
    // Relay ID
    @JsonProperty("rid")
    int rid;

    @JsonProperty("zones")
    List<Zone> zones = new ArrayList<Zone>();
}

class Zone {
    @JsonProperty("status")
    int status;

    @JsonProperty("message")
    String msg;

    @JsonProperty("algo")
    int algo;
}

