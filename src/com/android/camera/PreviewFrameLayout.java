/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * A layout which handles the preview aspect ratio.
 */
public class PreviewFrameLayout extends RelativeLayout {
    /** A callback to be invoked when the preview frame's size changes. */
    public interface OnSizeChangedListener {
        public void onSizeChanged();
    }

    private double mAspectRatio = 4.0 / 3.0;
    private View mFrame;
    private View mPreviewBorder;
    private final DisplayMetrics mMetrics = new DisplayMetrics();

    public PreviewFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        ((Activity) context).getWindowManager()
                .getDefaultDisplay().getMetrics(mMetrics);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mFrame = (View) findViewById(R.id.frame);
        mPreviewBorder = (View) findViewById(R.id.preview_border);
    }

    public void setAspectRatio(double ratio) {
        if (ratio <= 0.0) throw new IllegalArgumentException();

        if (mAspectRatio != ratio) {
            mAspectRatio = ratio;
            requestLayout();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Calculate the width and the height of preview frame.
        int frameWidth = getWidth();
        int frameHeight = getHeight();
        int hPadding = 0;
        int vPadding = 0;
        if (mPreviewBorder != null) {
            hPadding = mPreviewBorder.getPaddingLeft() + mPreviewBorder.getPaddingRight();
            vPadding = mPreviewBorder.getPaddingBottom() + mPreviewBorder.getPaddingTop();
        }
        int previewHeight = frameHeight - vPadding;
        int previewWidth = frameWidth - hPadding;
        if (previewWidth > previewHeight * mAspectRatio) {
            previewWidth = (int) (previewHeight * mAspectRatio + .5);
        } else {
            previewHeight = (int) (previewWidth / mAspectRatio + .5);
        }

        // Measure and layout preview frame.
        int hSpace = ((r - l) - previewWidth) / 2;
        int vSpace = ((b - t) - previewHeight) / 2;
        mFrame.measure(
                MeasureSpec.makeMeasureSpec(previewWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(previewHeight, MeasureSpec.EXACTLY));
        mFrame.layout(hSpace, vSpace, frameWidth + hSpace, frameHeight + vSpace);

        // Measure and layout the border of preview frame.
        if (mPreviewBorder != null) {
            frameWidth = previewWidth + hPadding;
            frameHeight = previewHeight + vPadding;
            hSpace = ((r - l) - frameWidth) / 2;
            vSpace = ((b - t) - frameHeight) / 2;
            mPreviewBorder.measure(
                    MeasureSpec.makeMeasureSpec(frameWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(frameHeight, MeasureSpec.EXACTLY));
            mPreviewBorder.layout(hSpace, vSpace, frameWidth + hSpace, frameHeight + vSpace);
        }
    }
}
