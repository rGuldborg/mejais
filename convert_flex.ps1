Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName WindowsBase

function Convert-Webp {
    param(
        [Parameter(Mandatory = $true)][string]$inputPath,
        [Parameter(Mandatory = $true)][string]$outputPath,
        [Parameter(Mandatory = $true)][System.Windows.Media.Color]$targetColor
    )

    $resolvedInput = (Resolve-Path -Path $inputPath).Path
    $outputDirectory = Split-Path -Path $outputPath -Parent

    if ($outputDirectory -and -not (Test-Path -Path $outputDirectory)) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }

    $fsIn = [System.IO.File]::OpenRead($resolvedInput)
    try {
        $decoder = [System.Windows.Media.Imaging.BitmapDecoder]::Create(
            $fsIn,
            [System.Windows.Media.Imaging.BitmapCreateOptions]::PreservePixelFormat,
            [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
        )
        $frame = $decoder.Frames[0]

        $converted = New-Object System.Windows.Media.Imaging.FormatConvertedBitmap
        $converted.BeginInit()
        $converted.Source = $frame
        $converted.DestinationFormat = [System.Windows.Media.PixelFormats]::Pbgra32
        $converted.EndInit()

        $width = $converted.PixelWidth
        $height = $converted.PixelHeight
        $stride = $width * 4
        $pixels = New-Object byte[] ($stride * $height)
        $converted.CopyPixels($pixels, $stride, 0)

        for ($i = 0; $i -lt $pixels.Length; $i += 4) {
            $alpha = $pixels[$i + 3]
            if ($alpha -eq 0) {
                continue
            }
            $pixels[$i] = [byte]$targetColor.B
            $pixels[$i + 1] = [byte]$targetColor.G
            $pixels[$i + 2] = [byte]$targetColor.R
        }

        $writeable = New-Object System.Windows.Media.Imaging.WriteableBitmap(
            $width,
            $height,
            $converted.DpiX,
            $converted.DpiY,
            [System.Windows.Media.PixelFormats]::Pbgra32,
            $null
        )
        $rect = New-Object System.Windows.Int32Rect(0, 0, $width, $height)
        $writeable.WritePixels($rect, $pixels, $stride, 0)

        $encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
        $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($writeable))

        $fsOut = [System.IO.File]::Create($outputPath)
        try {
            $encoder.Save($fsOut)
        }
        finally {
            $fsOut.Close()
        }
    }
    finally {
        $fsIn.Close()
    }
}

$source = "src/main/resources/org/example/images/roles/Flex_icon.webp"
$darkColor = [System.Windows.Media.Colors]::White
$lightColor = [System.Windows.Media.Colors]::Black

Convert-Webp -inputPath $source -outputPath "src/main/resources/org/example/images/roles/dark/flex.png" -targetColor $darkColor
Convert-Webp -inputPath $source -outputPath "src/main/resources/org/example/images/roles/light/flex.png" -targetColor $lightColor
