//
//  RTCSystemSetting.m
//  RTCSystemSetting
//
//  Created by ninty on 2017/5/29.
//  Copyright © 2017年 ninty. All rights reserved.
//

#import "RTCSystemSetting.h"
#import <SystemConfiguration/CaptiveNetwork.h>
#import <CoreTelephony/CTTelephonyNetworkInfo.h>
#import <ifaddrs.h>
#import <net/if.h>


@import UIKit;
@import MediaPlayer;

@interface RCTSystemSetting()
@end

@implementation RCTSystemSetting {
    bool hasListeners;
    long skipSetVolumeCount;

     NSDictionary *setting;
    MPVolumeView *volumeView;
    UISlider *volumeSlider;
}

-(instancetype)init{
    self = [super init];
    [[UIDevice currentDevice] setBatteryMonitoringEnabled:YES];
    if(self){
        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(volumeChanged:)
                                                     name:@"AVSystemController_SystemVolumeDidChangeNotification"
                                                   object:nil];
    }

    [self initVolumeView];
#ifdef PRIVATE_API
    [self initSetting];
#endif

    return self;
}

-(void)initVolumeView{
    skipSetVolumeCount = 0;
    volumeView = [[MPVolumeView alloc] initWithFrame:CGRectMake(-[UIScreen mainScreen].bounds.size.width, 0, 0, 0)];
    [self showVolumeUI:YES];
    for (UIView* view in volumeView.subviews) {
        if ([view.class.description isEqualToString:@"MPVolumeSlider"]){
            volumeSlider = (UISlider*)view;
            break;
        }
    }
}

#ifdef PRIVATE_API
-(void)initSetting{
    BOOL newSys = [UIDevice currentDevice].systemVersion.doubleValue >= 10.0;
}
#endif

+(BOOL)requiresMainQueueSetup{
    return YES;
}

RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(setBrightness:(float)val resolve:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject){
    dispatch_sync(dispatch_get_main_queue(), ^{
        [[UIScreen mainScreen] setBrightness:val];
        resolve([NSNumber numberWithBool:YES]);
    });
}

RCT_EXPORT_METHOD(getBrightness:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject){
    resolve([NSNumber numberWithDouble:[UIScreen mainScreen].brightness]);
}

RCT_EXPORT_METHOD(setVolume:(float)val config:(NSDictionary *)config){
    skipSetVolumeCount++;
    dispatch_sync(dispatch_get_main_queue(), ^{
        id showUI = [config objectForKey:@"showUI"];
        [self showVolumeUI:(showUI != nil && [showUI boolValue])];
        volumeSlider.value = val;
    });
}

RCT_EXPORT_METHOD(getVolume:(NSString *)type resolve:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject){
    dispatch_sync(dispatch_get_main_queue(), ^{
        resolve([NSNumber numberWithFloat:[volumeSlider value]]);
    });
}

RCT_EXPORT_METHOD(activeListener:(NSString *)type resolve:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject){
    if([type isEqualToString:@"bluetooth"]){
#ifdef BLUETOOTH
        [cb setDelegate:self];
#endif
        resolve(@YES);
    }else{
         reject(@"-1", [NSString stringWithFormat:@"unsupported listener type: %@", type], nil);
    }
}

-(void)showVolumeUI:(BOOL)flag{
    if(flag && [volumeView superview]){
        [volumeView removeFromSuperview];
    }else if(!flag && ![volumeView superview]){
        [[[[UIApplication sharedApplication] keyWindow] rootViewController].view addSubview:volumeView];
    }
}

-(void)openSetting:(NSString*)service{
#ifdef PRIVATE_API
    NSString *url = [setting objectForKey:service];
    dispatch_sync(dispatch_get_main_queue(), ^{
        [[UIApplication sharedApplication] openURL:[NSURL URLWithString:url] options:[NSDictionary new] completionHandler:nil];
        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(applicationWakeUp:)
                                                     name:UIApplicationWillEnterForegroundNotification
                                                   object:nil];
    });
#else
    NSLog(@"Fail to open [%@]. These APIs which start with 'switch*()' will cause a rejection from App Store, and you can use these APIs only when you distribute app outside App Store, see see https://github.com/c19354837/react-native-system-setting/blob/master/iOS.md", service);
#endif
}


-(void)applicationWakeUp:(NSNotification*)notification{
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationWillEnterForegroundNotification object:nil];
    [self sendEventWithName:@"EventEnterForeground" body:nil];
}

- (NSArray<NSString *> *)supportedEvents
{
    return @[@"EventVolume", @"EventEnterForeground", @"EventBluetoothChange"];
}

-(void)startObserving {
    hasListeners = YES;
}

-(void)stopObserving {
    hasListeners = NO;
}

-(void)volumeChanged:(NSNotification *)notification{
    if(skipSetVolumeCount == 0 && hasListeners){
        float volume = [[[notification userInfo] objectForKey:@"AVSystemController_AudioVolumeNotificationParameter"] floatValue];
        [self sendEventWithName:@"EventVolume" body:@{@"value": [NSNumber numberWithFloat:volume]}];
    }
    if(skipSetVolumeCount > 0){
        skipSetVolumeCount--;
    }
}

- (NSDictionary *) powerState {
#if RCT_DEV && (!TARGET_IPHONE_SIMULATOR) && !TARGET_OS_TV
    if ([UIDevice currentDevice].isBatteryMonitoringEnabled != true) {
        RCTLogWarn(@"Battery monitoring is not enabled. "
                   "You need to enable monitoring with `[UIDevice currentDevice].batteryMonitoringEnabled = TRUE`");
    }
#endif
#if RCT_DEV && TARGET_IPHONE_SIMULATOR && !TARGET_OS_TV
    if ([UIDevice currentDevice].batteryState == UIDeviceBatteryStateUnknown) {
        RCTLogWarn(@"Battery state `unknown` and monitoring disabled, this is normal for simulators and tvOS.");
    }
#endif

    return @{
#if TARGET_OS_TV
             @"batteryLevel": @1,
             @"batteryState": @"full",
#else
             @"batteryLevel": @([UIDevice currentDevice].batteryLevel),
             @"batteryState": [@[@"unknown", @"unplugged", @"charging", @"full"] objectAtIndex: [UIDevice currentDevice].batteryState],
             @"lowPowerMode": @([NSProcessInfo processInfo].isLowPowerModeEnabled),
#endif
             };
}


RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getPowerStateSync) {
    return self.powerState;
}

RCT_EXPORT_METHOD(getPowerState:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    resolve(self.powerState);
}

- (float) getBatteryLevel {
    return [self.powerState[@"batteryLevel"] floatValue];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getBatteryLevelSync) {
    return @(self.getBatteryLevel);
}

RCT_EXPORT_METHOD(getBatteryLevel:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    resolve(@(self.getBatteryLevel));
}


@end
