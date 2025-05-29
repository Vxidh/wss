import pyautogui
import time

class KeyboardCommands:
    def __init__(self):
        pyautogui.FAILSAFE = True
    
    def press_key(self, data):
        """Press a single key"""
        key = data.get('key')
        presses = data.get('presses', 1)
        interval = data.get('interval', 0.0)
        
        if not key:
            raise ValueError("Missing 'key' parameter")
        
        pyautogui.press(key, presses=presses, interval=interval)
        
        return {
            "status": "success",
            "action": "key_press",
            "key": key,
            "presses": presses
        }
    
    def key_combo(self, data):
        """Press key combination"""
        keys = data.get('keys', [])
        
        if not keys or not isinstance(keys, list):
            raise ValueError("Missing or invalid 'keys' parameter (should be array)")
        
        pyautogui.hotkey(*keys)
        
        return {
            "status": "success",
            "action": "key_combo",
            "keys": keys
        }
    
    def type_text(self, data):
        """Type text"""
        text = data.get('text', '')
        interval = data.get('interval', 0.0)
        
        pyautogui.typewrite(text, interval=interval)
        
        return {
            "status": "success",
            "action": "type_text",
            "text": text,
            "length": len(text)
        }