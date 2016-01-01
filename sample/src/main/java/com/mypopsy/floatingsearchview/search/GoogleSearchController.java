package com.mypopsy.floatingsearchview.search;


import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.PublishSubject;

@Singleton
public class GoogleSearchController implements SearchController {

    private static final int DEFAULT_DEBOUNCE = 700; // milliseconds

    private final GoogleSearch mSearch;
    private PublishSubject<String> mQuerySubject = PublishSubject.create();
    private Subscription mSubscription;
    private Listener mListener;

    @Inject
    public GoogleSearchController(GoogleSearch search) {
        mSearch = search;
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void search(String query) {
        ensureSubscribed();
        mQuerySubject.onNext(query);
    }

    @Override
    public void cancel() {
        if(mSubscription != null) mSubscription.unsubscribe();
        mSubscription = null;
    }

    private Observable<SearchResult[]> getQueryObservable(String query) {
        return mSearch.search(query)
                .flatMap(new Func1<Response, Observable<SearchResult[]>>() {
                    @Override
                    public Observable<SearchResult[]> call(Response response) {
                        if (response.responseData == null)
                            return Observable.error(new SearchException(response.responseDetails));
                        return Observable.just(response.responseData.results);
                    }
                })
                .retry(new Func2<Integer, Throwable, Boolean>() {
                    @Override
                    public Boolean call(Integer integer, Throwable throwable) {
                        return throwable instanceof InterruptedIOException;
                    }
                });
    }

    private void ensureSubscribed() {
        if(mSubscription != null) return;
        mSubscription = mQuerySubject.asObservable()
                .debounce(DEFAULT_DEBOUNCE, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .flatMap(new Func1<String, Observable<SearchResult[]>>() {
                             @Override
                             public Observable<SearchResult[]> call(String query) {
                                 if(mListener != null) mListener.onSearchStarted(query);
                                 return getQueryObservable(query)
                                         .onErrorResumeNext(new Func1<Throwable, Observable<SearchResult[]>>() {
                                             @Override
                                             public Observable<SearchResult[]> call(Throwable throwable) {
                                                 if(mListener != null) mListener.onSearchError(throwable);
                                                 return Observable.empty();
                                             }
                                         });
                             }
                         }
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<SearchResult[]>() {
                    @Override
                    public void call(SearchResult[] searchResults) {
                        if(mListener != null) mListener.onSearchResults(searchResults);
                    }
                });
    }
}
