$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$excluded = @(".git", "node_modules", ".next", "build", "artifacts", "gradle-8.10.2", ".gradle", ".kotlin", "local.properties", "AGENTS.md", ".log")
$files = Get-ChildItem -LiteralPath $root -Recurse -File -Force | Where-Object {
  $relative = $_.FullName.Substring($root.Length).TrimStart("\\")
  $_.Extension -ne ".log" -and -not ($excluded | Where-Object { $relative -match "(^|\\)" + [regex]::Escape($_) + "(\\|$)" })
}

$patterns = @(
  'tk_[A-Za-z0-9]{20,}',
  'https?://(?:[A-Za-z0-9-]+\.)*(?:lvziwang\.top|lvziw\.top)',
  '(?-i)(?:REMOTE_PASSWORD|MYSQL_PASSWORD|ACCESS_KEY)\s*=\s*(["'']?)(?!replace-with|\$|<)[^"''\s#]+',
  '(?i)(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3})',
  '(?i)(?:/home/|C:\\Users\\)[A-Za-z0-9._-]+'
)

$matches = foreach ($file in $files) {
  Select-String -LiteralPath $file.FullName -Pattern $patterns -AllMatches -ErrorAction SilentlyContinue
}

if ($matches) {
  $matches | ForEach-Object { "{0}:{1}: {2}" -f $_.Path, $_.LineNumber, $_.Line.Trim() }
  throw "Public repository audit failed. Review the findings above."
}

Write-Output "Public repository audit passed."
