package com.mypopsy.widget

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Xml
import android.view.*
import android.view.View.OnFocusChangeListener
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.annotation.*
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.MarginLayoutParamsCompat
import androidx.core.view.ViewCompat
import com.mypopsy.widget.internal.R
import com.mypopsy.widget.internal.RoundRectDrawableWithShadow
import com.mypopsy.widget.internal.SuggestionItemDecorator
import com.mypopsy.widget.internal.ViewUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class FloatingSearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.floatingSearchViewStyle)
    : RelativeLayout(context, attrs, defStyle) {

    companion object {
        private const val DEFAULT_BACKGROUND_COLOR = -0x70000000
        private const val DEFAULT_CONTENT_COLOR = -0xf0f10

        private const val DEFAULT_RADIUS = 2
        private const val DEFAULT_ELEVATION = 2
        private const val DEFAULT_MAX_ELEVATION = 2

        private const val DEFAULT_DURATION_ENTER: Long = 300
        private const val DEFAULT_DURATION_EXIT: Long = 400

        private val DECELERATE: Interpolator = DecelerateInterpolator(3f)
        private val ACCELERATE: Interpolator = AccelerateInterpolator(2f)

        @JvmStatic private fun unwrap(icon: Drawable): Drawable? {
            return if (Build.VERSION.SDK_INT >= 23 && icon is DrawableWrapper)
                icon.drawable else DrawableCompat.unwrap(icon)
        }

        class RecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
            androidx.recyclerview.widget.RecyclerView(context, attrs, defStyle) {

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(e: MotionEvent): Boolean {
                val child = findChildViewUnder(e.x, e.y)
                return child != null && super.onTouchEvent(e)
            }
        }

        class LogoEditText@JvmOverloads constructor(context: Context,
                                                    attrs: AttributeSet? = null, defStyle: Int = 0)
            : AppCompatEditText(context, attrs, defStyle) {
            private var logo: Drawable? = null
            private var logoShown = false
            private var dirty = false

            fun showLogo(shown: Boolean) {
                logoShown = shown
            }

            fun setLogo(@DrawableRes res: Int) {
                if (res == 0) setLogo(null) else ContextCompat.getDrawable(context, res)?.let {
                    setLogo(it)
                }
            }

            fun setLogo(logo: Drawable?) {
                this.logo = logo
                dirty = true
            }

            override fun onDraw(canvas: Canvas) {
                if (logoShown && logo != null) {
                    if (dirty) {
                        updateLogoBounds()
                        dirty = false
                    }
                    logo!!.draw(canvas)
                } else super.onDraw(canvas)
            }

            // fit center
            private fun updateLogoBounds() {
                logo?.apply {
                    val logoHeight = height.coerceAtMost(intrinsicHeight)
                    val top = (height - logoHeight) / 2
                    val logoWidth = intrinsicWidth * logoHeight / intrinsicHeight
                    setBounds(0, top, logoWidth, top + logoHeight)
                }
            }
        }
    }

    private val mAdapterObserver: androidx.recyclerview.widget.RecyclerView.AdapterDataObserver =
        object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                onChanged()
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                onChanged()
            }

            override fun onChanged() {
                updateSuggestionsVisibility()
            }
        }

    interface OnSearchListener {
        fun onSearchAction(text: CharSequence)
    }

    interface OnIconClickListener {
        fun onNavigationClick()
    }

    interface OnSearchFocusChangedListener {
        fun onFocusChanged(focused: Boolean)
    }

    private var mSearchInput: LogoEditText? = null
    private var mNavButtonView: ImageView? = null
    private var mRecyclerView: RecyclerView? = null
    private var mSearchContainer: ViewGroup? = null
    private var mDivider: View? = null
    private var mActionMenu: ActionMenuView? = null

    private var mActivity: Activity? = null

    private var mSearchBackground: RoundRectDrawableWithShadow? = null
    private var mCardDecorator: SuggestionItemDecorator? = null

    private val mAlwaysShowingMenu = arrayListOf<Int>()

    private var mFocusListener: OnSearchFocusChangedListener? = null
    private var mNavigationClickListener: OnIconClickListener? = null
    private var mBackgroundDrawable: Drawable? = null
    private var mSuggestionsShown = false

    var text: CharSequence
        get() = mSearchInput?.text?:""
        set(value) { mSearchInput?.setText(value) }

    var hint: CharSequence
        get() = mSearchInput?.hint?:""
        set(value) { mSearchInput?.hint = value }

    init {
        mActivity = if (isInEditMode) {
            null
        } else {
            getActivity()
        }

        isFocusable = true
        isFocusableInTouchMode = true

        inflate(getContext(), R.layout.fsv_floating_search_layout, this)

        mSearchInput = findViewById(R.id.fsv_search_text)
        mNavButtonView = findViewById(R.id.fsv_search_action_navigation)
        mRecyclerView = findViewById(R.id.fsv_suggestions_list)
        mDivider = findViewById(R.id.fsv_suggestions_divider)
        mSearchContainer = findViewById(R.id.fsv_search_container)
        mActionMenu = findViewById(R.id.fsv_search_action_menu)

        //TODO: move elevation parameters to XML attributes

        //TODO: move elevation parameters to XML attributes
        mSearchBackground = RoundRectDrawableWithShadow(
            DEFAULT_CONTENT_COLOR,
            ViewUtils.dpToPx(DEFAULT_RADIUS).toFloat(),
            ViewUtils.dpToPx(DEFAULT_ELEVATION).toFloat(),
            ViewUtils.dpToPx(DEFAULT_MAX_ELEVATION).toFloat()
        )
        mSearchBackground!!.apply{
            addPaddingForCorners = true
            mCardDecorator = SuggestionItemDecorator(mutate())
            }
        applyXmlAttributes(attrs, defStyle, 0)
        setupViews()
    }

    @SuppressLint("CustomViewStyleable")
    private fun applyXmlAttributes(attrs: AttributeSet?, @AttrRes defStyleAttr: Int, @StyleRes defStyleRes: Int) {
        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.FloatingSearchView, defStyleAttr, defStyleRes
        )

        // Search bar width
        val suggestionsContainer = findViewById<View>(R.id.fsv_suggestions_container)
        val searchBarWidth = a.getDimensionPixelSize(
            R.styleable.FloatingSearchView_fsv_searchBarWidth,
            mSearchContainer!!.layoutParams.width
        )
        mSearchContainer!!.layoutParams.width = searchBarWidth
        suggestionsContainer.layoutParams.width = searchBarWidth

        // Divider
        mDivider!!.background = a.getDrawable(R.styleable.FloatingSearchView_android_divider)
        val dividerHeight =
            a.getDimensionPixelSize(R.styleable.FloatingSearchView_android_dividerHeight, -1)
        val dividerLP = mDivider!!.layoutParams as MarginLayoutParams
        if (mDivider!!.background != null && dividerHeight != -1) dividerLP.height = dividerHeight
        val maxShadowSize: Float = mSearchBackground!!.maxShadowSize
        val cornerRadius: Float = mSearchBackground!!.cornerRadius
        val horizontalPadding = (RoundRectDrawableWithShadow.calculateHorizontalPadding(
            maxShadowSize, cornerRadius, false
        ) + .5f).toInt()
        dividerLP.setMargins(
            horizontalPadding,
            dividerLP.topMargin,
            horizontalPadding,
            dividerLP.bottomMargin
        )
        mDivider!!.layoutParams = dividerLP

        // Content inset
        val searchParams = mSearchInput!!.layoutParams as MarginLayoutParams
        val contentInsetStart = a.getDimensionPixelSize(
            R.styleable.FloatingSearchView_contentInsetStart,
            MarginLayoutParamsCompat.getMarginStart(searchParams)
        )
        val contentInsetEnd = a.getDimensionPixelSize(
            R.styleable.FloatingSearchView_contentInsetEnd,
            MarginLayoutParamsCompat.getMarginEnd(searchParams)
        )
        MarginLayoutParamsCompat.setMarginStart(searchParams, contentInsetStart)
        MarginLayoutParamsCompat.setMarginEnd(searchParams, contentInsetEnd)

        // anything else
        setLogo(a.getDrawable(R.styleable.FloatingSearchView_logo))
        setContentBackgroundColor(
            a.getColor(
                R.styleable.FloatingSearchView_fsv_contentBackgroundColor,
                DEFAULT_CONTENT_COLOR
            )
        )
        setRadius(
            a.getDimensionPixelSize(
                R.styleable.FloatingSearchView_fsv_cornerRadius,
                ViewUtils.dpToPx(DEFAULT_RADIUS)
            ).toFloat()
        )
        inflateMenu(a.getResourceId(R.styleable.FloatingSearchView_fsv_menu, 0))
        setPopupTheme(a.getResourceId(R.styleable.FloatingSearchView_popupTheme, 0))
        hint = a.getString(R.styleable.FloatingSearchView_android_hint).toString()
        setIcon(a.getDrawable(R.styleable.FloatingSearchView_fsv_icon))
        a.recycle()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupViews() {
        mSearchContainer?.apply {
            layoutTransition = getDefaultLayoutTransition()

            //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
            mSearchBackground?.let {
                background = it
                minimumHeight = it.getMinHeight().toInt()
                minimumWidth = it.getMinWidth().toInt()
            }
        }
        mRecyclerView?.apply {
            addItemDecoration(mCardDecorator!!)
            setHasFixedSize(true)
            visibility = INVISIBLE
        }
        mBackgroundDrawable = background
        mBackgroundDrawable = mBackgroundDrawable?.mutate() ?: ColorDrawable(DEFAULT_BACKGROUND_COLOR)
        background = mBackgroundDrawable
        mBackgroundDrawable!!.alpha = 0
        mNavButtonView!!.setOnClickListener { mNavigationClickListener?.onNavigationClick() }
        setOnTouchListener { _: View?, _: MotionEvent? ->
            if (!isActivated) return@setOnTouchListener false
            isActivated = false
            true
        }
        mSearchInput!!.onFocusChangeListener =
            OnFocusChangeListener { _: View?, hasFocus: Boolean ->
                if (hasFocus != isActivated) isActivated = hasFocus
            }
        mSearchInput!!.setOnKeyListener { _: View?, keyCode: Int, _: KeyEvent? ->
            if (keyCode != KeyEvent.KEYCODE_ENTER) return@setOnKeyListener false
            isActivated = false
            true
        }
    }

    fun setRadius(radius: Float) {
        radius.let {
            mSearchBackground!!.cornerRadius = it
            mCardDecorator!!.setCornerRadius(it)
        }
    }

    fun setContentBackgroundColor(@ColorInt color: Int) {
        color.let {
            mSearchBackground!!.setColor(it)
            mCardDecorator!!.setBackgroundColor(it)
            mActionMenu!!.setBackgroundColor(it)
        }
    }

    fun getMenu(): Menu {
        return mActionMenu!!.menu
    }

    fun setPopupTheme(@StyleRes resId: Int) {
        mActionMenu!!.popupTheme = resId
    }

    @SuppressLint("ResourceType")
    fun inflateMenu(@MenuRes menuRes: Int) {
        if (menuRes == 0) return
        if (isInEditMode) return
        getActivity().getMenuInflater().inflate(menuRes, mActionMenu!!.menu)
        try {
            resources.getLayout(menuRes).use { parser ->
                val attrs = Xml.asAttributeSet(parser)
                parseMenu(parser, attrs)
            }
        } catch (e: XmlPullParserException) {
            // should not happens
            throw InflateException("Error parsing menu XML", e)
        } catch (e: IOException) {
            throw InflateException("Error parsing menu XML", e)
        }
    }

    fun setOnSearchListener(listener: OnSearchListener) {
        mSearchInput!!.setOnKeyListener { _: View?, keyCode: Int, _: KeyEvent? ->
            if (keyCode != KeyEvent.KEYCODE_ENTER) return@setOnKeyListener false
            listener.onSearchAction(mSearchInput!!.text.toString())
            true
        }
    }

    fun setOnSearchListener(init: (CharSequence)-> Unit) {
        val listener = object: OnSearchListener {
            override fun onSearchAction(text: CharSequence) {
                init(text)
            }
        }
        mSearchInput!!.setOnKeyListener { _: View?, keyCode: Int, _: KeyEvent? ->
            if (keyCode != KeyEvent.KEYCODE_ENTER) return@setOnKeyListener false
            listener.onSearchAction(mSearchInput!!.text.toString())
            true
        }
    }

    fun setOnMenuItemClickListener(listener: ActionMenuView.OnMenuItemClickListener?) {
        mActionMenu!!.setOnMenuItemClickListener(listener)
    }

    fun setOnMenuItemClickListener(init: (MenuItem)->Unit) {
        val listener = ActionMenuView.OnMenuItemClickListener {
            init(it)
            true
        }
        mActionMenu!!.setOnMenuItemClickListener(listener)
    }

    override fun setActivated(activated: Boolean) {
        if (activated == isActivated) return
        super.setActivated(activated)
        if (activated) {
            mSearchInput!!.requestFocus()
            ViewUtils.showSoftKeyboardDelayed(mSearchInput!!, 100)
        } else {
            requestFocus()
            ViewUtils.closeSoftKeyboard(mActivity!!)
        }
        mFocusListener?.onFocusChanged(activated)
        showMenu(!activated)
        fadeIn(activated)
        updateSuggestionsVisibility()
    }

    fun setOnIconClickListener(navigationClickListener: OnIconClickListener) {
        mNavigationClickListener = navigationClickListener
    }

    fun setOnIconClickListener(init: (ImageView)-> Unit) {
        val navigationClickListener = object: OnIconClickListener {
            override fun onNavigationClick() {
                init(mNavButtonView!!)
            }
        }
        mNavigationClickListener = navigationClickListener
    }

    fun setOnSearchFocusChangedListener(focusListener: OnSearchFocusChangedListener) {
        mFocusListener = focusListener
    }

    fun setOnSearchFocusChangedListener(init: (Boolean)-> Unit) {
        val focusListener = object: OnSearchFocusChangedListener {
            override fun onFocusChanged(focused: Boolean) {
                init(focused)
            }
        }
        mFocusListener = focusListener
    }

    fun addTextChangedListener(textWatcher: TextWatcher) {
        mSearchInput!!.addTextChangedListener(textWatcher)
    }

    fun removeTextChangedListener(textWatcher: TextWatcher?) {
        mSearchInput!!.removeTextChangedListener(textWatcher)
    }

    fun setAdapter(adapter: androidx.recyclerview.widget.RecyclerView.Adapter<out androidx.recyclerview.widget.RecyclerView.ViewHolder>) {
        getAdapter()?.unregisterAdapterDataObserver(mAdapterObserver)
        adapter.registerAdapterDataObserver(mAdapterObserver)
        mRecyclerView!!.adapter = adapter
    }

    fun setItemAnimator(itemAnimator: androidx.recyclerview.widget.RecyclerView.ItemAnimator?) {
        mRecyclerView!!.itemAnimator = itemAnimator
    }

    fun addItemDecoration(decoration: androidx.recyclerview.widget.RecyclerView.ItemDecoration?) {
        mRecyclerView!!.addItemDecoration(decoration!!)
    }

    fun setLogo(drawable: Drawable?) {
        mSearchInput!!.setLogo(drawable)
    }

    fun setLogo(@DrawableRes resId: Int) {
        mSearchInput!!.setLogo(resId)
    }

    fun setIcon(@DrawableRes resId: Int) {
        showIcon(resId != 0)
        mNavButtonView!!.setImageResource(resId)
    }

    fun setIcon(drawable: Drawable?) {
        showIcon(drawable != null)
        mNavButtonView!!.setImageDrawable(drawable)
    }

    fun showLogo(show: Boolean) {
        mSearchInput!!.showLogo(show)
    }

    fun showIcon(show: Boolean) {
        mNavButtonView!!.visibility = if (show) VISIBLE else GONE
    }

    fun getIcon(): Drawable? {
        return if (mNavButtonView == null) null else mNavButtonView!!.drawable
    }

    fun getAdapter(): androidx.recyclerview.widget.RecyclerView.Adapter<out androidx.recyclerview.widget.RecyclerView.ViewHolder?>? {
        return mRecyclerView!!.adapter
    }

    fun getDefaultLayoutTransition(): LayoutTransition = LayoutTransition()

    @SuppressLint("ObjectAnimatorBinding")
    private fun fadeIn(enter: Boolean) {
        val backgroundAnim = ObjectAnimator.ofInt(mBackgroundDrawable, "alpha", if (enter) 255 else 0)
        backgroundAnim.apply {
            duration = if (enter) DEFAULT_DURATION_ENTER else DEFAULT_DURATION_EXIT
            interpolator = if (enter) DECELERATE else ACCELERATE
            start()
            val icon = unwrap(getIcon()!!)
            icon?.let {
                val iconAnim: ObjectAnimator = ObjectAnimator.ofFloat(icon, "progress", if (enter) 1F else 0F)
                iconAnim.let {
                    duration = this.duration
                    interpolator = this.interpolator
                }
                iconAnim.start()
            }
        }
    }

    private fun getSuggestionsCount(): Int {
        val adapter = getAdapter() ?: return 0
        return adapter.itemCount
    }

    private fun updateSuggestionsVisibility() {
        showSuggestions(isActivated && getSuggestionsCount() > 0)
    }

    private fun suggestionsShown() = mSuggestionsShown

    private fun showSuggestions(show: Boolean) {
        if (show == suggestionsShown()) return
        mSuggestionsShown = show
        val childCount = mRecyclerView!!.childCount
        var translation = 0
        val endAction = Runnable {
            if (show) updateDivider() else {
                showDivider(false)
                mRecyclerView!!.visibility = INVISIBLE
                mRecyclerView!!.translationY = -mRecyclerView!!.height.toFloat()
            }
        }
        if (show) {
            updateDivider()
            mRecyclerView!!.visibility = VISIBLE
            if (mRecyclerView!!.translationY == 0f) mRecyclerView!!.translationY =
                -mRecyclerView!!.height.toFloat()
        } else if (childCount > 0) translation =
            -mRecyclerView!!.getChildAt(childCount - 1).bottom else showDivider(false)
        val listAnim = ViewCompat.animate(mRecyclerView!!)
            .translationY(translation.toFloat())
            .setDuration(if (show) DEFAULT_DURATION_ENTER else DEFAULT_DURATION_EXIT)
            .setInterpolator(if (show) DECELERATE else ACCELERATE)
            .withLayer()
            .withEndAction(endAction)
        if (show || childCount > 0) listAnim.start() else endAction.run()
    }

    private fun showDivider(visible: Boolean) {
        mDivider!!.visibility = if (visible) VISIBLE else GONE
        var shadows =
            RoundRectDrawableWithShadow.TOP or RoundRectDrawableWithShadow.LEFT or RoundRectDrawableWithShadow.RIGHT
        if (!visible) shadows = shadows or RoundRectDrawableWithShadow.BOTTOM
        mSearchBackground!!.setShadow(shadows)
    }

    private fun updateDivider() {
        showDivider(isActivated && getSuggestionsCount() > 0)
    }

    private fun getActivity(): Activity {
        var context = context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        throw IllegalStateException()
    }

    private fun showMenu(visible: Boolean) {
        val menu = getMenu()
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (mAlwaysShowingMenu.contains(item.itemId)) continue
            item.isVisible = visible
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    @SuppressLint("CustomViewStyleable", "PrivateResource")
    private fun parseMenu(parser: XmlPullParser, attrs: AttributeSet) {
        var eventType = parser.eventType
        var tagName: String
        var lookingForEndOfUnknownTag = false
        var unknownTagName: String? = null

        // This loop will skip to the menu start tag
        do {
            if (eventType == XmlPullParser.START_TAG) {
                tagName = parser.name
                if (tagName == "menu") {
                    // Go to next tag
                    eventType = parser.next()
                    break
                }
                throw RuntimeException("Expecting menu, got $tagName")
            }
            eventType = parser.next()
        } while (eventType != XmlPullParser.END_DOCUMENT)
        var reachedEndOfMenu = false
        while (!reachedEndOfMenu) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (lookingForEndOfUnknownTag) {
                        break
                    }
                    tagName = parser.name
                    if (tagName == "item") {
                        val a = context.obtainStyledAttributes(
                            attrs,
                            androidx.appcompat.R.styleable.MenuItem
                        )
                        val itemShowAsAction =
                            a.getInt(androidx.appcompat.R.styleable.MenuItem_showAsAction, -1)
                        if (itemShowAsAction and MenuItem.SHOW_AS_ACTION_ALWAYS != 0) {
                            val itemId = a.getResourceId(
                                androidx.appcompat.R.styleable.MenuItem_android_id,
                                NO_ID
                            )
                            if (itemId != NO_ID) mAlwaysShowingMenu.add(itemId)
                        }
                        a.recycle()
                    } else {
                        lookingForEndOfUnknownTag = true
                        unknownTagName = tagName
                    }
                }
                XmlPullParser.END_TAG -> {
                    tagName = parser.name
                    if (lookingForEndOfUnknownTag && tagName == unknownTagName) {
                        lookingForEndOfUnknownTag = false
                        unknownTagName = null
                    } else if (tagName == "menu") {
                        reachedEndOfMenu = true
                    }
                }
                XmlPullParser.END_DOCUMENT -> throw RuntimeException("Unexpected end of document")
            }
            eventType = parser.next()
        }
    }


}