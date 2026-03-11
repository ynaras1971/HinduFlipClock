DEPLOY TO ANDROID PHONE/TABLET
================================

STEP 1 — Install Android Studio
---------------------------------
1. Download from https://developer.android.com/studio
2. Run the installer, accept all defaults
3. On first launch, let it download the Android SDK (takes ~5 min)

STEP 2 — Open this project
----------------------------
1. In Android Studio: File > Open
2. Navigate to: C:\Users\ynara\HinduFlipClock
3. Click OK and wait for Gradle sync to finish (~2 min first time)
   - If it asks to upgrade Gradle, click "Don't remind me again" or accept

STEP 3 — Get a free weather API key
-------------------------------------
1. Go to https://openweathermap.org/
2. Click "Sign In" > "Create an Account" (free)
3. After signing in, go to your Profile > "My API Keys"
4. Copy the default API key (it may take 10-30 min to activate)

STEP 4 — Enable Developer Mode on your phone/tablet
-----------------------------------------------------
Android phone:
1. Go to Settings > About Phone
2. Tap "Build Number" 7 times quickly
3. You'll see "You are now a developer!"
4. Go back to Settings > Developer Options
5. Enable "USB Debugging"

Tablet: same steps, may be under Settings > About Tablet

STEP 5 — Connect your device and run
--------------------------------------
1. Connect your phone/tablet to your PC with a USB cable
2. On your phone, accept the "Allow USB Debugging?" prompt
3. In Android Studio, in the toolbar you'll see a device dropdown
   - Your phone should appear (e.g. "Samsung Galaxy Tab ...")
   - If it shows "No devices", check USB cable and try a different port
4. Click the green Play button (▶) or press Shift+F10
5. Android Studio will build and install the app — takes 1-2 min
6. The app launches automatically on your device

STEP 6 — Configure the app
----------------------------
Once the app is running:
1. Tap the settings icon (top right corner)
2. Enter your city: e.g. "Mumbai, IN" or "New York, US"
3. Paste your OpenWeatherMap API key
4. Choose °C or °F
5. Enable/disable chimes
6. Tap Save

STEP 7 — Keep it running as a clock display
---------------------------------------------
The app keeps the screen on automatically.
For best results as a permanent display:
- Go to Settings > Display > Screen Timeout → set to "Never" or maximum
- Plug the device into a charger
- Place in landscape orientation

TROUBLESHOOTING
================
"Gradle sync failed":
  - Make sure you have internet access
  - Try File > Invalidate Caches and Restart

"Device not recognized":
  - Install your phone manufacturer's USB driver for Windows
    (Samsung: Samsung USB Driver; Google Pixel: Google USB Driver)
  - Try a different USB cable (must be data cable, not charge-only)

"App crashes on launch":
  - Check Logcat in Android Studio for error details

"Weather not showing":
  - API key takes 10-30 min to activate after creation
  - Make sure the city format is correct: "Mumbai, IN"

HOW THE APP WORKS
==================
- Screen stays on permanently (FLAG_KEEP_SCREEN_ON)
- Flip clock updates every second with animated digit flip
- Date shown below clock in DD-MMM-YYYY format
- Weather fetched from OpenWeatherMap every 10 minutes
- Chimes: soft bell tone at :00, :15, :30, :45 past each hour
- Hindu calendar (Panchang):
  - Samvatsara (60-year Jovian cycle name)
  - Masa (lunar month, Amanta system)
  - Paksha + Tithi (lunar day, Shukla/Krishna)
  - Festivals and observances for the day
