param(
    [switch]$Apply
)

$ErrorActionPreference = "Stop"

# Make sure we're inside a Git repository.
$root = git rev-parse --show-toplevel 2>$null

if ($LASTEXITCODE -ne 0 -or -not $root) {
    Write-Error "This is not a Git repository."
    exit 1
}

Set-Location $root

Write-Host "Repository: $root"
Write-Host ""

# Get all tracked paths from Git.
$gitPaths = @(git ls-files)

# Get all files and directories on disk.
$diskItems = Get-ChildItem -Path $root -Recurse -Force |
    Where-Object {
        $_.FullName -notmatch "\\\.git(\\|$)"
    }

# Build a case-insensitive lookup of paths on disk.
$diskLookup = @{}

foreach ($item in $diskItems) {
    $relative = $item.FullName.Substring($root.Length).TrimStart('\')

    # Normalize Windows paths to Git-style paths.
    $relative = $relative.Replace('\', '/')

    $normalized = $relative.ToLowerInvariant()

    $diskLookup[$normalized] = $relative
}

# Find paths where Git's casing differs from the filesystem's casing.
$changes = @()

foreach ($gitPath in $gitPaths) {
    # Normalize Git path separators.
    $gitPathNormalized = $gitPath.Replace('\', '/')
    $normalized = $gitPathNormalized.ToLowerInvariant()

    if ($diskLookup.ContainsKey($normalized)) {
        $actualPath = $diskLookup[$normalized]

        # Compare casing only; both paths now use '/'.
        if ($gitPathNormalized -cne $actualPath) {
            $changes += [PSCustomObject]@{
                GitPath    = $gitPathNormalized
                ActualPath = $actualPath
            }
        }
    }
}

if ($changes.Count -eq 0) {
    Write-Host "No case-only path changes found."
    exit 0
}

Write-Host "Case-only path changes found:"
Write-Host ""

foreach ($change in $changes) {
    Write-Host "  $($change.GitPath)"
    Write-Host "    -> $($change.ActualPath)"
}

Write-Host ""

# Dry run unless -Apply was specified.
if (-not $Apply) {
    Write-Host "DRY RUN: No changes were made."
    Write-Host ""
    Write-Host "To apply these changes, run:"
    Write-Host "  .\fix-git-case.ps1 -Apply"
    exit 0
}

Write-Host "Applying changes..."
Write-Host ""

foreach ($change in $changes) {
    $oldPath = $change.GitPath
    $newPath = $change.ActualPath

    # Git needs an intermediate name on case-insensitive filesystems.
    $temporaryPath = "$oldPath.__git_case_fix__"

    Write-Host "  $oldPath"
    Write-Host "    -> $temporaryPath"

    git mv -- "$oldPath" "$temporaryPath"

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to rename '$oldPath'."
        exit 1
    }

    Write-Host "    -> $newPath"

    git mv -- "$temporaryPath" "$newPath"

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to rename '$temporaryPath'."
        exit 1
    }
}

Write-Host ""
Write-Host "Done."
Write-Host ""
Write-Host "Git status:"
git status --short