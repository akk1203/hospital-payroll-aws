# Fast deploy to the existing test stack. Skips CodePipeline (~3–4 min).
# Requires: AWS CLI, Java 21, Maven. Region must match the stack (ap-southeast-2).
#
#   .\scripts\deploy-fast.ps1           # Lambdas + UI
#   .\scripts\deploy-fast.ps1 -What ui  # UI only (~10s)
#   .\scripts\deploy-fast.ps1 -What api # Lambdas only

param(
    [ValidateSet("all", "ui", "api")]
    [string]$What = "all",
    [string]$StackName = "hospital-payroll-test",
    [string]$Region = "ap-southeast-2"
)

$ErrorActionPreference = "Stop"
$ScriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$Root = Split-Path -Parent $ScriptDir
Set-Location $Root
if (-not (Test-Path (Join-Path $Root "template.yaml"))) {
    throw "Run this from hospital-payroll-aws (template.yaml not found in $Root)"
}

function Get-StackOutput([string]$Key) {
    aws cloudformation describe-stacks `
        --region $Region `
        --stack-name $StackName `
        --query "Stacks[0].Outputs[?OutputKey=='$Key'].OutputValue" `
        --output text
}

function Sync-Ui {
    $apiUrl = Get-StackOutput "ApiUrl"
    $bucket = Get-StackOutput "UiBucketName"
    if (-not $apiUrl -or $apiUrl -eq "None" -or -not $bucket -or $bucket -eq "None") {
        throw "Could not read ApiUrl / UiBucketName from stack $StackName"
    }
    $configPath = Join-Path $Root "ui\config.js"
    $apiUrl = ($apiUrl | Out-String).Trim().Trim('"')
    $line = "window.PAYROLL_API = ""{0}/api"";" -f $apiUrl.TrimEnd("/")
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($configPath, $line + "`n", $utf8)
    aws s3 sync (Join-Path $Root "ui") "s3://$bucket" --region $Region --delete --exclude "*.md" --exclude "*.example"
    $uiUrl = Get-StackOutput "UiUrl"
    Write-Host ""
    Write-Host "Open this URL in the browser (do not open ui\index.html from disk):"
    Write-Host $uiUrl
    Write-Host ""
}

function Update-LambdaZip([string]$FunctionName, [string]$Zip, [string]$DataBucket, [string]$Key) {
    if (-not (Test-Path $Zip)) {
        throw "Missing $Zip"
    }
    Write-Host ("{0}: {1:N1} MB -> s3://{2}/{3}" -f $FunctionName, ((Get-Item $Zip).Length / 1MB), $DataBucket, $Key)
    aws s3 cp $Zip "s3://$DataBucket/$Key" --region $Region
    if ($LASTEXITCODE -ne 0) {
        throw "S3 upload failed for $Key"
    }
    $published = aws lambda update-function-code `
        --region $Region `
        --function-name $FunctionName `
        --s3-bucket $DataBucket `
        --s3-key $Key `
        --publish | ConvertFrom-Json
    aws lambda wait function-updated --region $Region --function-name $FunctionName
    $version = $published.Version
    if (-not $version -or $version -eq "`$LATEST") {
        $published = aws lambda publish-version `
            --region $Region `
            --function-name $FunctionName | ConvertFrom-Json
        $version = $published.Version
    }
    if (-not $version) {
        throw "Lambda $FunctionName did not return a published version"
    }
    aws lambda update-alias `
        --region $Region `
        --function-name $FunctionName `
        --name live `
        --function-version $version | Out-Null
    Write-Host "$FunctionName alias live -> version $version"
}

function Deploy-Api {
    cmd /c "mvn -f `"$Root\pom.xml`" -DskipTests package"
    if ($LASTEXITCODE -ne 0) {
        throw "Maven package failed"
    }
    $functionName = Get-StackOutput "FunctionName"
    if (-not $functionName -or $functionName -eq "None") {
        $functionName = "$StackName-api"
    }
    $filesName = Get-StackOutput "FilesFunctionName"
    if (-not $filesName -or $filesName -eq "None") {
        $filesName = "$StackName-files"
    }

    $dataBucket = aws lambda get-function-configuration `
        --region $Region `
        --function-name $functionName `
        --query "Environment.Variables.DATA_BUCKET" `
        --output text
    if (-not $dataBucket -or $dataBucket -eq "None") {
        $dataBucket = aws cloudformation describe-stack-resources `
            --region $Region `
            --stack-name $StackName `
            --query "StackResources[?LogicalResourceId=='DataBucket'].PhysicalResourceId" `
            --output text
    }
    if (-not $dataBucket -or $dataBucket -eq "None") {
        throw "Could not find the data S3 bucket for Lambda upload"
    }

    Update-LambdaZip $functionName (Join-Path $Root "api\target\function.zip") $dataBucket "lambda/api.zip"

    aws lambda get-function --region $Region --function-name $filesName | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "The files Lambda ($filesName) is not in AWS yet. Create it once by pushing main (pipeline) or:"
        Write-Host "  sam deploy --template-file template.yaml --stack-name $StackName --resolve-s3 --capabilities CAPABILITY_IAM --no-confirm-changeset"
        Write-Host "Until then, Excel/PDF download and attendance Excel upload will fail."
        Write-Host ""
        return
    }
    Update-LambdaZip $filesName (Join-Path $Root "files\target\function.zip") $dataBucket "lambda/files.zip"
}

Write-Host "Fast deploy ($What) -> $StackName in $Region"
if ($What -eq "all" -or $What -eq "api") {
    Deploy-Api
}
if ($What -eq "all" -or $What -eq "ui") {
    Sync-Ui
}
Write-Host "Done. Recalculate payroll in the UI so existing slips pick up new pay rules."
