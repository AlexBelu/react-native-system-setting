package com.ninty.system.setting;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import android.os.PowerManager;
import android.os.BatteryManager;
import static android.os.BatteryManager.BATTERY_STATUS_CHARGING;
import static android.os.BatteryManager.BATTERY_STATUS_FULL;
import static android.provider.Settings.Secure.getString;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;

/**
 * Created by ninty on 2017/5/29.
 */

public class SystemSetting extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {

    private String TAG = SystemSetting.class.getSimpleName();

    private static final String VOL_VOICE_CALL = "call";
    private static final String VOL_SYSTEM = "system";
    private static final String VOL_RING = "ring";
    private static final String VOL_MUSIC = "music";
    private static final String VOL_ALARM = "alarm";
    private static final String VOL_NOTIFICATION = "notification";
    private static String BATTERY_STATE = "batteryState";
    private static String BATTERY_LEVEL= "batteryLevel";
    private static String LOW_POWER_MODE = "lowPowerMode";

    private double mLastBatteryLevel = -1;

    private ReactApplicationContext mContext;
    private AudioManager am;
    private VolumeBroadcastReceiver volumeBR;
    private volatile BroadcastReceiver airplaneBR;

    public SystemSetting(ReactApplicationContext reactContext) {
        super(reactContext);
        mContext = reactContext;
        reactContext.addLifecycleEventListener(this);
        am = (AudioManager) mContext.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        volumeBR = new VolumeBroadcastReceiver();
    }

    private void registerVolumeReceiver() {
        if (!volumeBR.isRegistered()) {
            IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            mContext.registerReceiver(volumeBR, filter);
            volumeBR.setRegistered(true);
        }
    }

    private void unregisterVolumeReceiver() {
        if (volumeBR.isRegistered()) {
            mContext.unregisterReceiver(volumeBR);
            volumeBR.setRegistered(false);
        }
    }


    @Override
    public String getName() {
        return SystemSetting.class.getSimpleName();
    }

    @ReactMethod
    public void setScreenMode(int mode, Promise promise) {
        mode = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL ? mode : Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        checkAndSet(Settings.System.SCREEN_BRIGHTNESS_MODE, mode, promise);
    }

    @ReactMethod
    public void getScreenMode(Promise promise) {
        try {
            int mode = Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
            promise.resolve(mode);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "err", e);
            promise.reject("-1", "get screen mode fail", e);
        }
    }

    @ReactMethod
    public void setBrightness(float val, Promise promise) {
        final int brightness = (int) (val * 255);
        checkAndSet(Settings.System.SCREEN_BRIGHTNESS, brightness, promise);
    }

    @ReactMethod
    public void setAppBrightness(float val) {
        final Activity curActivity = getCurrentActivity();
        if (curActivity == null) {
            return;
        }
        final WindowManager.LayoutParams lp = curActivity.getWindow().getAttributes();
        lp.screenBrightness = val;
        curActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                curActivity.getWindow().setAttributes(lp);
            }
        });
    }

    @ReactMethod
    public void getAppBrightness(Promise promise) {
        final Activity curActivity = getCurrentActivity();
        if (curActivity == null) {
            return;
        }
        try {
            float result = curActivity.getWindow().getAttributes().screenBrightness;
            if (result < 0) {
                int val = Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                promise.resolve(val * 1.0f / 255);
            } else {
                promise.resolve(result);
            }
        } catch (Exception e) {
            Log.e(TAG, "err", e);
            promise.reject("-1", "get app's brightness fail", e);
        }
    }

    @ReactMethod
    public void getBrightness(Promise promise) {
        try {
            int val = Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            promise.resolve(val * 1.0f / 255);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "err", e);
            promise.reject("-1", "get brightness fail", e);
        }
    }

    @ReactMethod
    public void setVolume(float val, ReadableMap config) {
        unregisterVolumeReceiver();
        String type = config.getString("type");
        boolean playSound = config.getBoolean("playSound");
        boolean showUI = config.getBoolean("showUI");
        int volType = getVolType(type);
        int flags = 0;
        if (playSound) {
            flags |= AudioManager.FLAG_PLAY_SOUND;
        }
        if (showUI) {
            flags |= AudioManager.FLAG_SHOW_UI;
        }
        try {
            am.setStreamVolume(volType, (int) (val * am.getStreamMaxVolume(volType)), flags);
        } catch (SecurityException e) {
            if (val == 0) {
                Log.w(TAG, "setVolume(0) failed. See https://github.com/c19354837/react-native-system-setting/issues/48");
                NotificationManager notificationManager =
                        (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !notificationManager.isNotificationPolicyAccessGranted()) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    mContext.startActivity(intent);
                }
            }
            Log.e(TAG, "err", e);
        }
        registerVolumeReceiver();
    }

    @ReactMethod
    public void getVolume(String type, Promise promise) {
        promise.resolve(getNormalizationVolume(type));
    }

    private void checkAndSet(String name, int value, Promise promise) {
        boolean reject = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(mContext)) {
            reject = true;
        } else {
            try {
                Settings.System.putInt(mContext.getContentResolver(), name, value);
                int newVal = Settings.System.getInt(mContext.getContentResolver(), name);
                if (newVal != value) {
                    reject = true;
                }
            } catch (Settings.SettingNotFoundException e) {
                Log.e(TAG, "err", e);
                //ignore
            } catch (SecurityException e) {
                Log.e(TAG, "err", e);
                reject = true;
            }
        }
        if (reject) {
            promise.reject("-1", "write_settings permission is blocked by system");
        } else {
            promise.resolve(true);
        }
    }

    private float getNormalizationVolume(String type) {
        int volType = getVolType(type);
        return am.getStreamVolume(volType) * 1.0f / am.getStreamMaxVolume(volType);
    }

    private int getVolType(String type) {
        switch (type) {
            case VOL_VOICE_CALL:
                return AudioManager.STREAM_VOICE_CALL;
            case VOL_SYSTEM:
                return AudioManager.STREAM_SYSTEM;
            case VOL_RING:
                return AudioManager.STREAM_RING;
            case VOL_ALARM:
                return AudioManager.STREAM_ALARM;
            case VOL_NOTIFICATION:
                return AudioManager.STREAM_NOTIFICATION;
            default:
                return AudioManager.STREAM_MUSIC;
        }
    }

    @ReactMethod
    public void openAppSystemSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.setData(Uri.parse("package:" + mContext.getPackageName()));
        if (intent.resolveActivity(mContext.getPackageManager()) != null) {
            mContext.startActivity(intent);
        }
    }

    @ReactMethod
    public void openWriteSetting() {
        Intent intent = new Intent(SysSettings.WRITESETTINGS.action, Uri.parse("package:" + mContext.getPackageName()));
        mContext.getCurrentActivity().startActivity(intent);
    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    @Override
    public void onHostResume() {
        registerVolumeReceiver();
    }

    @Override
    public void onHostPause() {
        unregisterVolumeReceiver();
    }

    @Override
    public void onHostDestroy() {
    }

@Override
        public void onReceive(Context context, Intent intent) 
{
     WritableMap powerState = getPowerStateFromIntent(intent);

        if(powerState == null) {
          return;
        }

        String batteryState = powerState.getString(BATTERY_STATE);
        Double batteryLevel = powerState.getDouble(BATTERY_LEVEL);
        Boolean powerSaveState = powerState.getBoolean(LOW_POWER_MODE);

        if(mLastBatteryLevel != batteryLevel) {
            sendEvent(getReactApplicationContext(), "RNDeviceInfo_batteryLevelDidChange", batteryLevel); 
            mLastBatteryLevel = batteryLevel;
        }
}

    private class VolumeBroadcastReceiver extends BroadcastReceiver {

        private boolean isRegistered = false;

        public void setRegistered(boolean registered) {
            isRegistered = registered;
        }

        public boolean isRegistered() {
            return isRegistered;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.media.VOLUME_CHANGED_ACTION")) {
                WritableMap para = Arguments.createMap();
                para.putDouble("value", getNormalizationVolume(VOL_MUSIC));
                para.putDouble(VOL_VOICE_CALL, getNormalizationVolume(VOL_VOICE_CALL));
                para.putDouble(VOL_SYSTEM, getNormalizationVolume(VOL_SYSTEM));
                para.putDouble(VOL_RING, getNormalizationVolume(VOL_RING));
                para.putDouble(VOL_MUSIC, getNormalizationVolume(VOL_MUSIC));
                para.putDouble(VOL_ALARM, getNormalizationVolume(VOL_ALARM));
                para.putDouble(VOL_NOTIFICATION, getNormalizationVolume(VOL_NOTIFICATION));
                try {
                    mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("EventVolume", para);
                } catch (RuntimeException e) {
                    // Possible to interact with volume before JS bundle execution is finished.
                    // This is here to avoid app crashing.
                }
            }
        
        }
    }

    
   @ReactMethod(isBlockingSynchronousMethod = true)
      public WritableMap getPowerStateSync() {
      Intent intent = getReactApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      return getPowerStateFromIntent(intent);
  }

   @ReactMethod
      public void getPowerState(Promise p) { p.resolve(getPowerStateSync()); }

   @ReactMethod(isBlockingSynchronousMethod = true)
       public double getBatteryLevelSync() {
       Intent intent = getReactApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
       WritableMap powerState = getPowerStateFromIntent(intent);

       if(powerState == null) {
        return 0;
      }

    return powerState.getDouble(BATTERY_LEVEL);
    }

   @ReactMethod
  public void getBatteryLevel(Promise p) { p.resolve(getBatteryLevelSync()); }


@Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    }
  
  private WritableMap getPowerStateFromIntent (Intent intent) {
    if(intent == null) {
      return null;
    }

    int batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int batteryScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    int isPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

    float batteryPercentage = batteryLevel / (float)batteryScale;

    String batteryState = "unknown";

    if(isPlugged == 0) {
      batteryState = "unplugged";
    } else if(status == BATTERY_STATUS_CHARGING) {
      batteryState = "charging";
    } else if(status == BATTERY_STATUS_FULL) {
      batteryState = "full";
    }

    PowerManager powerManager = (PowerManager)getReactApplicationContext().getSystemService(Context.POWER_SERVICE);
    boolean powerSaveMode = false;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      powerSaveMode = powerManager.isPowerSaveMode();
    }

    WritableMap powerState = Arguments.createMap();
    powerState.putString(BATTERY_STATE, batteryState);
    powerState.putDouble(BATTERY_LEVEL, batteryPercentage);
    powerState.putBoolean(LOW_POWER_MODE, powerSaveMode);

    return powerState;
  }
 
  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         @Nullable Object data) {
    reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, data);
  }

}
