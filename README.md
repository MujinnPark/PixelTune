# PixelTune
Non-root Pixel tuner using Shizuku. Every tweak has a revert. Clean-room rewrite; vendor-specific (vivo/MTK/Rakuten/Samsung-style) keys, placebo keys, and conflicting/unsafe entries were removed.

## Build with GitHub (no Android Studio)
1. Create a repo at github.com/new named `PixelTune`.
2. Unzip this project. In that folder (the hidden `.github` folder MUST be included, so use git, not drag-and-drop):
   git init && git add . && git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/PixelTune.git
   git push -u origin main
3. Open the repo's **Actions** tab. "Build APK" starts automatically (or **Run workflow**). ~5 min first time.
4. Open the finished run, scroll to **Artifacts**, download `PixelTune-apk`, unzip, install `app-release.apk`.
5. To publish a Release with the APK attached: `git tag v1.0.0 && git push origin v1.0.0`.

## Use it
Install Shizuku, start it via Developer options > Wireless debugging (re-do after each reboot), open PixelTune, tap Grant access.

## Notes
- Release builds are signed with the debug key so they install directly. For a stable key, add a keystore + GitHub Secrets and a signingConfig.
- If the build fails, open the failed step in Actions and read the first red error.
