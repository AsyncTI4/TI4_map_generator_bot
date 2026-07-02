# Website Integration

This document describes how the TI4 Map Generator Bot integrates with the AsyncTI4 website UI.

## Game Webhooks

Trusted external integrations can subscribe to non-FoW game changes without polling. Server operators create `webhook_user` rows with the callback URL and SHA-256 API key hash.

Supported events:

- `TURN_CHANGED`
- `PHASE_CHANGED`

Endpoints:

- `GET /api/public/game/webhook/eventTypes` returns supported event names, trigger descriptions, delivery settings, and payload fields.
- `PUT /api/public/game/{gameName}/webhook` with `X-API-Key` and JSON body:

```json
{ "eventTypes": ["TURN_CHANGED", "PHASE_CHANGED"] }
```

- `DELETE /api/public/game/{gameName}/webhook` with `X-API-Key`

Example `TURN_CHANGED` payload:

```json
{
  "gameName": "pbd1234",
  "eventType": "TURN_CHANGED",
  "phaseOfGame": "action",
  "round": 5,
  "activePlayerId": "123456789012345678",
  "activeFaction": "arborec",
  "timestamp": "2026-06-02T14:30:00Z"
}
```

Example `PHASE_CHANGED` payload:

```json
{
  "gameName": "pbd1234",
  "eventType": "PHASE_CHANGED",
  "previousPhaseOfGame": "strategy",
  "phaseOfGame": "agenda",
  "round": 4,
  "timestamp": "2026-06-02T14:30:00Z"
}
```

Delivery is asynchronous. Webhook failures never block game flow; transient failures may be retried once, and permanent 4xx failures are not retried.

## Image Asset Pipeline

Game assets (faction banners, planet images, system tiles, etc.) are automatically uploaded to a CloudFront-backed S3 bucket and served to the website.

### How It Works

```
Local PNG/JPG Files (src/main/resources)
    ↓
GitHub Actions (on push to master)
    ↓
Convert to WebP + Upload to S3
    ↓
CloudFront CDN (images.asyncti4.com)
    ↓
Website UI
```

### Automated Process

When changes are pushed to the `master` branch:

1. GitHub Actions scans the S3 bucket to see what images already exist
2. Only NEW images are converted from PNG/JPG to WebP format
3. Converted images are uploaded to `bot-images-asyncti4.com` S3 bucket
4. Images are immediately available via CloudFront at `https://images.asyncti4.com/`

**Workflow**: `.github/workflows/convert-and-upload-images.yml`
**Script**: `scripts/convert_and_upload_to_s3.py`

### Adding New Images

1. Add PNG/JPG files to `src/main/resources/` in the appropriate subdirectory
2. Create a pull request (images won't be uploaded yet)
3. After merging to master, images are automatically converted and uploaded
4. Website can access them at `https://images.asyncti4.com/{path}/{filename}.webp`

### Manual Upload

For testing or one-off uploads:

```bash
pip install -r scripts/requirements.txt
python scripts/convert_and_upload_to_s3.py
```

### Infrastructure

AWS infrastructure is managed via Terraform in the `aws-resources` repository. The setup uses OIDC for keyless authentication via the `GithubActionsRole` IAM role.
