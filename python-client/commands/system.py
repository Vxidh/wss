import pyautogui
import time
import base64
import io
import webbrowser
import subprocess # For running shell commands and launching applications
import os         # For file path manipulation and launching applications
import requests   # For downloading and uploading files

class SystemCommands:
    def __init__(self):
        # You can add any global settings or initializations for system commands here
        pass

    def screenshot(self, params):
        filename = params.get('filename')

        try:
            # Take the screenshot
            screenshot_pil = pyautogui.screenshot()

            # Generate a default filename if none provided
            if not filename:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                filename = f"screenshot_{timestamp}.png"

            # --- IMPORTANT: Construct the full path explicitly ---
            # This makes it clear where the file WILL be saved
            current_dir = os.getcwd() # Get the current working directory of the script
            full_path = os.path.join(current_dir, filename)

            # Save the image to the constructed full path
            screenshot_pil.save(full_path) 
            # Add a debug print to the Python client's console
            print(f"DEBUG: Screenshot successfully saved to: {full_path}") # <--- NEW DEBUG PRINT

            # Save to a BytesIO object for base64 encoding (for response)
            buffered_image = io.BytesIO()
            screenshot_pil.save(buffered_image, format="PNG")
            img_str = base64.b64encode(buffered_image.getvalue()).decode('utf-8')

            return {
                "status": "success",
                "message": f"Screenshot taken and saved as {full_path}", # Return full path in message
                "data": {
                    "filename": full_path, # Return the full path for clarity in response
                    "base64_image": img_str,
                    'size': screenshot_pil.size # Include size for completeness
                }
            }
        except Exception as e:
            # Print the error to console for debugging on the client side
            print(f"ERROR: Failed to take or save screenshot: {e}") 
            return {
                "status": "error",
                "message": f"Failed to take or save screenshot: {e}"
            }
    
    def get_screen_size(self, data):
        """Get screen dimensions"""
        size = pyautogui.size()
        
        return {
            "status": "success",
            "action": "get_screen_size",
            "width": size.width,
            "height": size.height
        }
    
    def ping(self, data):
        """Simple ping command"""
        return {
            "status": "success",
            "action": "ping",
            "timestamp": time.time(),
            "message": "pong"
        }

    def open_url(self, params): 
        """Open a URL in the default web browser."""
        url = params.get('url')
        if not url:
            return {"status": "error", "message": "Missing 'url' parameter for open_url."}
        
        try:
            print(f"System: Opening URL: {url}")
            webbrowser.open(url) 
            return {"status": "success", "message": f"Successfully opened URL: {url}"}
        except Exception as e:
            print(f"System: Error opening URL {url}: {e}")
            return {"status": "error", "message": f"Failed to open URL: {e}"}

    # --- NEW: File Operations ---

    def read_file(self, params):
        """
        Reads the content of a text file.
        params: {'path': 'path/to/file.txt'}
        """
        file_path = params.get('path')
        if not file_path:
            return {"status": "error", "message": "Missing 'path' parameter for read_file."}
        
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            print(f"System: Read content from {file_path}")
            return {"status": "success", "message": f"File '{file_path}' read successfully.", "content": content}
        except FileNotFoundError:
            return {"status": "error", "message": f"File not found: {file_path}"}
        except Exception as e:
            return {"status": "error", "message": f"Error reading file '{file_path}': {str(e)}"}

    def write_file(self, params):
        """
        Writes content to a text file. Overwrites by default, appends if specified.
        params: {'path': 'path/to/file.txt', 'content': 'Hello World', 'append': False}
        """
        file_path = params.get('path')
        content = params.get('content', '')
        append_mode = params.get('append', False) # If True, appends; otherwise, overwrites

        if not file_path:
            return {"status": "error", "message": "Missing 'path' parameter for write_file."}
        
        mode = 'a' if append_mode else 'w'
        try:
            # Ensure directory exists
            os.makedirs(os.path.dirname(file_path), exist_ok=True)
            with open(file_path, mode, encoding='utf-8') as f:
                f.write(content)
            print(f"System: Wrote content to {file_path} (mode: {mode})")
            return {"status": "success", "message": f"Content written to '{file_path}' successfully."}
        except Exception as e:
            return {"status": "error", "message": f"Error writing to file '{file_path}': {str(e)}"}

    def download_file(self, params):
        """
        Downloads a file from a URL to a specified local path.
        params: {'url': 'http://example.com/file.txt', 'destination_path': 'C:/temp/downloaded.txt'}
        """
        url = params.get('url')
        destination_path = params.get('destination_path')

        if not url or not destination_path:
            return {"status": "error", "message": "Missing 'url' or 'destination_path' parameter for download_file."}
        
        try:
            print(f"System: Downloading from {url} to {destination_path}")
            response = requests.get(url, stream=True)
            response.raise_for_status() # Raise an exception for HTTP errors (4xx or 5xx)

            # Ensure directory exists
            os.makedirs(os.path.dirname(destination_path), exist_ok=True)

            with open(destination_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            print(f"System: Downloaded {url} to {destination_path}")
            return {"status": "success", "message": f"File downloaded to '{destination_path}' successfully."}
        except requests.exceptions.RequestException as e:
            return {"status": "error", "message": f"Network error downloading file: {str(e)}"}
        except Exception as e:
            return {"status": "error", "message": f"Error downloading file: {str(e)}"}

    def upload_file(self, params):
        """
        Uploads a local file to a specified URL via HTTP POST.
        params: {'source_path': 'C:/temp/upload.txt', 'upload_url': 'http://example.com/upload', 'form_field_name': 'file'}
        """
        source_path = params.get('source_path')
        upload_url = params.get('upload_url')
        form_field_name = params.get('form_field_name', 'file') # Name of the form field for the file

        if not source_path or not upload_url:
            return {"status": "error", "message": "Missing 'source_path' or 'upload_url' parameter for upload_file."}
        
        if not os.path.exists(source_path):
            return {"status": "error", "message": f"Source file not found for upload: {source_path}"}

        try:
            print(f"System: Uploading {source_path} to {upload_url}")
            with open(source_path, 'rb') as f:
                files = {form_field_name: f}
                response = requests.post(upload_url, files=files)
                response.raise_for_status() # Raise an exception for HTTP errors

            print(f"System: Uploaded {source_path}. Server response: {response.status_code}")
            return {"status": "success", "message": f"File '{source_path}' uploaded successfully. Server response: {response.text}"}
        except requests.exceptions.RequestException as e:
            return {"status": "error", "message": f"Network error uploading file: {str(e)}"}
        except Exception as e:
            return {"status": "error", "message": f"Error uploading file: {str(e)}"}

    # --- NEW: OS Control Commands ---

    def run_shell_command(self, params):
        """
        Executes a shell command on the system.
        WARNING: This command is extremely powerful and can be dangerous if used with untrusted input.
                 Ensure commands are sanitized or come from a trusted source.
        params: {
            'command': 'dir C:\\', (Windows) or 'ls -l /' (Linux/macOS)
            'wait_for_completion': True, # Optional: wait for command to finish (default: True)
            'capture_output': True       # Optional: capture stdout/stderr (default: True)
        }
        """
        command = params.get('command')
        wait_for_completion = params.get('wait_for_completion', True)
        capture_output = params.get('capture_output', True)

        if not command:
            return {"status": "error", "message": "Missing 'command' parameter for run_shell_command."}

        try:
            print(f"System: Running shell command: '{command}'")
            if wait_for_completion:
                result = subprocess.run(
                    command,
                    shell=True, # DANGER: Set to True to run command via shell (e.g., cmd.exe, bash)
                                # Consider False if you can split command into list and avoid shell injection
                    capture_output=capture_output,
                    text=True,  # Decode stdout/stderr as text
                    check=True  # Raise CalledProcessError for non-zero exit codes
                )
                return {
                    "status": "success",
                    "message": f"Command '{command}' executed.",
                    "stdout": result.stdout if capture_output else None,
                    "stderr": result.stderr if capture_output else None,
                    "returncode": result.returncode
                }
            else:
                # For non-blocking execution
                subprocess.Popen(
                    command,
                    shell=True, # DANGER: Same warning as above
                    stdout=subprocess.DEVNULL, # Don't capture output for non-blocking
                    stderr=subprocess.DEVNULL
                )
                print(f"System: Launched shell command '{command}' in background.")
                return {"status": "success", "message": f"Command '{command}' launched in background."}
        except subprocess.CalledProcessError as e:
            return {
                "status": "error",
                "message": f"Shell command failed with exit code {e.returncode}: {e}",
                "stdout": e.stdout,
                "stderr": e.stderr,
                "returncode": e.returncode
            }
        except Exception as e:
            return {"status": "error", "message": f"Error running shell command: {str(e)}"}

    def launch_application(self, params):
        """
        Launches an application or executable.
        params: {
            'app_path': 'C:\\Program Files\\Notepad++\\notepad++.exe', (Windows) or '/Applications/TextEdit.app' (macOS)
            'args': ['file.txt', '--new-window'] # Optional: list of arguments to pass to the app
        }
        """
        app_path = params.get('app_path')
        args = params.get('args', [])

        if not app_path:
            return {"status": "error", "message": "Missing 'app_path' parameter for launch_application."}
        
        try:
            # Construct the command list: [app_path, arg1, arg2, ...]
            command_list = [app_path] + args
            
            # Use subprocess.Popen for non-blocking launch
            subprocess.Popen(command_list) 
            
            print(f"System: Launched application: '{app_path}' with args: {args}")
            return {"status": "success", "message": f"Application '{app_path}' launched successfully."}
        except FileNotFoundError:
            return {"status": "error", "message": f"Application not found at path: {app_path}"}
        except Exception as e:
            return {"status": "error", "message": f"Error launching application '{app_path}': {str(e)}"}