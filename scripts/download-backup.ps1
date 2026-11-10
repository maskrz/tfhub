
$config = "$HOME\.config\tfhub-rclone\rclone.conf"
$destination = "$HOME\Downloads"

if (-not (Test-Path $config)) {
    Write-Error "Brak konfiguracji rclone: $config"
    exit 1
}

$backups = @(
    rclone --config $config lsf tfhub-crypt: --files-only |
        Where-Object { $_ -match '^tfhub_backup_.*\.xlsx$' } |
        Sort-Object -Descending
)

if ($LASTEXITCODE -ne 0) {
    Write-Error "Nie udało się pobrać listy backupów."
    exit 1
}

if ($backups.Count -eq 0) {
    Write-Error "Nie znaleziono backupów."
    exit 1
}

$latest = $backups[0]
$output = Join-Path $destination $latest

Write-Host "Pobieram: $latest"

rclone --config $config copyto "tfhub-crypt:$latest" $output

if ($LASTEXITCODE -ne 0) {
    Write-Error "Pobieranie nie powiodło się."
    exit 1
}

Write-Host "Backup zapisany w: $output"
