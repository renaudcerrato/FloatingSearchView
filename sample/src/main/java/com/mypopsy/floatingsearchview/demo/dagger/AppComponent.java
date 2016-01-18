package com.mypopsy.floatingsearchview.demo.dagger;

import com.mypopsy.floatingsearchview.demo.MainActivity;
import com.mypopsy.floatingsearchview.demo.search.SearchController;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {RetrofitModule.class, SearchModule.class})
public interface AppComponent {
    void inject(MainActivity mainActivity);
    SearchController getSearch();
}
