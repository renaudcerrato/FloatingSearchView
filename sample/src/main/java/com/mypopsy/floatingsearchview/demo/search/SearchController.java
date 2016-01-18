package com.mypopsy.floatingsearchview.demo.search;

import android.support.annotation.MainThread;

public interface SearchController {

    interface Listener {
        @MainThread void onSearchStarted(String query);
        @MainThread void onSearchResults(SearchResult ...results);
        @MainThread void onSearchError(Throwable throwable);
    }

    void setListener(Listener listener);
    void search(String query);
    void cancel();
}
