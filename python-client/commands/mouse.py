import pyautogui
import time

class MouseCommands:
    def __init__(self):
        pyautogui.FAILSAFE = True
        pyautogui.PAUSE = 0.1
    
    def move(self, data):
        """Move mouse to coordinates"""
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
        """Click mouse at coordinates"""
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
        """Drag from one point to another"""
        from_x = data.get('from_x')
        from_y = data.get('from_y')
        to_x = data.get('to_x')
        to_y = data.get('to_y')
        duration = data.get('duration', 0.5)
        button = data.get('button', 'left')
        
        if None in [from_x, from_y, to_x, to_y]:
            raise ValueError("Missing coordinates for drag operation")
        
        pyautogui.drag(to_x - from_x, to_y - from_y, duration=duration, button=button)
        
        return {
            "status": "success",
            "action": "mouse_drag",
            "from": {"x": from_x, "y": from_y},
            "to": {"x": to_x, "y": to_y}
        }
    
    def scroll(self, data):
        """Scroll mouse wheel"""
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