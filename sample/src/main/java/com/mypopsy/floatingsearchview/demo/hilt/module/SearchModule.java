package com.mypopsy.floatingsearchview.demo.hilt.module;

import com.mypopsy.floatingsearchview.demo.search.GoogleSearch;
import com.mypopsy.floatingsearchview.demo.search.GoogleSearchController;
import com.mypopsy.floatingsearchview.demo.search.SearchController;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import retrofit2.Retrofit;

@InstallIn(SingletonComponent.class)
@Module
public class SearchModule {

    @Provides
    @Singleton
    GoogleSearch provideGoogleSearch(Retrofit.Builder builder) {
        return builder.baseUrl(GoogleSearch.BASE_URL).build().create(GoogleSearch.class);
    }

    @Provides
    SearchController provideSearchController(GoogleSearchController searchController) {
        return searchController;
    }
}
