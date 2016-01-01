package com.mypopsy.floatingsearchview.dagger;

import com.mypopsy.floatingsearchview.MainActivity;
import com.mypopsy.floatingsearchview.search.SearchController;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {RetrofitModule.class, SearchModule.class})
public interface AppComponent {
    void inject(MainActivity mainActivity);
    SearchController getSearch();
}
