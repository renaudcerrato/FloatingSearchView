package com.mypopsy.floatingsearchview.demo.search;

public class SearchCursor {

    public long estimatedResultCount;
    public String moreResultsUrl;
    public long currentPageIndex;
    public Page pages[];

    public static class Page {
        public String start;
        public int label;
    }
}
