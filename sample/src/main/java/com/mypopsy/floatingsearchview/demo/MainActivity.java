package com.mypopsy.floatingsearchview.demo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable;
import androidx.appcompat.widget.ActionMenuView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.mypopsy.drawable.SearchArrowDrawable;
import com.mypopsy.drawable.ToggleDrawable;
import com.mypopsy.drawable.model.CrossModel;
import com.mypopsy.drawable.util.Bezier;
import com.mypopsy.floatingsearchview.demo.adapter.ArrayRecyclerAdapter;
import com.mypopsy.floatingsearchview.demo.search.SearchController;
import com.mypopsy.floatingsearchview.demo.search.SearchResult;
import com.mypopsy.floatingsearchview.demo.utils.PackageUtils;
import com.mypopsy.floatingsearchview.demo.utils.ViewUtils;
import com.mypopsy.widget.FloatingSearchView;

import java.util.ArrayList;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity implements
        ActionMenuView.OnMenuItemClickListener,
        SearchController.Listener {

    private static final int REQ_CODE_SPEECH_INPUT = 42;

    private FloatingSearchView mSearchView;
    private SearchAdapter mAdapter;

    @Inject SearchController mSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //DaggerAppComponent.builder().build().inject(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSearch.setListener(this);

        mSearchView = (FloatingSearchView) findViewById(R.id.search);
        mAdapter = new SearchAdapter();
        mSearchView.setAdapter(mAdapter);
        mSearchView.showLogo(true);
        mSearchView.setItemAnimator(new CustomSuggestionItemAnimator(mSearchView));

        updateNavigationIcon("Search");

        mSearchView.showIcon(shouldShowNavigationIcon());

        mSearchView.setOnIconClickListener(() -> {
            // toggle
            mSearchView.setActivated(!mSearchView.isActivated());
        });

        mSearchView.setOnSearchListener(text -> mSearchView.setActivated(false));

        mSearchView.setOnMenuItemClickListener(this);

        mSearchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence query, int start, int before, int count) {
                showClearButton(query.length() > 0 && mSearchView.isActivated());
                search(query.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        mSearchView.setOnSearchFocusChangedListener(focused -> {
            boolean textEmpty = mSearchView.getText().length() == 0;

            showClearButton(focused && !textEmpty);
            if(!focused) showProgressBar(false);
            mSearchView.showLogo(!focused && textEmpty);

            if (focused)
                mSearchView.showIcon(true);
            else
                mSearchView.showIcon(shouldShowNavigationIcon());
        });

        mSearchView.setText(null);
    }

    private void search(String query) {
        showProgressBar(mSearchView.isActivated());
        mSearch.search(query);
    }

    private void updateNavigationIcon(String title) {
        Context context = mSearchView.getContext();
        Drawable drawable = null;

        switch(title) {
            case "Search":
                drawable = new SearchArrowDrawable(context);
                break;
            case "Drawer":
                drawable = new DrawerArrowDrawable(context);
                break;
            case "Custom":
                drawable = new CustomDrawable(context);
                break;
        }

        if (drawable != null) {
            drawable = DrawableCompat.wrap(drawable);
            DrawableCompat.setTint(drawable, ViewUtils.getThemeAttrColor(context, android.R.attr.colorControlNormal));
            mSearchView.setIcon(drawable);
        }
    }

    private boolean shouldShowNavigationIcon() {
        return mSearchView.getMenu().findItem(R.id.menu_toggle_icon).isChecked();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && null != data) {
                ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                mSearchView.setActivated(true);
                mSearchView.setText(result.get(0));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSearch.cancel();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {

        switch ((String) item.getTitle()) {
            case "Clear":
                mSearchView.setText(null);
                mSearchView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                break;
            case "Icon Visible":
                item.setChecked(!item.isChecked());
                mSearchView.showIcon(item.isChecked());
                break;
            case "Text to speech":
                PackageUtils.startTextToSpeech(this, getString(R.string.speech_prompt), REQ_CODE_SPEECH_INPUT);
                break;
            case"Search":
            case "Drawer":
            case "Custom":
                updateNavigationIcon((String) item.getTitle());
                Toast.makeText(MainActivity.this, item.getTitle(), Toast.LENGTH_SHORT).show();
                break;
        }
        return true;
    }

    @Override
    public void onSearchStarted(String query) {
        //nothing to do
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onSearchResults(SearchResult... searchResults) {
        mAdapter.setNotifyOnChange(false);
        mAdapter.clear();
        if (searchResults != null) mAdapter.addAll(searchResults);
        mAdapter.setNotifyOnChange(true);
        mAdapter.notifyDataSetChanged();
        showProgressBar(false);
    }

    @Override
    public void onSearchError(Throwable throwable) {
        onSearchResults(getErrorResult(throwable));
    }

    private void onItemClick(SearchResult result) {
        mSearchView.setActivated(false);
        if(!TextUtils.isEmpty(result.url)) PackageUtils.start(this, Uri.parse(result.url));
    }


    private void showProgressBar(boolean show) {
        mSearchView.getMenu().findItem(R.id.menu_progress).setVisible(show);
    }

    private void showClearButton(boolean show) {
        mSearchView.getMenu().findItem(R.id.menu_clear).setVisible(show);
    }

    public void onGithubClick(View view) {
        PackageUtils.start(this, Uri.parse("https://github.com/renaudcerrato/FloatingSearchView"));
    }

    private static SearchResult getErrorResult(Throwable throwable) {
        return new SearchResult(
                "<font color='red'>"+
                "<b>"+throwable.getClass().getSimpleName()+":</b>"+
                "</font> " + throwable.getMessage());
    }

    private class SearchAdapter extends ArrayRecyclerAdapter<SearchResult, SuggestionViewHolder> {

        private LayoutInflater inflater;

        SearchAdapter() {
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public SuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
        TextView text, url;

        public SuggestionViewHolder(final View itemView) {
            super(itemView);
            left = (ImageView) itemView.findViewById(R.id.icon_start);
            right= (ImageView) itemView.findViewById(R.id.icon_end);
            text = (TextView) itemView.findViewById(R.id.text);
            url = (TextView) itemView.findViewById(R.id.url);
            left.setImageResource(R.drawable.ic_google);
            itemView.findViewById(R.id.text_container)
                    .setOnClickListener(v -> onItemClick(mAdapter.getItem(getBindingAdapterPosition())));
            right.setOnClickListener(v -> mSearchView.setText(text.getText()));
        }

        void bind(SearchResult result) {
            text.setText(Html.fromHtml(result.title));
            url.setText(result.visibleUrl);
            url.setVisibility(result.visibleUrl == null ? View.GONE : View.VISIBLE);
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
            holder.itemView.setTranslationX(0);
            holder.itemView.setTranslationY(-holder.itemView.getHeight());
            holder.itemView.setAlpha(0);
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

    private static class CustomDrawable extends ToggleDrawable {

        public CustomDrawable(Context context) {
            super(context);
            float radius = ViewUtils.dpToPx(9);

            CrossModel cross = new CrossModel(radius*2);

            // From circle to cross
            add(Bezier.quadrant(radius, 0), cross.downLine);
            add(Bezier.quadrant(radius, 90), cross.upLine);
            add(Bezier.quadrant(radius, 180), cross.upLine);
            add(Bezier.quadrant(radius, 270), cross.downLine);
        }
    }
}
