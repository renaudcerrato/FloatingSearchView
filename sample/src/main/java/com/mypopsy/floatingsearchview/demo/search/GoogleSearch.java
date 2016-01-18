package com.mypopsy.floatingsearchview.demo.search;


import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;

// https://developers.google.com/web-search/docs/?csw=1#API_Overview
public interface GoogleSearch {

    String BASE_URL = "https://ajax.googleapis.com";

    @GET("/ajax/services/search/web?v=1.0")
    Observable<Response> search(@Query("q") String query);
}
