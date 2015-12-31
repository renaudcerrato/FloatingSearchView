package com.mypopsy.widget;

import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.AttrRes;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.MenuRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.v4.view.MarginLayoutParamsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorCompat;
import android.support.v7.widget.ActionMenuView;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.InflateException;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.mypopsy.drawable.SearchArrowDrawable;
import com.mypopsy.drawable.ToggleDrawable;
import com.mypopsy.widget.internal.RoundRectDrawableWithShadow;
import com.mypopsy.widget.internal.SuggestionItemDecorator;
import com.mypopsy.widget.internal.ViewUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.mypopsy.widget.internal.RoundRectDrawableWithShadow.BOTTOM;
import static com.mypopsy.widget.internal.RoundRectDrawableWithShadow.LEFT;
import static com.mypopsy.widget.internal.RoundRectDrawableWithShadow.RIGHT;
import static com.mypopsy.widget.internal.RoundRectDrawableWithShadow.TOP;


public class FloatingSearchView extends RelativeLayout {

    private static final int DEFAULT_BACKGROUND_COLOR = 0x90000000;
    private static final int DEFAULT_CONTENT_COLOR = 0xfff0f0f0;

    private static final int DEFAULT_RADIUS = 2;
    private static final int DEFAULT_ELEVATION = 2;
    private static final int DEFAULT_MAX_ELEVATION = 2;

    private static final long DEFAULT_DURATION_ENTER = 300;
    private static final long DEFAULT_DURATION_EXIT = 400;

    private static final Interpolator DECELERATE = new DecelerateInterpolator(3f);
    private static final Interpolator ACCELERATE = new AccelerateInterpolator(2f);

    private RecyclerView.AdapterDataObserver mAdapterObserver = new android.support.v7.widget.RecyclerView.AdapterDataObserver() {

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            onChanged();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            onChanged();
        }

        @Override
        public void onChanged() {
            updateSuggestionsVisibility();
        }
    };

    public interface OnSearchListener {
        void onSearchAction(CharSequence text);
    }

    public interface OnNavigationClickListener {
        void onNavigationClick();
    }

    public interface OnSearchFocusChangedListener {
        void onFocusChanged(boolean focused);
    }

    final private EditText mSearchInput;
    final private ImageView mNavButtonView;
    final private RecyclerView mRecyclerView;
    final private ViewGroup mSearchContainer;
    final private View mDivider;
    final private ActionMenuView mActionMenu;

    final private Activity mActivity;

    final private RoundRectDrawableWithShadow mSearchBackground;
    final private SuggestionItemDecorator mCardDecorator;

    final private List<Integer> mAlwaysShownMenuViews = new ArrayList<>();

    private OnSearchFocusChangedListener mFocusListener;
    private OnNavigationClickListener mNavigationClickListener;
    private Drawable mBackgroundDrawable;
    
    public FloatingSearchView(Context context) {
        this(context, null);
    }

    public FloatingSearchView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.floatingSearchViewStyle);
    }

    public FloatingSearchView(Context context, AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mActivity = getActivity();

        setFocusable(true);
        setFocusableInTouchMode(true);

        inflate(getContext(), R.layout.fsv_floating_search_layout, this);

        mSearchInput = (EditText)findViewById(R.id.fsv_search_text);
        mNavButtonView = (ImageView) findViewById(R.id.fsv_search_action_navigation);
        mRecyclerView = (RecyclerView) findViewById(R.id.fsv_suggestions_list);
        mDivider = findViewById(R.id.fsv_suggestions_divider);
        mSearchContainer = (ViewGroup) findViewById(R.id.fsv_search_container);
        mActionMenu = (ActionMenuView) findViewById(R.id.fsv_search_action_menu);

        //TODO: move elevation parameters to XML attributes
        mSearchBackground = new RoundRectDrawableWithShadow(
                DEFAULT_CONTENT_COLOR, ViewUtils.dpToPx(DEFAULT_RADIUS),
                ViewUtils.dpToPx(DEFAULT_ELEVATION),
                ViewUtils.dpToPx(DEFAULT_MAX_ELEVATION));
        mSearchBackground.setAddPaddingForCorners(true);

        mCardDecorator = new SuggestionItemDecorator(mSearchBackground.mutate());

        applyXmlAttributes(attrs, defStyleAttr, 0);
        setupViews();
    }

    private void applyXmlAttributes(AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        final TypedArray a = getContext().obtainStyledAttributes(attrs,
                R.styleable.FloatingSearchView, defStyleAttr, defStyleRes);

        // Search bar width
        View suggestionsContainer = findViewById(R.id.fsv_suggestions_container);
        int searchBarWidth = a.getDimensionPixelSize(R.styleable.FloatingSearchView_fsv_searchBarWidth,
                mSearchContainer.getLayoutParams().width);
        mSearchContainer.getLayoutParams().width = searchBarWidth;
        suggestionsContainer.getLayoutParams().width = searchBarWidth;

        // Divider
        mDivider.setBackgroundDrawable(a.getDrawable(R.styleable.FloatingSearchView_android_divider));
        int dividerHeight = a.getDimensionPixelSize(R.styleable.FloatingSearchView_android_dividerHeight, -1);

        MarginLayoutParams dividerLP = (MarginLayoutParams) mDivider.getLayoutParams();

        if(mDivider.getBackground() != null && dividerHeight != -1)
            dividerLP.height = dividerHeight;

        float maxShadowSize = mSearchBackground.getMaxShadowSize();
        float cornerRadius = mSearchBackground.getCornerRadius();
        int horizontalPadding = (int) (RoundRectDrawableWithShadow.calculateHorizontalPadding(
                maxShadowSize, cornerRadius, false) + .5f);

        dividerLP.setMargins(horizontalPadding, dividerLP.topMargin, horizontalPadding, dividerLP.bottomMargin);
        mDivider.setLayoutParams(dividerLP);

        // Content inset
        MarginLayoutParams searchParams = (MarginLayoutParams) mSearchInput.getLayoutParams();

        int contentInsetStart = a.getDimensionPixelSize(R.styleable.FloatingSearchView_contentInsetStart,
                MarginLayoutParamsCompat.getMarginStart(searchParams));
        int contentInsetEnd = a.getDimensionPixelSize(R.styleable.FloatingSearchView_contentInsetEnd,
                MarginLayoutParamsCompat.getMarginEnd(searchParams));

        MarginLayoutParamsCompat.setMarginStart(searchParams, contentInsetStart);
        MarginLayoutParamsCompat.setMarginEnd(searchParams, contentInsetEnd);

        // anything else
        setContentBackgroundColor(a.getColor(R.styleable.FloatingSearchView_fsv_contentBackgroundColor, DEFAULT_CONTENT_COLOR));
        setRadius(a.getDimensionPixelSize(R.styleable.FloatingSearchView_fsv_cornerRadius, ViewUtils.dpToPx(DEFAULT_RADIUS)));
        inflateMenu(a.getResourceId(R.styleable.FloatingSearchView_fsv_menu, 0));
        setPopupTheme(a.getResourceId(R.styleable.FloatingSearchView_popupTheme, 0));
        setHint(a.getString(R.styleable.FloatingSearchView_android_hint));
        setNavigationIcon(a.getDrawable(R.styleable.FloatingSearchView_navigationIcon));

        a.recycle();
    }

    private void setupViews() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            mSearchContainer.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);

        mSearchContainer.setBackgroundDrawable(mSearchBackground);
        mSearchContainer.setMinimumHeight((int) mSearchBackground.getMinHeight());
        mSearchContainer.setMinimumWidth((int) mSearchBackground.getMinWidth());

        mRecyclerView.addItemDecoration(mCardDecorator);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setVisibility(View.INVISIBLE);

        mBackgroundDrawable = getBackground();

        if(mBackgroundDrawable != null)
            mBackgroundDrawable = mBackgroundDrawable.mutate();
        else
            mBackgroundDrawable = new ColorDrawable(DEFAULT_BACKGROUND_COLOR);

        setBackgroundDrawable(mBackgroundDrawable);
        mBackgroundDrawable.setAlpha(0);

        mActionMenu.setLayoutTransition(getDefaultMenuLayoutTransition());

        if(getNavigationIcon() == null) {
            Drawable icon = new SearchArrowDrawable(getContext());
            int colorControl = ViewUtils.getThemeAttrColor(getContext(), R.attr.colorControlNormal);
            setNavigationIcon(ViewUtils.getTinted(icon, colorControl));
        }

        mNavButtonView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mNavigationClickListener != null)
                    mNavigationClickListener.onNavigationClick();
            }
        });

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!isActivated()) return false;
                setActivated(false);
                return true;
            }
        });

        mSearchInput.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus != isActivated()) setActivated(hasFocus);
            }
        });

        mSearchInput.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if (keyCode != KeyEvent.KEYCODE_ENTER) return false;
                setActivated(false);
                return true;
            }
        });
    }

    public void setRadius(float radius) {
        mSearchBackground.setCornerRadius(radius);
        mCardDecorator.setCornerRadius(radius);
    }

    public void setContentBackgroundColor(@ColorInt int color) {
        mSearchBackground.setColor(color);
        mCardDecorator.setBackgroundColor(color);
        mActionMenu.setBackgroundColor(color);
    }

    public void setPopupTheme(@StyleRes int resId) {
        mActionMenu.setPopupTheme(resId);
    }

    public void inflateMenu(@MenuRes int menuRes) {
        if(menuRes == 0) return;
        getActivity().getMenuInflater().inflate(menuRes, mActionMenu.getMenu());

        XmlResourceParser parser = null;
        try {
            //noinspection ResourceType
            parser = getResources().getLayout(menuRes);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            parseMenu(parser, attrs);
        } catch (XmlPullParserException | IOException e) {
            throw new InflateException("Error inflating menu XML", e);
        } finally {
            if (parser != null) parser.close();
        }
    }

    public void setOnSearchListener(final OnSearchListener listener) {
        mSearchInput.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode != KeyEvent.KEYCODE_ENTER) return false;
                listener.onSearchAction(mSearchInput.getText());
                return true;
            }
        });
    }

    public void setOnMenuItemClickListener(ActionMenuView.OnMenuItemClickListener listener) {
        mActionMenu.setOnMenuItemClickListener(listener);
    }

    public CharSequence getText() {
        return mSearchInput.getText();
    }

    public void setText(CharSequence text) {
        mSearchInput.setText(text);
    }

    public void setHint(CharSequence hint) {
        mSearchInput.setHint(hint);
    }
    
    @Override
    public void setActivated(boolean activated) {
        if(activated == isActivated()) return;

        super.setActivated(activated);

        if(activated) {
            mSearchInput.requestFocus();
            ViewUtils.showSoftKeyboardDelayed(mSearchInput, 100);
        }else {
            requestFocus();
            ViewUtils.closeSoftKeyboard(mActivity);
        }

        if(mFocusListener != null)
            mFocusListener.onFocusChanged(activated);

        showMenu(!activated);
        fadeIn(activated);
        updateSuggestionsVisibility();
    }

    public void setOnNavigationClickListener(OnNavigationClickListener navigationClickListener) {
        mNavigationClickListener = navigationClickListener;
    }

    public void setOnSearchFocusChangedListener(OnSearchFocusChangedListener focusListener) {
        mFocusListener = focusListener;
    }

    public void addTextChangedListener(TextWatcher textWatcher) {
        mSearchInput.addTextChangedListener(textWatcher);
    }

    public void removeTextChangedListener(TextWatcher textWatcher) {
        mSearchInput.removeTextChangedListener(textWatcher);
    }

    public void setAdapter(RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter) {
        RecyclerView.Adapter<? extends RecyclerView.ViewHolder> old = getAdapter();
        if(old != null) old.unregisterAdapterDataObserver(mAdapterObserver);
        adapter.registerAdapterDataObserver(mAdapterObserver);
        mRecyclerView.setAdapter(adapter);
    }

    public void setItemAnimator(RecyclerView.ItemAnimator itemAnimator) {
        mRecyclerView.setItemAnimator(itemAnimator);
    }

    public void addItemDecoration(RecyclerView.ItemDecoration decoration) {
        mRecyclerView.addItemDecoration(decoration);
    }

    public void setNavigationIcon(@DrawableRes int resId) {
        showNavigationIcon(resId != 0);
        mNavButtonView.setImageResource(resId);
    }

    public void setNavigationIcon(Drawable drawable) {
        showNavigationIcon(drawable != null);
        mNavButtonView.setImageDrawable(drawable);
    }

    public void showNavigationIcon(boolean show) {
        mNavButtonView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public Drawable getNavigationIcon() {
        if(mNavButtonView == null) return null;
        return mNavButtonView.getDrawable();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public RecyclerView.Adapter<? extends RecyclerView.ViewHolder> getAdapter() {
        return mRecyclerView.getAdapter();
    }

    protected LayoutTransition getDefaultMenuLayoutTransition() {
        return new ScaleLayoutTransition();
    }

    private void fadeIn(boolean enter) {
        ValueAnimator backgroundAnim;

        if(Build.VERSION.SDK_INT >= 19)
            backgroundAnim = ObjectAnimator.ofInt(mBackgroundDrawable, "alpha", enter ? 255 : 0);
        else {
            backgroundAnim = ValueAnimator.ofInt(enter ? 0 : 255, enter ? 255 : 0);
            backgroundAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (Integer) animation.getAnimatedValue();
                    mBackgroundDrawable.setAlpha(value);
                }
            });
        }

        backgroundAnim.setDuration(enter ? DEFAULT_DURATION_ENTER : DEFAULT_DURATION_EXIT);
        backgroundAnim.setInterpolator(enter ? DECELERATE : ACCELERATE);
        backgroundAnim.start();

        Drawable icon = unwrap(getNavigationIcon());

        if(icon instanceof ToggleDrawable) {
            ObjectAnimator iconAnim = ObjectAnimator.ofFloat(icon, "progress", enter ? 1 : 0);
            iconAnim.setDuration(backgroundAnim.getDuration());
            iconAnim.setInterpolator(backgroundAnim.getInterpolator());
            iconAnim.start();
        }
    }

    private int getSuggestionsCount() {
        RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter = getAdapter();
        if(adapter == null) return 0;
        return adapter.getItemCount();
    }

    private void updateSuggestionsVisibility() {
        showSuggestions(isActivated() && getSuggestionsCount() > 0);
    }

    private boolean suggestionsShown() {
        return mDivider.getVisibility() == View.VISIBLE;
    }

    private void showSuggestions(final boolean enter) {
        if(enter == suggestionsShown()) return;

        int childCount = mRecyclerView.getChildCount();
        int translation = 0;

        final Runnable endAction = new Runnable() {
            @Override
            public void run() {
                if(enter)
                    updateDivider();
                else {
                    showDivider(false);
                    mRecyclerView.setVisibility(View.INVISIBLE);
                    mRecyclerView.setTranslationY(-mRecyclerView.getHeight());
                }
            }
        };

        if(enter) {
            updateDivider();
            mRecyclerView.setVisibility(VISIBLE);
            if(mRecyclerView.getTranslationY() == 0)
                mRecyclerView.setTranslationY(-mRecyclerView.getHeight());
        }else if(childCount > 0)
            translation = -mRecyclerView.getChildAt(childCount - 1).getBottom();
        else
            showDivider(false);

        ViewPropertyAnimatorCompat listAnim = ViewCompat.animate(mRecyclerView)
                .translationY(translation)
                .setDuration(enter ? DEFAULT_DURATION_ENTER : DEFAULT_DURATION_EXIT)
                .setInterpolator(enter ? DECELERATE : ACCELERATE)
                .withLayer()
                .withEndAction(endAction);

        if(enter || childCount > 0)
            listAnim.start();
        else
            endAction.run();
    }

    private void showDivider(boolean visible) {
        mDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
        int shadows = TOP|LEFT|RIGHT;
        if(!visible) shadows|=BOTTOM;
        mSearchBackground.setShadow(shadows);
    }

    private void updateDivider() {
        showDivider(isActivated() && getSuggestionsCount() > 0);
    }

    @NonNull
    private Activity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity)context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        throw new IllegalStateException();
    }

    private void showMenu(boolean visible) {
        int count = mActionMenu.getChildCount();
        for(int i = 0; i < count; i++) {
            View view = mActionMenu.getChildAt(i);
            if(!mAlwaysShownMenuViews.contains(view.getId()))
                view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void parseMenu(XmlPullParser parser, AttributeSet attrs)
            throws XmlPullParserException, IOException {

        int eventType = parser.getEventType();
        String tagName;
        boolean lookingForEndOfUnknownTag = false;
        String unknownTagName = null;

        // This loop will skip to the menu start tag
        do {
            if (eventType == XmlPullParser.START_TAG) {
                tagName = parser.getName();
                if (tagName.equals("menu")) {
                    // Go to next tag
                    eventType = parser.next();
                    break;
                }

                throw new RuntimeException("Expecting menu, got " + tagName);
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        boolean reachedEndOfMenu = false;

        while (!reachedEndOfMenu) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (lookingForEndOfUnknownTag) {
                        break;
                    }

                    tagName = parser.getName();
                    if (tagName.equals("item")) {
                        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.MenuItem);
                        int itemShowAsAction = a.getInt(R.styleable.MenuItem_showAsAction, -1);

                        if((itemShowAsAction & MenuItem.SHOW_AS_ACTION_ALWAYS) != 0) {
                            int itemId = a.getResourceId(R.styleable.MenuItem_android_id, NO_ID);
                            if(itemId != NO_ID) mAlwaysShownMenuViews.add(itemId);
                        }
                        a.recycle();
                    } else {
                        lookingForEndOfUnknownTag = true;
                        unknownTagName = tagName;
                    }
                    break;

                case XmlPullParser.END_TAG:
                    tagName = parser.getName();
                    if (lookingForEndOfUnknownTag && tagName.equals(unknownTagName)) {
                        lookingForEndOfUnknownTag = false;
                        unknownTagName = null;
                    } else if (tagName.equals("menu")) {
                        reachedEndOfMenu = true;
                    }
                    break;

                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document");
            }

            eventType = parser.next();
        }
    }

    static private Drawable unwrap(Drawable icon) {
        if(icon instanceof android.support.v7.graphics.drawable.DrawableWrapper)
            return ((android.support.v7.graphics.drawable.DrawableWrapper)icon).getWrappedDrawable();
        if(icon instanceof android.support.v4.graphics.drawable.DrawableWrapper)
            return ((android.support.v4.graphics.drawable.DrawableWrapper)icon).getWrappedDrawable();
        if(Build.VERSION.SDK_INT >= 23 && icon instanceof android.graphics.drawable.DrawableWrapper)
            return ((android.graphics.drawable.DrawableWrapper)icon).getDrawable();
        return icon;
    }

    private static class ScaleLayoutTransition extends LayoutTransition {

        public ScaleLayoutTransition() {

            PropertyValuesHolder pvhScaleInX = PropertyValuesHolder.ofFloat("scaleX", 0, 1);
            PropertyValuesHolder pvhScaleInY = PropertyValuesHolder.ofFloat("scaleY", 0, 1);
            PropertyValuesHolder pvhAlphaIn = PropertyValuesHolder.ofFloat("alpha", 0, 1);

            PropertyValuesHolder pvhScaleOutX = PropertyValuesHolder.ofFloat("scaleX", 1, 0);
            PropertyValuesHolder pvhScaleOutY = PropertyValuesHolder.ofFloat("scaleY", 1, 0);
            PropertyValuesHolder pvhAlphaOut = PropertyValuesHolder.ofFloat("alpha", 1, 0);

            ObjectAnimator scaleIn = ObjectAnimator.ofPropertyValuesHolder((Object)null, pvhAlphaIn, pvhScaleInX, pvhScaleInY);
            ObjectAnimator scaleOut = ObjectAnimator.ofPropertyValuesHolder((Object)null, pvhAlphaOut, pvhScaleOutX, pvhScaleOutY);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                enableTransitionType(LayoutTransition.CHANGING);

            setAnimator(APPEARING, scaleIn);
            setDuration(APPEARING, DEFAULT_DURATION_ENTER);
            setInterpolator(APPEARING, DECELERATE);

            setAnimator(DISAPPEARING, scaleOut);
            setDuration(DISAPPEARING, DEFAULT_DURATION_EXIT / 2);
            setInterpolator(DISAPPEARING, ACCELERATE);

            setInterpolator(CHANGE_APPEARING, getInterpolator(APPEARING));
            setDuration(CHANGE_APPEARING, getDuration(APPEARING));

            setInterpolator(CHANGE_DISAPPEARING, getInterpolator(DISAPPEARING));
            setDuration(CHANGE_DISAPPEARING, getDuration(DISAPPEARING));


            setStartDelay(DISAPPEARING, 0);
            setStartDelay(CHANGE_APPEARING, 0);
            setStartDelay(CHANGE_DISAPPEARING, getDuration(DISAPPEARING));
            setStartDelay(APPEARING, getDuration(CHANGE_APPEARING));

        }
    }

    private static class RecyclerView extends android.support.v7.widget.RecyclerView {

        public RecyclerView(Context context) {
            super(context);
        }

        public RecyclerView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public RecyclerView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            View child = findChildViewUnder(e.getX(), e.getY());
            return child != null && super.onTouchEvent(e);
        }
    }
}
