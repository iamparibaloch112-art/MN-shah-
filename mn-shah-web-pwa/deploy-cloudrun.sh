#!/usr/bin/env bash
set -euo pipefail

# Google Cloud Run deployment helper for MN SHAH AI PWA
# Set these environment variables before running, or pass them via Google Secret Manager:
#   PROJECT_ID, REGION (default: asia-southeast1), SERVICE_NAME (default: mn-shah-ai-pwa)
#   GOOGLE_CLIENT_ID, OWNER_PASSWORD, SESSION_SECRET, GEMINI_API_KEY

PROJECT_ID="${PROJECT_ID:?Set PROJECT_ID to your Google Cloud project ID}"
REGION="${REGION:-asia-southeast1}"
SERVICE_NAME="${SERVICE_NAME:-mn-shah-ai-pwa}"
ALLOWED_OWNER_EMAIL="${ALLOWED_OWNER_EMAIL:-iamparibaloch112@gmail.com}"

echo "Deploying $SERVICE_NAME to Cloud Run ($PROJECT_ID / $REGION)..."

gcloud run deploy "$SERVICE_NAME" \
  --project "$PROJECT_ID" \
  --region "$REGION" \
  --source . \
  --platform managed \
  --allow-unauthenticated \
  --port 8080 \
  --set-env-vars "NODE_ENV=production,ALLOWED_OWNER_EMAIL=${ALLOWED_OWNER_EMAIL},GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID:-}" \
  --set-secrets "OWNER_PASSWORD=mn-shah-owner-password:latest,SESSION_SECRET=mn-shah-session-secret:latest,GEMINI_API_KEY=mn-shah-gemini-key:latest"

echo "Deployment complete. Open the Service URL above in Android Chrome and tap 'Add to Home screen'."
