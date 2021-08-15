import {  useEffect, useState } from 'react';
import { NativeEventEmitter, NativeModules } from 'react-native';


const SystemSettingNative = NativeModules.SystemSetting

const deviceInfoEmitter = new NativeEventEmitter(NativeModules.SystemSetting);
export function useBatteryLevel(): number | null {
  const [batteryLevel, setBatteryLevel] = useState<number | null>(null);

  useEffect(() => {
    const setInitialValue = async () => {
      const initialValue: number = await SystemSettingNative.getBatteryLevel();
      setBatteryLevel(initialValue);
    };

    const onChange = (level: number) => {
      setBatteryLevel(level);
    };

    setInitialValue();

    const subscription = deviceInfoEmitter.addListener(
      'RNDeviceInfo_batteryLevelDidChange',
      onChange
    );

    return () => subscription.remove();
  }, []);

  return batteryLevel;
}