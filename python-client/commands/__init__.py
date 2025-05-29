from .mouse import MouseCommands
from .keyboard import KeyboardCommands
from .system import SystemCommands 

class CommandDispatcher:
    def __init__(self):
        self.mouse = MouseCommands()
        self.keyboard = KeyboardCommands()
        self.system = SystemCommands() 

        self.commands = {
            'mouse_move': self.mouse.move,
            'mouse_click': self.mouse.click,
            'mouse_drag': self.mouse.drag,
            'mouse_scroll': self.mouse.scroll,

            'key_press': self.keyboard.press_key,
            'key_combo': self.keyboard.key_combo,
            'type_text': self.keyboard.type_text,

            'screenshot': self.system.screenshot,
            'get_screen_size': self.system.get_screen_size,
            'ping': self.system.ping,
            'open_url': self.system.open_url, 
        }

    def execute_command(self, command_data):
        action = command_data.get('action')
        params = command_data.get('params', {})
        request_id = command_data.get('requestId') 

        if not action:
            return {
                "status": "error",
                "message": "Missing 'action' field",
                "requestId": request_id
            }

        if action not in self.commands:
            return {
                "status": "error",
                "message": f"Unknown command: {action}",
                "requestId": request_id
            }

        try:
            result = self.commands[action](params)
            
            if request_id and isinstance(result, dict) and 'requestId' not in result:
                result['requestId'] = request_id
            
            return result
        except Exception as e:
            print(f"Error executing command '{action}': {e}")
            return {
                "status": "error",
                "action": action,
                "message": str(e),
                "requestId": request_id
            }