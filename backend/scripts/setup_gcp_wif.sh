#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"
REGION="${GCP_REGION:-europe-west1}"
GITHUB_REPOSITORY="errezeeta/fitbit-air-dashboard"
POOL_ID="fitbit-air-github"
PROVIDER_ID="github-actions"
DEPLOYER_SA="fitbit-air-deployer"
RUNTIME_SA="fitbit-air-runtime"

if [[ -z "$PROJECT_ID" || "$PROJECT_ID" == "(unset)" ]]; then
  printf 'Set GCP_PROJECT_ID or select a project in Cloud Shell first.\n' >&2
  exit 2
fi

if ! gcloud billing projects describe "$PROJECT_ID" --format='value(billingEnabled)' >/dev/null; then
  printf 'Could not read billing status for project %s; stopping without changes.\n' "$PROJECT_ID" >&2
  exit 2
fi
BILLING_ENABLED="$(gcloud billing projects describe "$PROJECT_ID" --format='value(billingEnabled)' | tr '[:upper:]' '[:lower:]')"
if [[ "$BILLING_ENABLED" != "true" ]]; then
  printf 'Billing is not enabled for %s. This script will not enable billing or deploy resources.\n' "$PROJECT_ID" >&2
  exit 2
fi

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
DEPLOYER_EMAIL="${DEPLOYER_SA}@${PROJECT_ID}.iam.gserviceaccount.com"
RUNTIME_EMAIL="${RUNTIME_SA}@${PROJECT_ID}.iam.gserviceaccount.com"

# API activation and IAM setup only; this script does not deploy a service or add secret values.
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  secretmanager.googleapis.com \
  health.googleapis.com \
  --project="$PROJECT_ID"

ensure_service_account() {
  local name="$1"
  local email="$2"
  if ! gcloud iam service-accounts describe "$email" --project="$PROJECT_ID" >/dev/null 2>&1; then
    gcloud iam service-accounts create "$name" \
      --project="$PROJECT_ID" \
      --display-name="Fitbit Air ${name#fitbit-air-}"
  fi
}

ensure_secret() {
  local name="$1"
  if ! gcloud secrets describe "$name" --project="$PROJECT_ID" >/dev/null 2>&1; then
    gcloud secrets create "$name" \
      --project="$PROJECT_ID" \
      --replication-policy=automatic
  fi
}

ensure_service_account "$DEPLOYER_SA" "$DEPLOYER_EMAIL"
ensure_service_account "$RUNTIME_SA" "$RUNTIME_EMAIL"

ensure_secret fitbit-air-google-client-secret
ensure_secret fitbit-air-app-signing-secret
ensure_secret fitbit-air-google-refresh-token

if ! gcloud iam workload-identity-pools describe "$POOL_ID" \
  --project="$PROJECT_ID" --location=global >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create "$POOL_ID" \
    --project="$PROJECT_ID" \
    --location=global \
    --display-name="Fitbit Air GitHub Actions"
fi

if ! gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project="$PROJECT_ID" --location=global \
  --workload-identity-pool="$POOL_ID" \
  --provider="$PROVIDER_ID" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers create-oidc "$PROVIDER_ID" \
    --project="$PROJECT_ID" \
    --location=global \
    --workload-identity-pool="$POOL_ID" \
    --display-name="Fitbit Air GitHub deploy" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
    --attribute-condition="assertion.repository == '${GITHUB_REPOSITORY}' && assertion.ref == 'refs/heads/main'"
fi

# The deployer builds from source (sourceDeveloper) and sets service IAM for
# --allow-unauthenticated (run.admin); run.admin alone has NO Artifact Registry
# or Cloud Build permissions, so both are required for a source deploy.
for role in roles/run.sourceDeveloper roles/run.admin roles/serviceusage.serviceUsageConsumer; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${DEPLOYER_EMAIL}" \
    --role="$role" \
    --quiet >/dev/null
done
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_EMAIL" \
  --project="$PROJECT_ID" \
  --member="serviceAccount:${DEPLOYER_EMAIL}" \
  --role="roles/iam.serviceAccountUser" \
  --quiet >/dev/null

# A source deploy builds with the Compute Engine default service account (the
# default build identity since the 2024 Cloud Build change), so it needs the
# builder role and the deployer must be able to act as it. Without both, the
# deploy fails with "caller does not have permission to act as service account".
COMPUTE_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${COMPUTE_SA}" \
  --role="roles/run.builder" \
  --quiet >/dev/null
gcloud iam service-accounts add-iam-policy-binding "$COMPUTE_SA" \
  --project="$PROJECT_ID" \
  --member="serviceAccount:${DEPLOYER_EMAIL}" \
  --role="roles/iam.serviceAccountUser" \
  --quiet >/dev/null

POOL_RESOURCE="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}"
gcloud iam service-accounts add-iam-policy-binding "$DEPLOYER_EMAIL" \
  --project="$PROJECT_ID" \
  --member="principalSet://iam.googleapis.com/${POOL_RESOURCE}/attribute.repository/${GITHUB_REPOSITORY}" \
  --role="roles/iam.workloadIdentityUser" \
  --quiet >/dev/null

for secret in fitbit-air-google-client-secret fitbit-air-app-signing-secret fitbit-air-google-refresh-token; do
  gcloud secrets add-iam-policy-binding "$secret" \
    --project="$PROJECT_ID" \
    --member="serviceAccount:${RUNTIME_EMAIL}" \
    --role="roles/secretmanager.secretAccessor" \
    --quiet >/dev/null
done
gcloud secrets add-iam-policy-binding fitbit-air-google-refresh-token \
  --project="$PROJECT_ID" \
  --member="serviceAccount:${RUNTIME_EMAIL}" \
  --role="roles/secretmanager.secretVersionAdder" \
  --quiet >/dev/null

PROVIDER_RESOURCE="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"
printf '\nPrepared IAM/WIF only; no Cloud Run service was deployed and no secret values were added.\n'
printf 'Project: %s\nRegion default: %s\nBilling enabled: %s\n' "$PROJECT_ID" "$REGION" "$BILLING_ENABLED"
printf 'GCP_PROJECT_ID=%s\n' "$PROJECT_ID"
printf 'GCP_REGION=%s\n' "$REGION"
printf 'GCP_WIF_PROVIDER=%s\n' "$PROVIDER_RESOURCE"
printf 'GCP_SERVICE_ACCOUNT=%s\n' "$DEPLOYER_EMAIL"
printf 'GCP_RUNTIME_SERVICE_ACCOUNT=%s\n' "$RUNTIME_EMAIL"
printf '\nBefore deployment, add a version to fitbit-air-google-client-secret in Secret Manager,\n'
printf 'and add an app signing secret with: openssl rand -hex 32 | gcloud secrets versions add fitbit-air-app-signing-secret --data-file=-\n'
printf 'Do not paste either secret into GitHub, chat, or a repository file.\n'
