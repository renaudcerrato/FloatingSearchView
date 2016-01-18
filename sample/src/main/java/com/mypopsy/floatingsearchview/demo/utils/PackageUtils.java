package com.mypopsy.floatingsearchview.demo.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.mypopsy.floatingsearchview.demo.R;

import java.util.Locale;

/**
 * Created by renaud on 01/01/16.
 */
public class PackageUtils {

    static public void start(Context context,  @NonNull Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Bundle extras = new Bundle();
            extras.putBinder("android.support.customtabs.extra.SESSION", null);
            intent.putExtras(extras);
            intent.putExtra("android.support.customtabs.extra.TOOLBAR_COLOR",
                    ViewUtils.getThemeAttrColor(context, R.attr.colorPrimary));
        }
        try {
            context.startActivity(intent);
        }catch(ActivityNotFoundException e) {
            // unlikely to happen
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    static public void startTextToSpeech(Activity context, String prompt, int requestCode) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);
        try {
            context.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(context, context.getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }

}
