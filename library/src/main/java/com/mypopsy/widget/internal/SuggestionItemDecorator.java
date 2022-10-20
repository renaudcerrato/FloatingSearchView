package com.mypopsy.widget.internal;

import static com.mypopsy.widget.internal.RoundRectDrawableWithShadow.BOTTOM;
import static com.mypopsy.widget.internal.RoundRectDrawableWithShadow.LEFT;
import static com.mypopsy.widget.internal.RoundRectDrawableWithShadow.RIGHT;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SuggestionItemDecorator extends RecyclerView.ItemDecoration {

    private final RoundRectDrawableWithShadow drawable;

    public SuggestionItemDecorator(RoundRectDrawableWithShadow drawable) {
        this.drawable = drawable;
    }

    @Override
    public void getItemOffsets(@NonNull Rect rect, @NonNull View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        int count = state.getItemCount();
        int shadows = LEFT|RIGHT;
        if(position == count - 1) shadows|=BOTTOM;
        drawable.setShadow(shadows);
        drawable.getPadding(rect);
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        int visibleCount = parent.getChildCount();
        int count = state.getItemCount();
        RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter = (RecyclerView.Adapter<? extends RecyclerView.ViewHolder>) parent.getAdapter();
        int adapterCount = adapter != null ? adapter.getItemCount() : 0;

        for (int i = 0; i < visibleCount; i++) {
            View view = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(view);
            float translationX = view.getTranslationX();
            float translationY = view.getTranslationY();
            float alpha = view.getAlpha();
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();

            int shadows = LEFT|RIGHT;
            if(position == count - 1 && adapterCount != 0) shadows|=BOTTOM;

            drawable.setAlpha((int) (255*alpha));
            drawable.setShadow(shadows);
            drawable.setBounds(0, 0, parent.getWidth(), view.getHeight());
            int saved = canvas.save();
                canvas.translate(parent.getPaddingLeft() + translationX,
                                view.getTop() + params.topMargin + translationY);
                drawable.draw(canvas);
            canvas.restoreToCount(saved);
        }
    }

    public void setBackgroundColor(@ColorInt int color) {
        drawable.setColor(color);
    }

    public void setCornerRadius(float radius) {
        drawable.setCornerRadius(radius);
    }
}
