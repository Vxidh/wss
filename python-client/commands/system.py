import pyautogui
import time
import base64
import io

class SystemCommands:
    def screenshot(self, data):
        """Take a screenshot"""
        region = data.get('region')  # [x, y, width, height]
        
        if region:
            screenshot = pyautogui.screenshot(region=region)
        else:
            screenshot = pyautogui.screenshot()
        
        # Convert to base64 for transmission
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