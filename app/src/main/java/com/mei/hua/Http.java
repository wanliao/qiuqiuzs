package com.mei.hua;


import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class Http {
    public static void get(String url, Callback callback){
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        okHttpClient.newCall(request).enqueue(callback);
    }

    public static void post(String url, FormBody.Builder builder,Callback callback){
        OkHttpClient okHttpClient = new OkHttpClient();
        FormBody build = builder.build();
        Request request = new Request.Builder()
                .url(url)
                .post(build)
                .build();
        okHttpClient.newCall(request).enqueue(callback);
    }
}

