package com.android.gpstest;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Config {
    @JsonProperty("annmr")
    int annmr;

    @JsonProperty("anunit")
    int anunit;

    @JsonProperty("cfgcnt")
    int cfgcnt;

    @JsonProperty("chanam")
    String chnam;

    @JsonProperty("data_rate")
    int data_rate;

    @JsonProperty("dgnmr")
    int dgnmr;

    @JsonProperty("digunit")
    int digunit;

    @JsonProperty("fnom")
    int fnom;

    @JsonProperty("format")
    int format;

    @JsonProperty("fracsec")
    int fracsec;

    @JsonProperty("idcode")
    int idcode;

    @JsonProperty("num_pmu")
    int num_pmu;

    @JsonProperty("phunit")
    int phunit;

    @JsonProperty("phnmr")
    int phnmr;

    @JsonProperty("soc")
    int soc;

    @JsonProperty("stn")
    String stn;

    @JsonProperty("time_base")
    int time_base;

}
