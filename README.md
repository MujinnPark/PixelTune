# PixelTune
Non-root Android tuner using Shizuku, built for Pixel and extended to Samsung, Xiaomi and realme/OPPO/OnePlus devices.

## What it does
- **Scans the device first.** After Shizuku connects, one scan reads the vendor, Android API level, ROM and whether setting writes actually work. A tweak that does not fit the device is greyed out with the reason, and is refused if applied anyway.
- **Verifies after applying.** Settings are read back. A pack where none of the values stuck is shown as Rejected and not recorded as applied.
- **Reverts to what you had.** The previous value of every settings key is saved before a tweak touches it, and restored on revert. `device_config` flags and system properties are not snapshotted: flags are deleted back to their defaults, and properties reset on reboot.
- **Undoes itself after a reboot.** Tweaks PixelTune applied are reverted once after each reboot. The reboot is detected with the kernel boot id, falling back to the boot count and then uptime.
- **Vendor packs are experimental.** A device-specific pack only shows up on the matching vendor, and only if its `needs` probes pass.

## Packs
Pack contents live in `app/src/main/assets/packs.json`. Fields: `vendors`, `minSdk`, `maxSdk`, `needs` (`pkg:`, `prop:`, `setting:ns:key` probes).
- **Settings packs:** every key was checked against AOSP `Settings.java` and sits in its real namespace. Keys that AOSP does not define were removed.
- **System properties:** only `debug.hwui.renderer` is offered, because it is the one HWUI property verified as a real switch.
- **`device_config` flags** (the two system-flag packs) are NOT verified against source. They are accepted by Android, but nothing proves the flags change behaviour, especially off Pixel.
- Nothing here has a proven speed gain. Cleanup and privacy packs say so.
- Setup needed on some ROMs: Xiaomi/HyperOS needs "USB debugging (Security settings)"; ColorOS/realme UI needs "Permission monitoring" off or "Disable system optimization" on. The app shows this when writes are blocked.

## Build with GitHub (no Android Studio)
1. Push this project to a GitHub repo (use git, so the hidden `.github` folder is included).
2. Open the repo's **Actions** tab. "Build APK" runs on every push to `main` (or use **Run workflow**).
3. Open the finished run, download the `PixelTune-apk` artifact, unzip, and install `app-release.apk`.
4. To publish a Release with the APK attached: `git tag v1.0.0 && git push origin v1.0.0`.

## Use it
Install Shizuku, start it via Developer options > Wireless debugging (re-do after each reboot), open PixelTune, tap Grant access.

## Notes
- Release builds are signed with the debug key so they install directly. CI generates a new debug key on every run, so a new build cannot update the previous one: uninstall first. For a stable key, add a keystore and GitHub Secrets and a signingConfig.
- If the build fails, open the failed step in Actions and read the first red error.
