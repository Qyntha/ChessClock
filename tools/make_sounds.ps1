<#
    生成棋钟需要的两个提示音（16bit / 44.1kHz / 单声道 PCM WAV）：
      * app/src/main/res/raw/byoyomi_tick.wav  读秒剩余 ≤5 秒时每秒的“滴”
      * app/src/main/res/raw/flag_fall.wav     时间走完判负的“两声降调”

    想换成自己的音频时，直接覆盖这两个文件即可（保持文件名不变）。
    用法：pwsh -File tools/make_sounds.ps1
#>
param(
    [string]$ResDir = (Join-Path $PSScriptRoot '..\app\src\main\res')
)

$ErrorActionPreference = 'Stop'
$sampleRate = 44100

function New-WavBytes {
    param(
        [double[]]$Samples,   # -1.0 ~ 1.0
        [int]$SampleRate
    )
    $dataSize = $Samples.Length * 2
    $bytes = New-Object byte[] (44 + $dataSize)
    $enc = [System.Text.Encoding]::ASCII

    $enc.GetBytes('RIFF').CopyTo($bytes, 0)
    [System.BitConverter]::GetBytes([int](36 + $dataSize)).CopyTo($bytes, 4)
    $enc.GetBytes('WAVE').CopyTo($bytes, 8)
    $enc.GetBytes('fmt ').CopyTo($bytes, 12)
    [System.BitConverter]::GetBytes([int]16).CopyTo($bytes, 16)          # fmt chunk 大小
    [System.BitConverter]::GetBytes([int16]1).CopyTo($bytes, 20)         # PCM
    [System.BitConverter]::GetBytes([int16]1).CopyTo($bytes, 22)         # 单声道
    [System.BitConverter]::GetBytes([int]$SampleRate).CopyTo($bytes, 24)
    [System.BitConverter]::GetBytes([int]($SampleRate * 2)).CopyTo($bytes, 28) # 字节率
    [System.BitConverter]::GetBytes([int16]2).CopyTo($bytes, 32)         # block align
    [System.BitConverter]::GetBytes([int16]16).CopyTo($bytes, 34)        # 位深
    $enc.GetBytes('data').CopyTo($bytes, 36)
    [System.BitConverter]::GetBytes([int]$dataSize).CopyTo($bytes, 40)

    for ($i = 0; $i -lt $Samples.Length; $i++) {
        $v = [Math]::Max(-1.0, [Math]::Min(1.0, $Samples[$i]))
        $s = [int16][Math]::Round($v * 32767)
        $bytes[44 + $i * 2] = [byte]($s -band 0xFF)
        $bytes[44 + $i * 2 + 1] = [byte](($s -shr 8) -band 0xFF)
    }
    return $bytes
}

function New-Tone {
    param(
        [double]$Freq,
        [double]$Seconds,
        [int]$SampleRate,
        [double]$Amplitude = 0.6,
        [double]$AttackSeconds = 0.003,
        [double]$ReleaseSeconds = 0.0
    )
    $n = [int]($Seconds * $SampleRate)
    $out = New-Object double[] $n
    for ($i = 0; $i -lt $n; $i++) {
        $t = $i / [double]$SampleRate
        $env = 1.0
        if ($t -lt $AttackSeconds) { $env = $t / $AttackSeconds }
        $release = $Seconds - $ReleaseSeconds
        if ($ReleaseSeconds -gt 0 -and $t -gt $release) {
            $env = [Math]::Min($env, ($Seconds - $t) / $ReleaseSeconds)
        }
        # 末尾 8ms 强制淡出，避免爆音
        $fadeStart = $Seconds - 0.008
        if ($t -gt $fadeStart) { $env = [Math]::Min($env, ($Seconds - $t) / 0.008) }
        $out[$i] = $Amplitude * $env * [Math]::Sin(2 * [Math]::PI * $Freq * $t)
    }
    return $out
}

function Join-Samples {
    param([double[][]]$Parts)
    $total = 0
    foreach ($p in $Parts) { $total += $p.Length }
    $out = New-Object double[] $total
    $offset = 0
    foreach ($p in $Parts) {
        [Array]::Copy($p, 0, $out, $offset, $p.Length)
        $offset += $p.Length
    }
    return $out
}

$resFull = [System.IO.Path]::GetFullPath($ResDir)
$rawDir = Join-Path $resFull 'raw'
if (-not (Test-Path $rawDir)) { New-Item -ItemType Directory -Path $rawDir | Out-Null }

# “滴”：1200Hz 短音，90ms
$tick = New-Tone -Freq 1200 -Seconds 0.09 -SampleRate $sampleRate -Amplitude 0.55
[System.IO.File]::WriteAllBytes((Join-Path $rawDir 'byoyomi_tick.wav'), (New-WavBytes -Samples $tick -SampleRate $sampleRate))

# “时间到”：780Hz + 520Hz 两声降调
$flag = Join-Samples -Parts @(
    (New-Tone -Freq 780 -Seconds 0.22 -SampleRate $sampleRate -Amplitude 0.75 -ReleaseSeconds 0.02),
    (New-Tone -Freq 520 -Seconds 0.38 -SampleRate $sampleRate -Amplitude 0.75 -ReleaseSeconds 0.05)
)
[System.IO.File]::WriteAllBytes((Join-Path $rawDir 'flag_fall.wav'), (New-WavBytes -Samples $flag -SampleRate $sampleRate))

Get-ChildItem $rawDir -Filter *.wav | ForEach-Object { "{0}  {1} 字节" -f $_.Name, $_.Length }
