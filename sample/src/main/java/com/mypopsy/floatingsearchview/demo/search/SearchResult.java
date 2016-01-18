package com.mypopsy.floatingsearchview.demo.search;

public class SearchResult {
    public String visibleUrl;
    public String url;
    public String title;
    public String content;


    public SearchResult(String title) {
        this(title, null);
    }

    public SearchResult(String title, String content) {
        this(title, content, null);
    }

    public SearchResult(String title, String content, String url) {
        this(title, content, url, url);
    }

    public SearchResult(String title, String content, String url, String visibleUrl) {
        this.title = title;
        this.content = content;
        this.url = url;
        this.visibleUrl = visibleUrl;
    }
}
