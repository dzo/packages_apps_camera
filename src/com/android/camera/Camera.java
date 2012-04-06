/*
 * Copyright (C) 2007 The Android Open Source Project
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

import com.android.camera.ui.CameraPicker;
import com.android.camera.ui.FaceView;
import com.android.camera.ui.IndicatorControlContainer;
import com.android.camera.ui.PopupManager;
import com.android.camera.ui.Rotatable;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.ui.SharePopup;
import com.android.camera.ui.ZoomControl;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.hardware.CameraSound;
import android.location.Location;
import android.media.CameraProfile;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

import java.util.HashMap;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import android.graphics.ImageFormat;
import android.os.SystemProperties;

import android.graphics.Paint.Align;


/** The Camera activity which can preview and take pictures. */
public class Camera extends ActivityBase implements FocusManager.Listener,
        View.OnTouchListener, ShutterButton.OnShutterButtonListener,
        SurfaceHolder.Callback, ModePicker.OnModeChangeListener,
        FaceDetectionListener, CameraPreference.OnPreferenceChangedListener,
        LocationManager.Listener {

    private static final String TAG = "camera";
    private static final String TAG_PROFILE = "qcamera PROFILE";

    private static final String[] OTHER_SETTING_KEYS = {
            CameraSettings.KEY_RECORD_LOCATION,
            CameraSettings.KEY_PICTURE_SIZE,
            CameraSettings.KEY_JPEG_QUALITY,
            CameraSettings.KEY_PICTURE_FORMAT,
            CameraSettings.KEY_COLOR_EFFECT,
            CameraSettings.KEY_SATURATION,
            CameraSettings.KEY_CONTRAST
         };
    private static final String[] OTHER_SETTING_KEYS_1 = {
            CameraSettings.KEY_AUTOEXPOSURE,
            CameraSettings.KEY_ANTIBANDING,
            CameraSettings.KEY_TOUCH_AF_AEC,
            //CameraSettings.KEY_SKIN_TONE_ENHANCEMENT,
            CameraSettings.KEY_SELECTABLE_ZONE_AF,
            CameraSettings.KEY_SHARPNESS
        };
    private static final String[] OTHER_SETTING_KEYS_2 = {
            CameraSettings.KEY_ISO,
            CameraSettings.KEY_FOCUS_MODE,
            CameraSettings.KEY_LENSSHADING,
            CameraSettings.KEY_FACE_DETECTION,
            CameraSettings.KEY_HISTOGRAM,
            CameraSettings.KEY_DENOISE,
            CameraSettings.KEY_REDEYE_REDUCTION,
            CameraSettings.KEY_ZSL,
            CameraSettings.KEY_AE_BRACKET_HDR
        };
    /*Histogram variables*/
    private GraphView mGraphView;
    private static final int STATS_DATA = 257;
    public static int statsdata[] = new int[STATS_DATA];
    public static boolean mHiston = false;
    public static boolean mBrightnessVisible = true;

    public HashMap otherSettingKeys = new HashMap(3);
    private static final int CROP_MSG = 1;
    private static final int FIRST_TIME_INIT = 2;
    private static final int CLEAR_SCREEN_DELAY = 3;
    private static final int SET_CAMERA_PARAMETERS_WHEN_IDLE = 4;
    private static final int CHECK_DISPLAY_ROTATION = 5;
    private static final int SHOW_TAP_TO_FOCUS_TOAST = 6;
    private static final int UPDATE_THUMBNAIL = 7;

    private static final int SET_SKIN_TONE_FACTOR = 9;

    // The subset of parameters we need to update in setCameraParameters().
    private static final int UPDATE_PARAM_INITIALIZE = 1;
    private static final int UPDATE_PARAM_ZOOM = 2;
    private static final int UPDATE_PARAM_PREFERENCE = 4;
    private static final int UPDATE_PARAM_ALL = -1;

    // When setCameraParametersWhenIdle() is called, we accumulate the subsets
    // needed to be updated in mUpdateSet.
    private int mUpdateSet;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private static final int ZOOM_STOPPED = 0;
    private static final int ZOOM_START = 1;
    private static final int ZOOM_STOPPING = 2;

    public boolean mFaceDetectionEnabled = false;

    private int mZoomState = ZOOM_STOPPED;
    private boolean mSmoothZoomSupported = false;
    private int mZoomValue;  // The current zoom value.
    private int mZoomMax;
    private int mTargetZoomValue;
    private int mBurstSnapNum;
    private int mReceivedSnapNum;
    private ZoomControl mZoomControl;

    private Parameters mParameters;
    private Parameters mInitialParams;
    private boolean mFocusAreaSupported;
    private boolean mMeteringAreaSupported;
    private boolean mAeLockSupported;
    private boolean mAwbLockSupported;
    private boolean mTouchAfAecFlag;

    private MyOrientationEventListener mOrientationListener;
    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    // The orientation compensation for icons and thumbnails. Ex: if the value
    // is 90, the UI components should be rotated 90 degrees counter-clockwise.
    private int mOrientationCompensation = 0;
    private ComboPreferences mPreferences;

    private static final String sTempCropFilename = "crop-temp";

    private ContentProviderClient mMediaProviderClient;
    private SurfaceHolder mSurfaceHolder = null;
    private ShutterButton mShutterButton;
    private GestureDetector mPopupGestureDetector;
    private boolean mOpenCameraFail = false;
    private boolean mCameraDisabled = false;
    private boolean mFaceDetectionStarted = false;

    private View mPreviewPanel;  // The container of PreviewFrameLayout.
    private PreviewFrameLayout mPreviewFrameLayout;
    private View mPreviewFrame;  // Preview frame area.
    private RotateDialogController mRotateDialog;

    // A popup window that contains a bigger thumbnail and a list of apps to share.
    private SharePopup mSharePopup;
    // The bitmap of the last captured picture thumbnail and the URI of the
    // original picture.
    private Thumbnail mThumbnail;
    private static final int MINIMUM_BRIGHTNESS = 0;
    private static final int MAXIMUM_BRIGHTNESS = 6;
    private int mbrightness = 3;
    private int mbrightness_step = 1;
    private ProgressBar brightnessProgressBar;
    // An imageview showing showing the last captured picture thumbnail.
    private RotateImageView mThumbnailView;
    private ModePicker mModePicker;
    private FaceView mFaceView;
    private RotateLayout mFocusAreaIndicator;
    private Rotatable mReviewCancelButton;
    private Rotatable mReviewDoneButton;

    // Constant from android.hardware.Camera.Parameters
    private static final String KEY_PICTURE_FORMAT = "picture-format";
    private static final String PIXEL_FORMAT_JPEG = "jpeg";
    private static final String PIXEL_FORMAT_RAW = "raw";

    // mCropValue and mSaveUri are used only if isImageCaptureIntent() is true.
    private String mCropValue;
    private Uri mSaveUri;

    // Small indicators which show the camera settings in the viewfinder.
    private TextView mExposureIndicator;
    private ImageView mGpsIndicator;
    private ImageView mFlashIndicator;
    private ImageView mSceneIndicator;
    private ImageView mWhiteBalanceIndicator;
    private ImageView mFocusIndicator;
    // A view group that contains all the small indicators.
    private Rotatable mOnScreenIndicators;

    // We use a thread in ImageSaver to do the work of saving images and
    // generating thumbnails. This reduces the shot-to-shot time.
    private ImageSaver mImageSaver;

    private CameraSound mCameraSound;

    private Runnable mDoSnapRunnable = new Runnable() {
        public void run() {
            onShutterButtonClick();
        }
    };

    private final StringBuilder mBuilder = new StringBuilder();
    private final Formatter mFormatter = new Formatter(mBuilder);
    private final Object[] mFormatterArgs = new Object[1];

    /**
     * An unpublished intent flag requesting to return as soon as capturing
     * is completed.
     *
     * TODO: consider publishing by moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    // The display rotation in degrees. This is only valid when mCameraState is
    // not PREVIEW_STOPPED.
    private int mDisplayRotation;
    // The value for android.hardware.Camera.setDisplayOrientation.
    private int mDisplayOrientation;
    private boolean mPausing;
    private boolean mFirstTimeInitialized;
    private boolean mIsImageCaptureIntent;
    private int mCameraId;

    private static final int PREVIEW_STOPPED = 0;
    private static final int IDLE = 1;  // preview is active
    // Focus is in progress. The exact focus state is in Focus.java.
    private static final int FOCUSING = 2;
    private static final int SNAPSHOT_IN_PROGRESS = 3;
    private int mCameraState = PREVIEW_STOPPED;
    private boolean mSnapshotOnIdle = false;
    private boolean mAspectRatioChanged = false;

    private ContentResolver mContentResolver;
    private boolean mDidRegister = false;

    private static final int MAX_SHARPNESS_LEVEL = 6;

    private LocationManager mLocationManager;

    private void stopCamera() {
        if(mGraphView != null)
            mGraphView.setCameraObject(null);

        stopPreview();
        // Close the camera now because other activities may need to use it.
        closeCamera();
        if (mCameraSound != null) mCameraSound.release();
        resetScreenOn();

        // Clear UI.
        collapseCameraControls();
        if (mSharePopup != null) mSharePopup.dismiss();
        if (mFaceView != null) mFaceView.clear();

        if (mFirstTimeInitialized) {
            mOrientationListener.disable();
            if (mImageSaver != null) {
                mImageSaver.finish();
                mImageSaver = null;
            }
            if (!mIsImageCaptureIntent && mThumbnail != null && !mThumbnail.fromFile()) {
                mThumbnail.saveTo(new File(getFilesDir(), Thumbnail.LAST_THUMB_FILENAME));
            }
        }

        if (mDidRegister) {
            unregisterReceiver(mReceiver);
            mDidRegister = false;
        }
        if (mLocationManager != null) mLocationManager.recordLocation(false);
        updateExposureOnScreenIndicator(0);

        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }

        // If we are in an image capture intent and has taken
        // a picture, we just clear it in onPause.
        mJpegImageData = null;

        // Remove the messages in the event queue.
        mHandler.removeMessages(FIRST_TIME_INIT);
        mHandler.removeMessages(CHECK_DISPLAY_ROTATION);
        mFocusManager.removeMessages();
    }

    private class CameraErrorCb
        implements android.hardware.Camera.ErrorCallback {
        private static final String TAG = "CameraErrorCallback";

        public void onError(int error, android.hardware.Camera camera) {
            Log.e(TAG, "Got camera error callback. error=" + error);
            if (error == android.hardware.Camera.CAMERA_ERROR_SERVER_DIED) {
                // We are not sure about the current state of the app (in preview or
                // snapshot or recording). Closing the app is better than creating a
                // new Camera object.
                throw new RuntimeException("Media server died.");
            } else if (error == android.hardware.Camera.CAMERA_ERROR_UNKNOWN) {
                stopCamera();
                Util.showErrorAndFinish(Camera.this, R.string.cannot_connect_camera);
            }
        }
    }

    private final ShutterCallback mShutterCallback = new ShutterCallback();
    private final PostViewPictureCallback mPostViewPictureCallback =
            new PostViewPictureCallback();
    private final RawPictureCallback mRawPictureCallback =
            new RawPictureCallback();
    private final AutoFocusCallback mAutoFocusCallback =
            new AutoFocusCallback();
    private final StatsCallback mStatsCallback = new StatsCallback();
    private final ZoomListener mZoomListener = new ZoomListener();
    private final CameraErrorCb mErrorCallback = new CameraErrorCb();
    private long mFocusStartTime;
    private long mCaptureStartTime;
    private long mShutterCallbackTime;
    private long mPostViewPictureCallbackTime;
    private long mRawPictureCallbackTime;
    private long mJpegPictureCallbackTime;
    private long mOnResumeTime;
    private long mShutterupTime;
    private long mPicturesRemaining;
    private byte[] mJpegImageData;


    // These latency time are for the CameraLatency test.
    public long mAutoFocusTime;
    public long mShutterLag;
    public long mShutterToPictureDisplayedTime;
    public long mPictureDisplayedToJpegCallbackTime;
    public long mJpegCallbackFinishTime;

    // This handles everything about focus.
    private FocusManager mFocusManager;
    private String mSceneMode;
    private Toast mNotSelectableToast;
    private Toast mNoShareToast;

    private final Handler mHandler = new MainHandler();
    private IndicatorControlContainer mIndicatorControlContainer;
    private PreferenceGroup mPreferenceGroup;

    // multiple cameras support
    private int mNumberOfCameras;
    private int mFrontCameraId;
    private int mBackCameraId;

    private boolean mQuickCapture;

    private static final int MIN_SCE_FACTOR = -10;
    private static final int MAX_SCE_FACTOR = +10;
    private int SCE_FACTOR_STEP = 10;
    private int mskinToneValue = 0;
    private boolean mSkinToneSeekBar= false;
    private boolean mSeekBarInitialized = false;
    private SeekBar skinToneSeekBar;
    private TextView LeftValue;
    private TextView RightValue;
    private TextView Title;

    private int mSnapshotMode;
    private boolean zslrestartPreview;

    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CLEAR_SCREEN_DELAY: {
                    getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case FIRST_TIME_INIT: {
                    initializeFirstTime();
                    break;
                }

                case SET_CAMERA_PARAMETERS_WHEN_IDLE: {
                    setCameraParametersWhenIdle(0);
                    break;
                }

                case CHECK_DISPLAY_ROTATION: {
                    // Set the display orientation if display rotation has changed.
                    // Sometimes this happens when the device is held upside
                    // down and camera app is opened. Rotation animation will
                    // take some time and the rotation value we have got may be
                    // wrong. Framework does not have a callback for this now.
                    if (Util.getDisplayRotation(Camera.this) != mDisplayRotation) {
                        setDisplayOrientation();
                    }
                    if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
                        mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
                    }
                    break;
                }

                case SHOW_TAP_TO_FOCUS_TOAST: {
                    showTapToFocusToast();
                    break;
                }

                case UPDATE_THUMBNAIL: {
                    mImageSaver.updateThumbnail();
                    break;
                }

                case SET_SKIN_TONE_FACTOR: {
                    Log.e(TAG, "yyan set tone bar: mSceneMode = " + mSceneMode);
                    setSkinToneFactor();
                    mSeekBarInitialized = true;
                    // skin tone ie enabled only for auto,party and portrait BSM
                    // when color effects are not enabled
                    String colorEffect = mPreferences.getString(
                        CameraSettings.KEY_COLOR_EFFECT,
                        getString(R.string.pref_camera_coloreffect_default));
                    if((//Parameters.SCENE_MODE_AUTO.equals(mSceneMode) ||
                        Parameters.SCENE_MODE_PARTY.equals(mSceneMode) ||
                        //Parameters.SCENE_MODE_OFF.equals(mSceneMode) ||
                        Parameters.SCENE_MODE_PORTRAIT.equals(mSceneMode))&&
                        (Parameters.EFFECT_NONE.equals(colorEffect))) {
                        ;
                    }
                    else{
                        Log.e(TAG, "yyan Skin tone bar: disable");
                        disableSkinToneSeekBar();
                    }
                    break;
                }
            }
        }
    }

    private void resetExposureCompensation() {
        String value = mPreferences.getString(CameraSettings.KEY_EXPOSURE,
                CameraSettings.EXPOSURE_DEFAULT_VALUE);
        if (!CameraSettings.EXPOSURE_DEFAULT_VALUE.equals(value)) {
            Editor editor = mPreferences.edit();
            editor.putString(CameraSettings.KEY_EXPOSURE, "0");
            editor.apply();
            if (mIndicatorControlContainer != null) {
                mIndicatorControlContainer.reloadPreferences();
            }
        }
    }

    private void keepMediaProviderInstance() {
        // We want to keep a reference to MediaProvider in camera's lifecycle.
        // TODO: Utilize mMediaProviderClient instance to replace
        // ContentResolver calls.
        if (mMediaProviderClient == null) {
            mMediaProviderClient = getContentResolver()
                    .acquireContentProviderClient(MediaStore.AUTHORITY);
        }
    }

    // Snapshots can only be taken after this is called. It should be called
    // once only. We could have done these things in onCreate() but we want to
    // make preview screen appear as soon as possible.
    private void initializeFirstTime() {
        if (mFirstTimeInitialized) return;

        // Create orientation listenter. This should be done first because it
        // takes some time to get first orientation.
        mOrientationListener = new MyOrientationEventListener(Camera.this);
        mOrientationListener.enable();

        // Initialize location sevice.
        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, getContentResolver());
        initOnScreenIndicator();
        mLocationManager.recordLocation(recordLocation);

        keepMediaProviderInstance();
        checkStorage();

        // Initialize last picture button.
        mContentResolver = getContentResolver();
        if (!mIsImageCaptureIntent) {  // no thumbnail in image capture intent
            initThumbnailButton();
        }

        // Initialize shutter button.
        mShutterButton = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setOnShutterButtonListener(this);
        mShutterButton.setVisibility(View.VISIBLE);

        // Initialize focus UI.
        mPreviewFrame = findViewById(R.id.camera_preview);
        mPreviewFrame.setOnTouchListener(this);
        mFocusAreaIndicator = (RotateLayout) findViewById(R.id.focus_indicator_rotate_layout);
        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
        boolean mirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
        mFocusManager.initialize(mFocusAreaIndicator, mPreviewFrame, mFaceView, this,
                mirror, mDisplayOrientation);
        mImageSaver = new ImageSaver();
        Util.initializeScreenBrightness(getWindow(), getContentResolver());
        installIntentFilter();
        mGraphView = (GraphView)findViewById(R.id.graph_view);
        if(mGraphView == null)
            Log.e(TAG, "mGraphView is null");
        mGraphView.setCameraObject(Camera.this);

        initializeZoom();
        updateOnScreenIndicators();

        startFaceDetection();

        // Show the tap to focus toast if this is the first start.
        if (mFocusAreaSupported &&
                mPreferences.getBoolean(CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN, true)) {
            // Delay the toast for one second to wait for orientation.
            mHandler.sendEmptyMessageDelayed(SHOW_TAP_TO_FOCUS_TOAST, 1000);
        }

        mFirstTimeInitialized = true;
        addIdleHandler();
    }

    private void addIdleHandler() {
        MessageQueue queue = Looper.myQueue();
        queue.addIdleHandler(new MessageQueue.IdleHandler() {
            public boolean queueIdle() {
                Storage.ensureOSXCompatible();
                return false;
            }
        });
    }

    private void initThumbnailButton() {
        // Load the thumbnail from the disk.
        mThumbnail = Thumbnail.loadFrom(new File(getFilesDir(), Thumbnail.LAST_THUMB_FILENAME));
        updateThumbnailButton();
    }

    private void updateThumbnailButton() {
        // Update last image if URI is invalid and the storage is ready.
        if ((mThumbnail == null || !Util.isUriValid(mThumbnail.getUri(), mContentResolver))
                && mPicturesRemaining >= 0) {
            mThumbnail = Thumbnail.getLastThumbnail(mContentResolver);
        }
        if (mThumbnail != null) {
            mThumbnailView.setBitmap(mThumbnail.getBitmap());
        } else {
            mThumbnailView.setBitmap(null);
        }
    }

    // If the activity is paused and resumed, this method will be called in
    // onResume.
    private void initializeSecondTime() {
        // Start orientation listener as soon as possible because it takes
        // some time to get first orientation.
        mOrientationListener.enable();

        // Start location update if needed.
        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, getContentResolver());
        mLocationManager.recordLocation(recordLocation);

        installIntentFilter();
        mImageSaver = new ImageSaver();
        initializeZoom();
        keepMediaProviderInstance();
        checkStorage();
        hidePostCaptureAlert();

        if(mGraphView != null)
          mGraphView.setCameraObject(Camera.this);

        if (!mIsImageCaptureIntent) {
            updateThumbnailButton();
            mModePicker.setCurrentMode(ModePicker.MODE_CAMERA);
        }
    }

    private class ZoomChangeListener implements ZoomControl.OnZoomChangedListener {
        // only for immediate zoom
        @Override
        public void onZoomValueChanged(int index) {
            Camera.this.onZoomValueChanged(index);
        }

        // only for smooth zoom
        @Override
        public void onZoomStateChanged(int state) {
            if (mPausing) return;

            Log.v(TAG, "zoom picker state=" + state);
            if (state == ZoomControl.ZOOM_IN) {
                Camera.this.onZoomValueChanged(mZoomMax);
            } else if (state == ZoomControl.ZOOM_OUT) {
                Camera.this.onZoomValueChanged(0);
            } else {
                mTargetZoomValue = -1;
                if (mZoomState == ZOOM_START) {
                    mZoomState = ZOOM_STOPPING;
                    mCameraDevice.stopSmoothZoom();
                }
            }
        }
    }

    private void initializeZoom() {
        // Get the parameter to make sure we have the up-to-date zoom value.
        mParameters = mCameraDevice.getParameters();
        if (!mParameters.isZoomSupported()) return;
        mZoomMax = mParameters.getMaxZoom();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        mZoomControl.setZoomMax(mZoomMax);
        mZoomControl.setZoomIndex(mParameters.getZoom());
        mZoomControl.setSmoothZoomSupported(mSmoothZoomSupported);
        mZoomControl.setOnZoomChangeListener(new ZoomChangeListener());
        mCameraDevice.setZoomChangeListener(mZoomListener);
    }

    private void onZoomValueChanged(int index) {
        // Not useful to change zoom value when the activity is paused.
        if (mPausing) return;

        if (mSmoothZoomSupported) {
            if (mTargetZoomValue != index && mZoomState != ZOOM_STOPPED) {
                mTargetZoomValue = index;
                if (mZoomState == ZOOM_START) {
                    mZoomState = ZOOM_STOPPING;
                    mCameraDevice.stopSmoothZoom();
                }
            } else if (mZoomState == ZOOM_STOPPED && mZoomValue != index) {
                mTargetZoomValue = index;
                mCameraDevice.startSmoothZoom(index);
                mZoomState = ZOOM_START;
            }
        } else {
            mZoomValue = index;
            setCameraParametersWhenIdle(UPDATE_PARAM_ZOOM);
        }
    }
    @Override

    public void startFaceDetection() {
        if (mFaceDetectionEnabled == false
                || mFaceDetectionStarted || mCameraState != IDLE) return;
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = true;
            mFaceView = (FaceView) findViewById(R.id.face_view);
            mFaceView.clear();
            mFaceView.setVisibility(View.VISIBLE);
            mFaceView.setDisplayOrientation(mDisplayOrientation);
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            mFaceView.setMirror(info.facing == CameraInfo.CAMERA_FACING_FRONT);
            mFaceView.resume();
            mCameraDevice.setFaceDetectionListener(this);
            mCameraDevice.startFaceDetection();
        }
     }

    @Override
    public void stopFaceDetection() {
        if (mFaceDetectionEnabled == false || mFaceDetectionStarted == false)
            return;
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = false;
            mCameraDevice.setFaceDetectionListener(null);
            mCameraDevice.stopFaceDetection();
            if (mFaceView != null) mFaceView.clear();
//            mFaceView.setVisibility(View.INVISIBLE);
        }
    }

    private class PopupGestureListener
            extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            // Check if the popup window is visible.
            View popup = mIndicatorControlContainer.getActiveSettingPopup();
            if (popup == null) return false;


            // Let popup window, indicator control or preview frame handle the
            // event by themselves. Dismiss the popup window if users touch on
            // other areas.
            if (!Util.pointInView(e.getX(), e.getY(), popup)
                    && !Util.pointInView(e.getX(), e.getY(), mIndicatorControlContainer)
                    && !Util.pointInView(e.getX(), e.getY(), mPreviewFrame)) {
                mIndicatorControlContainer.dismissSettingPopup();
                // Let event fall through.
            }
            return false;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        // Check if the popup window should be dismissed first.
        if (mPopupGestureDetector != null && mPopupGestureDetector.onTouchEvent(m)) {
            return true;
        }

        return super.dispatchTouchEvent(m);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Received intent action=" + action);
            if (action.equals(Intent.ACTION_MEDIA_MOUNTED)
                    || action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
                    || action.equals(Intent.ACTION_MEDIA_CHECKING)) {
                checkStorage();
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                checkStorage();
                if (!mIsImageCaptureIntent) {
                    updateThumbnailButton();
                }
            }
        }
    };

    private void initOnScreenIndicator() {
        mGpsIndicator = (ImageView) findViewById(R.id.onscreen_gps_indicator);
        mExposureIndicator = (TextView) findViewById(R.id.onscreen_exposure_indicator);
        mFlashIndicator = (ImageView) findViewById(R.id.onscreen_flash_indicator);
        mSceneIndicator = (ImageView) findViewById(R.id.onscreen_scene_indicator);
        mWhiteBalanceIndicator =
                (ImageView) findViewById(R.id.onscreen_white_balance_indicator);
        mFocusIndicator = (ImageView) findViewById(R.id.onscreen_focus_indicator);
    }

    @Override
    public void showGpsOnScreenIndicator(boolean hasSignal) {
        if (mGpsIndicator == null) {
            return;
        }
        if (hasSignal) {
            mGpsIndicator.setImageResource(R.drawable.ic_viewfinder_gps_on);
        } else {
            mGpsIndicator.setImageResource(R.drawable.ic_viewfinder_gps_no_signal);
        }
        mGpsIndicator.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideGpsOnScreenIndicator() {
        if (mGpsIndicator == null) {
            return;
        }
        mGpsIndicator.setVisibility(View.GONE);
    }

    private void updateExposureOnScreenIndicator(int value) {
        if (mExposureIndicator == null) {
            return;
        }
        if (value == 0) {
            mExposureIndicator.setText("");
            mExposureIndicator.setVisibility(View.GONE);
        } else {
            float step = mParameters.getExposureCompensationStep();
            mFormatterArgs[0] = value * step;
            mBuilder.delete(0, mBuilder.length());
            mFormatter.format("%+1.1f", mFormatterArgs);
            String exposure = mFormatter.toString();
            mExposureIndicator.setText(exposure);
            mExposureIndicator.setVisibility(View.VISIBLE);
        }
    }

    private void updateFlashOnScreenIndicator(String value) {
        if (mFlashIndicator == null) {
            return;
        }
        if (Parameters.FLASH_MODE_AUTO.equals(value)) {
            mFlashIndicator.setImageResource(R.drawable.ic_indicators_landscape_flash_auto);
            mFlashIndicator.setVisibility(View.VISIBLE);
        } else if (Parameters.FLASH_MODE_ON.equals(value)) {
            mFlashIndicator.setImageResource(R.drawable.ic_indicators_landscape_flash_on);
            mFlashIndicator.setVisibility(View.VISIBLE);
        } else if (Parameters.FLASH_MODE_OFF.equals(value)) {
            mFlashIndicator.setVisibility(View.GONE);
        }
    }

    private void updateSceneOnScreenIndicator(boolean isVisible) {
        if (mSceneIndicator == null) {
            return;
        }
        mSceneIndicator.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void updateWhiteBalanceOnScreenIndicator(String value) {
        if (mWhiteBalanceIndicator == null) {
            return;
        }
        if (Parameters.WHITE_BALANCE_AUTO.equals(value)) {
            mWhiteBalanceIndicator.setVisibility(View.GONE);
        } else {
            if (Parameters.WHITE_BALANCE_FLUORESCENT.equals(value)) {
                mWhiteBalanceIndicator.setImageResource(R.drawable.ic_indicators_fluorescent);
            } else if (Parameters.WHITE_BALANCE_INCANDESCENT.equals(value)) {
                mWhiteBalanceIndicator.setImageResource(R.drawable.ic_indicators_incandescent);
            } else if (Parameters.WHITE_BALANCE_DAYLIGHT.equals(value)) {
                mWhiteBalanceIndicator.setImageResource(R.drawable.ic_indicators_sunlight);
            } else if (Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT.equals(value)) {
                mWhiteBalanceIndicator.setImageResource(R.drawable.ic_indicators_cloudy);
            }
            mWhiteBalanceIndicator.setVisibility(View.VISIBLE);
        }
    }

    private void updateFocusOnScreenIndicator(String value) {
        if (mFocusIndicator == null) {
            return;
        }
        if (Parameters.FOCUS_MODE_INFINITY.equals(value)) {
            mFocusIndicator.setImageResource(R.drawable.ic_indicators_landscape);
            mFocusIndicator.setVisibility(View.VISIBLE);
        } else if (Parameters.FOCUS_MODE_MACRO.equals(value)) {
            mFocusIndicator.setImageResource(R.drawable.ic_indicators_macro);
            mFocusIndicator.setVisibility(View.VISIBLE);
        } else {
            mFocusIndicator.setVisibility(View.GONE);
        }
    }

    private void updateOnScreenIndicators() {
        boolean isAutoScene = !(Parameters.SCENE_MODE_AUTO.equals(mParameters.getSceneMode()));
        updateSceneOnScreenIndicator(isAutoScene);
        updateExposureOnScreenIndicator(CameraSettings.readExposure(mPreferences));
        updateFlashOnScreenIndicator(mParameters.getFlashMode());
        updateWhiteBalanceOnScreenIndicator(mParameters.getWhiteBalance());
        updateFocusOnScreenIndicator(mParameters.getFocusMode());
    }
    private final class ShutterCallback
            implements android.hardware.Camera.ShutterCallback {
        public void onShutter() {
            mShutterCallbackTime = System.currentTimeMillis();
            mShutterLag = mShutterCallbackTime - mCaptureStartTime;
            Log.v(TAG, "mShutterLag = " + mShutterLag + "ms");
            mFocusManager.onShutter();
        }
    }

    private final class StatsCallback
           implements android.hardware.Camera.CameraDataCallback {
        public void onCameraData(int [] data, android.hardware.Camera camera) {
            //if(!mPreviewing || !mHiston || !mFirstTimeInitialized){
            if(!mHiston || !mFirstTimeInitialized){
                return;
            }
            /*The first element in the array stores max hist value . Stats data begin from second value*/
            synchronized(statsdata) {
                System.arraycopy(data,0,statsdata,0,STATS_DATA);
            }
            if(mGraphView != null) {
                mGraphView.PreviewChanged();
            }
        }
    }

    private final class PostViewPictureCallback implements PictureCallback {
        public void onPictureTaken(
                byte [] data, android.hardware.Camera camera) {
            mPostViewPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToPostViewCallbackTime = "
                    + (mPostViewPictureCallbackTime - mShutterCallbackTime)
                    + "ms");
        }
    }

    private final class RawPictureCallback implements PictureCallback {
        public void onPictureTaken(
                byte [] rawData, android.hardware.Camera camera) {
            mRawPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToRawCallbackTime = "
                    + (mRawPictureCallbackTime - mShutterCallbackTime) + "ms");

            if (mShutterupTime != 0)
                Log.e(TAG,"<qcamera PROFILE> Snapshot to Thumb Latency = "
                        + (mRawPictureCallbackTime - mShutterupTime) + " ms");


        }
    }

    private final class JpegPictureCallback implements PictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        public void onPictureTaken(
                final byte [] jpegData, final android.hardware.Camera camera) {
            if (mPausing) {
                return;
            }
            mReceivedSnapNum = mReceivedSnapNum + 1;

            mJpegPictureCallbackTime = System.currentTimeMillis();
            // If postview callback has arrived, the captured image is displayed
            // in postview callback. If not, the captured image is displayed in
            // raw picture callback.
            if (mPostViewPictureCallbackTime != 0) {
                mShutterToPictureDisplayedTime =
                        mPostViewPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mPostViewPictureCallbackTime;
            } else {
                mShutterToPictureDisplayedTime =
                        mRawPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mRawPictureCallbackTime;
            }
            Log.v(TAG, "mPictureDisplayedToJpegCallbackTime = "
                    + mPictureDisplayedToJpegCallbackTime + "ms");

            if (mShutterupTime != 0)
                Log.e(TAG,"<qcamera PROFILE> Snapshot to Snapshot Latency = "
                        + (mJpegPictureCallbackTime - mShutterupTime) + " ms");

            if (!mIsImageCaptureIntent && mSnapshotMode != CameraInfo.CAMERA_SUPPORT_MODE_ZSL
                && mReceivedSnapNum == mBurstSnapNum) {
                    startPreview();
                    startFaceDetection();
            } else if(mReceivedSnapNum == mBurstSnapNum){
                setCameraState(IDLE);
            }

            if (!mIsImageCaptureIntent) {
                Size s = mParameters.getPictureSize();
                if (s==null) {
                    Log.e(TAG,"error: getPictureSize = null !");
                }
                else{
                    mImageSaver.addImage(jpegData, mLocation, s.width, s.height);
                }
            } else {
                mJpegImageData = jpegData;
                if (!mQuickCapture) {
                    showPostCaptureAlert();
                } else {
                    doAttach();
                }
            }

            // Check this in advance of each shot so we don't add to shutter
            // latency. It's true that someone else could write to the SD card in
            // the mean time and fill it, but that could have happened between the
            // shutter press and saving the JPEG too.
            checkStorage();

            long now = System.currentTimeMillis();
            mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
            Log.e(TAG, "mJpegCallbackFinishTime = "
                    + mJpegCallbackFinishTime + "ms");
            if (mReceivedSnapNum == mBurstSnapNum) {
                mJpegPictureCallbackTime = 0;
            }
        }
    }

    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
        // no support
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromtouch) {
        }
        public void onStopTrackingTouch(SeekBar bar) {
        }
    };

    private OnSeekBarChangeListener mskinToneSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
        // no support
        }

        public void onProgressChanged(SeekBar bar, int progress, boolean fromtouch) {
            int value = (progress + MIN_SCE_FACTOR) * SCE_FACTOR_STEP;
            if(progress > (MAX_SCE_FACTOR - MIN_SCE_FACTOR)/2){
                RightValue.setText(String.valueOf(value));
                LeftValue.setText("");
            } else if (progress < (MAX_SCE_FACTOR - MIN_SCE_FACTOR)/2){
                LeftValue.setText(String.valueOf(value));
                RightValue.setText("");
            } else {
                LeftValue.setText("");
                RightValue.setText("");
            }
            if(value != mskinToneValue) {
                mskinToneValue = value;
                mParameters = mCameraDevice.getParameters();
                mParameters.set("skinToneEnhancement", String.valueOf(mskinToneValue));
                mCameraDevice.setParameters(mParameters);
            }
        }

        public void onStopTrackingTouch(SeekBar bar) {
            Log.e(TAG, "yyan Set onStopTrackingTouch mskinToneValue = " + mskinToneValue);
            Editor editor = mPreferences.edit();
            editor.putString(CameraSettings.KEY_SKIN_TONE_ENHANCEMENT_FACTOR,
                             Integer.toString(mskinToneValue));
            editor.apply();
        }
    };

    private final class AutoFocusCallback
            implements android.hardware.Camera.AutoFocusCallback {
        public void onAutoFocus(
                boolean focused, android.hardware.Camera camera) {
            if (mPausing) return;

            mAutoFocusTime = System.currentTimeMillis() - mFocusStartTime;
            Log.v(TAG, "mAutoFocusTime = " + mAutoFocusTime + "ms");
            setCameraState(IDLE);
            mFocusManager.onAutoFocus(focused);
        }
    }

    private final class ZoomListener
            implements android.hardware.Camera.OnZoomChangeListener {
        @Override
        public void onZoomChange(
                int value, boolean stopped, android.hardware.Camera camera) {
            Log.v(TAG, "Zoom changed: value=" + value + ". stopped=" + stopped);
            mZoomValue = value;

            // Update the UI when we get zoom value.
            mZoomControl.setZoomIndex(value);

            // Keep mParameters up to date. We do not getParameter again in
            // takePicture. If we do not do this, wrong zoom value will be set.
            mParameters.setZoom(value);

            if (stopped && mZoomState != ZOOM_STOPPED) {
                if (mTargetZoomValue != -1 && value != mTargetZoomValue) {
                    mCameraDevice.startSmoothZoom(mTargetZoomValue);
                    mZoomState = ZOOM_START;
                } else {
                    mZoomState = ZOOM_STOPPED;
                }
            }
        }
    }

    // Each SaveRequest remembers the data needed to save an image.
    private static class SaveRequest {
        byte[] data;
        Location loc;
        int width, height;
        long dateTaken;
        int previewWidth;
    }

    // We use a queue to store the SaveRequests that have not been completed
    // yet. The main thread puts the request into the queue. The saver thread
    // gets it from the queue, does the work, and removes it from the queue.
    //
    // There are several cases the main thread needs to wait for the saver
    // thread to finish all the work in the queue:
    // (1) When the activity's onPause() is called, we need to finish all the
    // work, so other programs (like Gallery) can see all the images.
    // (2) When we need to show the SharePop, we need to finish all the work
    // too, because we want to show the thumbnail of the last image taken.
    //
    // If the queue becomes too long, adding a new request will block the main
    // thread until the queue length drops below the threshold (QUEUE_LIMIT).
    // If we don't do this, we may face several problems: (1) We may OOM
    // because we are holding all the jpeg data in memory. (2) We may ANR
    // when we need to wait for saver thread finishing all the work (in
    // onPause() or showSharePopup()) because the time to finishing a long queue
    // of work may be too long.
    private class ImageSaver extends Thread {
        private static final int QUEUE_LIMIT = 3;

        private ArrayList<SaveRequest> mQueue;
        private Thumbnail mPendingThumbnail;
        private Object mUpdateThumbnailLock = new Object();
        private boolean mStop;

        // Runs in main thread
        public ImageSaver() {
            mQueue = new ArrayList<SaveRequest>();
            start();
        }

        // Runs in main thread
        public void addImage(final byte[] data, Location loc, int width,
                int height) {
            SaveRequest r = new SaveRequest();
            r.data = data;
            r.loc = (loc == null) ? null : new Location(loc);  // make a copy
            r.width = width;
            r.height = height;
            r.dateTaken = System.currentTimeMillis();
            if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                r.previewWidth = mPreviewFrameLayout.getHeight();
            } else {
                r.previewWidth = mPreviewFrameLayout.getWidth();
            }
            synchronized (this) {
                while (mQueue.size() >= QUEUE_LIMIT) {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // ignore.
                    }
                }
                mQueue.add(r);
                notifyAll();  // Tell saver thread there is new work to do.
            }
        }

        // Runs in saver thread
        @Override
        public void run() {
            while (true) {
                SaveRequest r;
                synchronized (this) {
                    if (mQueue.isEmpty()) {
                        notifyAll();  // notify main thread in waitDone

                        // Note that we can only stop after we saved all images
                        // in the queue.
                        if (mStop) break;

                        try {
                            wait();
                        } catch (InterruptedException ex) {
                            // ignore.
                        }
                        continue;
                    }
                    r = mQueue.get(0);
                }
                storeImage(r.data, r.loc, r.width, r.height, r.dateTaken,
                        r.previewWidth);
                synchronized(this) {
                    mQueue.remove(0);
                    notifyAll();  // the main thread may wait in addImage
                }
            }
        }

        // Runs in main thread
        public void waitDone() {
            synchronized (this) {
                while (!mQueue.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // ignore.
                    }
                }
            }
            updateThumbnail();
        }

        // Runs in main thread
        public void finish() {
            waitDone();
            synchronized (this) {
                mStop = true;
                notifyAll();
            }
            try {
                join();
            } catch (InterruptedException ex) {
                // ignore.
            }
        }

        // Runs in main thread (because we need to update mThumbnailView in the
        // main thread)
        public void updateThumbnail() {
            Thumbnail t;
            synchronized (mUpdateThumbnailLock) {
                mHandler.removeMessages(UPDATE_THUMBNAIL);
                t = mPendingThumbnail;
                mPendingThumbnail = null;
            }

            if (t != null) {
                mThumbnail = t;
                mThumbnailView.setBitmap(mThumbnail.getBitmap());
            }
            // Share popup may still have the reference to the old thumbnail. Clear it.
            mSharePopup = null;
        }

        // Runs in saver thread
        private void storeImage(final byte[] data, Location loc, int width,
                int height, long dateTaken, int previewWidth) {
            String pictureFormat = mParameters.get(KEY_PICTURE_FORMAT);
            String title = Util.createJpegName(dateTaken);
            int orientation = Exif.getOrientation(data);

            Uri uri = Storage.addImage(mContentResolver, title, pictureFormat,
                    dateTaken, loc, orientation, data, width, height);
            if (uri != null) {
                boolean needThumbnail;
                synchronized (this) {
                    // If the number of requests in the queue (include the
                    // current one) is greater than 1, we don't need to generate
                    // thumbnail for this image. Because we'll soon replace it
                    // with the thumbnail for some image later in the queue.
                    needThumbnail = (mQueue.size() <= 1);
                }
                if (needThumbnail) {
                    // Create a thumbnail whose width is equal or bigger than
                    // that of the preview.
                    int ratio = (int) Math.ceil((double) width / previewWidth);
                    int inSampleSize = Integer.highestOneBit(ratio);
                    Thumbnail t = Thumbnail.createThumbnail(
                                data, orientation, inSampleSize, uri);
                    synchronized (mUpdateThumbnailLock) {
                        // We need to update the thumbnail in the main thread,
                        // so send a message to run updateThumbnail().
                        mPendingThumbnail = t;
                        mHandler.sendEmptyMessage(UPDATE_THUMBNAIL);
                    }
                }
                Util.broadcastNewPicture(Camera.this, uri);
            }
        }
    }

    private void setCameraState(int state) {
        mCameraState = state;
        switch (state) {
            case SNAPSHOT_IN_PROGRESS:
            case FOCUSING:
                enableCameraControls(false);
                break;
            case IDLE:
            case PREVIEW_STOPPED:
                enableCameraControls(true);
                break;
        }
    }

    @Override
    public boolean capture() {
        // If we are already in the middle of taking a snapshot then ignore.
        if (mCameraState == SNAPSHOT_IN_PROGRESS || mCameraDevice == null) {
            return false;
        }
        mCaptureStartTime = System.currentTimeMillis();
        qcameraUtilProfile("Snap start at");
        mPostViewPictureCallbackTime = 0;
        mJpegImageData = null;

        // Set rotation and gps data.
        Util.setRotationParameter(mParameters, mCameraId, mOrientation);
        String pictureFormat = mParameters.get(KEY_PICTURE_FORMAT);
        Location loc = null;
        if (pictureFormat != null &&
            PIXEL_FORMAT_JPEG.equalsIgnoreCase(pictureFormat)) {
            loc = mLocationManager.getCurrentLocation();
        }

        Util.setGpsParameters(mParameters, loc);
        qcameraUtilProfile("pre set parm");
        mCameraDevice.setParameters(mParameters);

        if(mHiston) {
            mHiston = false;
            mCameraDevice.setHistogramMode(null);
            if(mGraphView != null)
                mGraphView.setVisibility(View.INVISIBLE);
        }

        qcameraUtilProfile("pre-snap");

        mReceivedSnapNum = 0;
        mCameraDevice.takePicture(mShutterCallback, mRawPictureCallback,
                mPostViewPictureCallback, new JpegPictureCallback(loc));
        if (mSnapshotMode == CameraInfo.CAMERA_SUPPORT_MODE_ZSL) {
                zslrestartPreview = false;
        }
        mFaceDetectionStarted = false;
        setCameraState(SNAPSHOT_IN_PROGRESS);
        mParameters = mCameraDevice.getParameters();
        mBurstSnapNum = mParameters.getInt("num-snaps-per-shutter");
        return true;
    }

    @Override
    public void setFocusParameters() {
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    @Override
    public void playSound(int soundId) {
        mCameraSound.playSound(soundId);
    }

    private boolean saveDataToFile(String filePath, byte[] data) {
        FileOutputStream f = null;
        try {
            f = new FileOutputStream(filePath);
            f.write(data);
        } catch (IOException e) {
            return false;
        } finally {
            Util.closeSilently(f);
        }
        return true;
    }

    private void getPreferredCameraId() {
        mPreferences = new ComboPreferences(this);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        mCameraId = CameraSettings.readPreferredCameraId(mPreferences);

        // Testing purpose. Launch a specific camera through the intent extras.
        int intentCameraId = Util.getCameraFacingIntentExtras(this);
        if (intentCameraId != -1) {
            mCameraId = intentCameraId;
        }
    }

    Thread mCameraOpenThread = new Thread(new Runnable() {
        public void run() {
            try {
                qcameraUtilProfile("open camera");
                mCameraDevice = Util.openCamera(Camera.this, mCameraId);
                qcameraUtilProfile("camera opended");
            } catch (CameraHardwareException e) {
                mOpenCameraFail = true;
            } catch (CameraDisabledException e) {
                mCameraDisabled = true;
            }
        }
    });

    Thread mCameraPreviewThread = new Thread(new Runnable() {
        public void run() {
            initializeCapabilities();
            startPreview();
        }
    });

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        qcameraUtilProfile("Create camera");
        getPreferredCameraId();
        String[] defaultFocusModes = getResources().getStringArray(
                R.array.pref_camera_focusmode_default_array);
        mFocusManager = new FocusManager(mPreferences, defaultFocusModes);

        /*
         * To reduce startup time, we start the camera open and preview threads.
         * We make sure the preview is started at the end of onCreate.
         */
        mCameraOpenThread.start();

        mIsImageCaptureIntent = isImageCaptureIntent();
        setContentView(R.layout.camera);
        if (mIsImageCaptureIntent) {
            mReviewDoneButton = (Rotatable) findViewById(R.id.btn_done);
            mReviewCancelButton = (Rotatable) findViewById(R.id.btn_cancel);
            findViewById(R.id.btn_cancel).setVisibility(View.VISIBLE);
        } else {
            mThumbnailView = (RotateImageView) findViewById(R.id.thumbnail);
            mThumbnailView.enableFilter(false);
            mThumbnailView.setVisibility(View.VISIBLE);
        }

        mRotateDialog = new RotateDialogController(this, R.layout.rotate_dialog);

        mPreferences.setLocalId(this, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();
        mQuickCapture = getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);

        mShutterupTime = 0;

        // we need to reset exposure for the preview
        resetExposureCompensation();

        Util.enterLightsOutMode(getWindow());

        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceChanged / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceView preview = (SurfaceView) findViewById(R.id.camera_preview);
        SurfaceHolder holder = preview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        brightnessProgressBar = (ProgressBar) findViewById(R.id.progress);
        if (brightnessProgressBar instanceof SeekBar) {
            SeekBar seeker = (SeekBar) brightnessProgressBar;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        brightnessProgressBar.setMax(MAXIMUM_BRIGHTNESS);
        brightnessProgressBar.setProgress(mbrightness);


        skinToneSeekBar = (SeekBar) findViewById(R.id.skintoneseek);
        skinToneSeekBar.setOnSeekBarChangeListener(mskinToneSeekListener);
        skinToneSeekBar.setVisibility(View.INVISIBLE);
        Title = (TextView)findViewById(R.id.skintonetitle);
        RightValue = (TextView)findViewById(R.id.skintoneright);
        LeftValue = (TextView)findViewById(R.id.skintoneleft);

        // Make sure camera device is opened.
        try {
            mCameraOpenThread.join();
            mCameraOpenThread = null;
            if (mOpenCameraFail) {
                Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
                return;
            } else if (mCameraDisabled) {
                Util.showErrorAndFinish(this, R.string.camera_disabled);
                return;
            }
        } catch (InterruptedException ex) {
            // ignore
        }
        mCameraPreviewThread.start();

        if (mIsImageCaptureIntent) {
            setupCaptureParams();
        } else {
            mModePicker = (ModePicker) findViewById(R.id.mode_picker);
            mModePicker.setVisibility(View.VISIBLE);
            mModePicker.setOnModeChangeListener(this);
            mModePicker.setCurrentMode(ModePicker.MODE_CAMERA);
        }

        mZoomControl = (ZoomControl) findViewById(R.id.zoom_control);
        mOnScreenIndicators = (Rotatable) findViewById(R.id.on_screen_indicators);
        mLocationManager = new LocationManager(this, this);

        mBackCameraId = CameraHolder.instance().getBackCameraId();
        mFrontCameraId = CameraHolder.instance().getFrontCameraId();

        // Wait until the camera settings are retrieved.
        synchronized (mCameraPreviewThread) {
            try {
                mCameraPreviewThread.wait();
            } catch (InterruptedException ex) {
                // ignore
            }
        }

        // Do this after starting preview because it depends on camera
        // parameters.
        initializeIndicatorControl();
        mCameraSound = new CameraSound();

        // Make sure preview is started.
        try {
            mCameraPreviewThread.join();
        } catch (InterruptedException ex) {
            // ignore
        }
        mCameraPreviewThread = null;
    }

    private void overrideCameraSettings(final String flashMode,
            final String whiteBalance, final String focusMode,
            final String exposureMode, final String touchMode) {
        if (mIndicatorControlContainer != null) {
            mIndicatorControlContainer.overrideSettings(
                    CameraSettings.KEY_FLASH_MODE, flashMode,
                    CameraSettings.KEY_WHITE_BALANCE, whiteBalance,
                    CameraSettings.KEY_FOCUS_MODE, focusMode,
                    CameraSettings.KEY_EXPOSURE, exposureMode,
                    CameraSettings.KEY_TOUCH_AF_AEC, touchMode);
        }
    }

    private void updateSceneModeUI() {
        // If scene mode is set, we cannot set flash mode, white balance, and
        // focus mode, instead, we read it from driver
        if (!Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            overrideCameraSettings(mParameters.getFlashMode(),
                    mParameters.getWhiteBalance(), mParameters.getFocusMode(),
                    Integer.toString(mParameters.getExposureCompensation()),
                    mParameters.getTouchAfAec());
        } else {
            overrideCameraSettings(null, null, null, null, null);
        }
    }

    private void loadCameraPreferences() {
        CameraSettings settings = new CameraSettings(this, mInitialParams,
                mCameraId, CameraHolder.instance().getCameraInfo());
        mPreferenceGroup = settings.getPreferenceGroup(R.xml.camera_preferences);
    }

    private void initializeIndicatorControl() {
        // setting the indicator buttons.
        mIndicatorControlContainer =
                (IndicatorControlContainer) findViewById(R.id.indicator_control);
        if (mIndicatorControlContainer == null) return;
        loadCameraPreferences();
        otherSettingKeys.put(0, OTHER_SETTING_KEYS);
        otherSettingKeys.put(1, OTHER_SETTING_KEYS_1);
        otherSettingKeys.put(2, OTHER_SETTING_KEYS_2);
        final String[] SETTING_KEYS = {
                CameraSettings.KEY_FLASH_MODE,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.KEY_SCENE_MODE};

        CameraPicker.setImageResourceId(R.drawable.ic_switch_photo_facing_holo_light);
        mIndicatorControlContainer.initialize(this, mPreferenceGroup,
                mParameters.isZoomSupported(),
                SETTING_KEYS,otherSettingKeys);/* OTHER_SETTING_KEYS);*/
        updateSceneModeUI();
        mIndicatorControlContainer.setListener(this);
    }

    private boolean collapseCameraControls() {
        if ((mIndicatorControlContainer != null)
                && mIndicatorControlContainer.dismissSettingPopup()) {
            return true;
        }
        return false;
    }

    private void enableCameraControls(boolean enable) {
        if (mIndicatorControlContainer != null) {
            mIndicatorControlContainer.setEnabled(enable);
        }
        if (mModePicker != null) mModePicker.setEnabled(enable);
        if (mZoomControl != null) mZoomControl.setEnabled(enable);
        if (mThumbnailView != null) mThumbnailView.setEnabled(enable);
    }

    private class MyOrientationEventListener
            extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = Util.roundOrientation(orientation, mOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation
                    + Util.getDisplayRotation(Camera.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                setOrientationIndicator(mOrientationCompensation);
            }

            // Show the toast after getting the first orientation changed.
            if (mHandler.hasMessages(SHOW_TAP_TO_FOCUS_TOAST)) {
                mHandler.removeMessages(SHOW_TAP_TO_FOCUS_TOAST);
                showTapToFocusToast();
            }
        }
    }

    private void setOrientationIndicator(int orientation) {
        Rotatable[] indicators = {mThumbnailView, mModePicker, mSharePopup,
                mIndicatorControlContainer, mZoomControl, mFocusAreaIndicator, mFaceView,
                mReviewCancelButton, mReviewDoneButton, mRotateDialog, mOnScreenIndicators};
        for (Rotatable indicator : indicators) {
            if (indicator != null) indicator.setOrientation(orientation);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMediaProviderClient != null) {
            mMediaProviderClient.release();
            mMediaProviderClient = null;
        }
    }

    private void checkStorage() {
        mPicturesRemaining = Storage.getAvailableSpace();
        if (mPicturesRemaining > Storage.LOW_STORAGE_THRESHOLD) {
            mPicturesRemaining = (mPicturesRemaining - Storage.LOW_STORAGE_THRESHOLD)
                    / Storage.PICTURE_SIZE;
        } else if (mPicturesRemaining > 0) {
            mPicturesRemaining = 0;
        }

        updateStorageHint();
    }

    @OnClickAttr
    public void onThumbnailClicked(View v) {
        if (isCameraIdle() && mThumbnail != null) {
            showSharePopup();
        }
    }

    @OnClickAttr
    public void onReviewRetakeClicked(View v) {
        hidePostCaptureAlert();
        startPreview();
        startFaceDetection();
    }

    @OnClickAttr
    public void onReviewDoneClicked(View v) {
        doAttach();
    }

    @OnClickAttr
    public void onReviewCancelClicked(View v) {
        doCancel();
    }

    private void doAttach() {
        if (mPausing) {
            return;
        }

        byte[] data = mJpegImageData;

        if (mCropValue == null) {
            // First handle the no crop case -- just return the value.  If the
            // caller specifies a "save uri" then write the data to it's
            // stream. Otherwise, pass back a scaled down version of the bitmap
            // directly in the extras.
            if (mSaveUri != null) {
                OutputStream outputStream = null;
                try {
                    outputStream = mContentResolver.openOutputStream(mSaveUri);
                    outputStream.write(data);
                    outputStream.close();

                    setResultEx(RESULT_OK);
                    finish();
                } catch (IOException ex) {
                    // ignore exception
                } finally {
                    Util.closeSilently(outputStream);
                }
            } else {
                int orientation = Exif.getOrientation(data);
                Bitmap bitmap = Util.makeBitmap(data, 50 * 1024);
                bitmap = Util.rotate(bitmap, orientation);
                setResultEx(RESULT_OK,
                        new Intent("inline-data").putExtra("data", bitmap));
                finish();
            }
        } else {
            // Save the image to a temp file and invoke the cropper
            Uri tempUri = null;
            FileOutputStream tempStream = null;
            try {
                File path = getFileStreamPath(sTempCropFilename);
                path.delete();
                tempStream = openFileOutput(sTempCropFilename, 0);
                tempStream.write(data);
                tempStream.close();
                tempUri = Uri.fromFile(path);
            } catch (FileNotFoundException ex) {
                setResultEx(Activity.RESULT_CANCELED);
                finish();
                return;
            } catch (IOException ex) {
                setResultEx(Activity.RESULT_CANCELED);
                finish();
                return;
            } finally {
                Util.closeSilently(tempStream);
            }

            Bundle newExtras = new Bundle();
            if (mCropValue.equals("circle")) {
                newExtras.putString("circleCrop", "true");
            }
            if (mSaveUri != null) {
                newExtras.putParcelable(MediaStore.EXTRA_OUTPUT, mSaveUri);
            } else {
                newExtras.putBoolean("return-data", true);
            }

            Intent cropIntent = new Intent("com.android.camera.action.CROP");

            cropIntent.setData(tempUri);
            cropIntent.putExtras(newExtras);

            startActivityForResult(cropIntent, CROP_MSG);
        }
    }

    private void doCancel() {
        setResultEx(RESULT_CANCELED, new Intent());
        finish();
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        if (mPausing || collapseCameraControls() || mCameraState == SNAPSHOT_IN_PROGRESS) return;

        // Do not do focus if there is not enough storage.
        if (pressed && !canTakePicture()) return;

        if (pressed) {
            qcameraUtilProfile("focus button");
            mFocusManager.onShutterDown();
        } else {
            mFocusManager.onShutterUp();
        }
    }

    @Override
    public void onShutterButtonClick() {
        if (mPausing || collapseCameraControls()) return;

        // Do not take the picture if there is not enough storage.
        if (mPicturesRemaining <= 0) {
            Log.i(TAG, "Not enough space or storage not ready. remaining=" + mPicturesRemaining);
            return;
        }

        Log.v(TAG, "onShutterButtonClick: mCameraState=" + mCameraState);

        // If the user wants to do a snapshot while the previous one is still
        // in progress, remember the fact and do it after we finish the previous
        // one and re-start the preview. Snapshot in progress also includes the
        // state that autofocus is focusing and a picture will be taken when
        // focus callback arrives.
        if (mFocusManager.isFocusingSnapOnFinish() || mCameraState == SNAPSHOT_IN_PROGRESS) {
            mSnapshotOnIdle = true;
            return;
        }

        mSnapshotOnIdle = false;
        if(mSnapshotMode == CameraInfo.CAMERA_SUPPORT_MODE_ZSL) {
            mFocusManager.setZslEnable(true);
        }else{
            mFocusManager.setZslEnable(false);
        }
        mShutterupTime = System.currentTimeMillis();
        mFocusManager.doSnap();
    }

    private OnScreenHint mStorageHint;

    private void updateStorageHint() {
        String noStorageText = null;

        if (mPicturesRemaining == Storage.UNAVAILABLE) {
            noStorageText = getString(R.string.no_storage);
        } else if (mPicturesRemaining == Storage.PREPARING) {
            noStorageText = getString(R.string.preparing_sd);
        } else if (mPicturesRemaining == Storage.UNKNOWN_SIZE) {
            noStorageText = getString(R.string.access_sd_fail);
        } else if (mPicturesRemaining < 1L) {
            noStorageText = getString(R.string.not_enough_space);
        }

        if (noStorageText != null) {
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(this, noStorageText);
            } else {
                mStorageHint.setText(noStorageText);
            }
            mStorageHint.show();
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
    }

    private void installIntentFilter() {
        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_CHECKING);
        intentFilter.addDataScheme("file");
        registerReceiver(mReceiver, intentFilter);
        mDidRegister = true;
    }

    @Override
    protected void doOnResume() {
        if (mOpenCameraFail || mCameraDisabled) return;

        mPausing = false;
        mJpegPictureCallbackTime = 0;
        mZoomValue = 0;

        // Start the preview if it is not started.
        if (mCameraState == PREVIEW_STOPPED) {
            try {
                mCameraDevice = Util.openCamera(this, mCameraId);
                initializeCapabilities();
                resetExposureCompensation();
                startPreview();
                startFaceDetection();
            } catch (CameraHardwareException e) {
                Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
                return;
            } catch (CameraDisabledException e) {
                Util.showErrorAndFinish(this, R.string.camera_disabled);
                return;
            }
        }

        //Check if the skinTone SeekBar could not be enabled during updateCameraParametersPreference()
       //due to the finite latency of loading the seekBar layout when switching modes
       // for same Camera Device instance
        if (mSkinToneSeekBar != true)
        {
            Log.e(TAG, "yyan send tone bar: mSkinToneSeekBar = " + mSkinToneSeekBar);
            mHandler.sendEmptyMessage(SET_SKIN_TONE_FACTOR);
        }

        if (mSurfaceHolder != null) {
            // If first time initialization is not finished, put it in the
            // message queue.
            if (!mFirstTimeInitialized) {
                mHandler.sendEmptyMessage(FIRST_TIME_INIT);
            } else {
                initializeSecondTime();
            }
        }
        keepScreenOnAwhile();

        if (mCameraState == IDLE) {
            mOnResumeTime = SystemClock.uptimeMillis();
            mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
        }
        // Dismiss open menu if exists.
        PopupManager.getInstance(this).notifyShowPopup(null);
    }

    private void resizeForPreviewAspectRatio() {
        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
        int degrees = Util.getDisplayRotation(this);
        Size size = mParameters.getPictureSize();
        // If Camera orientation and display Orientation is not aligned,
        // FrameLayout's Aspect Ration needs to be rotated

        if (this.getRequestedOrientation()
                == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            if(((info.orientation - degrees + 360)%180) == 0)
                mPreviewFrameLayout.setAspectRatio((double) size.height / size.width);
            else
                mPreviewFrameLayout.setAspectRatio((double) size.width / size.height);
        } else if (this.getRequestedOrientation()
                == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            if(((info.orientation - degrees + 360)%180) == 90)
                mPreviewFrameLayout.setAspectRatio((double) size.height / size.width);
            else
                mPreviewFrameLayout.setAspectRatio((double) size.width / size.height);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        resizeForPreviewAspectRatio();
    }

    @Override
    protected void onPause() {
        mPausing = true;
        stopCamera();
        super.onPause();
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CROP_MSG: {
                Intent intent = new Intent();
                if (data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        intent.putExtras(extras);
                    }
                }
                setResultEx(resultCode, intent);
                finish();

                File path = getFileStreamPath(sTempCropFilename);
                path.delete();

                break;
            }
        }
    }

    private boolean canTakePicture() {
        return isCameraIdle() && (mPicturesRemaining > 0);
    }

    protected android.hardware.Camera getCamera() {
        return mCameraDevice;
    }

    @Override
    public void autoFocus() {
        if(mCameraState != SNAPSHOT_IN_PROGRESS) {
            mFocusStartTime = System.currentTimeMillis();
            qcameraUtilProfile("Start autofocus");
            mCameraDevice.autoFocus(mAutoFocusCallback);
            setCameraState(FOCUSING);
            qcameraUtilProfile("post autofocus");
        }
    }

    @Override
    public void cancelAutoFocus() {
        mCameraDevice.cancelAutoFocus();
        setCameraState(IDLE);
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    // Preview area is touched. Handle touch focus.
    @Override
    public boolean onTouch(View v, MotionEvent e) {
        if (mPausing || mCameraDevice == null || !mFirstTimeInitialized
                || mCameraState == SNAPSHOT_IN_PROGRESS) {
            return false;
        }

        // Do not trigger touch focus if popup window is opened.
        if (collapseCameraControls()) return false;

        //If Touch AF/AEC is disabled in UI, return
        if(this.mTouchAfAecFlag == false) {
            return false;
        }

        // Check if metering area or focus area is supported.
        if (!mFocusAreaSupported && !mMeteringAreaSupported) return false;

        return mFocusManager.onTouch(e);
    }

    @Override
    public void onBackPressed() {
        if (!isCameraIdle()) {
            // ignore backs while we're taking a picture
            return;
        } else if (!collapseCameraControls()) {
            super.onBackPressed();
        }
    }

    @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_FOCUS:
                    if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                        onShutterButtonFocus(true);
                    }
                    return true;
                case KeyEvent.KEYCODE_CAMERA:
                    if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                        onShutterButtonClick();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if ( (mCameraState != PREVIEW_STOPPED) &&
                            (mFocusManager.getCurrentFocusState() != mFocusManager.STATE_FOCUSING) &&
                            (mFocusManager.getCurrentFocusState() != mFocusManager.STATE_FOCUSING_SNAP_ON_FINISH) ) {
                        if (mbrightness > MINIMUM_BRIGHTNESS) {
                            mbrightness-=mbrightness_step;

                            /* Set the "luma-adaptation" parameter */
                            mParameters = mCameraDevice.getParameters();
                            mParameters.set("luma-adaptation", String.valueOf(mbrightness));
                            mCameraDevice.setParameters(mParameters);
                        }

                        brightnessProgressBar.setProgress(mbrightness);
                        brightnessProgressBar.setVisibility(View.VISIBLE);

                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if ( (mCameraState != PREVIEW_STOPPED) &&
                            (mFocusManager.getCurrentFocusState() != mFocusManager.STATE_FOCUSING) &&
                            (mFocusManager.getCurrentFocusState() != mFocusManager.STATE_FOCUSING_SNAP_ON_FINISH) ) {
                        if (mbrightness < MAXIMUM_BRIGHTNESS) {
                            mbrightness+=mbrightness_step;

                            /* Set the "luma-adaptation" parameter */
                            mParameters = mCameraDevice.getParameters();
                            mParameters.set("luma-adaptation", String.valueOf(mbrightness));
                            mCameraDevice.setParameters(mParameters);

                        }
                        brightnessProgressBar.setProgress(mbrightness);
                        brightnessProgressBar.setVisibility(View.VISIBLE);

                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    // If we get a dpad center event without any focused view, move
                    // the focus to the shutter button and press it.
                    if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                        // Start auto-focus immediately to reduce shutter lag. After
                        // the shutter button gets the focus, onShutterButtonFocus()
                        // will be called again but it is fine.
                        if (collapseCameraControls()) return true;
                        onShutterButtonFocus(true);
                        if (mShutterButton.isInTouchMode()) {
                            mShutterButton.requestFocusFromTouch();
                        } else {
                            mShutterButton.requestFocus();
                        }
                        mShutterButton.setPressed(true);
                    }
                    return true;
            }

            return super.onKeyDown(keyCode, event);
        }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
                    onShutterButtonFocus(false);
                }
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null) {
            Log.d(TAG, "holder.getSurface() == null");
            return;
        }

        Log.v(TAG, "surfaceChanged. w=" + w + ". h=" + h);

        // We need to save the holder for later use, even when the mCameraDevice
        // is null. This could happen if onResume() is invoked after this
        // function.
        mSurfaceHolder = holder;

        // The mCameraDevice will be null if it fails to connect to the camera
        // hardware. In this case we will show a dialog and then finish the
        // activity, so it's OK to ignore it.
        if (mCameraDevice == null) return;

        // Sometimes surfaceChanged is called after onPause or before onResume.
        // Ignore it.
        if (mPausing || isFinishing()) return;

        // Set preview display if the surface is being created. Preview was
        // already started. Also restart the preview if display rotation has
        // changed. Sometimes this happens when the device is held in portrait
        // and camera app is opened. Rotation animation takes some time and
        // display rotation in onCreate may not be what we want.
        if (mCameraState == PREVIEW_STOPPED) {
            startPreview();
            startFaceDetection();
        } else {
            if (Util.getDisplayRotation(this) != mDisplayRotation) {
                setDisplayOrientation();
            }
            if (holder.isCreating()) {
                // Set preview display if the surface is being created and preview
                // was already started. That means preview display was set to null
                // and we need to set it now.
                setPreviewDisplay(holder);
            }
        }

        // If first time initialization is not finished, send a message to do
        // it later. We want to finish surfaceChanged as soon as possible to let
        // user see preview first.
        if (!mFirstTimeInitialized) {
            mHandler.sendEmptyMessage(FIRST_TIME_INIT);
        } else {
            initializeSecondTime();
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
        mSurfaceHolder = null;
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            CameraHolder.instance().release();
            mFaceDetectionStarted = false;
            mCameraDevice.setZoomChangeListener(null);
            mCameraDevice.setFaceDetectionListener(null);
            mCameraDevice.setErrorCallback(null);
            mCameraDevice = null;
            setCameraState(PREVIEW_STOPPED);
            mFocusManager.onCameraReleased();
        }
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            mCameraDevice.setPreviewDisplay(holder);
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    private void setDisplayOrientation() {
        mDisplayRotation = Util.getDisplayRotation(this);
        mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation, mCameraId);
        mCameraDevice.setDisplayOrientation(mDisplayOrientation);
        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(mDisplayOrientation);
        }
    }

    private void startPreview() {
        if (mPausing || isFinishing()) return;

        qcameraUtilProfile("start preview & set parms");

        mFocusManager.resetTouchFocus();

        mCameraDevice.setErrorCallback(mErrorCallback);

        // If we're previewing already, stop the preview first (this will blank
        // the screen).
        if (mCameraState != PREVIEW_STOPPED)
        {
            //Always stop preview regardless of ZSL mode. ZSL mode case is handled in onPictureTaken()
            //~punits
            stopPreview();
        }

        setPreviewDisplay(mSurfaceHolder);
        setDisplayOrientation();

        if (!mSnapshotOnIdle) {
            // If the focus mode is continuous autofocus, call cancelAutoFocus to
            // resume it because it may have been paused by autoFocus call.
            if (Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mFocusManager.getFocusMode())) {
                mCameraDevice.cancelAutoFocus();
            }
            mFocusManager.setAeAwbLock(false); // Unlock AE and AWB.
        }
        setCameraParameters(UPDATE_PARAM_ALL);

        // Inform the mainthread to go on the UI initialization.
        if (mCameraPreviewThread != null) {
            synchronized (mCameraPreviewThread) {
                mCameraPreviewThread.notify();
            }
        }

        try {
            qcameraUtilProfile("start preview");
            Log.v(TAG, "startPreview");
            mCameraDevice.startPreview();
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        }

        mZoomState = ZOOM_STOPPED;
        setCameraState(IDLE);
        mFocusManager.onPreviewStarted();

        if (mSnapshotOnIdle) {
            mHandler.post(mDoSnapRunnable);
        }
    }

    private void stopPreview() {
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            mCameraDevice.cancelAutoFocus(); // Reset the focus.
            mCameraDevice.stopPreview();
//            mFaceDetectionStarted = false;
        }
        setCameraState(PREVIEW_STOPPED);
        mFocusManager.onPreviewStopped();
    }

    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    private void updateCameraParametersInitialize() {
        // Reset preview frame rate to the maximum because it may be lowered by
        // video camera application.
        List<Integer> frameRates = mParameters.getSupportedPreviewFrameRates();
        if (frameRates != null) {
            Integer max = Collections.max(frameRates);
            mParameters.setPreviewFrameRate(max);
        }

        mParameters.setRecordingHint(false);

        // Disable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters.get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "false");
        }
    }

    private void updateCameraParametersZoom() {
        // Set zoom.
        if (mParameters.isZoomSupported()) {
            mParameters.setZoom(mZoomValue);
        }
    }

    private void updateCameraParametersPreference() {
        if (mAeLockSupported) {
            mParameters.setAutoExposureLock(mFocusManager.getAeAwbLock());
        }

        if (mAwbLockSupported) {
            mParameters.setAutoWhiteBalanceLock(mFocusManager.getAeAwbLock());
        }

        if (mFocusAreaSupported) {
            mParameters.setFocusAreas(mFocusManager.getFocusAreas());
        }

        if (mMeteringAreaSupported) {
            // Use the same area for focus and metering.
            mParameters.setMeteringAreas(mFocusManager.getMeteringAreas());
        }

        Size old_size = mParameters.getPictureSize();
        if (old_size == null) {
             mParameters.setPictureSize(640, 480);
             old_size = mParameters.getPictureSize();
             Log.e(TAG, "Reset old picture size : "+ old_size.width + " " + old_size.height);
        }
        Log.v(TAG, "Old picture size : "+ old_size.width + " " + old_size.height);

        // Set picture size.
        String pictureSize = mPreferences.getString(
                CameraSettings.KEY_PICTURE_SIZE, null);

        try {
            if (pictureSize == null) {
                CameraSettings.initialCameraPictureSize(this, mParameters);
            } else {
                List<Size> supported = mParameters.getSupportedPictureSizes();
                if (null != supported) {
                    CameraSettings.setCameraPictureSize(
                        pictureSize, supported, mParameters);
                } else
                     Log.e(TAG, "Error - getSupportedPictureSizes is null" );
            }

            // Set the preview frame aspect ratio according to the picture size.
            Size size = mParameters.getPictureSize();
            if (size == null) {
                mParameters.setPictureSize(640, 480);
                size = mParameters.getPictureSize();
                Log.e(TAG, "Reset new picture size : "+ old_size.width + " " + old_size.height);
            }
            Log.e(TAG, "New picture size : "+ size.width + " " + size.height);
            if(!size.equals(old_size)&& mCameraState != PREVIEW_STOPPED) {
                Log.v(TAG, "new picture size id different from old picture size , stop preview. Start preview will be called at a later point");
                stopPreview();
                mAspectRatioChanged = true;
            }

            mPreviewPanel = findViewById(R.id.frame_layout);
            mPreviewFrameLayout = (PreviewFrameLayout) findViewById(R.id.frame);
            resizeForPreviewAspectRatio();

            // Set a preview size that is closest to the viewfinder height and has
            // the right aspect ratio.
            List<Size> sizes = mParameters.getSupportedPreviewSizes();
            Size optimalSize = Util.getOptimalPreviewSize(this,
                    sizes, (double) size.width / size.height);
            Size original = mParameters.getPreviewSize();

            if (original == null || optimalSize == null)
                mParameters.setPreviewSize(640, 480);
            else if (!original.equals(optimalSize)) {
                mParameters.setPreviewSize(optimalSize.width, optimalSize.height);

                // Zoom related settings will be changed for different preview
                // sizes, so set and read the parameters to get lastest values
                mCameraDevice.setParameters(mParameters);
                mParameters = mCameraDevice.getParameters();
            }
            // Set jpeg thumbnail size according picture aspect ratio
            List<Size> thumbnail_sizes = mParameters.getSupportedJpegThumbnailSizes();
            Size currThumbnailSize = mParameters.getJpegThumbnailSize();

            /* Get recommended postview/thumbnail sizes based on aspect ratio */
            int mAspectRatio = (int)((optimalSize.width * 4096) / optimalSize.height);
            int mTempAspectRatio;
            for (Size s : thumbnail_sizes) {
                mTempAspectRatio = (int) ((s.width * 4096) / s.height);
                if(mTempAspectRatio == mAspectRatio)
                {
                     Log.v(TAG, "Setting thumbnail width:"+s.width +" height:"+ s.height);
                     mParameters.setJpegThumbnailSize(s.width, s.height);
                     break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Handled NULL pointer Exception");
        }
        Size z = mParameters.getPreviewSize();
        Log.v(TAG, "Preview size is " + z.width + "x" + z.height);

       // To set preview format as YV12 , run command
       // "adb shell setprop "debug.camera.yv12" true"
       String yv12formatset = SystemProperties.get("debug.camera.yv12");
       if(yv12formatset.equals("true")) {
          Log.e(TAG, "preview format set to YV12");
         mParameters.setPreviewFormat (ImageFormat.YV12);
       }

        // Since change scene mode may change supported values,
        // Set scene mode first,
        mSceneMode = mPreferences.getString(
                CameraSettings.KEY_SCENE_MODE,
                getString(R.string.pref_camera_scenemode_default));
        if (isSupported(mSceneMode, mParameters.getSupportedSceneModes())) {
            if (!mParameters.getSceneMode().equals(mSceneMode)) {
                mParameters.setSceneMode(mSceneMode);
                mCameraDevice.setParameters(mParameters);

                // Setting scene mode will change the settings of flash mode,
                // white balance, and focus mode. Here we read back the
                // parameters, so we can know those settings.
                mParameters = mCameraDevice.getParameters();
            }
        } else {
            mSceneMode = mParameters.getSceneMode();
            if (mSceneMode == null) {
                mSceneMode = Parameters.SCENE_MODE_AUTO;
            }
        }

        String zsl = mPreferences.getString(CameraSettings.KEY_ZSL,
                                  getString(R.string.pref_camera_zsl_default));
        if(zsl.equals("on")) {
            //Switch on ZSL mode
            mSnapshotMode = CameraInfo.CAMERA_SUPPORT_MODE_ZSL;
            mParameters.setCameraMode(1);
            mFocusManager.setZslEnable(true);

            // Currently HDR is not supported under ZSL mode
            Editor editor = mPreferences.edit();
            editor.putString(CameraSettings.KEY_AE_BRACKET_HDR, getString(R.string.pref_camera_ae_bracket_hdr_value_off));
            editor.apply();
        }else{
            mSnapshotMode = CameraInfo.CAMERA_SUPPORT_MODE_NONZSL;
            mParameters.setCameraMode(0);
            mFocusManager.setZslEnable(false);
        }

        String hdr = mPreferences.getString(CameraSettings.KEY_AE_BRACKET_HDR,
                                  getString(R.string.pref_camera_ae_bracket_hdr_default));
        mParameters.setAEBracket(hdr);

        if (hdr.equalsIgnoreCase(getString(R.string.pref_camera_ae_bracket_hdr_value_hdr))) {
            mParameters.set("num-snaps-per-shutter", "2");
        } else if (hdr.equalsIgnoreCase(getString(R.string.pref_camera_ae_bracket_hdr_value_ae_bracket))) {
            String burst_exp = SystemProperties.get("persist.capture.burst.exposures", "");
            Log.i(TAG, "capture-burst-exposures = " + burst_exp);
            if ( (burst_exp != null) && (burst_exp.length()>0) ) {
                mParameters.set("capture-burst-exposures", burst_exp);
            }
            if (burst_exp != null) {
                String[] split_values = burst_exp.split(",");
                if (split_values != null) {
                    String snapshot_number = "";
                    snapshot_number += split_values.length;
                    Log.i(TAG, "num-snaps-per-shutter = " + snapshot_number);
                    mParameters.set("num-snaps-per-shutter", snapshot_number);
                }
            }
        }else {
            mParameters.set("num-snaps-per-shutter", "1");
        }

        // Set JPEG quality.
        String jpegQuality = mPreferences.getString(
                CameraSettings.KEY_JPEG_QUALITY,
                getString(R.string.pref_camera_jpegquality_default));

        //mUnsupportedJpegQuality = false;
        Size pic_size = mParameters.getPictureSize();
        if (pic_size == null) {
            Log.e(TAG, "error getPictureSize: size is null");
        }
        else{
            if("100".equals(jpegQuality) && (pic_size.width >= 3200)){
                //mUnsupportedJpegQuality = true;
            }else {
                mParameters.setJpegQuality(JpegEncodingQualityMappings.getQualityNumber(jpegQuality));
            }
        }

        // Set ISO parameter.
        String iso = mPreferences.getString(
                CameraSettings.KEY_ISO,
                getString(R.string.pref_camera_iso_default));
        if (isSupported(iso,
                mParameters.getSupportedIsoValues())) {
                mParameters.setISOValue(iso);
         }

        //Set LensShading
        /*String lensshade = mPreferences.getString(
                CameraSettings.KEY_LENSSHADING,
                getString(R.string.pref_camera_lensshading_default));
        if (isSupported(lensshade,
                mParameters.getSupportedLensShadeModes())) {
                mParameters.setLensShade(lensshade);
        }

        //Set Memory Color Enhancement
        String mce = mPreferences.getString(
                CameraSettings.KEY_MEMORY_COLOR_ENHANCEMENT,
                getString(R.string.pref_camera_mce_default));
        if (isSupported(mce,
                mParameters.getSupportedMemColorEnhanceModes())) {
                mParameters.setMemColorEnhance(mce);
        }*/

        // Set Redeye Reduction
        String redeyeReduction = mPreferences.getString(
                CameraSettings.KEY_REDEYE_REDUCTION,
                getString(R.string.pref_camera_redeyereduction_default));
        if (isSupported(redeyeReduction,
            mParameters.getSupportedRedeyeReductionModes())) {
            mParameters.setRedeyeReductionMode(redeyeReduction);
        }

        //Set Histogram
        String histogram = mPreferences.getString(
                CameraSettings.KEY_HISTOGRAM,
                getString(R.string.pref_camera_histogram_default));
        if (isSupported(histogram,
                mParameters.getSupportedHistogramModes()) && mCameraDevice != null) {
                // Call for histogram
                if(histogram.equals("enable")) {
                    if(mGraphView != null) {
                        mGraphView.setVisibility(View.VISIBLE);
                    }
                    mCameraDevice.setHistogramMode(mStatsCallback);
                    mHiston = true;

                } else {
                    mHiston = false;
                    if(mGraphView != null)
                        mGraphView.setVisibility(View.INVISIBLE);
                     mCameraDevice.setHistogramMode(null);
                }
        }
        //Set Brightness.
        mParameters.set("luma-adaptation", String.valueOf(mbrightness));


         // Set auto exposure parameter.
         String autoExposure = mPreferences.getString(
                 CameraSettings.KEY_AUTOEXPOSURE,
                 getString(R.string.pref_camera_autoexposure_default));
         if (isSupported(autoExposure, mParameters.getSupportedAutoexposure())) {
             mParameters.setAutoExposure(autoExposure);
         }

         // Set Selectable Zone Af parameter.
         String selectableZoneAf = mPreferences.getString(
             CameraSettings.KEY_SELECTABLE_ZONE_AF,
             getString(R.string.pref_camera_selectablezoneaf_default));
         List<String> str = mParameters.getSupportedSelectableZoneAf();
         if (isSupported(selectableZoneAf, mParameters.getSupportedSelectableZoneAf())) {
             mParameters.setSelectableZoneAf(selectableZoneAf);
         }

         // Set face detetction parameter.
         String faceDetection = mPreferences.getString(
             CameraSettings.KEY_FACE_DETECTION,
             getString(R.string.pref_camera_facedetection_default));

         if (isSupported(faceDetection, mParameters.getSupportedFaceDetectionModes())) {
             mParameters.setFaceDetectionMode(faceDetection);
             if(faceDetection.equals("on") && mFaceDetectionEnabled == false) {
               mFaceDetectionEnabled = true;
               startFaceDetection();
             }
             if(faceDetection.equals("off") && mFaceDetectionEnabled == true) {
               stopFaceDetection();
               mFaceDetectionEnabled = false;
             }
         }

        // Set sharpness parameter.
        String sharpnessStr = mPreferences.getString(
                CameraSettings.KEY_SHARPNESS,
                getString(R.string.pref_camera_sharpness_default));
        int sharpness = Integer.parseInt(sharpnessStr) *
                (mParameters.getMaxSharpness()/MAX_SHARPNESS_LEVEL);
        if((0 <= sharpness) &&
                (sharpness <= mParameters.getMaxSharpness()))
            mParameters.setSharpness(sharpness);


        // Set contrast parameter.
        String contrastStr = mPreferences.getString(
                CameraSettings.KEY_CONTRAST,
                getString(R.string.pref_camera_contrast_default));
        int contrast = Integer.parseInt(contrastStr);
        if((0 <= contrast) &&
                (contrast <= mParameters.getMaxContrast()))
            mParameters.setContrast(contrast);



         // Set anti banding parameter.
         String antiBanding = mPreferences.getString(
                 CameraSettings.KEY_ANTIBANDING,
                 getString(R.string.pref_camera_antibanding_default));
         if (isSupported(antiBanding, mParameters.getSupportedAntibanding())) {
             mParameters.setAntibanding(antiBanding);
         }

        // For the following settings, we need to check if the settings are
        // still supported by latest driver, if not, ignore the settings.



        // Set color effect parameter.
        String colorEffect = mPreferences.getString(
                CameraSettings.KEY_COLOR_EFFECT,
                getString(R.string.pref_camera_coloreffect_default));
        if (isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
            mParameters.setColorEffect(colorEffect);
        }

        // skin tone ie enabled only for auto,party and portrait BSM
        // when color effects are not enabled
        if((//Parameters.SCENE_MODE_AUTO.equals(mSceneMode) ||
            Parameters.SCENE_MODE_PARTY.equals(mSceneMode) ||
            //Parameters.SCENE_MODE_OFF.equals(mSceneMode) ||
            Parameters.SCENE_MODE_PORTRAIT.equals(mSceneMode))&&
            (Parameters.EFFECT_NONE.equals(colorEffect))) {
            //Set Skin Tone Correction factor
            Log.e(TAG, "yyan set tone bar: mSceneMode = " + mSceneMode);
            if(mSeekBarInitialized == true)
                setSkinToneFactor();
        }

        String saturationStr = mPreferences.getString(
                CameraSettings.KEY_SATURATION,
                getString(R.string.pref_camera_saturation_default));
            int saturation = Integer.parseInt(saturationStr);
        Log.e(TAG, "<DEBUG> saturation is " + saturation);
            if((0 <= saturation) &&
                (saturation <= mParameters.getMaxSaturation()))
            mParameters.setSaturation(saturation);

        // Set exposure compensation
        int value = CameraSettings.readExposure(mPreferences);
        int max = mParameters.getMaxExposureCompensation();
        int min = mParameters.getMinExposureCompensation();
        if (value >= min && value <= max) {
            mParameters.setExposureCompensation(value);
        } else {
            Log.w(TAG, "invalid exposure range: " + value);
        }

        // Set Picture Format
        // Picture Formats specified in UI should be consistent with
        // PIXEL_FORMAT_JPEG and PIXEL_FORMAT_RAW constants
        String pictureFormat = mPreferences.getString(
                CameraSettings.KEY_PICTURE_FORMAT,
                getString(R.string.pref_camera_picture_format_default));
        mParameters.set(KEY_PICTURE_FORMAT, pictureFormat);

        /* Set wavelet denoise mode */
        if (mParameters.getSupportedDenoiseModes() != null) {
            String Denoise = mPreferences.getString( CameraSettings.KEY_DENOISE,
                             getString(R.string.pref_camera_denoise_default));
            mParameters.setDenoise(Denoise);
        }

        if (Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            // Set flash mode.
            String flashMode = mPreferences.getString(
                    CameraSettings.KEY_FLASH_MODE,
                    getString(R.string.pref_camera_flashmode_default));
            List<String> supportedFlash = mParameters.getSupportedFlashModes();
            if (isSupported(flashMode, supportedFlash)) {
                mParameters.setFlashMode(flashMode);
            } else {
                flashMode = mParameters.getFlashMode();
                if (flashMode == null) {
                    flashMode = getString(
                            R.string.pref_camera_flashmode_no_flash);
                }
            }

            // Set white balance parameter.
            String whiteBalance = mPreferences.getString(
                    CameraSettings.KEY_WHITE_BALANCE,
                    getString(R.string.pref_camera_whitebalance_default));
            if (isSupported(whiteBalance,
                    mParameters.getSupportedWhiteBalance())) {
                mParameters.setWhiteBalance(whiteBalance);
            } else {
                whiteBalance = mParameters.getWhiteBalance();
                if (whiteBalance == null) {
                    whiteBalance = Parameters.WHITE_BALANCE_AUTO;
                }
            }
            // Set Touch AF/AEC parameter.
            String touchAfAec = mPreferences.getString(
                 CameraSettings.KEY_TOUCH_AF_AEC,
                 getString(R.string.pref_camera_touchafaec_default));
            if (isSupported(touchAfAec, mParameters.getSupportedTouchAfAec())) {
                mParameters.setTouchAfAec(touchAfAec);
            }

            // Set focus mode.
            mFocusManager.overrideFocusMode(null);
            mParameters.setFocusMode(mFocusManager.getFocusMode());
        } else {
            mParameters.setTouchAfAec(mParameters.TOUCH_AF_AEC_OFF);
            mFocusManager.resetTouchFocus();
            mFocusManager.overrideFocusMode(mParameters.getFocusMode());
        }
        try {
            if(mParameters.getTouchAfAec().equals(mParameters.TOUCH_AF_AEC_ON))
                this.mTouchAfAecFlag = true;
            else
                this.mTouchAfAecFlag = false;
        } catch(Exception e){
            Log.e(TAG, "Handled NULL pointer Exception");
        }
    }

    // We separate the parameters into several subsets, so we can update only
    // the subsets actually need updating. The PREFERENCE set needs extra
    // locking because the preference can be changed from GLThread as well.
    private void setCameraParameters(int updateSet) {
        mParameters = mCameraDevice.getParameters();

        if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
            updateCameraParametersInitialize();
        }

        if ((updateSet & UPDATE_PARAM_ZOOM) != 0) {
            updateCameraParametersZoom();
        }

        if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
            updateCameraParametersPreference();
        }

        mCameraDevice.setParameters(mParameters);
    }

    // If the Camera is idle, update the parameters immediately, otherwise
    // accumulate them in mUpdateSet and update later.
    private void setCameraParametersWhenIdle(int additionalUpdateSet) {
        mUpdateSet |= additionalUpdateSet;
        if (mCameraDevice == null) {
            // We will update all the parameters when we open the device, so
            // we don't need to do anything now.
            mUpdateSet = 0;
            return;
        } else if (isCameraIdle()) {
            if(zslrestartPreview){
                Log.e(TAG, "ZSL enabled, restarting preview");
                startPreview();
                zslrestartPreview = false;
            }

            setCameraParameters(mUpdateSet);
            updateSceneModeUI();
            mUpdateSet = 0;
        } else {
            if (!mHandler.hasMessages(SET_CAMERA_PARAMETERS_WHEN_IDLE)) {
                mHandler.sendEmptyMessageDelayed(
                        SET_CAMERA_PARAMETERS_WHEN_IDLE, 1000);
            }
        }
        if(mAspectRatioChanged) {
            Log.e(TAG, "Picture Aspect Ratio changed, Starting preview");
            startPreview();
            mAspectRatioChanged = false;
        }
     }

    private void gotoGallery() {
        MenuHelper.gotoCameraImageGallery(this);
    }

    private boolean isCameraIdle() {
        return (mCameraState == IDLE) || (mFocusManager.isFocusCompleted());
    }

    private boolean isImageCaptureIntent() {
        String action = getIntent().getAction();
        return (MediaStore.ACTION_IMAGE_CAPTURE.equals(action));
    }

    private void setupCaptureParams() {
        Bundle myExtras = getIntent().getExtras();
        if (myExtras != null) {
            mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            mCropValue = myExtras.getString("crop");
        }
    }

    private void showPostCaptureAlert() {
        if (mIsImageCaptureIntent) {
            Util.fadeOut(mIndicatorControlContainer);
            Util.fadeOut(mShutterButton);

            int[] pickIds = {R.id.btn_retake, R.id.btn_done};
            for (int id : pickIds) {
                Util.fadeIn(findViewById(id));
            }
        }
    }

    private void hidePostCaptureAlert() {
        if (mIsImageCaptureIntent) {
            int[] pickIds = {R.id.btn_retake, R.id.btn_done};
            for (int id : pickIds) {
                Util.fadeOut(findViewById(id));
            }

            Util.fadeIn(mShutterButton);
            Util.fadeIn(mIndicatorControlContainer);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // Only show the menu when camera is idle.
        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setVisible(isCameraIdle());
        }

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (mIsImageCaptureIntent) {
            // No options menu for attach mode.
            return false;
        } else {
            addBaseMenuItems(menu);
        }
        return true;
    }

    private void addBaseMenuItems(Menu menu) {
        MenuHelper.addSwitchModeMenuItem(menu, ModePicker.MODE_VIDEO, new Runnable() {
            public void run() {
                switchToOtherMode(ModePicker.MODE_VIDEO);
            }
        });
/*
        MenuHelper.addSwitchModeMenuItem(menu, ModePicker.MODE_PANORAMA, new Runnable() {
            public void run() {
                switchToOtherMode(ModePicker.MODE_PANORAMA);
            }
        });
*/
        if (mNumberOfCameras > 1) {
            menu.add(R.string.switch_camera_id)
                    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    CameraSettings.writePreferredCameraId(mPreferences,
                            ((mCameraId == mFrontCameraId)
                            ? mBackCameraId : mFrontCameraId));
                    onSharedPreferenceChanged();
                    return true;
                }
            }).setIcon(android.R.drawable.ic_menu_camera);
        }
    }

    private boolean switchToOtherMode(int mode) {
        if (isFinishing()) return false;
        if (mImageSaver != null) mImageSaver.waitDone();
        MenuHelper.gotoMode(mode, Camera.this);
        mHandler.removeMessages(FIRST_TIME_INIT);
        finish();
        return true;
    }

    public boolean onModeChanged(int mode) {
        if (mode != ModePicker.MODE_CAMERA) {
            return switchToOtherMode(mode);
        } else {
            return true;
        }
    }

    public void onSharedPreferenceChanged() {
        // ignore the events after "onPause()"
        if (mPausing) return;

        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, getContentResolver());
        mLocationManager.recordLocation(recordLocation);

        int cameraId = CameraSettings.readPreferredCameraId(mPreferences);
        if (mCameraId != cameraId) {
            // Restart the activity to have a crossfade animation.
            // TODO: Use SurfaceTexture to implement a better and faster
            // animation.
            if (mIsImageCaptureIntent) {
                // If the intent is camera capture, stay in camera capture mode.
                MenuHelper.gotoCameraMode(this, getIntent());
            } else {
                MenuHelper.gotoCameraMode(this);
            }

            finish();
        } else {
            String zsl = mPreferences.getString(CameraSettings.KEY_ZSL,
			    getString(R.string.pref_camera_zsl_default));
            if((mSnapshotMode != CameraInfo.CAMERA_SUPPORT_MODE_ZSL &&
                zsl.equals("on"))) {
                Log.v(TAG, "Camera App Enabled ZSL. Restart Preview "+ zsl);
                zslrestartPreview = true;
            }
            if((mSnapshotMode == CameraInfo.CAMERA_SUPPORT_MODE_ZSL &&
                zsl.equals("off"))) {
                Log.v(TAG, "Camera App Disabled ZSL. Restart Preview "+ zsl);
                zslrestartPreview = true;
            }
            setCameraParametersWhenIdle(UPDATE_PARAM_PREFERENCE);
        }

        updateOnScreenIndicators();

        if (mSeekBarInitialized == true){
            Log.e(TAG, "yyan onSharedPreferenceChanged Skin tone bar: change");
                    // skin tone ie enabled only for auto,party and portrait BSM
                    // when color effects are not enabled
                    String colorEffect = mPreferences.getString(
                        CameraSettings.KEY_COLOR_EFFECT,
                        getString(R.string.pref_camera_coloreffect_default));
                    if((//Parameters.SCENE_MODE_AUTO.equals(mSceneMode) ||
                        Parameters.SCENE_MODE_PARTY.equals(mSceneMode) ||
                        //Parameters.SCENE_MODE_OFF.equals(mSceneMode) ||
                        Parameters.SCENE_MODE_PORTRAIT.equals(mSceneMode))&&
                        (Parameters.EFFECT_NONE.equals(colorEffect))) {
                        ;
                    }
                    else{
                        disableSkinToneSeekBar();
                    }
        }

    }

    private void qcameraUtilProfile( String msg) {
        Log.e(TAG_PROFILE, " "+ msg +":" + System.currentTimeMillis()/1000.0);
    }
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        keepScreenOnAwhile();
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    public void onRestorePreferencesClicked() {
        if (mPausing) return;
        Runnable runnable = new Runnable() {
            public void run() {
                restorePreferences();
            }
        };
        mRotateDialog.showAlertDialog(
                getString(R.string.confirm_restore_title),
                getString(R.string.confirm_restore_message),
                getString(android.R.string.ok), runnable,
                getString(android.R.string.cancel), null);
    }

    private void restorePreferences() {
        // Reset the zoom. Zoom value is not stored in preference.
        if (mParameters.isZoomSupported()) {
            mZoomValue = 0;
            setCameraParametersWhenIdle(UPDATE_PARAM_ZOOM);
            mZoomControl.setZoomIndex(0);
        }
        if (mIndicatorControlContainer != null) {
            mIndicatorControlContainer.dismissSettingPopup();
            CameraSettings.restorePreferences(Camera.this, mPreferences,
                    mParameters);
            mIndicatorControlContainer.reloadPreferences();
            onSharedPreferenceChanged();
        }
    }

    public void onOverriddenPreferencesClicked() {
        if (mPausing) return;
        if (mNotSelectableToast == null) {
            String str = getResources().getString(R.string.not_selectable_in_scene_mode);
            mNotSelectableToast = Toast.makeText(Camera.this, str, Toast.LENGTH_SHORT);
        }
        mNotSelectableToast.show();
    }

    private void showSharePopup() {
        mImageSaver.waitDone();
        Uri uri = mThumbnail.getUri();
        if (mSharePopup == null || !uri.equals(mSharePopup.getUri())) {
            // SharePopup window takes the mPreviewPanel as its size reference.
            mSharePopup = new SharePopup(this, uri, mThumbnail.getBitmap(),
                    mOrientationCompensation, mPreviewPanel);
        }
        mSharePopup.showAtLocation(mThumbnailView, Gravity.NO_GRAVITY, 0, 0);
    }

    @Override
    public void onFaceDetection(Face[] faces, android.hardware.Camera camera) {
            mFaceView.setFaces(faces);
    }

    private void showTapToFocusToast() {
        new RotateTextToast(this, R.string.tap_to_focus, mOrientation).show();
        // Clear the preference.
        Editor editor = mPreferences.edit();
        editor.putBoolean(CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN, false);
        editor.apply();
    }

    private void initializeCapabilities() {
        mInitialParams = mCameraDevice.getParameters();
        mFocusManager.initializeParameters(mInitialParams);
        mFocusAreaSupported = (mInitialParams.getMaxNumFocusAreas() > 0
                && isSupported(Parameters.FOCUS_MODE_AUTO,
                        mInitialParams.getSupportedFocusModes()));
        mMeteringAreaSupported = (mInitialParams.getMaxNumMeteringAreas() > 0);
        mAeLockSupported = mInitialParams.isAutoExposureLockSupported();
        mAwbLockSupported = mInitialParams.isAutoWhiteBalanceLockSupported();
    }

    private void setSkinToneFactor(){
       if (skinToneSeekBar == null) return;
/*
       String skinToneEnhancementPref = mPreferences.getString(
                CameraSettings.KEY_SKIN_TONE_ENHANCEMENT,
       getString(R.string.pref_camera_skinToneEnhancement_default));
 */
       String skinToneEnhancementPref = "enable";
       if(isSupported(skinToneEnhancementPref,
               mParameters.getSupportedSkinToneEnhancementModes())){
         if(skinToneEnhancementPref.equals("enable")) {
             int skinToneValue =0;
             int progress;
               //yyan get the value for the first time!
               if (mskinToneValue ==0){
                  String factor = mPreferences.getString(CameraSettings.KEY_SKIN_TONE_ENHANCEMENT_FACTOR, "0");
                  skinToneValue = Integer.parseInt(factor);
               }

               Log.e(TAG, "yyan Skin tone bar: enable = " + mskinToneValue);
               enableSkinToneSeekBar();
               //yyan: as a wrokaround set progress again to show the actually progress on screen.
               if(skinToneValue != 0) {
                   progress = (skinToneValue/SCE_FACTOR_STEP)-MIN_SCE_FACTOR;
                   skinToneSeekBar.setProgress(progress);
               }
               //mskinToneSeekListener.onProgressChanged(skinToneSeekBar,progress,true);
               //skinToneSeekBar.onProgressRefresh(1.0f, true);
          } else {
              Log.e(TAG, "yyan Skin tone bar: disable");
               disableSkinToneSeekBar();
          }
       } else {
           Log.e(TAG, "yyan Skin tone bar: Not supported");
          skinToneSeekBar.setVisibility(View.INVISIBLE);
       }
    }

    private void enableSkinToneSeekBar() {
        int progress;
        if(brightnessProgressBar != null)
           brightnessProgressBar.setVisibility(View.INVISIBLE);
        skinToneSeekBar.setMax(MAX_SCE_FACTOR-MIN_SCE_FACTOR);
        skinToneSeekBar.setVisibility(View.VISIBLE);
        skinToneSeekBar.requestFocus();
        if (mskinToneValue != 0) {
            progress = (mskinToneValue/SCE_FACTOR_STEP)-MIN_SCE_FACTOR;
            mskinToneSeekListener.onProgressChanged(skinToneSeekBar,progress,false);
        }else {
            progress = (MAX_SCE_FACTOR-MIN_SCE_FACTOR)/2;
            RightValue.setText("");
            LeftValue.setText("");
        }
        skinToneSeekBar.setProgress(progress);
        Title.setText("Skin Tone Enhancement");
        Title.setVisibility(View.VISIBLE);
        RightValue.setVisibility(View.VISIBLE);
        LeftValue.setVisibility(View.VISIBLE);
        mSkinToneSeekBar = true;
    }

    private void disableSkinToneSeekBar() {
         skinToneSeekBar.setVisibility(View.INVISIBLE);
         Title.setVisibility(View.INVISIBLE);
         RightValue.setVisibility(View.INVISIBLE);
         LeftValue.setVisibility(View.INVISIBLE);
         mskinToneValue = 0;
         mSkinToneSeekBar = false;
         Editor editor = mPreferences.edit();
         editor.putString(CameraSettings.KEY_SKIN_TONE_ENHANCEMENT_FACTOR,
                            Integer.toString(mskinToneValue - MIN_SCE_FACTOR));
         editor.apply();
         if(brightnessProgressBar != null)
             brightnessProgressBar.setVisibility(View.VISIBLE);
    }
}
/*
 * Provide a mapping for Jpeg encoding quality levels
 * from String representation to numeric representation.
 */
class JpegEncodingQualityMappings {
    private static final String TAG = "JpegEncodingQualityMappings";
    private static final int DEFAULT_QUALITY = 85;
    private static HashMap<String, Integer> mHashMap =
            new HashMap<String, Integer>();

    static {
        mHashMap.put("normal",    CameraProfile.QUALITY_LOW);
        mHashMap.put("fine",      CameraProfile.QUALITY_MEDIUM);
        mHashMap.put("superfine", CameraProfile.QUALITY_HIGH);
    }

    // Retrieve and return the Jpeg encoding quality number
    // for the given quality level.
    public static int getQualityNumber(String jpegQuality) {
        try{
            int qualityPercentile = Integer.parseInt(jpegQuality);
            if(qualityPercentile >= 0 && qualityPercentile <=100)
                return qualityPercentile;
            else
                return DEFAULT_QUALITY;
        } catch(NumberFormatException nfe){
            //chosen quality is not a number, continue
        }
        Integer quality = mHashMap.get(jpegQuality);
        if (quality == null) {
            Log.w(TAG, "Unknown Jpeg quality: " + jpegQuality);
            return DEFAULT_QUALITY;
        }
        return CameraProfile.getJpegEncodingQualityParameter(quality.intValue());
    }
}
//-------------
 //Graph View Class

class GraphView extends View {
    private Bitmap  mBitmap;
    private Paint   mPaint = new Paint();
    private Canvas  mCanvas = new Canvas();
    private float   mScale = (float)3;
    private float   mWidth;
    private float   mHeight;
    private Camera mCamera;
    private android.hardware.Camera mGraphCameraDevice;
    private float scaled;
    private static final int STATS_SIZE = 256;
    private static final String TAG = "GraphView";


    public GraphView(Context context, AttributeSet attrs) {
        super(context,attrs);

        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
    }
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        mCanvas.setBitmap(mBitmap);
        mWidth = w;
        mHeight = h;
        super.onSizeChanged(w, h, oldw, oldh);
    }
    @Override
    protected void onDraw(Canvas canvas) {
        Log.v(TAG, "in Camera.java ondraw");
        if(!Camera.mHiston ) {
            Log.e(TAG, "returning as histogram is off ");
            return;
        }
    if (mBitmap != null) {
        final Paint paint = mPaint;
        final Canvas cavas = mCanvas;
        final float border = 5;
        float graphheight = mHeight - (2 * border);
        float graphwidth = mWidth - (2 * border);
        float left,top,right,bottom;
        float bargap = 0.0f;
        float barwidth = 1.0f;

        cavas.drawColor(0xFFAAAAAA);
        paint.setColor(Color.BLACK);

        for (int k = 0; k <= (graphheight /32) ; k++) {
            float y = (float)(32 * k)+ border;
            cavas.drawLine(border, y, graphwidth + border , y, paint);
        }
        for (int j = 0; j <= (graphwidth /32); j++) {
            float x = (float)(32 * j)+ border;
            cavas.drawLine(x, border, x, graphheight + border, paint);
        }
        paint.setColor(0xFFFFFFFF);
        synchronized(Camera.statsdata) {
            for(int i=1 ; i<=STATS_SIZE ; i++)  {
                scaled = Camera.statsdata[i]/mScale;
                if(scaled >= (float)STATS_SIZE)
                    scaled = (float)STATS_SIZE;
                left = (bargap * (i+1)) + (barwidth * i) + border;
                top = graphheight + border;
                right = left + barwidth;
                bottom = top - scaled;
                cavas.drawRect(left, top, right, bottom, paint);
            }
        }
        canvas.drawBitmap(mBitmap, 0, 0, null);

    }
        if(Camera.mHiston && mCamera!= null) {
            mGraphCameraDevice = mCamera.getCamera();
        if(mGraphCameraDevice != null)
            mGraphCameraDevice.sendHistogramData();
        }
    }
    public void PreviewChanged() {
 Log.e(TAG, "sravank in previewChanged graphview");
        invalidate();
    }
    public void setCameraObject(Camera camera) {
        mCamera = camera;
    }
}

