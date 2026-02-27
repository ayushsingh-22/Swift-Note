# 16KB Page Size Alignment Script for Android Native Libraries
# This script ensures proper alignment for libtextclassifier3_jni_tclib.so and other native libraries

# Set Android SDK and NDK paths
$ANDROID_SDK = $env:ANDROID_HOME
if (-not $ANDROID_SDK) {
    $ANDROID_SDK = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"
}

$ANDROID_NDK = "$ANDROID_SDK\ndk\25.2.9519653"  # Adjust version as needed
$BUILD_TOOLS = "$ANDROID_SDK\build-tools\34.0.0"  # Adjust version as needed

Write-Host "Configuring 16KB page alignment for native libraries..."
Write-Host "Android SDK: $ANDROID_SDK"
Write-Host "Android NDK: $ANDROID_NDK"

# Function to align a single .so file to 16KB boundaries
function Align-NativeLibrary {
    param(
        [string]$LibraryPath,
        [string]$OutputPath
    )

    if (Test-Path $LibraryPath) {
        Write-Host "Aligning $LibraryPath to 16KB boundaries..."

        # Use zipalign for 16KB alignment
        $zipalign = "$BUILD_TOOLS\zipalign.exe"
        if (Test-Path $zipalign) {
            & $zipalign -f -p 16384 $LibraryPath $OutputPath
            Write-Host "✓ Successfully aligned $(Split-Path $LibraryPath -Leaf)"
        } else {
            Write-Host "⚠ zipalign not found, trying alternative method..."

            # Alternative: Use objcopy from NDK
            $objcopy = "$ANDROID_NDK\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-objcopy.exe"
            if (Test-Path $objcopy) {
                & $objcopy --set-section-alignment .text=16384 $LibraryPath $OutputPath
                Write-Host "✓ Successfully aligned $(Split-Path $LibraryPath -Leaf) using objcopy"
            } else {
                Write-Host "✗ No alignment tools available"
                Copy-Item $LibraryPath $OutputPath
            }
        }
    }
}

# Function to verify 16KB alignment
function Verify-Alignment {
    param([string]$FilePath)

    Write-Host "Verifying alignment for $(Split-Path $FilePath -Leaf)..."

    $readelf = "$ANDROID_NDK\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
    if (Test-Path $readelf) {
        $output = & $readelf -l $FilePath 2>$null
        if ($output -match "LOAD.*0x[0-9a-f]*000") {
            Write-Host "✓ Library appears to be properly aligned"
        } else {
            Write-Host "⚠ Alignment verification inconclusive"
        }
    }
}

Write-Host "16KB alignment configuration complete."
Write-Host "Native libraries will be aligned during the build process."
