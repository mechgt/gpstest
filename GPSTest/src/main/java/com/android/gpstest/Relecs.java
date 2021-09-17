package com.android.gpstest;

import com.fasterxml.jackson.core.JsonFactory;

import java.util.List;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class Relecs {

    private static Relecs instance = null;
    private Api relecsAPI;

    private Relecs() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Api.BASE_URL)
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
    String BASE_URL = "http://172.22.22.111:5000/";

    @GET("sample")
    Call<List<PowerSample>> getSample(@Query("gpsid") int id);
}
