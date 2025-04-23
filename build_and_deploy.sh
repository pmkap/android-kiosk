#!/bin/sh

set -e

# build docker image
docker build -t android-kiosk-dev .

# run build in container
docker run --rm \
  -v $(pwd):/project/android-kiosk \
  android-kiosk-dev \
  ./gradlew assembleDebug

# uninstall
adb shell dpm remove-active-admin pl.snowdog.kiosk/.MyDeviceAdminReceiver || true
adb uninstall pl.snowdog.kiosk || true

# reinstall
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell dpm set-device-owner pl.snowdog.kiosk/.MyDeviceAdminReceiver
