package com.kfir.outfitai;

import android.view.View;
import android.view.ViewTreeObserver;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

//source: https://github.com/lofcoding/ImeOverlappingIssue
public class ImeUtils {

    public interface ImeListener {
        void onImeVisibilityChanged(boolean isVisible);
    }

    public static void addImeListener(View view, ImeListener listener) {
        ViewTreeObserver.OnGlobalLayoutListener layoutListener = () -> {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
            if (insets != null) {
                boolean isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                listener.onImeVisibilityChanged(isImeVisible);
            }
        };
        view.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }
}