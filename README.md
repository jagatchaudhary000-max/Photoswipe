# Photo Swipe: an Android app for cleaning up your gallery

Swipe **right to keep** a photo or video, and swipe **left to delete** it. Nothing is deleted
until you open **Review** and confirm, so a wrong swipe can always be undone.

## Features
- One card at a time with Tinder-style swipe, plus Keep / Undo / Delete buttons
- **Categories**: All, Photos, Videos, and every folder on the phone
  (Camera, Screenshots, WhatsApp Images, Downloads …), with the biggest folders first
- Shows the date, size and folder of each item; tap a video to play it
- Kept items stay hidden next time; "Start over" shows them again
- **Review screen**: a grid of everything marked for deletion, the space you'll free,
  and tap-to-rescue for anything you change your mind about
- **Move to trash** (recoverable for about 30 days) or **Delete forever**, both through
  Android's own confirmation dialog
- Everything stays on the phone. The app has no internet permission.

Needs **Android 11 or newer**.

## Run it on your phone
1. Install **Android Studio** (free) from developer.android.com/studio.
2. Unzip `PhotoSwipe.zip`, then in Android Studio choose **File → Open** and select the `PhotoSwipe` folder.
   Wait for "Gradle sync" to finish. The first sync downloads libraries, so it takes a few minutes.
   If Android Studio suggests upgrading the Gradle plugin, it's fine to accept.
3. On your phone, turn on **Developer options**: Settings → About phone → tap
   **Build number** 7 times. Then in Developer options, turn on **USB debugging**.
4. Connect the phone by USB, pick it in the device dropdown at the top, and press the green **Run ▶** button.
5. On the phone, tap **Allow access**, then choose **Allow all** so every photo is included.

To get an APK you can install or share without the cable, use
**Build → Build App Bundle(s)/APK(s) → Build APK(s)**.

## Code map
| File | What it does |
|---|---|
| `MediaRepository.kt` | Reads photos and videos from the gallery and saves your keep/delete choices |
| `SwipeViewModel.kt` | App logic: queue, categories, undo, rescue, delete |
| `ui/SwipeScreen.kt` | Swipe cards, category chips, buttons |
| `ui/ReviewScreen.kt` | Grid of items to delete and the final delete buttons |
| `ui/App.kt` | Permissions, screen switching, Android delete dialog |

## Ideas for later
- Duplicate and blurry photo detection
- Swipe up to move a photo into an album
- Sort by "largest first" to free space faster
