$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$Source = Resolve-Path (Join-Path $Root "..\mobile\app\src\main\res\mipmap-xxxhdpi\ic_launcher.png")
$BuildDir = Join-Path $Root "build"
$AssetsDir = Join-Path $Root "src\assets"

New-Item -ItemType Directory -Path $BuildDir -Force | Out-Null
New-Item -ItemType Directory -Path $AssetsDir -Force | Out-Null

$IconPng = Join-Path $AssetsDir "icon.png"
Copy-Item -LiteralPath $Source -Destination $IconPng -Force

function New-ResizedPngBytes {
  param(
    [string] $SourcePath,
    [int] $Size
  )

  $sourceImage = [System.Drawing.Image]::FromFile($SourcePath)
  try {
    $bitmap = New-Object System.Drawing.Bitmap $Size, $Size
    try {
      $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
      try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.DrawImage($sourceImage, 0, 0, $Size, $Size)
      } finally {
        $graphics.Dispose()
      }

      $stream = New-Object System.IO.MemoryStream
      try {
        $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
        return ,([byte[]]$stream.ToArray())
      } finally {
        $stream.Dispose()
      }
    } finally {
      $bitmap.Dispose()
    }
  } finally {
    $sourceImage.Dispose()
  }
}

function Write-UInt16LE {
  param([System.IO.BinaryWriter] $Writer, [int] $Value)
  $Writer.Write([byte]($Value -band 0xff))
  $Writer.Write([byte](($Value -shr 8) -band 0xff))
}

function Write-UInt32LE {
  param([System.IO.BinaryWriter] $Writer, [int] $Value)
  $Writer.Write([byte]($Value -band 0xff))
  $Writer.Write([byte](($Value -shr 8) -band 0xff))
  $Writer.Write([byte](($Value -shr 16) -band 0xff))
  $Writer.Write([byte](($Value -shr 24) -band 0xff))
}

function Write-UInt32BE {
  param([System.IO.BinaryWriter] $Writer, [int] $Value)
  $Writer.Write([byte](($Value -shr 24) -band 0xff))
  $Writer.Write([byte](($Value -shr 16) -band 0xff))
  $Writer.Write([byte](($Value -shr 8) -band 0xff))
  $Writer.Write([byte]($Value -band 0xff))
}

$icoSizes = @(16, 24, 32, 48, 64, 128, 256)
$icoImages = @()
foreach ($size in $icoSizes) {
  $icoImages += [pscustomobject]@{
    Size = $size
    Bytes = New-ResizedPngBytes -SourcePath $Source -Size $size
  }
}

$icoPath = Join-Path $BuildDir "icon.ico"
$icoStream = [System.IO.File]::Open($icoPath, [System.IO.FileMode]::Create)
try {
  $writer = New-Object System.IO.BinaryWriter $icoStream
  try {
    Write-UInt16LE -Writer $writer -Value 0
    Write-UInt16LE -Writer $writer -Value 1
    Write-UInt16LE -Writer $writer -Value $icoImages.Count

    $offset = 6 + ($icoImages.Count * 16)
    foreach ($image in $icoImages) {
      $widthByte = if ($image.Size -eq 256) { 0 } else { $image.Size }
      $writer.Write([byte]$widthByte)
      $writer.Write([byte]$widthByte)
      $writer.Write([byte]0)
      $writer.Write([byte]0)
      Write-UInt16LE -Writer $writer -Value 1
      Write-UInt16LE -Writer $writer -Value 32
      Write-UInt32LE -Writer $writer -Value $image.Bytes.Length
      Write-UInt32LE -Writer $writer -Value $offset
      $offset += $image.Bytes.Length
    }

    foreach ($image in $icoImages) {
      $writer.Write($image.Bytes)
    }
  } finally {
    $writer.Dispose()
  }
} finally {
  $icoStream.Dispose()
}

$icnsChunks = @(
  @{ Type = "icp4"; Size = 16 },
  @{ Type = "icp5"; Size = 32 },
  @{ Type = "icp6"; Size = 64 },
  @{ Type = "ic07"; Size = 128 },
  @{ Type = "ic08"; Size = 256 },
  @{ Type = "ic09"; Size = 512 },
  @{ Type = "ic10"; Size = 1024 }
)

$icnsData = @()
$totalSize = 8
foreach ($chunk in $icnsChunks) {
  $bytes = New-ResizedPngBytes -SourcePath $Source -Size $chunk.Size
  $icnsData += [pscustomobject]@{
    Type = $chunk.Type
    Bytes = $bytes
  }
  $totalSize += 8 + $bytes.Length
}

$icnsPath = Join-Path $BuildDir "icon.icns"
$icnsStream = [System.IO.File]::Open($icnsPath, [System.IO.FileMode]::Create)
try {
  $writer = New-Object System.IO.BinaryWriter $icnsStream
  try {
    $writer.Write([System.Text.Encoding]::ASCII.GetBytes("icns"))
    Write-UInt32BE -Writer $writer -Value $totalSize
    foreach ($chunk in $icnsData) {
      $writer.Write([System.Text.Encoding]::ASCII.GetBytes($chunk.Type))
      Write-UInt32BE -Writer $writer -Value (8 + $chunk.Bytes.Length)
      $writer.Write($chunk.Bytes)
    }
  } finally {
    $writer.Dispose()
  }
} finally {
  $icnsStream.Dispose()
}

Write-Host "Generated $IconPng"
Write-Host "Generated $icoPath"
Write-Host "Generated $icnsPath"
