import pyautogui
import time
import base64
import io
import webbrowser
import subprocess
import os
import requests
from datetime import datetime
import pygetwindow as gw
import traceback
import logging

log = logging.getLogger(__name__)

class SystemCommands:
    def __init__(self, node_client_ref=None):
        self.node_client = node_client_ref
        self.output_folder = "automation_proofs"
        self._ensure_proofs_directory_exists()

    def _ensure_proofs_directory_exists(self):
        if not os.path.exists(self.output_folder):
            os.makedirs(self.output_folder)
            log.info(f"Created automation proofs directory: {self.output_folder}")

    def screenshot(self, params):
        """
        Takes a single screenshot, saves it locally, and returns its Base64 encoded data.
        Params:
            filename (str, optional): The name of the file to save the screenshot locally.
                                      If not provided, a default timestamped name will be used.
        Returns:
            dict: Status, message, and 'data' containing filename, base64_image, and size.
        """
        filename = params.get('filename')

        try:
            # Take the screenshot
            screenshot_pil = pyautogui.screenshot()

            # Generate a default filename if none provided
            if not filename:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")[:-3] # Add milliseconds for more uniqueness
                filename = f"screenshot_{timestamp}.png"
            full_path = os.path.join(self.output_folder, filename)
            screenshot_pil.save(full_path)
            log.info(f"Screenshot successfully saved to: {full_path}")
            buffered_image = io.BytesIO()
            screenshot_pil.save(buffered_image, format="PNG")
            img_str = base64.b64encode(buffered_image.getvalue()).decode('utf-8')

            return {
                "status": "success",
                "message": f"Screenshot taken and saved as {full_path}",
                "data": {
                    "filename": full_path, 
                    "base64_image": img_str,
                    'size': screenshot_pil.size 
                }
            }
        except Exception as e:
            log.error(f"Failed to take or save screenshot: {e}")
            traceback.print_exc() # Print full traceback to logs for debugging
            return {
                "status": "error",
                "message": f"Failed to take or save screenshot: {e}",
                "traceback": traceback.format_exc(), # Include traceback in response for remote debugging
                "data": {"base64_image": None} # Ensure image_data is None on error
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
            log.info(f"Opening URL: {url}")
            webbrowser.open(url) 
            return {"status": "success", "message": f"Successfully opened URL: {url}"}
        except Exception as e:
            log.error(f"Error opening URL {url}: {e}")
            return {"status": "error", "message": f"Failed to open URL: {e}"}

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
            log.info(f"Read content from {file_path}")
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
            log.info(f"Wrote content to {file_path} (mode: {mode})")
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
            log.info(f"Downloading from {url} to {destination_path}")
            response = requests.get(url, stream=True)
            response.raise_for_status() # Raise an exception for HTTP errors (4xx or 5xx)

            # Ensure directory exists
            os.makedirs(os.path.dirname(destination_path), exist_ok=True)

            with open(destination_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            log.info(f"Downloaded {url} to {destination_path}")
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
            log.info(f"Uploading {source_path} to {upload_url}")
            with open(source_path, 'rb') as f:
                files = {form_field_name: f}
                response = requests.post(upload_url, files=files)
                response.raise_for_status() # Raise an exception for HTTP errors

            log.info(f"Uploaded {source_path}. Server response: {response.status_code}")
            return {"status": "success", "message": f"File '{source_path}' uploaded successfully. Server response: {response.text}"}
        except requests.exceptions.RequestException as e:
            return {"status": "error", "message": f"Network error uploading file: {str(e)}"}
        except Exception as e:
            return {"status": "error", "message": f"Error uploading file: {str(e)}"}

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
            log.info(f"Running shell command: '{command}'")
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
                log.info(f"Launched shell command '{command}' in background.")
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
            
            log.info(f"Launched application: '{app_path}' with args: {args}")
            return {"status": "success", "message": f"Application '{app_path}' launched successfully."}
        except FileNotFoundError:
            return {"status": "error", "message": f"Application not found at path: {app_path}"}
        except Exception as e:
            return {"status": "error", "message": f"Error launching application '{app_path}': {str(e)}"}
        
    def wait(self, params):
        """
        Pauses execution for a specified number of seconds.
        params: {'duration': float}
        """
        duration = params.get('duration')
        if duration is None:
            return {"status": "error", "message": "Missing 'duration' parameter for wait."}
        
        try:
            duration = float(duration)
            if duration < 0:
                return {"status": "error", "message": "Duration must be a non-negative number for wait."}
            
            log.info(f"Waiting for {duration} seconds...")
            time.sleep(duration)
            log.info(f"Wait complete.")
            return {"status": "success", "message": f"Waited for {duration} seconds."}
        except ValueError:
            return {"status": "error", "message": "Invalid 'duration' parameter. Must be a number."}
        except Exception as e:
            return {"status": "error", "message": f"Error during wait: {str(e)}"}
        
    def activate_window(self, params):
        log.info("Attempting to activate window...")
        timeout = params.get('timeout', 10)
        window_title_substring = params.get('window_title_substring')
        
        if not window_title_substring:
            log.error("activate_window: 'window_title_substring' parameter is required.")
            return {"status": "error", "message": "'window_title_substring' is required."}

        start_time = time.time()
        activated = False
        active_window_title = "None" 

        while time.time() - start_time < timeout:
            try:
                windows = gw.getWindowsWithTitle(window_title_substring)
                visible_windows = [w for w in windows if w.visible and not w.isMinimized]
                
                if visible_windows:
                    target_window = visible_windows[0]
                    log.info(f"Found window with title: '{target_window.title}' attempting to activate.")

                    target_window.activate()
                    time.sleep(0.5) 

                    active_after_activate = gw.getActiveWindow()
                    if active_after_activate and window_title_substring.lower() in active_after_activate.title.lower():
                        log.info(f"Successfully activated window directly: '{active_after_activate.title}'")
                        activated = True
                        active_window_title = active_after_activate.title
                        break
                    else:
                        log.warning(f"Direct activation failed for '{window_title_substring}'. Trying Alt+Tab...")
                        pyautogui.hotkey('alt', 'tab')
                        time.sleep(0.5)

                        active_after_alt_tab = gw.getActiveWindow()
                        if active_after_alt_tab and window_title_substring.lower() in active_after_alt_tab.title.lower():
                            log.info(f"Successfully activated window using Alt+Tab: '{active_after_alt_tab.title}'")
                            activated = True
                            active_window_title = active_after_alt_tab.title
                            break
                        else:
                            log.warning(f"Alt+Tab attempt for '{window_title_substring}' did not immediately activate. Current active: {active_after_alt_tab.title if active_after_alt_tab else 'None'}. Retrying...")
                            time.sleep(1) # Give it a moment before next try
                else:
                    log.debug(f"No visible window found with title substring '{window_title_substring}'. Retrying...")
                    time.sleep(1) 
            except Exception as e:
                log.warning(f"Error while trying to activate window: {e}")
                traceback.print_exc() # Print full traceback to logs for debugging
                time.sleep(1) 

        if activated:
            return {"status": "success", "message": f"Window '{active_window_title}' activated."}
        else:
            log.error(f"Failed to activate window with title substring '{window_title_substring}' within {timeout} seconds. Current active window: {active_window_title}")
            return {"status": "error", "message": f"Failed to activate window '{window_title_substring}'."}