# Pulse Dialer

Pulse Dialer is a Java-only Android phone and contacts interface built with Android Views, XML layouts, AndroidX, RecyclerView, Material Components and Groovy Gradle. It has no Internet permission, account, analytics, advertising or remote data service. Contacts and call history are read from Android providers on-device.

## Implemented features

- Midnight Blue dark theme by default, light theme, and follow-system mode.
- Recents backed by `CallLog.Calls`, with All, Missed, Incoming, Outgoing and Rejected filters, search, call type, date and duration. Access is gated on the app being the default phone app and holding call-log permission.
- Contacts backed by `ContactsContract`, including phone-number rows, entries without phone numbers, available contact photos, initials, alphabetical ordering, an A–Z jump index, search, provider-backed favorites, and add/edit/share/delete actions.
- Dialpad with editing, long-press clear, contact suggestions using local T9 mapping, system `ACTION_DIAL` fallback, and selectable Telecom accounts when more than one call-capable account is reported.
- SIM Board showing actual call-capable phone accounts reported by Telecom. It does not treat account order as a SIM slot number or claim that all device contacts live on a SIM.
- Default phone role request, `ACTION_DIAL` handling, `InCallService`, incoming call notification with answer/decline actions, and an in-call screen driven by Telecom call state.
- Settings for theme, accent, list density and keypad haptics, plus privacy and app-version information.
- Unit tests for T9 mapping and matching.

## Open and build

### Android Studio

In Android Studio, choose **Open** and select the project root containing `settings.gradle` (this directory). Let Gradle sync with the checked-in wrapper; in **Settings → Build, Execution, Deployment → Build Tools → Gradle**, select the **Gradle wrapper** and an embedded JDK 17 or newer. In **SDK Manager**, install Android SDK Platform 37 and Android SDK Build-Tools 36.0.0. Then select the `app` run configuration and the `debug` build variant and press Run. The Gradle configuration and build were verified from this project root with the same wrapper Android Studio imports.

The project uses Android Gradle Plugin 9.4.1, Gradle 9.7.1, compile and target SDK 37, and min SDK 26. Android Gradle Plugin 9.4 supports API 37 and requires Gradle 9.6.0 or newer. The newer Android Gradle Plugin 9.6.0 could not be resolved from the configured repositories in this environment, so the project uses the compatible plugin already installed in the local Gradle cache.

From the project directory, build the debug APK:

```sh
./gradlew clean assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Permissions and default phone role

Permissions are requested when a feature needs them. Contact browsing asks for Contacts access. Changing favorites or deleting a contact also needs Contacts write access. The SIM Board and the keypad's optional account selector ask for Phone state access to read Telecom's call-capable account list. Making a call from Pulse as the default dialer asks for Phone permission; otherwise the keypad opens the system dialer with `ACTION_DIAL`.

To enable Recents, open Settings → Default phone app → Set as default dialer and accept Android's system role confirmation. Then allow call history and incoming-call notification access when prompted. Android restricts call-log reads to eligible default phone handlers; Recents explains this restriction when the role or permission is missing. If the role is later removed, the app stops querying the call log.

## SIMs and SIM contacts

SIM Board and the keypad use Telecom's call-capable `PhoneAccountHandle` list and labels. These accounts are not assumed to be in SIM-slot order. If the device supplies two or more selectable accounts, the keypad provides separate account buttons as well as an account chooser. This behavior must be verified on the intended dual-SIM device.

SIM-stored contacts are not directly created, edited or deleted by this app. Android and device manufacturers vary in whether the Contacts provider exposes SIM-origin metadata or a SIM-management interface. The SIM Board explains that limitation rather than treating ordinary address-book contacts as SIM contacts.

## Tests and device verification

Run the requested build, unit tests and lint with:

```sh
./gradlew clean assembleDebug testDebugUnitTest lint
```

On 2026-10-29, `./gradlew clean assembleDebug testDebugUnitTest lint` completed successfully: the debug APK built, all five Java unit tests passed, and lint completed with 30 warnings and no errors. The Java unit tests exercise T9 mapping and matching. ADB reported no attached device or emulator in this workspace, so the app was not installed or launched. Actual call placement, incoming-call presentation, role grant behavior, provider permissions, and SIM account selection still need device verification. No calls are initiated by automated tests.

## Known limits

- Android/OEM policy controls call-log availability and may restrict access to SIM-origin contact metadata.
- Telecom call account labels are used as provided; the app does not infer carrier, slot number, or subscription identity from an account handle.
- Contact creation and editing use Android's system contact editor. Direct favorite and delete writes require Contacts write permission.
- The in-call implementation operates on the Telecom call currently surfaced to the UI; call-waiting and simultaneous multi-call controls need verification on devices that expose them.
- Full-screen incoming call UI and notification presentation depend on the OS, lock-screen settings, notification policy, and default-phone role.
