# Google Play Tagged Release Runbook

This runbook adds Google Play internal-track publishing to the existing GitHub tagged-release flow. A semantic tag like `v0.0.2` now drives both channels from the same commit.

## Release Outputs

For each pushed tag matching `v*.*.*`:

- `.github/workflows/release-apk-tag.yml` publishes `vr2xr.apk` to GitHub Releases
- `.github/workflows/release-play-tag.yml` uploads a signed App Bundle to Play `internal` track

## One-Time Setup

### 1) Play Console readiness

Create the app in Play Console and complete all required dashboard sections (app content, data safety, content rating, ads declaration, privacy policy, and testing setup as required by your account type).

If this is the first upload for the app, Play requires a manual upload in the Play Console before API-based automation works.

### 2) Generate upload keystore

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/vr2xr-upload.jks \
  -alias vr2xr-upload \
  -keyalg RSA -keysize 4096 -validity 10000
keytool -export -rfc \
  -keystore keystore/vr2xr-upload.jks \
  -alias vr2xr-upload \
  -file keystore/vr2xr-upload-cert.pem
```

Register the upload certificate in Play App Signing if Play prompts for it.

### 3) Configure Google Cloud Workload Identity Federation

```bash
export GCP_PROJECT_ID="<your-project-id>"
export GCP_PROJECT_NUMBER="<your-project-number>"
export GITHUB_ORG="Skarian"
export GITHUB_REPO="Skarian/vr2xr"
export WIF_POOL_ID="github"
export WIF_PROVIDER_ID="vr2xr"
export SA_NAME="vr2xr-play-publisher"
export SA_EMAIL="${SA_NAME}@${GCP_PROJECT_ID}.iam.gserviceaccount.com"

gcloud iam service-accounts create "${SA_NAME}" \
  --project="${GCP_PROJECT_ID}" \
  --display-name="vr2xr Play Publisher"

gcloud iam workload-identity-pools create "${WIF_POOL_ID}" \
  --project="${GCP_PROJECT_ID}" \
  --location="global" \
  --display-name="GitHub Actions Pool"

gcloud iam workload-identity-pools providers create-oidc "${WIF_PROVIDER_ID}" \
  --project="${GCP_PROJECT_ID}" \
  --location="global" \
  --workload-identity-pool="${WIF_POOL_ID}" \
  --display-name="vr2xr GitHub Provider" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
  --attribute-condition="assertion.repository_owner == '${GITHUB_ORG}'"

export WORKLOAD_IDENTITY_POOL_ID="projects/${GCP_PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL_ID}"

gcloud iam service-accounts add-iam-policy-binding "${SA_EMAIL}" \
  --project="${GCP_PROJECT_ID}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/${WORKLOAD_IDENTITY_POOL_ID}/attribute.repository/${GITHUB_REPO}"

gcloud services enable \
  androidpublisher.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  iam.googleapis.com \
  serviceusage.googleapis.com \
  --project="${GCP_PROJECT_ID}"
```

Capture provider name:

```bash
gcloud iam workload-identity-pools providers describe "${WIF_PROVIDER_ID}" \
  --project="${GCP_PROJECT_ID}" \
  --location="global" \
  --workload-identity-pool="${WIF_POOL_ID}" \
  --format="value(name)"
```

Expected format:

`projects/<number>/locations/global/workloadIdentityPools/<pool>/providers/<provider>`

### 4) Connect Play Console API access

In Play Console:

- Link Play Console to the same Google Cloud project
- Grant the service account permissions to manage internal releases for this app
- Confirm app policy pages are not blocking internal releases

### 5) Add GitHub variables and secrets

Repository variables:

- `GCP_PROJECT_ID`
- `GCP_WIF_PROVIDER`
- `GCP_WIF_SERVICE_ACCOUNT`

Repository secrets:

- `VR2XR_UPLOAD_KEYSTORE_B64`
- `VR2XR_UPLOAD_STORE_PASSWORD`
- `VR2XR_UPLOAD_KEY_ALIAS`
- `VR2XR_UPLOAD_KEY_PASSWORD`

Keystore base64 helper:

```bash
base64 < keystore/vr2xr-upload.jks | tr -d '\n'
```

### 6) First manual Play upload (required once)

Build and upload one signed release bundle manually in Play Console internal track:

```bash
export VR2XR_UPLOAD_STORE_FILE="$PWD/keystore/vr2xr-upload.jks"
export VR2XR_UPLOAD_STORE_PASSWORD="<redacted>"
export VR2XR_UPLOAD_KEY_ALIAS="vr2xr-upload"
export VR2XR_UPLOAD_KEY_PASSWORD="<redacted>"
export VR2XR_VERSION_NAME="0.0.1"
export VR2XR_VERSION_CODE="1"
./gradlew :app:bundleRelease
```

Upload `app/build/outputs/bundle/release/app-release.aab` through the Play Console UI internal track and confirm it is accepted.

## Per-Release Steps

1. Ensure `main` is in the desired release state
2. Create and push semantic tag:

```bash
git tag -a v0.0.2 -m "Release v0.0.2"
git push origin v0.0.2
```

3. Validate both Actions runs succeed:
   - `Publish Tagged APK Release`
   - `Publish Tagged Play Release`
4. Validate GitHub release has `vr2xr.apk`
5. Validate Play internal release version and notes

## Versioning Rules

`tools/release/derive_android_version.sh` maps tag to Android values:

- `versionName = MAJOR.MINOR.PATCH`
- `versionCode = MAJOR * 1000000 + MINOR * 1000 + PATCH`

Example: `v1.2.3` -> `versionName=1.2.3`, `versionCode=1002003`

## Automated Play Notes

`tools/release/generate_play_notes.sh` writes:

`app/src/main/play/release-notes/en-US/default.txt`

The script builds notes from commit subjects between the previous semantic tag and the current tag, with a hard 500-character limit for Play compliance.

## Troubleshooting

- Missing signing env vars: workflow fails in Gradle configuration. Verify secrets and keystore decode step
- Tag format rejected: tag must match `vMAJOR.MINOR.PATCH`
- WIF auth failure: verify `id-token: write`, provider string, repository principal binding, and API enablement
- `publishReleaseBundle` denied: verify service account Play app permissions and Play Console project linkage
- Version code already used: create a new higher semantic tag and republish

## Rollback Guidance

- If GitHub release is wrong and Play upload failed: delete the bad tag and push a corrected new tag
- If Play upload succeeded with bad build: halt rollout in Play Console and publish a hotfix tag
