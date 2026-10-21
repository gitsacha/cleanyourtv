# Optional Fire TV Home redirect

Fire OS can keep Amazon as the protected default Home even when
`set-home-activity` reports success. `HomeRedirectService` is an opt-in fallback:
when Amazon Home appears, it requests this launcher afterward. It does not
disable Amazon Home, replace its HOME role, or intercept the physical HOME key.

This port comes from the separate Clean TV helper tested on an AFTSSS Fire TV
Stick 3rd generation, Fire OS 7.7.1.7 / Android 9. See the
[device report](devices/amazon-fire-tv-stick-3rd-gen.md). It is experimental,
not a promise of instant Home takeover or support for every Fire OS release.

## Build and try it

Keep Amazon's launcher enabled. Do not use the normal Home-handover installer
on Fire TV: it expects the new launcher to own HOME. From the repo root, after
setting up JDK 17, Android SDK 34, `tools/venv` and `TV_HOST` as in README:

```sh
(cd launcher && ./gradlew assembleRelease)
tools/b64push.py launcher/app/build/outputs/apk/release/app-release.apk /data/local/tmp/tvhome.apk
tools/tv.py 'pm install -r /data/local/tmp/tvhome.apk'
tools/tv.py 'am start -n com.gathin.tvhome/.MainActivity'
```

No stock package is disabled by these commands. Hold OK on Settings and choose
**Launcher settings > Choose default home > Enable**. On a Fire TV this opens
Accessibility if the helper is not bound. Enable **Home redirect**
there. Other TVs without a configured redirect target retain the system Home picker.

If Fire OS hides the sideloaded service in Accessibility, enable just
`com.gathin.tvhome/.HomeRedirectService` over ADB for the current user. Read and
save `enabled_accessibility_services` and `accessibility_enabled` first, append
the component to that user's colon-separated list, and set the master switch
to 1. Preserve other components; never replace the entire list. Check binding
with `dumpsys accessibility` after a few seconds. A written setting is not proof
of binding or physical Home behavior.

The service is never enabled by the normal installer. It runs only after the
owner opts in through Accessibility. No screen-content retrieval, raw key
filtering, overlay, notification access, internet or periodic polling is used.
It observes window package/class metadata to recognize Amazon Home and cancel
a queued request when another app opens; it does not retain that metadata.

## Limitations

- On the tested firmware, Fire OS holds the activity start for roughly five
  seconds after Home. Amazon Home is visible first. Direct activity and
  PendingIntent starts both showed this hold; the 200 ms event debounce does
  not remove it.
- This is a redirect, not a new default launcher. Amazon Home and its services
  remain enabled and may consume memory.
- The stock package and two known Home classes are in `values/config.xml`.
  Other firmware can use different names or suppress events. Updates,
  force-stop and reinstall can break or disable binding.
- Requests are capped at three per five seconds to avoid fighting firmware
  indefinitely. Rapid Home presses can skip a request. No root, firmware patch
  or persistent ADB listener bypasses system restrictions.
- Test the real remote from another app, including after reboot. An injected
  key is insufficient. Short-session success is not multi-day reliability.

## Other TVs, including Nokia

The same service accepts #326's `home_redirect_package` key in `config.xml` or
a theme's `config.json`. For the Nokia Streaming Box 8000, add this to the theme:

```json
{"home_redirect_package": "com.google.android.tvlauncher"}
```

The built-in value is empty. Other TVs retain their system Home picker unless
a target is configured. On Fire TV, explicitly enabling the helper without a
configured target uses the Amazon package from `config.xml`.

Push the theme with `tools/theme.sh` or `tools/theme.ps1`, then enable **Home
redirect** in Accessibility. Its component is
`com.gathin.tvhome/.HomeRedirectService`. After upgrading from a build with
`FireTvHomeService`, enable **Home redirect** again in Accessibility and remove
the old component from any ADB-managed enabled-services list. Android does not
transfer accessibility approval when a service component is renamed.
The theme tools restart the app and restore previously enabled services so
the target is reread. If changing files manually, disable and re-enable this
service afterward. A theme alone never enables an accessibility service.

Only this service owns Home redirection. Do not add #326's parallel window
handler or `KEYCODE_HOME` interception to `HomeKeyService`; that service keeps
its existing ALL_APPS and Settings-key behavior. Both may be enabled when a
remote needs those keys, but only one service redirects stock Home. This also
keeps window events separate from the key-only changes proposed in #150.

The helper discovers the target package's declared HOME activities. Amazon's
known activity names remain configurable fallbacks. A package without a HOME
activity, a disabled/missing package or this launcher's own package is rejected.
It does not redirect arbitrary windows from the target package. Every target
uses the same 200 ms coalescing, cancellation when another app or stock utility
opens, immutable PendingIntent and three-request limit per rolling five seconds.
HOME remains with the stock launcher, so firmware may show it before redirecting.
There is no raw HOME-key capture or screen-content access.

**Choose default home > Use stock Home** saves the same persistent off switch
before opening the configured stock launcher. On Fire TV the button is named
**Use Amazon Home**. The off switch survives theme pushes; select **Enable** to
resume. Keep the stock launcher enabled. Validate real-remote Home, Back, cold
boot and intentional stock-Home use on each device. This integration has not
been tested on Nokia hardware.

## Remote buttons (optional)

Fire OS handles the remote's branded buttons (Prime Video, Amazon Music, a TV
maker's app) before any accessibility key filter sees them. On a TCL 58S571F
(Fire OS 8), InputKeyService never received the Music button (scancode 747)
while Fire OS opened its app. What Fire OS does leave behind is the launch
itself: each button starts the Appstore's `AppLaunchActivity` with a fixed
`amzns://` URI, from the system uid, and it shows up in the TV's own log.

`fire_tv_button_routes` in `values/config.xml` maps those launches to other
apps, one `source|package` per item. The source is the URI the button sends,
or the component it starts directly when its app is installed:

```xml
<string-array name="fire_tv_button_routes" translatable="false">
    <item>amzns://apps/android?p=com.amazon.bueller.music|com.spotify.tv.android</item>
    <item>amzns://apps/android?asin=B0CQF8SRMM|org.smarttube.stable</item>
</string-array>
```

To find a button's source, watch the log and press it:
`tools/tv.py 'logcat -c; sleep 8; logcat -d -s ActivityTaskManager:I | grep "START u0"'`.
Use the line that says `from uid 1000`. A button can log two lines (Prime
Video logs the Appstore request and the app's own start); route both to the
same package and the second is ignored.

This needs `READ_LOGS`, which Android only grants over ADB:
`tools/tv.py 'pm grant com.gathin.tvhome android.permission.READ_LOGS'`, then
disable and re-enable **Home redirect** in Accessibility. With no routes or
no grant, nothing reads the log. With both, a `logcat` process filtered to
`ActivityTaskManager` runs while the service is bound. Lines are matched in
memory and dropped; only system-uid launches that exactly match a route act.
Amazon's app may still open for a moment before the routed one replaces it.
On the TCL the routed app was started 23 to 82 ms after the button's launch
line.

To undo, empty the array, or revoke the grant with
`pm revoke com.gathin.tvhome android.permission.READ_LOGS`.

## Return to Amazon Home

In **Choose default home**, select **Use Amazon Home**. A local off switch is
saved before opening Amazon Home, so this works without a computer. To redirect
again, open this launcher from Amazon's app list and select **Enable**. The off
switch survives process death and is separate from theme/layout preferences.

For complete cleanup, disable **Home redirect** in Accessibility, or
remove only its short/full component from the current user's enabled-services
list over ADB. Preserve other services and the saved master state. A request
already accepted by Fire OS may still complete once when switching off; the
saved off switch blocks new requests.

After device work, turn debugging off, revoke authorization if offered, reboot
and confirm port 5555 is closed. The helper itself does not require ADB to run.
