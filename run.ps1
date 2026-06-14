$ErrorActionPreference = "Stop"

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvn) {
    & mvn clean package
} else {
    $tools = Join-Path (Get-Location) ".tools"
    $mavenDir = Join-Path $tools "apache-maven-3.9.9"
    if (!(Test-Path $mavenDir)) {
        New-Item -ItemType Directory -Force -Path $tools | Out-Null
        $zip = Join-Path $tools "apache-maven-3.9.9-bin.zip"
        if (!(Test-Path $zip)) {
            Invoke-WebRequest `
                -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip" `
                -OutFile $zip
        }
        Expand-Archive -Path $zip -DestinationPath $tools -Force
    }
    & (Join-Path $mavenDir "bin\mvn.cmd") clean package
}

java -Xmx2g -jar target\YYYP-IIS-1.0.0.jar
