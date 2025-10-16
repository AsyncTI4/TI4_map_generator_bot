# Website Integration

This document describes how the TI4 Map Generator Bot integrates with the AsyncTI4 website UI.

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
