package com.android.gpstest;

import android.annotation.SuppressLint;

import com.android.gpstest.util.PreferenceUtils;
import com.fasterxml.jackson.core.JsonFactory;

import java.util.List;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public class Relecs {

    String BASE_URL = "http://172.22.22.111:5000/";

    private static Relecs instance = null;
    private Api relecsAPI;

    @SuppressLint("DefaultLocale")
    private Relecs() {
        String host = PreferenceUtils.getString(Application.get().getString(R.string.pref_key_host));
        if (host == null) {
            host = Application.get().getString(R.string.pref_gps_host_default);
        }

        int port = Integer.valueOf(PreferenceUtils.getString(R.string.pref_key_port));
        BASE_URL = String.format("http://%s:%d", host, port);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
        relecsAPI = retrofit.create(Api.class);
    }

    public static synchronized Relecs getInstance() {
        if (instance == null) {
            instance = new Relecs();
        }
        return instance;
    }

    public Api getRelecsAPI() {
        return relecsAPI;
    }
}

interface Api {
    @GET("sample")
    Call<PowerSample> getSample(@Query("gpsid") int id, @Query("fracsec") int fracsec);

    @GET("config")
    Call<Config> getConfig(@Query("gpsid") int id);

    @POST("submit")
    Call<PRStatus> submit(@Query("gpsid") int id, @Body PowerSample sample);
}
