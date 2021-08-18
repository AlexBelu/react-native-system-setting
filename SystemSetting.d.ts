interface EmitterSubscription {
  remove: () => void;
}


type VolumeType =
  | "call"
  | "system"
  | "ring"
  | "music"
  | "alarm"
  | "notification";

interface VolumeConfig {
  type?: VolumeType;
  playSound?: boolean;
  showUI?: boolean;
}
interface VolumeData {
  value: number;
  call?: number;
  system?: number;
  ring?: number;
  music?: number;
  alarm?: number;
  notification?: number;
}

interface SystemSetting {
  getBrightness: () => Promise<number>;
  setBrightness: (val: number) => Promise<boolean>;
  setBrightnessForce: (val: number) => Promise<boolean>;
  getAppBrightness: () => Promise<number>;
  setAppBrightness: (val: number) => Promise<true>;
  useBrightnessLevel: () => number | null;
  grantWriteSettingPremission: () => void;
  getScreenMode: () => Promise<number>;
  setScreenMode: (val: number) => Promise<boolean>;
  saveBrightness: () => Promise<void>;
  restoreBrightness: () => number;
  getVolume: (type?: VolumeType) => Promise<number>;
  setVolume: (value: number, config?: VolumeConfig | VolumeType) => void;
  addVolumeListener: (
    callback: (volumeData: VolumeData) => void
  ) => EmitterSubscription;
  removeVolumeListener: (listener?: EmitterSubscription) => void;
  openAppSystemSettings: () => Promise<void>;
  removeListener: (listener?: EmitterSubscription) => void;
  getBatteryLevel: () => Promise<number>;
  useBatteryLevel: () => number | null;
}

declare const systemSetting: SystemSetting;
export default systemSetting;
