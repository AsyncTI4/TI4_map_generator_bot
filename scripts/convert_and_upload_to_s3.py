#!/usr/bin/env python3
"""
Unified script to convert images to WebP and upload to S3.
Only converts local PNG/JPG files if their WebP equivalent doesn't already exist in S3.
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
    """Initialize S3 client with AWS credentials."""
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
    """Check if the specified bucket exists and is accessible."""
    try:
        s3_client.head_bucket(Bucket=bucket_name)
        return True
    except ClientError as e:
        error_code = e.response["Error"]["Code"]
        if error_code == "404":
            return False
        else:
            logger.error(f"Error checking bucket {bucket_name}: {e}")
            return False


def get_all_s3_keys(s3_client, bucket_name, prefix=""):
    """
    Get all object keys in S3 bucket using list_objects_v2.
    Returns a set for O(1) lookup performance.
    """
    all_keys = set()
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
                    all_keys.add(obj["Key"])

            if not response.get("IsTruncated"):
                break

            continuation_token = response.get("NextContinuationToken")

        return all_keys

    except ClientError as e:
        logger.error(f"Failed to list objects in bucket {bucket_name}: {e}")
        raise


def is_image_file(file_path):
    """Check if file is a JPG or PNG image."""
    return file_path.suffix.lower() in [".jpg", ".jpeg", ".png"]


def convert_image_to_webp(source_file, dest_file):
    """Convert a single image file to WebP format."""
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


def upload_file(s3_client, local_file, bucket_name, s3_key):
    """Upload a single file to S3."""
    try:
        extra_args = {
            "ContentType": "image/webp",
            "CacheControl": "max-age=31536000",  # Cache for 1 year
        }

        s3_client.upload_file(str(local_file), bucket_name, s3_key, ExtraArgs=extra_args)
        return True, None
    except Exception as e:
        return False, str(e)


def scan_and_convert_images(source_dir, dest_dir, existing_s3_keys, prefix=""):
    """
    Scan local images and convert only those that don't exist in S3.

    Args:
        source_dir: Local source directory with PNG/JPG files
        dest_dir: Temporary directory for WebP conversion
        existing_s3_keys: Set of S3 object keys already in bucket
        prefix: S3 prefix for key comparison

    Returns:
        List of (local_webp_file, s3_key) tuples to upload
    """
    logger.info("=" * 50)
    logger.info("STAGE 1: SCANNING AND CONVERTING IMAGES")
    logger.info("=" * 50)

    files_to_upload = []
    converted_count = 0
    skipped_conversion_count = 0
    conversion_error_count = 0

    for root, dirs, files in os.walk(source_dir):
        root_path = Path(root)
        relative_path = root_path.relative_to(source_dir)
        dest_root = dest_dir / relative_path
        dest_root.mkdir(parents=True, exist_ok=True)

        for file in files:
            source_file = root_path / file

            if not is_image_file(source_file):
                continue

            # Calculate S3 key for the WebP version
            dest_filename = source_file.stem + ".webp"
            relative_webp_path = relative_path / dest_filename
            s3_key = str(relative_webp_path).replace("\\", "/")
            if prefix:
                s3_key = f"{prefix.rstrip('/')}/{s3_key}"

            # Check if WebP already exists in S3
            if s3_key in existing_s3_keys:
                logger.info(f"[SKIP CONVERT] Already in S3: {s3_key}")
                skipped_conversion_count += 1
                continue

            # Need to convert - check if local WebP exists
            dest_file = dest_root / dest_filename

            # Convert the image
            logger.info(f"[CONVERT] {source_file.name} -> {dest_filename}")
            if convert_image_to_webp(source_file, dest_file):
                converted_count += 1
                files_to_upload.append((dest_file, s3_key))
            else:
                conversion_error_count += 1

    logger.info("=" * 50)
    logger.info("CONVERSION SUMMARY:")
    logger.info(f"Images converted: {converted_count}")
    logger.info(f"Conversions skipped (already in S3): {skipped_conversion_count}")
    logger.info(f"Conversion errors: {conversion_error_count}")
    logger.info("=" * 50)

    return files_to_upload, converted_count, skipped_conversion_count, conversion_error_count


def upload_images(s3_client, files_to_upload, bucket_name):
    """
    Upload converted WebP images to S3 concurrently.

    Args:
        s3_client: Boto3 S3 client
        files_to_upload: List of (local_file, s3_key) tuples
        bucket_name: S3 bucket name

    Returns:
        uploaded_count, error_count
    """
    if not files_to_upload:
        logger.info("No files to upload - all images already exist in S3")
        return 0, 0

    logger.info("=" * 50)
    logger.info("STAGE 2: UPLOADING TO S3")
    logger.info("=" * 50)

    uploaded_count = 0
    error_count = 0

    # Upload files concurrently
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

    logger.info("=" * 50)
    logger.info("UPLOAD SUMMARY:")
    logger.info(f"Files uploaded: {uploaded_count}")
    logger.info(f"Upload errors: {error_count}")
    logger.info("=" * 50)

    return uploaded_count, error_count


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
        help="Source directory with PNG/JPG images (default: src/main/resources)",
    )
    parser.add_argument(
        "--dest-dir",
        help="Destination directory for WebP conversion (default: src/main/webp)",
    )

    args = parser.parse_args()

    # Setup directories
    if args.source_dir:
        source_dir = Path(args.source_dir)
    else:
        script_dir = Path(__file__).parent
        project_root = script_dir.parent
        source_dir = project_root / "src" / "main" / "resources"

    if args.dest_dir:
        dest_dir = Path(args.dest_dir)
    else:
        script_dir = Path(__file__).parent
        project_root = script_dir.parent
        dest_dir = project_root / "src" / "main" / "webp"

    if not source_dir.exists():
        logger.error(f"Source directory does not exist: {source_dir}")
        sys.exit(1)

    dest_dir.mkdir(parents=True, exist_ok=True)

    logger.info(f"Source directory: {source_dir}")
    logger.info(f"Destination directory: {dest_dir}")
    logger.info(f"S3 bucket: {args.bucket}")
    if args.prefix:
        logger.info(f"S3 prefix: {args.prefix}")

    try:
        # Initialize S3 client
        s3_client = setup_s3_client()

        # Check if bucket exists
        if not bucket_exists(s3_client, args.bucket):
            logger.error(f"Bucket {args.bucket} does not exist")
            sys.exit(1)

        logger.info(f"Bucket {args.bucket} exists and is accessible")

        # Fetch existing S3 keys
        logger.info("Fetching existing S3 objects...")
        existing_s3_keys = get_all_s3_keys(s3_client, args.bucket, args.prefix)
        logger.info(f"Found {len(existing_s3_keys)} existing objects in S3")

        # Scan local images and convert only new ones
        files_to_upload, converted, skipped_conv, conv_errors = scan_and_convert_images(
            source_dir, dest_dir, existing_s3_keys, args.prefix
        )

        if conv_errors > 0:
            logger.warning(f"Conversion completed with {conv_errors} errors")

        # Upload converted images
        uploaded, upload_errors = upload_images(s3_client, files_to_upload, args.bucket)

        # Final summary
        logger.info("=" * 50)
        logger.info("FINAL SUMMARY:")
        logger.info(f"Images converted: {converted}")
        logger.info(f"Conversions skipped (already in S3): {skipped_conv}")
        logger.info(f"Files uploaded: {uploaded}")
        logger.info(f"Conversion errors: {conv_errors}")
        logger.info(f"Upload errors: {upload_errors}")
        logger.info("=" * 50)

        total_errors = conv_errors + upload_errors
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
