package com.presence.attendance.core.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import com.google.android.material.button.MaterialButton;

/** Button that springs to 97% scale while pressed. Used for every button in the app. */
public final class SpringButton extends MaterialButton {

    private static final float PRESSED_SCALE = 0.97f;
    private static final float REST_SCALE = 1f;

    private final SpringAnimation scaleX = spring(DynamicAnimation.SCALE_X);
    private final SpringAnimation scaleY = spring(DynamicAnimation.SCALE_Y);

    public SpringButton(@NonNull Context context) {
        super(context);
    }

    public SpringButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SpringButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setPressed(boolean pressed) {
        final boolean changed = pressed != isPressed();
        super.setPressed(pressed);
        // The View constructor can call this before the field initialisers have run.
        if (!changed || scaleX == null || scaleY == null) {
            return;
        }
        // Animator duration scale 0 means the user has turned motion off.
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return;
        }
        final float target = pressed ? PRESSED_SCALE : REST_SCALE;
        scaleX.animateToFinalPosition(target);
        scaleY.animateToFinalPosition(target);
    }

    private SpringAnimation spring(DynamicAnimation.ViewProperty property) {
        // Critically damped: the design system bans bounce.
        final SpringForce force =
                new SpringForce(REST_SCALE)
                        .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
        return new SpringAnimation(this, property).setSpring(force);
    }
}
