# Publishing Recall Gallery on Google Play

App ID: `com.prasanglabs.recall` (permanent). Store name: **Recall Gallery – Photo Search**.

Everything in the repo is ready; the steps below are the parts only you can do in Google's web pages. They all work from a phone browser, though a laptop is easier for the long forms.

## 1. One-time setup (about 30 minutes, plus Google's review)

1. **Create a developer account** at <https://play.google.com/console/signup> (one-time fee of US$25). Choose a **personal** account unless Prasang Labs is a registered business; an organization account needs a D-U-N-S number but skips the tester rule in step 4.
2. **Verify your identity** when Play Console asks (ID document; takes a few days).
3. **Turn on GitHub builds of the Play bundle**: in GitHub, open the repo → **Settings → Secrets and variables → Actions → New repository secret**.
   - Name: `UPLOAD_KEY_PASSWORD`
   - Value: the password in the `README-UPLOAD-KEY.txt` file you were given.

   The next push builds `recall-gallery.aab`, signed with your upload key, and attaches it to the GitHub release.
4. **Publish the privacy policy page**: repo → **Settings → Pages** → *Build and deployment* → Source **Deploy from a branch** → Branch **main**, folder **/docs** → Save. After a minute it's live at `https://mudittiwari255.github.io/recall-gallery/privacy-policy.html`.

## 2. Create the app in Play Console

**Create app** → name *Recall Gallery – Photo Search*, default language English (India or US), **App**, **Free**. Accept the declarations.

Then work through the **Dashboard → Set up your app** list:

| Item | What to enter |
| --- | --- |
| **Privacy policy** | `https://mudittiwari255.github.io/recall-gallery/privacy-policy.html` |
| **App access** | All functionality is available without special access |
| **Ads** | No, the app has no ads |
| **Content rating** | Category: *Utility, productivity, communication, or other*. Answer **No** to every question (no violence, sexual content, gambling, user interaction or sharing of location). Result: rated for everyone. |
| **Target audience** | 18 and over (simplest; the app is a general tool). Not designed for children. |
| **News app** | No |
| **Data safety** | See below |
| **Government app** | No |
| **Financial features** | None |
| **Health** | None |

### Data safety answers

- Does your app collect or share any of the required user data types? **No.**
  Reason: photos and the numbers derived from them are processed only on the device and never transmitted. Google's definition of "collect" means sent off the device.
- Is all user data encrypted in transit? Not applicable (no user data is transmitted). If the form insists, answer **Yes** (the model download uses HTTPS).
- Do you provide a way for users to request data deletion? **Yes**: Settings → *Remove search engine and index*, or uninstall.

### Permission declarations (Play asks after your first upload)

**Photo and video permissions** (`READ_MEDIA_IMAGES`): choose that the app's core purpose needs broad access to photos.
> Recall Gallery is a photo gallery whose core feature is searching the user's whole photo library by description. It must read every photo to show the gallery and to build the on-device search index. Photos never leave the device. A one-time picker cannot provide this, because search has to cover the whole library and new photos are added automatically.

**Foreground service** (`dataSync`):
> Used while the app prepares the user's photos for search (on-device processing of local files) and while it downloads its on-device search model. The user starts it and sees a progress notification with a Pause button; it stops when the work is done.
> Video: record 20-30 seconds of the screen showing the setup card and the progress notification, upload it to YouTube as *Unlisted*, and paste the link.

## 3. Store listing (Grow → Store presence → Main store listing)

Texts and graphics are in `fastlane/metadata/android/en-US/`:

- **App name**: `title.txt`
- **Short description**: `short_description.txt`
- **Full description**: `full_description.txt`
- **App icon** (512 × 512): `images/icon.png`
- **Feature graphic** (1024 × 500): `images/featureGraphic.png`
- **Phone screenshots**: at least 2, ideally 4–6. Take them on your phone:
  1. The Search tab with a few results for a real query
  2. The welcome screen "Private by design"
  3. The Filters panel with the year slider
  4. A photo open with *Find similar photos*
  5. The All photos tab
- **App category**: Photography. **Tags**: Photo gallery, Search.
- **Contact email**: required by Google; it is shown publicly on the store page.

## 4. Testing, then production

New **personal** accounts (created after 13 Nov 2023) must run a **closed test with at least 12 testers who stay opted in for 14 days** before they can publish to everyone.

1. **Testing → Closed testing → Create track**, add testers by email (a Google Group is easiest), upload `recall-gallery.aab` from the latest GitHub release, roll out.
2. Share the opt-in link; each tester must accept and install from Play.
3. After 14 days, **Dashboard → Apply for production**, answer the questions about your test, and wait for review (usually a few days).
4. **Production → Create new release** with the latest `.aab`, roll out.

The **very first upload must be done by hand** in Play Console. It also registers your upload key: Play App Signing is on by default, and Google keeps the key that signs the app for users.

## 5. Optional: automatic uploads from GitHub

After the first manual upload, every push can send the new bundle to the **Internal testing** track automatically:

1. Google Cloud Console → create a project → **IAM & Admin → Service accounts** → create one → **Keys → Add key → JSON**, and download it.
2. Play Console → **Users and permissions → Invite new user** → the service account's email → give it **Release apps to testing tracks** for this app.
3. GitHub → Secrets → new secret `PLAY_SERVICE_ACCOUNT_JSON` with the whole JSON file as its value.
4. Releases arrive as drafts until the app has been published once. After that, add a repository **variable** `PLAY_RELEASE_STATUS` = `completed` so internal releases roll out by themselves.

## Notes

- **Two signing keys.** GitHub-release APKs are signed with the public sideload key in `keystore/recall.jks`; Play versions are signed by Google. Android won't update one with the other, so testers should uninstall the sideloaded app before installing from Play.
- **Version numbers** come from the GitHub run number, so every build is higher than the last, as Play requires.
- **Target SDK** is 36 (Android 16), as Google Play requires for new apps from 31 Aug 2026.
