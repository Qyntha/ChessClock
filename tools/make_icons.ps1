<#
    把一张方形原图直接缩成各密度的传统启动图标（不做任何抠图 / 裁切 / 透明化）：
      * mipmap-mdpi/ic_launcher.png        48x48
      * mipmap-hdpi/ic_launcher.png        72x72
      * mipmap-xhdpi/ic_launcher.png       96x96
      * mipmap-xxhdpi/ic_launcher.png      144x144
      * mipmap-xxxhdpi/ic_launcher.png     192x192
      * 同名的 ic_launcher_round.png 用同一张图、同样的缩放（原图本身就是圆形徽标）
      * tools/icon_preview.png             效果预览（原图 / 圆形遮罩效果 / 48px 实际像素放大）

    注意：项目里没有 mipmap-anydpi-v26/（自适应图标），
    因此系统不会走自适应图标路径，图标外圈的文字不会被裁掉。

    用法：pwsh -File tools/make_icons.ps1 -Source "D:\he.9\社徽.png"
#>
param(
    [Parameter(Mandatory = $true)][string]$Source,
    [string]$ResDir = (Join-Path $PSScriptRoot '..\app\src\main\res')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$resFull = [System.IO.Path]::GetFullPath($ResDir)
$toolsDir = $PSScriptRoot

function New-Graphics([System.Drawing.Bitmap]$Bitmap) {
    $g = [System.Drawing.Graphics]::FromImage($Bitmap)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    return $g
}

# 高质量插值缩放：整张原图铺满目标方形，保留白底和蓝色圆形，不裁切
function Resize-Icon {
    param(
        [System.Drawing.Bitmap]$Source,
        [int]$Size
    )
    $bmp = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = New-Graphics $bmp
    $g.Clear([System.Drawing.Color]::White)
    $rect = New-Object System.Drawing.Rectangle(0, 0, $Size, $Size)
    $g.DrawImage($Source, $rect)
    $g.Dispose()
    return $bmp
}

$src = [System.Drawing.Bitmap]::FromFile([System.IO.Path]::GetFullPath($Source))
try {
    Write-Host ("原图: {0}x{1}" -f $src.Width, $src.Height)

    $densities = @(
        @{ Dir = 'mipmap-mdpi'; Size = 48 },
        @{ Dir = 'mipmap-hdpi'; Size = 72 },
        @{ Dir = 'mipmap-xhdpi'; Size = 96 },
        @{ Dir = 'mipmap-xxhdpi'; Size = 144 },
        @{ Dir = 'mipmap-xxxhdpi'; Size = 192 }
    )

    foreach ($d in $densities) {
        $dir = Join-Path $resFull $d.Dir
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }

        $bmp = Resize-Icon -Source $src -Size $d.Size
        foreach ($name in @('ic_launcher.png', 'ic_launcher_round.png')) {
            $out = Join-Path $dir $name
            if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out -Force }
            $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
        }
        $bmp.Dispose()
        Write-Host ("{0}: ic_launcher.png / ic_launcher_round.png = {1}x{1}" -f $d.Dir, $d.Size)
    }

    # ---------------------------------------------------------------- 预览图
    $panel = 256
    $preview = New-Object System.Drawing.Bitmap(($panel * 3), $panel, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $pg = New-Graphics $preview
    $pg.Clear([System.Drawing.ColorTranslator]::FromHtml('#202020'))

    # 面板 1：原图缩放
    $panelBmp = Resize-Icon -Source $src -Size $panel
    $pg.DrawImage($panelBmp, 0, 0, $panel, $panel)

    # 面板 2：模拟圆形遮罩（部分启动器对 roundIcon 会这样裁切，用于确认外圈文字会不会被切）
    $circlePath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $circlePath.AddEllipse($panel, 0, $panel, $panel)
    $pg.SetClip($circlePath)
    $pg.DrawImage($panelBmp, $panel, 0, $panel, $panel)
    $pg.ResetClip()
    $circlePath.Dispose()
    $panelBmp.Dispose()

    # 面板 3：48px 实际像素用最近邻放大，检查最小尺寸下是否还看得清
    $small = New-Object System.Drawing.Bitmap(48, 48, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $sg = New-Graphics $small
    $sg.Clear([System.Drawing.Color]::White)
    $sg.DrawImage($src, (New-Object System.Drawing.Rectangle(0, 0, 48, 48)))
    $sg.Dispose()
    $pg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $pg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $pg.DrawImage($small, ($panel * 2), 0, $panel, $panel)
    $small.Dispose()

    $pg.Dispose()
    $previewPath = Join-Path $toolsDir 'icon_preview.png'
    if (Test-Path -LiteralPath $previewPath) { Remove-Item -LiteralPath $previewPath -Force }
    $preview.Save($previewPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $preview.Dispose()
    Write-Host "预览图已写出: $previewPath"
}
finally {
    $src.Dispose()
}
