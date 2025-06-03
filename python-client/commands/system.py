import pyautogui
import time
import base64
import io
import webbrowser
import subprocess 
import os         
import requests   
import uuid      
import cv2      
import numpy as np 
import threading 
import shutil
from datetime import datetime
import json
import psutil 
import pygetwindow as gw
import traceback
import logging
log = logging.getLogger(__name__)

class SystemCommands:
    def __init__(self, node_client_ref=None): 
        self.node_client = node_client_ref # Store reference to NodeClient for shared state
        self.is_recording = False         
        self.recording_thread = None      
        self.output_folder = "automation_proofs" 
        os.makedirs(self.output_folder, exist_ok=True)
        if not hasattr(self.node_client, 'current_task_state'):
            self.node_client.current_task_state = {}
        if 'active_recording_id' not in self.node_client.current_task_state:
            self.node_client.current_task_state['active_recording_id'] = None
            self.node_client.current_task_state['screenshot_paths'] = {}

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
        
    # In commands/system.py, inside the SystemCommands class

    def _screenshot_loop(self, interval_seconds, output_prefix, recording_id):
        """
        Internal method that runs in a separate thread to continuously take screenshots.
        """
        image_paths = []
        screenshot_counter = 0

        # Create a unique sub-folder for this recording session's images
        current_recording_folder = os.path.join(self.output_folder, recording_id)
        os.makedirs(current_recording_folder, exist_ok=True)

        print(f"Starting screenshot recording to {current_recording_folder} with interval {interval_seconds}s")

        while self.is_recording: # Loop as long as the flag is True
            try:
                screenshot_pil = pyautogui.screenshot()
                filename = f"{output_prefix}_{screenshot_counter:05d}.png" # e.g., 'automation_session_00001.png'
                file_path = os.path.join(current_recording_folder, filename)
                screenshot_pil.save(file_path)
                image_paths.append(file_path)
                screenshot_counter += 1
                time.sleep(interval_seconds)
            except Exception as e:
                print(f"Error during screenshot loop: {e}")
                # If an error occurs, stop recording to prevent continuous failures
                self.is_recording = False

        print("Screenshot recording loop stopped.")
        # Store the list of captured image paths and the folder for later video creation
        if self.node_client and hasattr(self.node_client, 'current_task_state'):
            self.node_client.current_task_state[recording_id] = {
                'image_paths': image_paths,
                'recording_folder': current_recording_folder
            }
            print(f"Screenshot paths stored in NodeClient state for recording ID: {recording_id}")

    def start_recording_proof(self, params):
        """
        Starts recording screenshots at a specified interval.
        Expected params: {'interval': float, 'output_prefix': str}
        """
        if self.is_recording:
            return {"status": "error", "message": "Already recording proof of automation."}

        interval = params.get('interval', 0.5) # Default to 0.5 seconds
        output_prefix = params.get('output_prefix', 'automation_session') # Default prefix

        # Generate a unique ID for this specific recording session
        recording_id = f"recording_{datetime.now().strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:8]}"

        self.is_recording = True # Set the flag to True to start the loop
        self.recording_thread = threading.Thread(
            target=self._screenshot_loop,
            args=(interval, output_prefix, recording_id),
            daemon=True # Daemon thread exits when main program exits
        )
        self.recording_thread.start()

        # Store the active recording ID in NodeClient's state
        if self.node_client and hasattr(self.node_client, 'current_task_state'):
            self.node_client.current_task_state['active_recording_id'] = recording_id
            print(f"Active recording ID set in NodeClient state: {recording_id}")

        return {
            "status": "success",
            "message": f"Started recording proof of automation (ID: {recording_id}) every {interval} seconds.",
            "recording_id": recording_id
        }

    def stop_recording_proof(self, params):
        """
        Stops recording screenshots and creates an MP4 video from the captured images.
        Expected params: {'recording_id': str (optional), 'video_filename': str (optional), 'fps': int (optional)}
        """
        # Determine the recording_id to process
        recording_id = params.get('recording_id')
        if not recording_id and self.node_client and hasattr(self.node_client, 'current_task_state'):
            recording_id = self.node_client.current_task_state.get('active_recording_id')

        # Check if there's an active recording session based on the flag or the ID
        if not self.is_recording and not recording_id:
             return {"status": "error", "message": "No active recording to stop or recording ID provided."}

        # Stop the screenshot capture loop
        self.is_recording = False
        if self.recording_thread and self.recording_thread.is_alive():
            print("Waiting for recording thread to finish its last screenshot cycle...")
            self.recording_thread.join(timeout=10) # Wait for thread to finish gracefully
            if self.recording_thread.is_alive():
                print("Warning: Recording thread did not terminate gracefully within timeout.")

        # Retrieve the collected image paths and folder from NodeClient's state
        # Pop the recording data to clean it from the active state
        recording_data = {}
        if self.node_client and hasattr(self.node_client, 'current_task_state') and recording_id in self.node_client.current_task_state:
            recording_data = self.node_client.current_task_state.pop(recording_id)
            if self.node_client.current_task_state.get('active_recording_id') == recording_id:
                del self.node_client.current_task_state['active_recording_id'] # Clear active ID if it matches
        else:
            return {"status": "error", "message": f"Could not find recording data for ID: {recording_id}. Was it already stopped or never started?"}


        image_paths = recording_data.get('image_paths', [])
        recording_folder = recording_data.get('recording_folder')

        if not image_paths:
            # Clean up empty folder even if no images
            try:
                if recording_folder and os.path.exists(recording_folder) and os.path.isdir(recording_folder):
                    shutil.rmtree(recording_folder)
                    print(f"Cleaned up empty temporary screenshot folder: {recording_folder}")
            except Exception as clean_e:
                print(f"Warning: Could not clean up empty folder {recording_folder}: {clean_e}")
            return {"status": "error", "message": "No screenshots captured to create video."}

        # Determine output filename and video parameters
        video_filename = params.get('video_filename', f"{recording_id}_proof.mp4")
        fps = params.get('fps', 20) # Default frames per second for the video
        if fps <= 0: fps = 1 # Prevent division by zero

        # Ensure video is saved in the main output_folder
        video_output_path = os.path.join(self.output_folder, video_filename)

        try:
            # Read the first image to get dimensions for the video
            first_image = cv2.imread(image_paths[0])
            if first_image is None:
                # If first image is not found, try to find a valid one or error
                valid_image_found = False
                for img_path in image_paths:
                    first_image = cv2.imread(img_path)
                    if first_image is not None:
                        valid_image_found = True
                        break
                if not valid_image_found:
                    return {"status": "error", "message": "Could not read any valid images to create video."}

            height, width, layers = first_image.shape
            size = (width, height)

            # Define the codec and create VideoWriter object
            fourcc = cv2.VideoWriter_fourcc(*'mp4v') # Codec for MP4
            out = cv2.VideoWriter(video_output_path, fourcc, fps, size)

            if not out.isOpened():
                print(f"Error: Could not open video writer for path: {video_output_path}")
                print("Common reasons: Incorrect FourCC code or file permissions.")
                return {"status": "error", "message": "Failed to initialize video writer. Check codec and path."}

            for image_path in image_paths:
                img = cv2.imread(image_path)
                if img is not None:
                    out.write(img) # Write the frame to the video
                else:
                    print(f"Warning: Could not read image {image_path}, skipping.")

            out.release() # Release the video writer
            print(f"Video created successfully at: {video_output_path}")

            # Optional: Clean up temporary screenshot folder after video creation
            if recording_folder and os.path.exists(recording_folder) and os.path.isdir(recording_folder):
                try:
                    shutil.rmtree(recording_folder)
                    print(f"Cleaned up temporary screenshot folder: {recording_folder}")
                except Exception as clean_e:
                    print(f"Warning: Could not clean up temporary folder {recording_folder}: {clean_e}")

            return {
                "status": "success",
                "message": f"Proof of automation video created: {video_output_path}",
                "video_path": video_output_path
            }

        except Exception as e:
            print(f"Error creating video: {e}")
            return {"status": "error", "message": f"Failed to create video: {str(e)}"}

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
            
            print(f"System: Waiting for {duration} seconds...")
            time.sleep(duration)
            print(f"System: Wait complete.")
            return {"status": "success", "message": f"Waited for {duration} seconds."}
        except ValueError:
            return {"status": "error", "message": "Invalid 'duration' parameter. Must be a number."}
        except Exception as e:
            return {"status": "error", "message": f"Error during wait: {str(e)}"}
        
    def activate_window(self, params):
        print("--- DEBUG: Entered activate_window function ---")
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
                # Find all windows that match the substring
                windows = gw.getWindowsWithTitle(window_title_substring) # Use gw alias
                
                # Filter for visible, non-minimized windows
                visible_windows = [w for w in windows if w.visible and not w.isMinimized]
                
                if visible_windows:
                    target_window = visible_windows[0]
                    
                    print(f"--- DEBUG: Found window with title: '{target_window.title}' attempting to activate. ---")
                    log.info(f"System: Attempting to activate window: '{target_window.title}' (PID: {target_window.pid if hasattr(target_window, 'pid') else 'N/A'})")

                    # Try normal activation first
                    target_window.activate()
                    time.sleep(0.5) 

                    # If not active, use Alt+Tab as a fallback
                    active_after_activate = gw.getActiveWindow()
                    if not (active_after_activate and window_title_substring.lower() in active_after_activate.title.lower()):
                        log.warning(f"System: Direct activation failed for '{window_title_substring}'. Trying Alt+Tab...")
                        
                        # Press Alt+Tab
                        pyautogui.hotkey('alt', 'tab')
                        time.sleep(0.5) # Give time for Alt+Tab switcher to appear

                        # Now, find the window in the Alt+Tab list and release Alt
                        # This part is tricky. A simpler approach is to repeatedly Alt+Tab until it's active.
                        # For robustness, we'll just try Alt+Tab a few times if needed.

                        # Re-check active window after Alt+Tab
                        active_after_alt_tab = gw.getActiveWindow()
                        if active_after_alt_tab and window_title_substring.lower() in active_after_alt_tab.title.lower():
                            log.info(f"System: Successfully activated window using Alt+Tab: '{active_after_alt_tab.title}'")
                            activated = True
                            active_window_title = active_after_alt_tab.title
                            break # Exit loop if activated
                        else:
                            # If still not active, try another Alt+Tab cycle (might need multiple for complex layouts)
                            log.warning(f"System: Alt+Tab attempt for '{window_title_substring}' did not immediately activate. Current active: {active_after_alt_tab.title if active_after_alt_tab else 'None'}. Retrying...")
                            pyautogui.hotkey('alt', 'tab') # Try again
                            time.sleep(0.5)
                            active_after_alt_tab_2 = gw.getActiveWindow()
                            if active_after_alt_tab_2 and window_title_substring.lower() in active_after_alt_tab_2.title.lower():
                                log.info(f"System: Successfully activated window after second Alt+Tab: '{active_after_alt_tab_2.title}'")
                                activated = True
                                active_window_title = active_after_alt_tab_2.title
                                break
                    else: # If direct activation worked
                        log.info(f"System: Successfully activated window directly: '{active_after_activate.title}'")
                        activated = True
                        active_window_title = active_after_activate.title
                        break 
                else:
                    log.debug(f"System: No visible window found with title substring '{window_title_substring}'. Retrying...")
                    time.sleep(1) 
            except Exception as e:
                print(f"DEBUG: Inner loop error in activate_window: {e}") 
                log.warning(f"System: Error while trying to activate window: {e}")
                time.sleep(1) 

        if activated:
            return {"status": "success", "message": f"Window '{active_window_title}' activated."}
        else:
            log.error(f"System: Failed to activate window with title substring '{window_title_substring}' within {timeout} seconds. Current active window: {active_window_title}")
            return {"status": "error", "message": f"Failed to activate window '{window_title_substring}'."}
