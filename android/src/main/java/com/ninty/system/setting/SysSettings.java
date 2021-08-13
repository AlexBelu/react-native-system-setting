package com.ninty.system.setting;

import android.provider.Settings;

/**
 * Created by ninty on 2017/7/2.
 */

public enum SysSettings {

    UNKNOW("", 0),
    WRITESETTINGS(Settings.ACTION_MANAGE_WRITE_SETTINGS, 4),

    public String action;
    public int requestCode;

    SysSettings(String action, int requestCode) {
        this.action = action;
        this.requestCode = requestCode;
    }

    public static SysSettings get(int requestCode){
        for (SysSettings setting : SysSettings.values()) {
            if(setting.requestCode == requestCode){
                return setting;
            }
        }
        return UNKNOW;
    }
}