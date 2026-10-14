# Fitbit Air backend

FastAPI service intended for Cloud Run. It keeps Google OAuth and Hermes credentials off the APK and repository.

## Security and data flow

- The app starts Google OAuth with PKCE. Cloud Run exchanges the code using the web-client secret and checks the allowlisted Google account.
- Google refresh tokens are stored only in Secret Manager. The app receives a signed, app-specific session token; it never receives the Google refresh token, Google client secret, or Hermes API key.
- The backend reads steps, sleep, and resting heart rate on demand. It does not write health metrics to a database.
- Hermes receives only the requested health summary through stateless `/v1/chat/completions`; the backend does not send Google tokens or email. Before sending metrics, it checks `/v1/toolsets` and refuses if any tools are enabled.
- Keep the Hermes API Server loopback-only. Cloud Run will need a separate HTTPS tunnel to reach it; configure that only after the API Server has a distinct key and `platform_toolsets.api_server: []` has been verified.

## Local tests

```sh
python3 -m venv backend/.venv
backend/.venv/bin/python -m pip install -r backend/requirements.txt
backend/.venv/bin/python -m unittest discover -s backend/tests -v
```

## Prepare Google Cloud (no deployment)

Use Cloud Shell while signed into the intended Google Cloud project. This script checks that billing is already enabled, enables required APIs, creates narrowly scoped service identities and GitHub Workload Identity Federation, and creates empty Secret Manager resources. It does **not** enable billing, add secret values, or deploy Cloud Run.

```sh
bash backend/scripts/setup_gcp_wif.sh
```

The script prints non-secret values to add as repository *Actions variables* in `errezeeta/fitbit-air-dashboard`:

- `GCP_PROJECT_ID`
- `GCP_REGION` (default `europe-west1`; change before deployment if needed)
- `GCP_WIF_PROVIDER`
- `GCP_SERVICE_ACCOUNT`
- `GCP_RUNTIME_SERVICE_ACCOUNT`

Also add these repository variables yourself:

- `GOOGLE_OAUTH_CLIENT_ID` — the existing OAuth web client ID
- `GOOGLE_ALLOWED_EMAIL` — the exact Google account allowed to use the app

Do not add secrets as GitHub variables. In Secret Manager, create a version for `fitbit-air-google-client-secret` using the OAuth web-client secret. Create the session-signing secret without displaying it:

```sh
openssl rand -hex 32 | gcloud secrets versions add fitbit-air-app-signing-secret --data-file=-
```

The refresh-token secret is intentionally empty until the first successful Google OAuth connection; Cloud Run adds its first version after consent.

## Manual deployment

The workflow `.github/workflows/deploy-backend.yml` is manual-only and first runs the backend tests. It requires the WIF variables above, existing versions of the two initial secrets, and the exact phrase `DEPLOY` in its dispatch form. It uses scale-to-zero and a maximum of one instance. Cloud Run can still incur charges; review the project's billing and estimated usage before dispatching.

After the first deploy, copy the printed Cloud Run URL's `/oauth/google/callback` URI into the Google OAuth web client’s authorized redirect URIs. The workflow also sets that URI on the service.

## Hermes, second stage

The service returns a clear `503` from `/api/assistant` until `HERMES_API_BASE_URL` and `HERMES_API_SERVER_KEY` are configured server-side. Do not expose the Hermes API Server directly to the phone. Before wiring the tunnel, restrict its API server toolsets to an explicit empty list, restart Hermes, and verify `/v1/toolsets` shows no enabled tools. The backend enforces that check on every assistant request.
