import {  useEffect, useState } from 'react';
import { NativeEventEmitter, NativeModules } from 'react-native';

const SystemSettingNative = NativeModules.SystemSetting

const deviceInfoEmitter = new NativeEventEmitter(NativeModules.SystemSetting);
export function useBatteryLevel(): number | null {
  const [batteryLevel, setBatteryLevel] = useState<number | null>(null);

  useEffect(() => {

    async function setInitialValue (){
       const initialValue: number = await SystemSettingNative.getBatteryLevel();
       setBatteryLevel(initialValue);
    };

    setInitialValue();

    const subscription = deviceInfoEmitter.addListener(
      'RNDeviceInfo_batteryLevelDidChange',
      (level: number)=>{setBatteryLevel(level)} 
    );

    return () => subscription.remove();
  }, []);
  
  return batteryLevel;
}

export function useBrightnessLevel(): number | null {
  const [brightnessLevel, setBrightnessLevel] = useState<number | null>(null);

  useEffect(() => {

    async function setInitialValue (){
       const initialValue: number = await SystemSettingNative.getBrightness();
       setBrightnessLevel(initialValue);
    };

    setInitialValue();

    const subscription = deviceInfoEmitter.addListener(
      'BrightnessLevelDidChange',
      (level: number)=>{setBrightnessLevel(level)} 
    );

    return () => subscription.remove();
  }, []);

  return brightnessLevel;
}