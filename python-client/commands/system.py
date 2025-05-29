import pyautogui
import time
import base64
import io
import webbrowser 

class SystemCommands:
    def screenshot(self, data):
        """Take a screenshot"""
        region = data.get('region')  
        
        if region:
            screenshot = pyautogui.screenshot(region=region)
        else:
            screenshot = pyautogui.screenshot()

        buffer = io.BytesIO()
        screenshot.save(buffer, format='PNG')
        img_str = base64.b64encode(buffer.getvalue()).decode()
        
        return {
            "status": "success",
            "action": "screenshot",
            "image": img_str,
            "size": screenshot.size,
            "region": region
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
        url = params.get('url')
        if not url:
            return {"status": "error", "message": "Missing 'url' parameter for open_url."}
        
        try:
            print(f"Opening URL: {url}")
            webbrowser.open(url) 
            return {"status": "success", "message": f"Successfully opened URL: {url}"}
        except Exception as e:
            print(f"Error opening URL {url}: {e}")
            return {"status": "error", "message": f"Failed to open URL: {e}"}