# input.py
import pyautogui
import time

class InputCommands:
    def __init__(self):
        pyautogui.FAILSAFE = True
        pyautogui.PAUSE = 0.1 

    # --- Keyboard Commands (existing) ---
    def press_key(self, data):
        # ... (your existing press_key code) ...
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
        # ... (your existing key_combo code) ...
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
        # ... (your existing type_text code) ...
        text = data.get('text', '')
        interval = data.get('interval', 0.0)
        
        pyautogui.typewrite(text, interval=interval)
        
        return {
            "status": "success",
            "action": "type_text",
            "text": text,
            "length": len(text)
        }

    # --- Mouse Commands (existing) ---
    def move(self, data):
        # ... (your existing move code) ...
        x = data.get('x', 0)
        y = data.get('y', 0)
        duration = data.get('duration', 0.0)
        
        pyautogui.moveTo(x, y, duration=duration)
        
        return {
            "status": "success",
            "action": "mouse_move",
            "x": x,
            "y": y
        }
    
    def click(self, data):
        # ... (your existing click code) ...
        x = data.get('x')
        y = data.get('y')
        button = data.get('button', 'left')
        clicks = data.get('clicks', 1)
        
        if x is not None and y is not None:
            pyautogui.click(x, y, clicks=clicks, button=button)
        else:
            pyautogui.click(clicks=clicks, button=button)
        
        return {
            "status": "success",
            "action": "mouse_click",
            "x": x,
            "y": y,
            "button": button,
            "clicks": clicks
        }
    
    def drag(self, data):
        # ... (your existing drag code, using dragTo as suggested earlier) ...
        from_x = data.get('from_x')
        from_y = data.get('from_y')
        to_x = data.get('to_x')
        to_y = data.get('to_y')
        duration = data.get('duration', 0.5)
        button = data.get('button', 'left')
        
        if None in [from_x, from_y, to_x, to_y]:
            raise ValueError("Missing coordinates for drag operation")
        
        pyautogui.moveTo(from_x, from_y)
        pyautogui.dragTo(to_x, to_y, duration=duration, button=button)
        
        return {
            "status": "success",
            "action": "mouse_drag",
            "from": {"x": from_x, "y": from_y},
            "to": {"x": to_x, "y": to_y},
            "button": button,
            "duration": duration
        }
    
    def scroll(self, data):
        # ... (your existing scroll code) ...
        x = data.get('x')
        y = data.get('y')
        clicks = data.get('clicks', 3)
        
        if x is not None and y is not None:
            pyautogui.scroll(clicks, x=x, y=y)
        else:
            pyautogui.scroll(clicks)
        
        return {
            "status": "success",
            "action": "mouse_scroll",
            "x": x,
            "y": y,
            "clicks": clicks
        }

    # --- NEW: Combined Keyboard + Mouse Command ---
    def combo_click(self, data):
        """
        Performs a mouse click while specified keyboard keys are held down.
        Useful for actions like Ctrl+Click (open in new tab) or Shift+Click.

        data: {
            'keys': list[str],  # List of keys to hold down, e.g., ['ctrl', 'shift', 'alt']
                                # Use lowercase key names like 'ctrl', 'shift', 'alt', 'win' (for Windows key)
            'x': int,           # Optional: X coordinate for the click
            'y': int,           # Optional: Y coordinate for the click
            'button': str,      # Optional: 'left', 'right', 'middle' (default: 'left')
            'clicks': int,      # Optional: Number of clicks (e.g., 2 for double-click, default: 1)
            'interval': float   # Optional: Interval between clicks if clicks > 1 (default: 0.0)
        }
        """
        keys_to_hold = data.get('keys', [])
        x = data.get('x')
        y = data.get('y')
        button = data.get('button', 'left')
        clicks = data.get('clicks', 1)
        interval = data.get('interval', 0.0) # Interval between clicks if multiple

        if not isinstance(keys_to_hold, list) or not keys_to_hold:
            raise ValueError("Missing or invalid 'keys' parameter. Must be a non-empty list of keys to hold.")

        mouse_click_args = {}
        if x is not None and y is not None:
            mouse_click_args['x'] = x
            mouse_click_args['y'] = y
        
        # Use pyautogui.hold context manager for clean key press/release
        with pyautogui.hold(*keys_to_hold):
            pyautogui.click(clicks=clicks, button=button, interval=interval, **mouse_click_args)

        return {
            "status": "success",
            "action": "combo_click",
            "keys_held": keys_to_hold,
            "clicked_at": {"x": x, "y": y} if x is not None and y is not None else "current_position",
            "button": button,
            "clicks": clicks
        }