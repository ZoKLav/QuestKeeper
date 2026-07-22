# QuestKeeper

QuestKeeper exists because I took my Quest 2 apart enough times that the proximity sensor ZIF connector broke, and then the headset kept going to sleep while I was recording first-person sex videos with Scrcpy and OBS.

It is a normal 2D Android app for a hacked-up Quest 2 that needs to stay awake when the proximity sensor is lying, broken, missing, or otherwise being useless.

QuestKeeper forces the Quest 2 to behave more like it is actually being worn, suppresses idle sleep from as many angles as Android will allow, helps keep ADB-over-Wi-Fi available, and still allows the physical power button to sleep the headset on purpose.

It supports both modern wireless ADB with random-port discovery and the classic `adb tcpip 5555` fallback, because sometimes the old dumb way is better.

## What QuestKeeper does

QuestKeeper applies a practical “keep awake but still sleep on command” profile for Quest 2.

It can apply the Quest proximity-sensor workaround after boot, re-apply the proximity workaround manually, push the Android screen timeout as high as Android allows, disable additional sleep/timeout paths where permission allows, hold a wake behavior while the app/service is active, and still allow the physical power button to put the headset to sleep.

It also includes wireless ADB helpers. It can enable the modern Quest/Android wireless ADB path, discover the random wireless ADB port when mDNS discovery is being used, copy usable `adb connect` commands, and provide a classic TCP/IP `:5555` fallback for people who prefer the older ADB-over-Wi-Fi flow.

The UI is red-themed, uses visible button press feedback, and keeps logs inside a fixed scrollable box so the Quest 2D app window does not expand forever.

## Important behavior notes

QuestKeeper is designed to suppress unwanted idle sleep, not to fight deliberate user sleep.

The physical power button should still sleep the headset. This is intentional. The point is to stop the headset from randomly timing out during development or monitoring, while still letting you manually turn the display off when you actually want to.

QuestKeeper uses Android’s `SCREEN_OFF_TIMEOUT` setting as one of its timeout-control layers. It also uses secure/global settings where Android allows it, which is why the app needs `WRITE_SECURE_SETTINGS` for the full feature set.

The app does not bypass Android or Quest security. USB debugging still needs to be enabled and authorized normally.

## Requirements

QuestKeeper expects a Meta Quest 2 with Developer Mode enabled.

You need ADB available on your computer, USB debugging accepted at least once inside the headset, QuestKeeper installed on the headset, and `WRITE_SECURE_SETTINGS` granted through ADB for the full feature set.

Some features may appear to run without the secure permission, but the important system-level controls require it.

## First-time setup

Install QuestKeeper on the headset using whatever sideloading method you normally use.

After installing it, connect the Quest 2 to your PC with USB. Put on the headset and accept the USB debugging prompt if it appears.

Check that ADB sees the headset:

```bat
adb devices
```

The Quest should show as:

```text
device
```

If it shows:

```text
unauthorized
```

put the headset on and accept the USB debugging prompt.

If it does not show up at all, check the cable, Developer Mode, USB debugging, and your ADB/driver setup.

Once ADB sees the headset, grant QuestKeeper the secure settings permission:

```bat
adb shell pm grant com.zoey.questkeeper android.permission.WRITE_SECURE_SETTINGS
```

You can verify the permission with:

```bat
adb shell dumpsys package com.zoey.questkeeper | findstr WRITE_SECURE_SETTINGS
```

You want to see something like:

```text
android.permission.WRITE_SECURE_SETTINGS: granted=true
```

If ADB says the package does not exist, QuestKeeper is not installed under the expected package name.

If ADB says the app did not request the permission, the installed APK is the wrong or older build.

## Normal use

Open QuestKeeper from the Quest app launcher.

Press:

```text
Apply Keeper Profile
```

This applies the main headset profile.

That profile applies the proximity close workaround, extends the screen timeout, changes secure sleep timeout settings where permission allows, applies stay-awake behavior where possible, and starts the foreground keeper service.

The app log will show which steps succeeded and which ones failed or were blocked.

If the log says secure settings failed, the app probably does not have `WRITE_SECURE_SETTINGS` yet.

## Boot behavior

QuestKeeper includes a boot receiver.

After the headset boots, QuestKeeper waits briefly and then applies the proximity workaround automatically. The delay is intentional because Quest and Android system services may not be ready immediately at startup.

The proximity workaround is applied about 30 seconds after boot.

## Proximity behavior

QuestKeeper uses the Quest power-manager proximity workaround.

The main action is the `prox_close` behavior, which tells the Quest to behave as though the proximity condition is closed for the configured duration. QuestKeeper applies this after boot and can also apply it manually from the UI.

There is also a manual restore/disable-style control for undoing the automation behavior when needed.

## Power-button behavior

QuestKeeper is supposed to prevent unwanted idle timeout while still allowing deliberate power-button sleep.

That means the app tries to suppress idle display shutoff, hold wake behavior while active, and keep the Quest usable during long sessions.

It is not meant to immediately wake the display again after you press the physical power button.

If pressing the power button does not sleep the headset, that is a bug.

## Wireless ADB modes

QuestKeeper supports two wireless ADB approaches.

The first is the modern Quest/Android wireless ADB path. This uses Android wireless debugging behavior and a random TCP port. Because the port changes, QuestKeeper attempts to discover the active ADB service and show a full command like this:

```bat
adb connect 192.168.1.123:37123
```

Use the exact IP and port shown by the app.

Do not assume plain IP-only connection will work with modern random-port wireless ADB.

The second option is the classic TCP/IP `:5555` fallback. This is the older and simpler ADB-over-Wi-Fi flow:

```bat
adb tcpip 5555
adb connect QUEST_IP:5555
```

After `adb tcpip 5555`, ADB switches the headset into TCP/IP mode on port `5555`.

Then you can connect with either:

```bat
adb connect QUEST_IP
```

or:

```bat
adb connect QUEST_IP:5555
```

ADB defaults to port `5555` when no port is supplied.

## Recommended ADB workflow

For the most predictable setup, connect the Quest 2 over USB first.

Confirm ADB sees it:

```bat
adb devices
```

Grant QuestKeeper secure settings permission if you have not already:

```bat
adb shell pm grant com.zoey.questkeeper android.permission.WRITE_SECURE_SETTINGS
```

Open QuestKeeper.

Press:

```text
Apply Keeper Profile
```

Then choose either modern wireless ADB or TCP/IP fallback.

Use modern wireless ADB if you want the current Android/Quest random-port wireless debugging behavior.

Use TCP/IP fallback if you prefer the classic `:5555` setup and do not want to rely on mDNS/random-port discovery.

## Modern wireless ADB flow

Open QuestKeeper.

Press:

```text
Enable Wireless ADB
```

Then press:

```text
Discover ADB Port
```

QuestKeeper will attempt to discover the active wireless ADB service and update the displayed connection command.

Copy the command and run it on your PC. It should look similar to:

```bat
adb connect 192.168.1.123:37123
```

The port may be different every time wireless ADB is enabled.

## TCP/IP fallback flow

Connect the Quest over USB first.

From your PC, you can run:

```bat
adb tcpip 5555
```

Then connect wirelessly:

```bat
adb connect QUEST_IP:5555
```

QuestKeeper also includes controls to help with this fallback path and copy the relevant command.

Once connected wirelessly, you can unplug USB.

## Finding the Quest IP address

QuestKeeper tries to show useful connection information when available.

You can also find the Quest IP address through ADB:

```bat
adb shell ip addr show wlan0
```

Look for the `inet` address, usually something like:

```text
192.168.x.x
```

Then connect with:

```bat
adb connect 192.168.x.x:5555
```

if you are using TCP/IP fallback.

If you are using modern wireless ADB, use the full random-port command shown by QuestKeeper instead.

## Buttons

### Apply Keeper Profile

Applies the main anti-timeout and proximity profile.

Use this after opening the app, after rebooting, or any time the Quest starts behaving like the settings reverted.

### Restore/Disable Proximity Automation

Attempts to undo or disable the proximity automation behavior.

Use this if the proximity workaround is no longer wanted or if you are troubleshooting headset sleep/proximity behavior.

### Enable Wireless ADB

Attempts to enable the modern wireless ADB path.

This may use a random port and may require discovery before you know the correct `adb connect` command.

### Discover ADB Port

Searches for the active wireless ADB service and updates the displayed connection command.

Use this when wireless ADB is enabled but you do not know the current port.

### TCP/IP Fallback

Uses or prepares the classic `adb tcpip 5555` flow.

Use this when you prefer connecting with:

```bat
adb connect QUEST_IP:5555
```

instead of dealing with random wireless debugging ports.

### Wireless First, Then TCP/IP Fallback

Attempts the modern wireless ADB path first, then gives you or prepares the classic TCP/IP fallback if discovery or connection is annoying.

Use this when you do not care which wireless ADB method works, you just want a usable connection.

### Copy Connect Command

Copies the most recent usable `adb connect` command.

Paste it into CMD or PowerShell on your PC.

### Disable Wireless ADB

Attempts to turn wireless ADB back off.

Use this when you are done testing or do not want ADB exposed over the network.

## UI behavior

QuestKeeper uses a red-themed 2D interface.

Buttons have visible press feedback so the app does not feel like it ignored input.

The log output is contained inside a fixed-height scrollable box. It should not expand the Quest 2D window endlessly.

If the log starts stretching the window again, the log layout probably regressed.

## Troubleshooting

### The app opens as a VR app or starts an OpenXR environment

That should not happen.

QuestKeeper is supposed to be a normal 2D Android app. The manifest should not contain the Oculus VR launcher category.

The launcher activity should use the normal Android launcher intent setup, not the Oculus VR category.

### The app says secure settings failed

Grant the permission from ADB:

```bat
adb shell pm grant com.zoey.questkeeper android.permission.WRITE_SECURE_SETTINGS
```

Then force close and reopen QuestKeeper.

### ADB says unauthorized

Put on the headset and accept the USB debugging prompt.

Then run:

```bat
adb devices
```

again.

### ADB does not show the Quest

Check Developer Mode.

Check the USB cable.

Check that the headset is awake.

Check whether the headset is asking you to allow USB debugging.

Check whether your PC sees the Quest at all.

Restart ADB if needed:

```bat
adb kill-server
adb start-server
adb devices
```

### `adb connect QUEST_IP` does not work

Plain IP-only ADB uses port `5555`.

If you are using modern random-port wireless ADB, you need the full IP and port:

```bat
adb connect QUEST_IP:RANDOM_PORT
```

If you want IP-only or `:5555`, use TCP/IP fallback instead:

```bat
adb tcpip 5555
adb connect QUEST_IP:5555
```

### Modern wireless ADB enabled, but no port appears

mDNS discovery may be blocked or unreliable on your network.

Try running this on your PC:

```bat
adb mdns services
```

If the service appears there, connect to the shown IP and port.

If discovery still fails, use TCP/IP fallback.

### TCP/IP fallback does not work

Make sure the Quest is connected over USB first and visible to ADB.

Run:

```bat
adb devices
```

Then:

```bat
adb tcpip 5555
```

Then connect with:

```bat
adb connect QUEST_IP:5555
```

If it still fails, check that the Quest and PC are on the same network and that the network is not isolating wireless clients.

### The power button will not sleep the headset

That is not intended behavior.

QuestKeeper should suppress idle sleep, not deliberate power-button sleep.

Disable the keeper profile or force close the app, then test again.

### The log makes the window huge

The current UI keeps logs inside a fixed-height scrollable box.

If the window expands again, the log view layout likely regressed.

### The buttons feel like they do nothing

The current UI includes visual press feedback.

A pressed button should visibly react. If it does not, the pressed-state drawable or touch animation probably regressed.

## Security notes

Wireless ADB gives debugging access over your local network.

Only enable it on networks you trust.

The modern wireless ADB path uses random ports and discovery. The ADB connection itself is authenticated, but the discovery layer can still be annoying, noisy, or undesirable depending on your network.

The TCP/IP fallback uses the predictable `5555` port. That is convenient and simple, but also easier to find on a network.

Turn wireless ADB off when you are done.

Do not enable wireless ADB on public or untrusted Wi-Fi.

## Package name

```text
com.zoey.questkeeper
```

Use this package name for ADB permission grants, package checks, and uninstall commands.

## Useful commands

Check connected devices:

```bat
adb devices
```

Grant secure settings permission:

```bat
adb shell pm grant com.zoey.questkeeper android.permission.WRITE_SECURE_SETTINGS
```

Check whether the app is installed:

```bat
adb shell pm path com.zoey.questkeeper
```

Check secure permission state:

```bat
adb shell dumpsys package com.zoey.questkeeper | findstr WRITE_SECURE_SETTINGS
```

Start classic ADB TCP/IP mode:

```bat
adb tcpip 5555
```

Connect over classic TCP/IP:

```bat
adb connect QUEST_IP:5555
```

Disconnect wireless ADB:

```bat
adb disconnect QUEST_IP:5555
```

Uninstall QuestKeeper:

```bat
adb uninstall com.zoey.questkeeper
```

Check the Quest Wi-Fi interface:

```bat
adb shell ip addr show wlan0
```

List mDNS-discovered ADB services from the PC:

```bat
adb mdns services
```
