# Check if running as administrator
if (-NOT ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")) {
    Write-Warning "Please run PowerShell as Administrator!"
    Write-Host "Instructions:"
    Write-Host "1. Close this PowerShell window"
    Write-Host "2. Right-click on PowerShell"
    Write-Host "3. Select 'Run as Administrator'"
    Write-Host "4. Navigate to the script location: cd C:\Users\Vibha\wss"
    Write-Host "5. Run this script again: .\install-chocolatey.ps1"
    pause
    exit
}

# Install Chocolatey
Set-ExecutionPolicy Bypass -Scope Process -Force
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# To install Chocolatey on Windows:
# 1. Open PowerShell as Administrator (right-click, "Run as Administrator")
# 2. Run the following command:
#    Set-ExecutionPolicy Bypass -Scope Process -Force; `
#    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; `
#    iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

Write-Host "Installation complete! Please close and reopen PowerShell as Administrator, then run:"
Write-Host "choco install jdk8 maven apache-httpd -y"
pause
