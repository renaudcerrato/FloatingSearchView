# FloatingSearchView #

Yet another floating search view implementation (also known as persistent search): that implementation supports menu, submenu, animated icon, and logo. Dropdown suggestions are backed by a `RecyclerView` and you can provide your own `RecyclerView.Adapter`, `ItemDecorator` or `ItemAnimator`. 

[DEMO APK](https://github.com/renaudcerrato/FloatingSearchView/raw/master/sample/sample-debug.apk)

<p align="center">
<a href="https://vid.me/bp7K" target="_blank">
<img src="https://github.com/renaudcerrato/FloatingSearchView/raw/master/assets/demo.gif">
</a>
</p>

# Usage #

Add a `FloatingSearchView` to your view hierarchy, make sure that it takes up the full width and height:

```
<com.mypopsy.widget.FloatingSearchView
        android:id="@+id/search"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:paddingTop="16dp"
        android:paddingRight="8dp"
        android:paddingLeft="8dp"
        android:background="#90000000"
        android:hint="@string/hint"
        app:logo="@drawable/logo"
        app:fsv_menu="@menu/search"/>
```

Then, configure your instance and set your listeners to react accordingly:

```
mSuggestionsAdapter = new MySuggestionsAdapter(); // use a RecyclerView.Adapter
mSearchView.setAdapter(mSuggestionsAdapter);
...
mSearchView.setOnIconClickListener(...);
mSearchView.setOnSearchListener(...);  
mSearchView.addTextChangedListener(...);
mSearchView.setOnSearchFocusChangedListener(...);
```

Look at the [sample](https://github.com/renaudcerrato/FloatingSearchView/blob/master/sample/src/main/java/com/mypopsy/floatingsearchview/MainActivity.java).

## Menu ##

The implementation fully supports menu (and submenu):

```
<com.mypopsy.widget.FloatingSearchView
	...
	app:fsv_menu="@menu/search"
/>
    
```

```
mSearchView.setOnMenuItemClickListener(...);
```

Menu items can be automatically hidden when the search view gets focus depending on the value of the MenuItem's `showAsAction`:

```
<!-- res/menu/menu.xml -->
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto">
    <item android:id="@+id/menu1" ... app:showAsAction="always"/> // always shown
    <item android:id="@+id/menu2" ... app:showAsAction="ifRoom"/> // item will hide on focus 
    <item android:id="@+id/menu3" ... app:showAsAction="never"/>  // item will go into the overflow menu
</menu>
```



# Styling #
TODO

# FAQ #
TODO
Since the implementation tries to focus on core logic instead of business logic as much as possible, common features like "tap to clear" or "indeterminate progress bar" are not built-in but can be easily implemented using menu as seen in the [sample](https://github.com/renaudcerrato/FloatingSearchView/blob/master/sample/src/main/java/com/mypopsy/floatingsearchview/MainActivity.java).

# Install #

This repositery can be found on JitPack:

https://jitpack.io/#renaudcerrato/FloatingSearchView

Add it in your root build.gradle at the end of repositories:
```
allprojects {
		repositories {
			...
			maven { url "https://jitpack.io" }
		}
	}
```

Add the dependency:
```
dependencies {
	        compile 'com.github.renaudcerrato:FloatingSearchView:1.0.0'
	}
```


