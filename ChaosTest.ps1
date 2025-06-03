# --- Configuration ---
# You MUST replace this with the actual nodeId of your Python client
# This ID is usually printed by your Python client when it connects to the Relay.
$nodeId = "988a53e0-0982-4eaf-ac50-5af20a7e07ad" 
$relayServerUrl = "http://localhost:8081" # The Relay Server's HTTP endpoint
$baseCommandUrl = "$relayServerUrl/send_test_command?nodeId=$nodeId&action="

# --- Helper Function: URL Encode Parameters ---
function ConvertTo-UrlEncodedJson {
    param (
        [Parameter(Mandatory=$true)]
        [hashtable]$Params
    )
    $json = $Params | ConvertTo-Json -Depth 10 -Compress # -Compress removes whitespace
    # URL encode the JSON string
    return [System.Web.HttpUtility]::UrlEncode($json)
}

# --- Main Script Execution ---
Write-Host "--- Starting Ultimate Automation Chaos Test (HTTP-based) ---" -ForegroundColor Yellow

if ($nodeId -eq "YOUR_PYTHON_CLIENT_NODE_ID_HERE") {
    Write-Error "ERROR: Please update the `$nodeId` variable in the script with your actual Python client's Node ID."
    Exit
}

# Variable to hold recording_id if successfully started
$activeRecordingId = $null

try {
    # 1. Start Recording Proof of Automation
    Write-Host "`n1. Sending: start_recording_proof" -ForegroundColor Cyan
    $action = "start_recording_proof"
    $params = @{ interval = 0.5; output_prefix = "chaos_test" }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop
        Write-Host "  Response: $($response.StatusCode) $($response.StatusDescription)" -ForegroundColor Green
        # Assuming the Relay might return the recording_id directly in the HTTP response body
        # or it's handled internally. For now, rely on Python client to store it.
        # If your Relay's HTTP endpoint *returns* the recording_id, you'd parse $response.Content here.
        # For this test, we'll assume the Python client stores it internally and stop_recording_proof picks it up.
    } catch {
        Write-Warning "  Failed to send start_recording_proof: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 2 seconds..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 2

    # 2. Get Current Screen Size
    Write-Host "`n2. Sending: get_screen_size" -ForegroundColor Cyan
    $action = "get_screen_size"
    $uri = "$baseCommandUrl$action" # No params needed
    try {
        $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop
        Write-Host "  Response: $($response.StatusCode) $($response.StatusDescription)" -ForegroundColor Green
        # You could parse $response.Content here if the Relay sends command result back via HTTP sync response
    } catch {
        Write-Warning "  Failed to send get_screen_size: $($_.Exception.Message)"
    }

    # 3. Open Browser to Google
    Write-Host "`n3. Sending: open_url (Google)" -ForegroundColor Cyan
    $action = "open_url"
    $params = @{ url = "https://www.google.com" }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent open_url command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send open_url (Google): $($_.Exception.Message)"
    }
    Write-Host "  Waiting 5 seconds for Google page to load..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 5

    Write-Host "`n3a. Sending: activate_window (Browser)" -ForegroundColor Magenta
    $action = "activate_window"
    # Choose a substring of your browser's window title that is reliable.
    # Common examples: "Google Chrome", "Mozilla Firefox", "Microsoft Edge", or just "Google"
    # When Google.com opens, its title usually includes "Google".
    $browserTitleSubstring = "Google" 
    $params = @{ window_title_substring = $browserTitleSubstring; timeout = 15 } # Increased timeout for window activation
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent activate_window command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send activate_window: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 2 seconds for activation to settle..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 2

    # 4. Type Search Query
    Write-Host "`n4. Sending: type_text" -ForegroundColor Cyan
    $action = "type_text"
    $params = @{ text = "AI Automation Examples" }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent type_text command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send type_text: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 2 seconds..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 2

    # 5. Press Enter
    Write-Host "`n5. Sending: key_press (Enter)" -ForegroundColor Cyan
    $action = "key_press"
    $params = @{ key = "enter" }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent key_press command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send key_press: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 3 seconds for search results to load..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 3

    # 6. Screenshot Search Results
    Write-Host "`n6. Sending: screenshot (search results)" -ForegroundColor Cyan
    $action = "screenshot"
    $filename = "chaos_search_results_$(Get-Date -Format 'yyyyMMdd_HHmmss').png"
    $params = @{ filename = $filename }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent screenshot command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send screenshot: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 1 second..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 1

    # 7. Run a Successful Shell Command
    Write-Host "`n7. Sending: run_shell_command (whoami)" -ForegroundColor Cyan
    $action = "run_shell_command"
    $command = "whoami" # Use "id -un" for Linux/macOS
    $params = @{ command = $command; wait_for_completion = $true; capture_output = $true }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent run_shell_command (whoami)." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send run_shell_command (whoami): $($_.Exception.Message)"
    }
    Write-Host "  Waiting 2 seconds..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 2

    # 8. Write Initial Log File
    Write-Host "`n8. Sending: write_file (initial log)" -ForegroundColor Cyan
    $action = "write_file"
    $logFilePath = "chaos_log.txt"
    $initialContent = "Chaos test initiated at $(Get-Date).`n"
    $params = @{ path = $logFilePath; content = $initialContent }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent write_file command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send write_file: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 1 second..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 1

    # 9. Download a Public Test File
    Write-Host "`n9. Sending: download_file (1MB.zip)" -ForegroundColor Cyan
    $action = "download_file"
    $downloadUrl = "http://speedtest.tele2.net/1MB.zip"
    $downloadDestPath = "downloaded_1MB_file.zip"
    $params = @{ url = $downloadUrl; destination_path = $downloadDestPath }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent download_file command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send download_file: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 5 seconds for download..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 5

    # 10. Simulate Failed API Call (via Download - for error handling)
    Write-Host "`n10. Sending: download_file (simulating failed API call)" -ForegroundColor Cyan
    $action = "download_file"
    $mockApiUrl = "http://localhost:8081/api/nonexistent_data" # This endpoint should not exist
    $apiResponsePath = "api_response.json"
    $params = @{ url = $mockApiUrl; destination_path = $apiResponsePath }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent simulated API call command (expected to fail on bot side)." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send simulated API call: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 2 seconds..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 2

    # 11. Append Placeholder Results to Log File (Actual results would be in bot's logs)
    Write-Host "`n11. Sending: write_file (append results to log)" -ForegroundColor Cyan
    $action = "write_file"
    $appendContent = "`n--- Observed Command Results (from bot's perspective) ---`n" +
                     "Download attempt for 1MB.zip completed (check Python client log for status).`n" +
                     "Simulated API call to $($mockApiUrl) attempted (check Python client log for error).`n" +
                     "End of test sequence: $(Get-Date).`n"
    $params = @{ path = $logFilePath; content = $appendContent; append = $true }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent write_file (append) command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send write_file (append): $($_.Exception.Message)"
    }
    Write-Host "  Waiting 1 second..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 1

    # 12. Read Log File
    Write-Host "`n12. Sending: read_file (read log)" -ForegroundColor Cyan
    $action = "read_file"
    $params = @{ path = $logFilePath }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent read_file command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send read_file: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 1 second..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 1

    # 13. Launch Notepad/TextEdit
    Write-Host "`n13. Sending: launch_application" -ForegroundColor Cyan
    $action = "launch_application"
    $appToLaunch = "notepad.exe" # Change to "/Applications/TextEdit.app" for macOS
    $params = @{ app_path = $appToLaunch }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent launch_application command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send launch_application: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 2 seconds for app to open..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 2

    # 14. Run a Failing Shell Command
    Write-Host "`n14. Sending: run_shell_command (designed to fail)" -ForegroundColor Cyan
    $action = "run_shell_command"
    $failCommand = "this_command_does_not_exist_123xyz"
    $params = @{ command = $failCommand; wait_for_completion = $true; capture_output = $true }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent failing run_shell_command (expected error on bot side)." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send failing run_shell_command: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 2 seconds..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 2

    # 15. Attempt Email Client Activation (via mailto: link)
    Write-Host "`n15. Sending: open_url (mailto: link)" -ForegroundColor Cyan
    $action = "open_url"
    $params = @{ url = "mailto:stress-test@example.com?subject=Automation%20Chaos%20Test%20Summary&body=See%20attached%20log." }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent mailto: command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send mailto: command: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 1 second..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 1

    # 16. Take Final Screenshot
    Write-Host "`n16. Sending: screenshot (final state)" -ForegroundColor Cyan
    $action = "screenshot"
    $finalFilename = "chaos_final_state_$(Get-Date -Format 'yyyyMMdd_HHmmss').png"
    $params = @{ filename = $finalFilename }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent final screenshot command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send final screenshot: $($_.Exception.Message)"
    }
    Write-Host "  Waiting 1 second..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 1

    # 17. Stop Recording Proof & Generate Video
    Write-Host "`n17. Sending: stop_recording_proof" -ForegroundColor Cyan
    $action = "stop_recording_proof"
    # IMPORTANT: The recording_id needs to be passed here if your system requires it.
    # If the Python client stores and retrieves it internally, you can omit.
    # Based on system.py, it tries to get from node_client.current_task_state.
    # So, we'll try without explicitly passing recording_id here for simplicity, assuming the bot maintains it.
    # If it fails, you might need to find a way to get the ID from the first response.
    $params = @{ video_filename = "chaos_test_video_$(Get-Date -Format 'yyyyMMdd_HHmmss').mp4"; fps = 10 }
    $encodedParams = ConvertTo-UrlEncodedJson $params
    $uri = "$baseCommandUrl$action&params=$encodedParams"
    try {
        Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "  Successfully sent stop_recording_proof command." -ForegroundColor Green
    } catch {
        Write-Warning "  Failed to send stop_recording_proof: $($_.Exception.Message)"
    }

    Write-Host "`n--- Ultimate Automation Chaos Test Complete! ---`n" -ForegroundColor Green

} catch {
    Write-Error "An unhandled error occurred during the script execution: $($_.Exception.Message)"
}