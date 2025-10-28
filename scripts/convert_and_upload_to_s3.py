#!/usr/bin/env python3
"""
Unified script to convert images to WebP and upload to S3.
- Converts local PNG/JPG files to WebP if they don't already exist in S3
- Copies existing WebP files if they don't already exist in S3
This avoids unnecessary conversion work when the asset is already on the server.
"""

import os
import sys
import argparse
from pathlib import Path
import boto3
from botocore.exceptions import NoCredentialsError, ClientError
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
import mimetypes
import shutil
from PIL import Image

# Set up logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# Configuration
DEFAULT_BUCKET = "bot-images-asyncti4.com"
MAX_WORKERS = 10  # Number of concurrent uploads


def setup_s3_client():
    try:
        session = boto3.Session()
        s3_client = session.client("s3")
        return s3_client
    except NoCredentialsError:
        logger.error("AWS credentials not found. Please configure your credentials.")
        logger.error("You can use: aws configure, environment variables, or IAM roles")
        sys.exit(1)
    except Exception as e:
        logger.error(f"Failed to initialize S3 client: {e}")
        sys.exit(1)


def bucket_exists(s3_client, bucket_name):
    try:
        s3_client.head_bucket(Bucket=bucket_name)
        return True
    except ClientError as e:
        if e.response["Error"]["Code"] != "404":
            logger.error(f"Error checking bucket {bucket_name}: {e}")
        return False


def compute_local_md5(file_path):
    """Compute MD5 hash of a local file to match S3 ETag format."""
    import hashlib

    md5_hash = hashlib.md5()
    try:
        with open(file_path, "rb") as f:
            # Read in chunks to handle large files efficiently
            for chunk in iter(lambda: f.read(8192), b""):
                md5_hash.update(chunk)
        # Return hex digest (S3 ETags are hex strings)
        return md5_hash.hexdigest()
    except Exception as e:
        logger.error(f"Failed to compute MD5 for {file_path}: {e}")
        return None


def get_all_s3_keys(s3_client, bucket_name, prefix="", with_etags=False):
    """Fetch all S3 keys from the bucket.

    Args:
        s3_client: Boto3 S3 client
        bucket_name: S3 bucket name
        prefix: Optional key prefix to filter results
        with_etags: If True, return dict with ETags; if False, return set of keys only

    Returns:
        dict or set: If with_etags=True, returns dict mapping S3 key to ETag (MD5 hash without quotes).
                     If with_etags=False, returns set of S3 keys only.
    """
    all_keys = {} if with_etags else set()
    continuation_token = None

    try:
        while True:
            list_params = {"Bucket": bucket_name, "MaxKeys": 1000}

            if prefix:
                list_params["Prefix"] = prefix

            if continuation_token:
                list_params["ContinuationToken"] = continuation_token

            response = s3_client.list_objects_v2(**list_params)

            if "Contents" in response:
                for obj in response["Contents"]:
                    if with_etags:
                        # Store key with its ETag (strip quotes from ETag)
                        etag = obj.get("ETag", "").strip('"')
                        all_keys[obj["Key"]] = etag
                    else:
                        # Just store the key
                        all_keys.add(obj["Key"])

            if not response.get("IsTruncated"):
                break

            continuation_token = response.get("NextContinuationToken")

        return all_keys

    except ClientError as e:
        logger.error(f"Failed to list objects in bucket {bucket_name}: {e}")
        raise


def is_image_file(file_path):
    return file_path.suffix.lower() in [".jpg", ".jpeg", ".png", ".webp"]


def _get_project_root():
    return Path(__file__).parent.parent


def _build_s3_key(relative_path, prefix=""):
    s3_key = str(relative_path).replace("\\", "/")
    if prefix:
        s3_key = f"{prefix.rstrip('/')}/{s3_key}"
    return s3_key


def _log_header(message):
    logger.info("=" * 50)
    logger.info(message)
    logger.info("=" * 50)


def _log_summary(title, metrics):
    logger.info("=" * 50)
    logger.info(title)
    for label, value in metrics.items():
        logger.info(f"{label}: {value}")
    logger.info("=" * 50)


def _scan_directory(source_dir, file_filter=None):
    for root, dirs, files in os.walk(source_dir):
        root_path = Path(root)
        relative_path = root_path.relative_to(source_dir)

        for file in files:
            local_file = root_path / file
            if file_filter and not file_filter(local_file):
                continue
            yield local_file, relative_path


def convert_image_to_webp(source_file, dest_file):
    try:
        with Image.open(source_file) as img:
            # WebP supports transparency, so preserve it
            if img.mode not in ("RGB", "RGBA", "L", "LA"):
                img = img.convert("RGBA")

            # Save as WebP with maximum quality
            img.save(dest_file, "WEBP", quality=100, optimize=True)
            return True
    except Exception as e:
        logger.error(f"Failed to convert {source_file}: {e}")
        return False


def _process_single_image(
    source_file, dest_root, relative_path, s3_key, existing_s3_keys, check_hash=False
):
    """Process a single image file: convert/copy and check if upload is needed.

    Args:
        source_file: Path to source image file
        dest_root: Destination directory for WebP files
        relative_path: Relative path for directory structure
        s3_key: S3 key where file will be uploaded
        existing_s3_keys: Dict mapping S3 keys to their ETags (or set of keys if check_hash=False)
        check_hash: If True, compare file hashes; if False, only check filename existence

    Returns:
        tuple: (action, dest_file, success) where action is one of:
            "skipped" - file exists in S3 (and hash matches if check_hash=True)
            "copied" - WebP file copied (new or updated)
            "converted" - image converted to WebP (new or updated)
            "error" - processing failed
    """
    # Default mode: just check if file exists in S3 by key
    if not check_hash:
        if s3_key in existing_s3_keys:
            logger.info(f"[SKIP CONVERT] Already in S3: {s3_key}")
            return "skipped", None, True

    # Calculate destination file
    dest_filename = source_file.stem + ".webp"
    dest_file = dest_root / dest_filename

    # Convert or copy the file
    if source_file.suffix.lower() == ".webp":
        # Already WebP, just copy
        try:
            shutil.copy2(source_file, dest_file)
            action = "copied"
        except Exception as e:
            logger.error(f"Failed to copy {source_file}: {e}")
            return "error", None, False
    else:
        # Convert to WebP
        if not convert_image_to_webp(source_file, dest_file):
            return "error", None, False
        action = "converted"

    # Hash checking mode: compare file contents
    if check_hash and s3_key in existing_s3_keys:
        # File exists in S3, compare hashes
        s3_etag = existing_s3_keys[s3_key]
        local_md5 = compute_local_md5(dest_file)

        if local_md5 and local_md5 == s3_etag:
            # Hashes match, skip upload
            logger.info(f"[SKIP] Hash matches S3: {s3_key}")
            return "skipped", None, True
        else:
            # Hashes differ, need to upload
            logger.info(f"[UPDATE] Hash differs, will re-upload: {s3_key}")
            logger.info(f"  {action.upper()} {source_file.name} -> {dest_filename}")
            return action, dest_file, True
    else:
        # New file or hash checking disabled, needs upload
        logger.info(f"[{action.upper()}] {source_file.name} -> {dest_filename}")
        return action, dest_file, True


def upload_file(s3_client, local_file, bucket_name, s3_key, content_type=None):
    """Upload a single file to S3."""
    try:
        # Auto-detect content type if not provided
        if content_type is None:
            content_type, _ = mimetypes.guess_type(str(local_file))
            if content_type is None:
                content_type = "application/octet-stream"

        extra_args = {
            "ContentType": content_type,
            "CacheControl": "max-age=31536000",  # Cache for 1 year
        }

        s3_client.upload_file(
            str(local_file), bucket_name, s3_key, ExtraArgs=extra_args
        )
        return True, None
    except Exception as e:
        return False, str(e)


def _concurrent_upload_batch(
    s3_client, files_to_upload, bucket_name, description="files"
):
    if not files_to_upload:
        return 0, 0

    uploaded_count = 0
    error_count = 0

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        future_to_file = {
            executor.submit(upload_file, s3_client, local_file, bucket_name, s3_key): (
                local_file,
                s3_key,
            )
            for local_file, s3_key in files_to_upload
        }

        for future in as_completed(future_to_file):
            local_file, s3_key = future_to_file[future]
            try:
                success, error = future.result()
                if success:
                    uploaded_count += 1
                    logger.info(
                        f"[UPLOAD] ({uploaded_count}/{len(files_to_upload)}): {s3_key}"
                    )
                else:
                    error_count += 1
                    logger.error(f"[ERROR] Failed to upload {s3_key}: {error}")
            except Exception as e:
                error_count += 1
                logger.error(f"Upload task failed for {s3_key}: {e}")

    _log_summary(
        f"UPLOAD SUMMARY for {description}:",
        {"Files uploaded": uploaded_count, "Upload errors": error_count},
    )

    return uploaded_count, error_count


def scan_and_convert_images(source_dir, dest_dir, existing_s3_keys, prefix="", check_hash=False):
    _log_header("STAGE 1: SCANNING AND CONVERTING IMAGES")

    files_to_upload = []
    stats = {"converted": 0, "copied": 0, "skipped": 0, "errors": 0}

    for source_file, relative_path in _scan_directory(
        source_dir, file_filter=is_image_file
    ):
        # Ensure destination directory exists
        dest_root = dest_dir / relative_path
        dest_root.mkdir(parents=True, exist_ok=True)

        # Calculate S3 key for WebP version
        dest_filename = source_file.stem + ".webp"
        relative_webp_path = relative_path / dest_filename
        s3_key = _build_s3_key(relative_webp_path, prefix)

        # Process the image
        action, dest_file, success = _process_single_image(
            source_file, dest_root, relative_path, s3_key, existing_s3_keys, check_hash
        )

        # Update statistics and upload list
        if action in ("copied", "converted"):
            stats[action] += 1
            files_to_upload.append((dest_file, s3_key))
        else:
            stats["skipped" if action == "skipped" else "errors"] += 1

    skip_reason = "hash matches S3" if check_hash else "already in S3"
    _log_summary(
        "CONVERSION SUMMARY:",
        {
            "Images converted": stats["converted"],
            "WebP files copied": stats["copied"],
            f"Conversions skipped ({skip_reason})": stats["skipped"],
            "Conversion errors": stats["errors"],
        },
    )

    return (
        files_to_upload,
        stats["converted"],
        stats["copied"],
        stats["skipped"],
        stats["errors"],
    )


def upload_images(s3_client, files_to_upload, bucket_name):
    if not files_to_upload:
        logger.info("No files to upload - all images already exist in S3")
        return 0, 0

    _log_header("STAGE 2: UPLOADING TO S3")
    return _concurrent_upload_batch(s3_client, files_to_upload, bucket_name, "images")


def scan_and_upload_directory(
    s3_client, source_dir, bucket_name, existing_s3_keys, prefix="", check_hash=False
):
    """Scan directory and upload files.

    Args:
        s3_client: Boto3 S3 client
        source_dir: Local directory to upload
        bucket_name: S3 bucket name
        existing_s3_keys: Dict mapping S3 keys to their ETags (or set of keys if check_hash=False)
        prefix: S3 key prefix
        check_hash: If True, compare file hashes; if False, only check filename existence

    Returns:
        tuple: (uploaded_count, skipped_count, error_count)
    """
    if not source_dir.exists():
        logger.info(f"Directory does not exist: {source_dir}")
        return 0, 0, 0

    mode_msg = "with hash checking" if check_hash else "replacing all files"
    _log_header(f"UPLOADING DIRECTORY: {source_dir} ({mode_msg})")

    # Scan all files
    files_to_upload = []
    skipped_count = 0

    for local_file, relative_path in _scan_directory(source_dir):
        s3_key = _build_s3_key(relative_path / local_file.name, prefix)

        # Default mode: skip only if exists
        if not check_hash:
            if s3_key in existing_s3_keys:
                logger.info(f"[SKIP] Already in S3: {s3_key}")
                skipped_count += 1
                continue
            files_to_upload.append((local_file, s3_key))
            continue

        # Hash checking mode: compare file contents
        if s3_key in existing_s3_keys:
            local_md5 = compute_local_md5(local_file)
            s3_etag = existing_s3_keys[s3_key]

            if local_md5 and local_md5 == s3_etag:
                logger.info(f"[SKIP] Hash matches S3: {s3_key}")
                skipped_count += 1
                continue
            else:
                logger.info(f"[UPDATE] Hash differs, will re-upload: {s3_key}")

        files_to_upload.append((local_file, s3_key))

    if not files_to_upload:
        msg = "all match S3" if check_hash else "all exist in S3"
        logger.info(f"No files to upload from {source_dir} ({msg})")
        return 0, skipped_count, 0

    logger.info(f"Found {len(files_to_upload)} files to upload ({skipped_count} skipped)")

    uploaded_count, error_count = _concurrent_upload_batch(
        s3_client, files_to_upload, bucket_name, f"files from {source_dir.name}"
    )

    return uploaded_count, skipped_count, error_count


def main():
    """Main function to run the unified conversion and upload process."""
    parser = argparse.ArgumentParser(
        description="Convert images to WebP and upload to S3 (only new files)"
    )
    parser.add_argument(
        "--bucket",
        default=DEFAULT_BUCKET,
        help=f"S3 bucket name (default: {DEFAULT_BUCKET})",
    )
    parser.add_argument(
        "--prefix", default="", help="S3 key prefix for uploaded files (optional)"
    )
    parser.add_argument(
        "--source-dir",
        help="Source directory with PNG/JPG/WebP images (default: src/main/resources)",
    )
    parser.add_argument(
        "--dest-dir",
        help="Destination directory for WebP conversion/copying (default: src/main/webp)",
    )
    parser.add_argument(
        "--check-hash",
        action="store_true",
        help="Enable hash checking to detect file changes (default: only check filename existence)",
    )

    args = parser.parse_args()

    # Setup directories
    project_root = _get_project_root()
    source_dir = Path(args.source_dir) if args.source_dir else project_root / "src" / "main" / "resources"
    dest_dir = Path(args.dest_dir) if args.dest_dir else project_root / "src" / "main" / "webp"

    if not source_dir.exists():
        logger.error(f"Source directory does not exist: {source_dir}")
        sys.exit(1)

    dest_dir.mkdir(parents=True, exist_ok=True)

    logger.info(f"Source directory: {source_dir}")
    logger.info(f"Destination directory: {dest_dir}")
    logger.info(f"S3 bucket: {args.bucket}")
    if args.prefix:
        logger.info(f"S3 prefix: {args.prefix}")
    logger.info(f"Hash checking: {'enabled' if args.check_hash else 'disabled (filename only)'}")

    try:
        # Initialize S3 client
        s3_client = setup_s3_client()

        # Check if bucket exists
        if not bucket_exists(s3_client, args.bucket):
            logger.error(f"Bucket {args.bucket} does not exist")
            sys.exit(1)

        logger.info(f"Bucket {args.bucket} exists and is accessible")

        # Fetch existing S3 keys (with or without ETags based on check_hash flag)
        logger.info("Fetching existing S3 objects...")
        existing_s3_keys = get_all_s3_keys(s3_client, args.bucket, args.prefix, args.check_hash)
        logger.info(f"Found {len(existing_s3_keys)} existing objects in S3")

        # Scan local images and convert only new ones
        files_to_upload, converted, copied, skipped_conv, conv_errors = (
            scan_and_convert_images(source_dir, dest_dir, existing_s3_keys, args.prefix, args.check_hash)
        )

        if conv_errors > 0:
            logger.warning(f"Conversion completed with {conv_errors} errors")

        # Upload converted images
        uploaded, upload_errors = upload_images(s3_client, files_to_upload, args.bucket)

        # Upload TypeScript files from web_data directory
        web_data_dir = project_root / "web_data"

        web_uploaded, web_skipped, web_errors = scan_and_upload_directory(
            s3_client, web_data_dir, args.bucket, existing_s3_keys, prefix="web_data", check_hash=args.check_hash
        )

        # Final summary
        total_errors = conv_errors + upload_errors + web_errors
        _log_summary(
            "FINAL SUMMARY:",
            {
                "Images converted": converted,
                "WebP files copied": copied,
                "Conversions skipped (already in S3)": skipped_conv,
                "Image files uploaded": uploaded,
                "Web data files uploaded": web_uploaded,
                "Web data files skipped": web_skipped,
                "Conversion errors": conv_errors,
                "Upload errors": upload_errors + web_errors,
            },
        )
        if total_errors > 0:
            logger.warning(f"Completed with {total_errors} total errors")
            sys.exit(1)
        else:
            logger.info("All operations completed successfully!")

    except KeyboardInterrupt:
        logger.info("Process interrupted by user")
        sys.exit(1)
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
