package com.mypopsy.floatingsearchview.demo.search;


import android.content.Context;
import android.text.TextUtils;

import com.mypopsy.floatingsearchview.demo.hilt.entrypoint.GoogleSearchControllerEntryPoint;

import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Scheduler;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

public class GoogleSearchController implements SearchController {

    private static final int DEFAULT_DEBOUNCE = 700; // milliseconds

    private final GoogleSearch mSearch;
    private final Scheduler.@NonNull Worker mWorker;
    private final PublishSubject<String> mQuerySubject = PublishSubject.create();
    private Subscription mSubscription;
    private Listener mListener;

    @Inject
    public GoogleSearchController(@ApplicationContext Context context) {
        GoogleSearchControllerEntryPoint entryPoint = EntryPointAccessors.fromApplication(context, GoogleSearchControllerEntryPoint.class);
        mSearch = entryPoint.getGoogleSearch();
        mWorker = AndroidSchedulers.mainThread().createWorker();
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
                .flatMap((Func1<Response, Observable<SearchResult[]>>) response -> {
                    if (response.responseData == null)
                        return Observable.error(new SearchException(response.responseDetails));
                    return Observable.just(response.responseData.results);
                })
                .retry((integer, throwable) -> throwable instanceof InterruptedIOException);
    }

    private void ensureSubscribed() {
        if(mSubscription != null && !mSubscription.isUnsubscribed()) return;
        mSubscription = mQuerySubject.asObservable()
                .debounce(DEFAULT_DEBOUNCE, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .flatMap((Func1<String, Observable<SearchResult[]>>) query -> {
                    if(TextUtils.isEmpty(query)) return Observable.just(null);
                    notifyStarted(query);
                    return getQueryObservable(query)
                            .onErrorResumeNext((Func1<Throwable, Observable<SearchResult[]>>) throwable -> {
                                notifyError(throwable);
                                return Observable.empty();
                            });
                })
//                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(searchResults -> {
                    if(mListener != null) mListener.onSearchResults(searchResults);
                });
    }

    private void notifyStarted(final String query) {
        if(mListener == null) return;
        dispatchOnMainThread(() -> mListener.onSearchStarted(query));
    }

    private void notifyError(final Throwable throwable) {
        if(mListener == null) return;
        dispatchOnMainThread(() -> mListener.onSearchError(throwable));
    }

    private void dispatchOnMainThread(Action0 action) {
        mWorker.schedule((Runnable) action);
    }
}
