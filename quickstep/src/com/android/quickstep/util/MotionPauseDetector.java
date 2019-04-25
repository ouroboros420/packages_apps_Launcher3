/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.util;

import android.content.Context;
import android.content.res.Resources;
import android.view.MotionEvent;

import com.android.launcher3.Alarm;
import com.android.launcher3.R;

/**
 * Given positions along x- or y-axis, tracks velocity and acceleration and determines when there is
 * a pause in motion.
 */
public class MotionPauseDetector {

    // The percentage of the previous speed that determines whether this is a rapid deceleration.
    // The bigger this number, the easier it is to trigger the first pause.
    private static final float RAPID_DECELERATION_FACTOR = 0.6f;

    /** If no motion is added for this amount of time, assume the motion has paused. */
    private static final long FORCE_PAUSE_TIMEOUT = 300;

    private final float mSpeedVerySlow;
    private final float mSpeedSomewhatFast;
    private final float mSpeedFast;
    private final Alarm mForcePauseTimeout;

    private Long mPreviousTime = null;
    private Float mPreviousPosition = null;
    private Float mPreviousVelocity = null;

    private Float mFirstPosition = null;

    private OnMotionPauseListener mOnMotionPauseListener;
    private boolean mIsPaused;
    // Bias more for the first pause to make it feel extra responsive.
    private boolean mHasEverBeenPaused;
    /** @see #setDisallowPause(boolean) */
    private boolean mDisallowPause;

    public MotionPauseDetector(Context context) {
        Resources res = context.getResources();
        mSpeedVerySlow = res.getDimension(R.dimen.motion_pause_detector_speed_very_slow);
        mSpeedSomewhatFast = res.getDimension(R.dimen.motion_pause_detector_speed_somewhat_fast);
        mSpeedFast = res.getDimension(R.dimen.motion_pause_detector_speed_fast);
        mForcePauseTimeout = new Alarm();
        mForcePauseTimeout.setOnAlarmListener(alarm -> updatePaused(true /* isPaused */));
    }

    /**
     * Get callbacks for when motion pauses and resumes, including an
     * immediate callback with the current pause state.
     */
    public void setOnMotionPauseListener(OnMotionPauseListener listener) {
        mOnMotionPauseListener = listener;
    }

    /**
     * @param disallowPause If true, we will not detect any pauses until this is set to false again.
     */
    public void setDisallowPause(boolean disallowPause) {
        mDisallowPause = disallowPause;
        updatePaused(false);
    }

    /**
     * Computes velocity and acceleration to determine whether the motion is paused.
     * @param position The x or y component of the motion being tracked.
     *
     * TODO: Use historical positions as well, e.g. {@link MotionEvent#getHistoricalY(int, int)}.
     */
    public void addPosition(float position, long time) {
        if (mFirstPosition == null) {
            mFirstPosition = position;
        }
        mForcePauseTimeout.setAlarm(FORCE_PAUSE_TIMEOUT);
        if (mPreviousTime != null && mPreviousPosition != null) {
            long changeInTime = Math.max(1, time - mPreviousTime);
            float changeInPosition = position - mPreviousPosition;
            float velocity = changeInPosition / changeInTime;
            if (mPreviousVelocity != null) {
                checkMotionPaused(velocity, mPreviousVelocity);
            }
            mPreviousVelocity = velocity;
        }
        mPreviousTime = time;
        mPreviousPosition = position;
    }

    private void checkMotionPaused(float velocity, float prevVelocity) {
        float speed = Math.abs(velocity);
        float previousSpeed = Math.abs(prevVelocity);
        boolean isPaused;
        if (mIsPaused) {
            // Continue to be paused until moving at a fast speed.
            isPaused = speed < mSpeedFast || previousSpeed < mSpeedFast;
        } else {
            if (velocity < 0 != prevVelocity < 0) {
                // We're just changing directions, not necessarily stopping.
                isPaused = false;
            } else {
                isPaused = speed < mSpeedVerySlow && previousSpeed < mSpeedVerySlow;
                if (!isPaused && !mHasEverBeenPaused) {
                    // We want to be more aggressive about detecting the first pause to ensure it
                    // feels as responsive as possible; getting two very slow speeds back to back
                    // takes too long, so also check for a rapid deceleration.
                    boolean isRapidDeceleration = speed < previousSpeed * RAPID_DECELERATION_FACTOR;
                    isPaused = isRapidDeceleration && speed < mSpeedSomewhatFast;
                }
            }
        }
        updatePaused(isPaused);
    }

    private void updatePaused(boolean isPaused) {
        if (mDisallowPause) {
            isPaused = false;
        }
        if (mIsPaused != isPaused) {
            mIsPaused = isPaused;
            if (mIsPaused) {
                mHasEverBeenPaused = true;
            }
            if (mOnMotionPauseListener != null) {
                mOnMotionPauseListener.onMotionPauseChanged(mIsPaused);
            }
        }
    }

    public void clear() {
        mPreviousTime = null;
        mPreviousPosition = null;
        mPreviousVelocity = null;
        mFirstPosition = null;
        setOnMotionPauseListener(null);
        mIsPaused = mHasEverBeenPaused = false;
        mForcePauseTimeout.cancelAlarm();
    }

    public boolean isPaused() {
        return mIsPaused;
    }

    public interface OnMotionPauseListener {
        void onMotionPauseChanged(boolean isPaused);
    }
}
