package com.mypopsy.floatingsearchview.search;

public interface SearchController {

    interface Listener {
        void onSearchStarted(String query);
        void onSearchResults(SearchResult ...results);
        void onSearchError(Throwable throwable);
    }

    void setListener(Listener listener);
    void search(String query);
    void cancel();
}
