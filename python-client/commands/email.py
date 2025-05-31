# email.py
import pyautogui
import time
import os # For file path manipulation for attachments

class EmailUICommands:
    def __init__(self):
        pyautogui.FAILSAFE = True
        pyautogui.PAUSE = 0.5 # A default pause between actions for UI stability

    def _find_and_click_image(self, image_path, confidence=0.9, attempts=3, retry_delay=1.0):
        """Helper to find an image on screen and click its center."""
        for attempt in range(attempts):
            try:
                # pyautogui.locateOnScreen returns (left, top, width, height) if found
                location = pyautogui.locateOnScreen(image_path, confidence=confidence)
                if location:
                    center_x, center_y = pyautogui.center(location)
                    pyautogui.click(center_x, center_y)
                    print(f"Email UI: Clicked on image: {image_path}")
                    return True
                else:
                    print(f"Email UI: Image not found (attempt {attempt+1}/{attempts}): {image_path}")
            except pyautogui.PyAutoGUIException as e:
                print(f"Email UI: Error locating image {image_path}: {e}")
            time.sleep(retry_delay)
        return False

    def activate_email_client(self, params):
        """
        Activates a specific email client application or browser tab.
        Requires the window title or app name.
        params: {'window_title_part': 'Outlook', 'app_name': 'firefox'}
        """
        window_title_part = params.get('window_title_part')
        app_name = params.get('app_name')

        if not (window_title_part or app_name):
            return {"status": "error", "message": "Missing 'window_title_part' or 'app_name' for activation."}

        try:
            if window_title_part:
                # Find all windows that contain the title part
                windows = pyautogui.getWindowsWithTitle(window_title_part)
                if windows:
                    # Activate the first matching window
                    windows[0].activate()
                    print(f"Email UI: Activated window with title part: '{window_title_part}'")
                    time.sleep(2) # Give time for the window to become active
                    return {"status": "success", "message": f"Activated window: '{window_title_part}'"}
                else:
                    return {"status": "error", "message": f"No window found with title part: '{window_title_part}'"}
            elif app_name:
                # This part is more OS-specific and might require subprocess.Popen
                # For basic activation, pyautogui.getWindowsWithTitle is often enough if the app is already open.
                # If the app needs to be launched, it would be a launch_application command.
                return {"status": "warning", "message": "App name activation is highly OS-dependent for already running apps. Try 'window_title_part' first."}
        except Exception as e:
            return {"status": "error", "message": f"Error activating email client: {str(e)}"}


    def compose_and_send_email_ui(self, params):
        """
        Composes and sends an email via a UI client (e.g., Outlook, Gmail in browser).
        Requires screenshots of 'Compose' button, 'To' field, 'Subject' field, 'Body' field, 'Send' button.
        
        params: {
            'compose_button_img': 'path/to/compose_button.png', # Mandatory: Screenshot of 'Compose' button
            'to_field_img': 'path/to/to_field.png',             # Mandatory: Screenshot of 'To' field label/area
            'subject_field_img': 'path/to/subject_field.png',   # Mandatory: Screenshot of 'Subject' field label/area
            'body_field_img': 'path/to/body_field.png',         # Mandatory: Screenshot of 'Body' field label/area
            'send_button_img': 'path/to/send_button.png',       # Mandatory: Screenshot of 'Send' button
            'recipient_email': 'recipient@example.com' or ['r1@ex.com', 'r2@ex.com'],
            'subject': 'Automated UI Email',
            'body': 'This is the body content.',
            'attachments': ['/path/to/file1.pdf', '/path/to/file2.jpg'] # Optional: Full paths to files
            'attach_button_img': 'path/to/attach_button.png',   # Mandatory if attachments are present
            'file_explorer_path_field_img': 'path/to/file_explorer_path_field.png', # Mandatory if attachments are present
            'file_explorer_open_button_img': 'path/to/file_explorer_open_button.png' # Mandatory if attachments are present
        }
        """
        compose_button_img = params.get('compose_button_img')
        to_field_img = params.get('to_field_img')
        subject_field_img = params.get('subject_field_img')
        body_field_img = params.get('body_field_img')
        send_button_img = params.get('send_button_img')
        
        recipient_email = params.get('recipient_email')
        subject = params.get('subject', '')
        body = params.get('body', '')
        attachments = params.get('attachments', [])

        attach_button_img = params.get('attach_button_img')
        file_explorer_path_field_img = params.get('file_explorer_path_field_img')
        file_explorer_open_button_img = params.get('file_explorer_open_button_img')


        if not all([compose_button_img, to_field_img, subject_field_img, body_field_img, send_button_img]):
            return {"status": "error", "message": "Missing required image paths for email UI composition."}
        if not recipient_email:
            return {"status": "error", "message": "Missing 'recipient_email' parameter."}
        if not (body or attachments):
             return {"status": "error", "message": "Email must have 'body' or 'attachments'."}
        if attachments and not all([attach_button_img, file_explorer_path_field_img, file_explorer_open_button_img]):
            return {"status": "error", "message": "Missing image paths for attachment process when 'attachments' are provided."}
        
        if isinstance(recipient_email, list):
            recipient_email = "; ".join(recipient_email) # Join for email clients

        try:
            # 1. Click Compose Button
            if not self._find_and_click_image(compose_button_img):
                return {"status": "error", "message": "Could not find 'Compose' button image."}
            time.sleep(2) # Wait for new email window/tab to open

            # 2. Fill To field
            if not self._find_and_click_image(to_field_img):
                 return {"status": "error", "message": "Could not find 'To' field image."}
            pyautogui.typewrite(recipient_email)
            pyautogui.press('tab') # Move to Subject field
            time.sleep(0.5)

            # 3. Fill Subject field
            # Assuming 'tab' from To field lands us in Subject. If not, use subject_field_img
            # if not self._find_and_click_image(subject_field_img):
            #     return {"status": "error", "message": "Could not find 'Subject' field image."}
            pyautogui.typewrite(subject)
            pyautogui.press('tab') # Move to Body field
            time.sleep(0.5)

            # 4. Fill Body field
            # Assuming 'tab' from Subject field lands us in Body. If not, use body_field_img
            # if not self._find_and_click_image(body_field_img):
            #     return {"status": "error", "message": "Could not find 'Body' field image."}
            pyautogui.typewrite(body)
            time.sleep(0.5)

            # 5. Handle Attachments
            for attachment_path in attachments:
                if not os.path.exists(attachment_path):
                    print(f"Email UI: Warning: Attachment file not found locally: {attachment_path}")
                    continue

                if not self._find_and_click_image(attach_button_img):
                    return {"status": "error", "message": "Could not find 'Attach File' button image."}
                time.sleep(2) # Wait for File Explorer/dialog to open

                # Type file path into File Explorer/dialog's path field
                if not self._find_and_click_image(file_explorer_path_field_img):
                    return {"status": "error", "message": "Could not find file explorer path field image."}
                pyautogui.typewrite(attachment_path)
                time.sleep(0.5)
                pyautogui.press('enter') # Or click 'Open' button image
                
                # Alternatively, if file explorer has an 'Open' button:
                # if not self._find_and_click_image(file_explorer_open_button_img):
                #     return {"status": "error", "message": "Could not find file explorer 'Open' button image."}
                
                time.sleep(2) # Wait for attachment to load

            # 6. Click Send Button
            if not self._find_and_click_image(send_button_img):
                return {"status": "error", "message": "Could not find 'Send' button image."}
            time.sleep(2) # Give time for email to send

            return {"status": "success", "message": f"Email composed and sent via UI to {recipient_email}."}

        except Exception as e:
            print(f"Email UI: Error composing/sending email via UI: {str(e)}")
            return {"status": "error", "message": f"Failed to compose/send email via UI: {str(e)}"}

    # Placeholder for reading email via UI (more complex, might need OCR)
    def read_latest_email_ui(self, params):
        """
        Reads the latest email in the inbox via UI.
        This is significantly more complex and often involves a mix of image recognition,
        keyboard navigation, and potentially OCR or clipboard reading.

        params: {
            'inbox_icon_img': 'path/to/inbox_icon.png', # Image to navigate to inbox
            'latest_email_selector_img': 'path/to/latest_email_line.png', # Image of latest email line/subject
            'body_area_img': 'path/to/email_body_area.png' # Image for the general body area to OCR/copy
        }
        """
        inbox_icon_img = params.get('inbox_icon_img')
        latest_email_selector_img = params.get('latest_email_selector_img')
        body_area_img = params.get('body_area_img')

        if not all([inbox_icon_img, latest_email_selector_img, body_area_img]):
            return {"status": "error", "message": "Missing required image paths for reading email UI."}

        try:
            # 1. Go to Inbox (if not already there)
            if not self._find_and_click_image(inbox_icon_img):
                return {"status": "error", "message": "Could not find 'Inbox' icon image."}
            time.sleep(2)

            # 2. Click the latest email (assumes it's at a consistent spot or recognizable)
            # This is tricky. You might need to press 'down' arrow key, or click a specific region.
            if not self._find_and_click_image(latest_email_selector_img):
                return {"status": "error", "message": "Could not find latest email selector image."}
            time.sleep(2) # Wait for email to open

            # 3. Extract content (most challenging part)
            # Option A: Copy entire email content to clipboard
            # pyautogui.hotkey('ctrl', 'a') # Select all
            # time.sleep(0.5)
            # pyautogui.hotkey('ctrl', 'c') # Copy
            # time.sleep(0.5)
            # full_email_text = pyautogui.paste() # Get clipboard content
            # print(f"Email UI: Read email content via clipboard.")
            # return {"status": "success", "message": "Latest email content copied to clipboard.", "content": full_email_text}

            # Option B: Use OCR (requires libraries like Tesseract and PyTesseract)
            # This is beyond the scope of this initial integration, but is where AI/CV comes in!
            # For a basic example, you'd capture a screenshot of the body area and run OCR on it.
            # from PIL import Image # requires Pillow
            # import pytesseract # requires Tesseract installed on system and pytesseract pip package

            # screen_region_to_ocr = pyautogui.locateOnScreen(body_area_img, confidence=0.8)
            # if screen_region_to_ocr:
            #     screenshot_img = pyautogui.screenshot(region=screen_region_to_ocr)
            #     ocr_text = pytesseract.image_to_string(screenshot_img)
            #     return {"status": "success", "message": "Email body OCR'd.", "content": ocr_text}
            # else:
            #    return {"status": "error", "message": "Could not locate email body area for OCR."}
            
            return {"status": "warning", "message": "Reading email content via UI is complex and requires specific implementation for your client. This is a placeholder."}

        except Exception as e:
            print(f"Email UI: Error reading email via UI: {str(e)}")
            return {"status": "error", "message": f"Failed to read email via UI: {str(e)}"}