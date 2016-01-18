package com.mypopsy.floatingsearchview.demo.search;

/**
 * Created by renaud on 01/01/16.
 */
public class SearchException extends Exception {
    public SearchException() {
    }

    public SearchException(String detailMessage) {
        super(detailMessage);
    }

    public SearchException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public SearchException(Throwable throwable) {
        super(throwable);
    }
}
