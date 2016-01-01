package com.mypopsy.floatingsearchview;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.annotation.AttrRes;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.mypopsy.drawable.SearchArrowDrawable;
import com.mypopsy.floatingsearchview.adapter.ArrayRecyclerAdapter;
import com.mypopsy.floatingsearchview.search.GoogleSearch;
import com.mypopsy.floatingsearchview.search.GoogleSearchFactory;
import com.mypopsy.floatingsearchview.search.Response;
import com.mypopsy.floatingsearchview.search.SearchResult;
import com.mypopsy.widget.FloatingSearchView;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.PublishSubject;

public class MainActivity extends AppCompatActivity implements ActionMenuView.OnMenuItemClickListener{

    private static final int REQ_CODE_SPEECH_INPUT = 42;

    private FloatingSearchView mSearchView;
    private GoogleSearch mSearch;
    private SearchAdapter mAdapter;
    private PublishSubject<String> mQuerySubject = PublishSubject.create();
    private Subscription mSubscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSearch = GoogleSearchFactory.get();

        mSearchView = (FloatingSearchView) findViewById(R.id.search);
        mSearchView.setAdapter(mAdapter = new SearchAdapter());
        mSearchView.showLogo(true);
        mSearchView.setItemAnimator(new CustomSuggestionItemAnimator(mSearchView));

        updateNavigationIcon(R.id.menu_icon_search);

        mSearchView.showNavigationIcon(shouldShowNavigationIcon());

        mSearchView.setOnNavigationClickListener(new FloatingSearchView.OnNavigationClickListener() {
            @Override
            public void onNavigationClick() {
                // toggle
                mSearchView.setActivated(!mSearchView.isActivated());
            }
        });

        mSearchView.setOnSearchListener(new FloatingSearchView.OnSearchListener() {
            @Override
            public void onSearchAction(CharSequence text) {
                mSearchView.setActivated(false);
            }
        });

        mSearchView.setOnMenuItemClickListener(this);

        mSearchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence query, int start, int before, int count) {
                showClearButton(query.length() > 0 && mSearchView.isActivated());
                doSearch(query.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        mSearchView.setOnSearchFocusChangedListener(new FloatingSearchView.OnSearchFocusChangedListener() {
            @Override
            public void onFocusChanged(final boolean focused) {
                boolean textEmpty = mSearchView.getText().length() == 0;

                showClearButton(focused && !textEmpty);
                mSearchView.showLogo(!focused && textEmpty);

                if (focused)
                    mSearchView.showNavigationIcon(true);
                else
                    mSearchView.showNavigationIcon(shouldShowNavigationIcon());
            }
        });

        mSearchView.setText(null);
    }

    private void doSearch(String query) {
        mQuerySubject.onNext(query);
    }

    private void updateNavigationIcon(int itemId) {
        Context context = mSearchView.getContext();
        Drawable drawable = null;

        switch(itemId) {
            case R.id.menu_icon_search:
                drawable = new SearchArrowDrawable(context);
                break;
            case R.id.menu_icon_drawer:
                drawable = new android.support.v7.graphics.drawable.DrawerArrowDrawable(context);
                break;
            case R.id.menu_icon_custom:
                //TODO
                drawable = null;
                break;
        }
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, getThemeAttrColor(context, R.attr.colorControlNormal));
        mSearchView.setIcon(drawable);
    }

    private boolean shouldShowNavigationIcon() {
        return mSearchView.getMenu().findItem(R.id.menu_toggle_icon).isChecked();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mSubscription = mQuerySubject.asObservable()
                .debounce(700, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .flatMap(new Func1<String, Observable<SearchResult[]>>() {
                             @Override
                             public Observable<SearchResult[]> call(String query) {
                                 return getQueryObservable(query)
                                         .onErrorResumeNext(new Func1<Throwable, Observable<SearchResult[]>>() {
                                             @Override
                                             public Observable<SearchResult[]> call(Throwable throwable) {
                                                 Log.e(getClass().getSimpleName(), throwable.getMessage(), throwable);
                                                 return Observable.just(new SearchResult[] {getErrorResult(throwable)});
                                             }
                                         });
                             }
                         }
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<SearchResult[]>() {
                    @Override
                    public void call(SearchResult[] searchResults) {
                        onSearchResults(searchResults);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mSubscription != null) mSubscription.unsubscribe();
        mSubscription = null;
    }

    private Observable<SearchResult[]> getQueryObservable(String query) {
        return mSearch.search(query)
                .flatMap(new Func1<Response, Observable<SearchResult[]>>() {
                    @Override
                    public Observable<SearchResult[]> call(Response response) {
                        if (response.responseData == null)
                            return Observable.error(new IllegalStateException(response.responseDetails));
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    mSearchView.setActivated(true);
                    mSearchView.setText(result.get(0));
                }
                break;
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_clear:
                mSearchView.setText(null);
                mSearchView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                break;
            case R.id.menu_toggle_icon:
                item.setChecked(!item.isChecked());
                mSearchView.showNavigationIcon(item.isChecked());
                break;
            case R.id.menu_tts:
                startTextToSpeech();
                break;
            case R.id.menu_icon_search:
            case R.id.menu_icon_drawer:
            case R.id.menu_icon_custom:
                updateNavigationIcon(item.getItemId());
                Toast.makeText(MainActivity.this, item.getTitle(), Toast.LENGTH_SHORT).show();
                break;
        }
        return true;
    }

    private void onSearchResults(SearchResult ...searchResults) {
        mAdapter.setNotifyOnChange(false);
        mAdapter.clear();
        if (searchResults != null) mAdapter.addAll(searchResults);
        mAdapter.setNotifyOnChange(true);
        mAdapter.notifyDataSetChanged();
    }

    private void startTextToSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(this, getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showClearButton(boolean show) {
        mSearchView.getMenu().findItem(R.id.menu_clear).setVisible(show);
    }

    public void onGithubClick(View view) {
        startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(BuildConfig.PROJECT_URL)));
    }

    private static SearchResult getErrorResult(Throwable throwable) {
        return new SearchResult(
                "<font color='red'>"+
                "<b>"+throwable.getClass().getSimpleName()+":</b>"+
                "</font> " + throwable.getMessage());
    }

    private static int getThemeAttrColor(Context context, @AttrRes int attr) {
        final int[] TEMP_ARRAY = new int[1];
        TEMP_ARRAY[0] = attr;
        TypedArray a = context.obtainStyledAttributes(null, TEMP_ARRAY);
        try {
            return a.getColor(0, 0);
        } finally {
            a.recycle();
        }
    }

    private class SearchAdapter extends ArrayRecyclerAdapter<SearchResult, SuggestionViewHolder> {

        private LayoutInflater inflater;

        SearchAdapter() {
            setHasStableIds(true);
        }

        @Override
        public SuggestionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if(inflater == null) inflater = LayoutInflater.from(parent.getContext());
            return new SuggestionViewHolder(inflater.inflate(R.layout.item_suggestion, parent, false));
        }

        @Override
        public void onBindViewHolder(SuggestionViewHolder holder, int position) {
            holder.bind(getItem(position));
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }

    private class SuggestionViewHolder extends RecyclerView.ViewHolder {

        ImageView left,right;
        TextView text;

        public SuggestionViewHolder(final View itemView) {
            super(itemView);
            left = (ImageView) itemView.findViewById(R.id.icon_start);
            right= (ImageView) itemView.findViewById(R.id.icon_end);
            text = (TextView) itemView.findViewById(R.id.text);
            left.setImageResource(R.drawable.ic_google);
            text.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mSearchView.setText(text.getText());
                    mSearchView.setActivated(false);
                }
            });
            right.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mSearchView.setText(text.getText().toString());
                }
            });
        }

        void bind(SearchResult result) {
            this.text.setText(Html.fromHtml(result.title));
        }
    }

    private static class CustomSuggestionItemAnimator extends BaseItemAnimator {

        private final static Interpolator INTERPOLATOR_ADD = new DecelerateInterpolator(3f);
        private final static Interpolator INTERPOLATOR_REMOVE = new AccelerateInterpolator(3f);

        private final FloatingSearchView mSearchView;

        public CustomSuggestionItemAnimator(FloatingSearchView searchView) {
            mSearchView = searchView;
            setAddDuration(150);
            setRemoveDuration(150);
        }

        @Override
        protected void preAnimateAdd(RecyclerView.ViewHolder holder) {
            if(!mSearchView.isActivated()) return;
            ViewCompat.setTranslationX(holder.itemView, 0);
            ViewCompat.setTranslationY(holder.itemView, -holder.itemView.getHeight());
            ViewCompat.setAlpha(holder.itemView, 0);
        }

        @Override
        protected ViewPropertyAnimatorCompat onAnimateAdd(RecyclerView.ViewHolder holder) {
            if(!mSearchView.isActivated()) return null;
            return ViewCompat.animate(holder.itemView)
                    .translationY(0)
                    .alpha(1)
                    .setStartDelay((getAddDuration() / 2) * holder.getLayoutPosition())
                    .setInterpolator(INTERPOLATOR_ADD);
        }

        @Override
        public boolean animateMove(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
            dispatchMoveFinished(holder);
            return false;
        }

        @Override
        protected ViewPropertyAnimatorCompat onAnimateRemove(RecyclerView.ViewHolder holder) {
            return ViewCompat.animate(holder.itemView)
                    .alpha(0)
                    .setStartDelay(0)
                    .setInterpolator(INTERPOLATOR_REMOVE);
        }

    }
}
