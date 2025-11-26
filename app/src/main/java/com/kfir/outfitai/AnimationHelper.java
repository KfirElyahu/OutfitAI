package com.kfir.outfitai;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public class AnimationHelper {

    public static void animateSlideUp(View view, long delay) {
        if (view == null) return;

        view.setTranslationY(400f);
        view.setAlpha(0f);

        view.animate()
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(delay)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }

    public static void animateFadeInDown(View view, long delay) {
        if (view == null) return;

        view.setTranslationY(-100f);
        view.setAlpha(0f);

        view.animate()
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(delay)
                .setDuration(450)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }
}