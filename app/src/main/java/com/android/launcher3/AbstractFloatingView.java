/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

import static com.android.launcher3.compat.AccessibilityManagerCompat.isAccessibilityEnabled;
import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;

import androidx.annotation.IntDef;

import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Base class for a View which shows a floating UI on top of the launcher UI.
 */
public abstract class AbstractFloatingView extends LinearLayout implements TouchController {

    @IntDef(flag = true, value = {
            TYPE_FOLDER,
            TYPE_ACTION_POPUP,
            TYPE_WIDGETS_BOTTOM_SHEET,
            TYPE_WIDGET_RESIZE_FRAME,
            TYPE_WIDGETS_FULL_SHEET,
            TYPE_ON_BOARD_POPUP,
            TYPE_DISCOVERY_BOUNCE,
            TYPE_SNACKBAR,
            TYPE_LISTENER,
            TYPE_ALL_APPS_EDU,

            TYPE_TASK_MENU,
            TYPE_OPTIONS_POPUP
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FloatingViewType {}
    public static final int TYPE_FOLDER = 1 << 0;
    public static final int TYPE_ACTION_POPUP = 1 << 1;
    public static final int TYPE_WIDGETS_BOTTOM_SHEET = 1 << 2;
    public static final int TYPE_WIDGET_RESIZE_FRAME = 1 << 3;
    public static final int TYPE_WIDGETS_FULL_SHEET = 1 << 4;
    public static final int TYPE_ON_BOARD_POPUP = 1 << 5;
    public static final int TYPE_DISCOVERY_BOUNCE = 1 << 6;
    public static final int TYPE_SNACKBAR = 1 << 7;
    public static final int TYPE_LISTENER = 1 << 8;
    public static final int TYPE_ALL_APPS_EDU = 1 << 9;

    // Popups related to quickstep UI
    public static final int TYPE_TASK_MENU = 1 << 10;
    public static final int TYPE_OPTIONS_POPUP = 1 << 11;

    public static final int TYPE_ALL = TYPE_FOLDER | TYPE_ACTION_POPUP
            | TYPE_WIDGETS_BOTTOM_SHEET | TYPE_WIDGET_RESIZE_FRAME | TYPE_WIDGETS_FULL_SHEET
            | TYPE_ON_BOARD_POPUP | TYPE_DISCOVERY_BOUNCE | TYPE_TASK_MENU
            | TYPE_OPTIONS_POPUP | TYPE_SNACKBAR | TYPE_LISTENER | TYPE_ALL_APPS_EDU;

    // Type of popups which should be kept open during launcher rebind
    public static final int TYPE_REBIND_SAFE = TYPE_WIDGETS_FULL_SHEET
            | TYPE_WIDGETS_BOTTOM_SHEET | TYPE_ON_BOARD_POPUP | TYPE_DISCOVERY_BOUNCE
            | TYPE_ALL_APPS_EDU;

    // Usually we show the back button when a floating view is open. Instead, hide for these types.
    public static final int TYPE_HIDE_BACK_BUTTON = TYPE_ON_BOARD_POPUP | TYPE_DISCOVERY_BOUNCE
            | TYPE_SNACKBAR | TYPE_WIDGET_RESIZE_FRAME | TYPE_LISTENER;

    public static final int TYPE_ACCESSIBLE = TYPE_ALL & ~TYPE_DISCOVERY_BOUNCE & ~TYPE_LISTENER
            & ~TYPE_ALL_APPS_EDU;

    // These view all have particular operation associated with swipe down interaction.
    public static final int TYPE_STATUS_BAR_SWIPE_DOWN_DISALLOW = TYPE_WIDGETS_BOTTOM_SHEET |
            TYPE_WIDGETS_FULL_SHEET | TYPE_WIDGET_RESIZE_FRAME | TYPE_ON_BOARD_POPUP |
            TYPE_DISCOVERY_BOUNCE | TYPE_TASK_MENU ;

    protected boolean mIsOpen;

    public AbstractFloatingView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AbstractFloatingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * We need to handle touch events to prevent them from falling through to the workspace below.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return true;
    }

    public final void close(boolean animate) {
        animate &= Utilities.areAnimationsEnabled(getContext());
        if (mIsOpen) {
            BaseActivity.fromContext(getContext()).getUserEventDispatcher()
                    .resetElapsedContainerMillis("container closed");
        }
        handleClose(animate);
        mIsOpen = false;
    }

    protected abstract void handleClose(boolean animate);

    /**
     * Creates a user-controlled animation to hint that the view will be closed if completed.
     * @param distanceToMove The max distance that elements should move from their starting point.
     */
    public void addHintCloseAnim(
            float distanceToMove, Interpolator interpolator, PendingAnimation target) { }

    public abstract void logActionCommand(int command);

    public int getLogContainerType() {
        return 0;
    }

    public final boolean isOpen() {
        return mIsOpen;
    }

    protected abstract boolean isOfType(@FloatingViewType int type);

    /** @return Whether the back is consumed. If false, Launcher will handle the back as well. */
    public boolean onBackPressed() {
        logActionCommand(1);
        close(true);
        return true;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return false;
    }

    protected void announceAccessibilityChanges() {
        Pair<View, String> targetInfo = getAccessibilityTarget();
        if (targetInfo == null || !isAccessibilityEnabled(getContext())) {
            return;
        }
        sendCustomAccessibilityEvent(
                targetInfo.first, TYPE_WINDOW_STATE_CHANGED, targetInfo.second);

        if (mIsOpen) {
            getAccessibilityInitialFocusView().performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        }
        ActivityContext.lookupContext(getContext()).getDragLayer()
                .sendAccessibilityEvent(TYPE_WINDOW_CONTENT_CHANGED);
    }

    protected Pair<View, String> getAccessibilityTarget() {
        return null;
    }

    /** Returns the View that Accessibility services should focus on first. */
    protected View getAccessibilityInitialFocusView() {
        return this;
    }

    /**
     * Returns a view matching FloatingViewType
     */
    public static <T extends AbstractFloatingView> T getOpenView(
            ActivityContext activity, @FloatingViewType int type) {
        BaseDragLayer dragLayer = activity.getDragLayer();
        if (dragLayer == null) return null;
        // Iterate in reverse order. AbstractFloatingView is added later to the dragLayer,
        // and will be one of the last views.
        for (int i = dragLayer.getChildCount() - 1; i >= 0; i--) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof AbstractFloatingView) {
                AbstractFloatingView view = (AbstractFloatingView) child;
                if (view.isOfType(type) && view.isOpen()) {
                    return (T) view;
                }
            }
        }
        return null;
    }

    public static void closeOpenContainer(ActivityContext activity,
            @FloatingViewType int type) {
        AbstractFloatingView view = getOpenView(activity, type);
        if (view != null) {
            view.close(true);
        }
    }

    public static void closeOpenViews(ActivityContext activity, boolean animate,
            @FloatingViewType int type) {
        BaseDragLayer dragLayer = activity.getDragLayer();
        // Iterate in reverse order. AbstractFloatingView is added later to the dragLayer,
        // and will be one of the last views.
        for (int i = dragLayer.getChildCount() - 1; i >= 0; i--) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof AbstractFloatingView) {
                AbstractFloatingView abs = (AbstractFloatingView) child;
                if (abs.isOfType(type)) {
                    abs.close(animate);
                }
            }
        }
    }

    public static void closeAllOpenViews(ActivityContext activity, boolean animate) {
        closeOpenViews(activity, animate, TYPE_ALL);
        activity.finishAutoCancelActionMode();
    }

    public static void closeAllOpenViews(ActivityContext activity) {
        closeAllOpenViews(activity, true);
    }

    public static void closeAllOpenViewsExcept(ActivityContext activity, boolean animate,
                                               @FloatingViewType int type) {
        closeOpenViews(activity, animate, TYPE_ALL & ~type);
        activity.finishAutoCancelActionMode();
    }

    public static void closeAllOpenViewsExcept(ActivityContext activity,
                                               @FloatingViewType int type) {
        closeAllOpenViewsExcept(activity, true, type);
    }

    public static AbstractFloatingView getTopOpenView(ActivityContext activity) {
        return getTopOpenViewWithType(activity, TYPE_ALL);
    }

    public static AbstractFloatingView getTopOpenViewWithType(ActivityContext activity,
            @FloatingViewType int type) {
        return getOpenView(activity, type);
    }

    public boolean canInterceptEventsInSystemGestureRegion() {
        return false;
    }
}
