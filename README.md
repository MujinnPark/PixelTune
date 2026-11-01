# PixelTune
Non-root Android tuner using Shizuku, built for Pixel and extended to Samsung, Xiaomi, vivo/iQOO and realme/OPPO/OnePlus devices.

## What it does
- **Scans the device first.** After Shizuku connects, one scan reads the vendor, Android API level, ROM and whether setting writes actually work. A tweak that does not fit the device is greyed out with the reason, and is refused if applied anyway.
- **Verifies after applying.** Settings are read back. A pack where none of the values stuck is shown as Rejected and not recorded as applied.
- **Reverts to what you had.** The previous value of every settings key is saved before a tweak touches it, and restored on revert. `device_config` flags and system properties are not snapshotted: flags are deleted back to their defaults, and properties reset on reboot.
- **Undoes itself after a reboot.** Tweaks PixelTune applied are reverted once after each reboot. The reboot is detected with the kernel boot id, falling back to the boot count and then uptime.
- **Vendor toggles and packs are experimental.** A device-specific pack only shows up on the matching vendor, and only if its `needs` probes pass.

## Packs
Pack contents live in `app/src/main/assets/packs.json`. Fields: `vendors`, `minSdk`, `maxSdk`, `needs` (`pkg:`, `prop:`, `setting:ns:key` probes).
- **Settings packs:** every key was checked against AOSP `Settings.java` and sits in its real namespace. Keys that AOSP does not define were removed.
- **System properties:** only `debug.hwui.renderer` is offered, because it is the one HWUI property verified as a real switch.
- **`device_config` flags** (the system-flag packs) are NOT verified against source. Android only lets the shell user write allowlisted flags: on a Pixel 6 Pro, 18 of 21 boost/freezer flags were rejected with "must add flag to the allowlist". Accepted does not prove the flag changes behaviour.
- **vivo/iQOO:** detected (vendor `vivo`) and given a setup hint, but no vivo-specific tweaks ship yet. Vendor keys need to be read from a real device first.
- **Game profile:** one game at a time. Applies Android's Game Mode (`cmd game mode performance`, `cmd game set --mode 2 --downscale X --fps Y`) and reverts with `cmd game reset`. Render scale is the part that cuts GPU load; some games opt out, and the FPS cap needs a screen that supports it.
- **Graphics driver (ANGLE):** optional per-game choice in the Game profile: Don't change, ANGLE, or Native. It writes `angle_gl_driver_selection_pkgs` and `angle_gl_driver_selection_values` (the keys and values in AOSP's ANGLE docs), merging with other apps' entries and restoring this game's previous entry on Reset game. It only affects OpenGL ES games, takes effect when the game next starts, and is unmeasured: no speed claim.
- **Measure FPS:** reads the game's own surface from `dumpsys SurfaceFlinger --timestats`, not `gfxinfo` (which does not see game frames). The output format was not checked against AOSP source, so the raw lines go to the log; "No reading" means the format or the game layer did not match.
  - Each reading is a 4-second sample and is saved to `Download/PixelTune/pixeltune_fps_<time>.txt` with the tweaks that were on. One reading proves nothing: to test a tweak, take 5 readings with it off and 5 with it on, in the same scene, then repeat in the opposite order and compare averages and lowest values. A single off/on pair on a Pixel 6 Pro (26 vs 30 FPS) was too noisy to count as a gain.
- **Fixed performance mode (risky) and Samsung GOS removal (risky):** both are off by default, have a revert, and are marked unmeasured. GOS removal survives reboots, so the reset-on-boot pass skips it; switch it off in the app to restore GOS.
- **Not ported from other optimizers:** thermal override (reports a hotter status, it does not stop throttling), auto-clicker and crosshair overlay (anti-cheat risk), kill-all and cache wipes as "boosts".
- **Refresh-rate check:** after a refresh-rate tweak is applied, PixelTune logs what `dumpsys display` reports (`renderFrameRate`). This is informational and never changes the Applied status; it may read "unreadable" on ROMs that do not print it.
- **Xiaomi refresh key:** `miui_refresh_rate` (secure) is not in AOSP. It is only offered on Xiaomi devices where the key already exists, and the previous value is restored on revert.
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
