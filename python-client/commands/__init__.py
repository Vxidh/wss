from .mouse import MouseCommands
from .keyboard import KeyboardCommands
from .system import SystemCommands
# import json # Not needed in this file if not directly parsing/dumping JSON

class CommandDispatcher:
    def __init__(self):
        self.mouse = MouseCommands()
        self.keyboard = KeyboardCommands()
        self.system = SystemCommands()

        self.commands = {
            # Mouse commands
            'mouse_move': self.mouse.move,
            'mouse_click': self.mouse.click,
            'mouse_drag': self.mouse.drag,
            'mouse_scroll': self.mouse.scroll,

            # Keyboard commands
            'key_press': self.keyboard.press_key,
            'key_combo': self.keyboard.key_combo,
            'type_text': self.keyboard.type_text,

            # System commands
            'screenshot': self.system.screenshot,
            'get_screen_size': self.system.get_screen_size,
            'ping': self.system.ping,
        }

    def execute_command(self, command_data):
        action = command_data.get('action')
        # This is the crucial line: extract the 'params'
        params = command_data.get('params', {}) # Default to empty dict if 'params' is not present

        if not action:
            return {
                "status": "error",
                "message": "Missing 'action' field"
            }

        if action not in self.commands:
            return {
                "status": "error",
                "message": f"Unknown command: {action}"
            }

        try:
            # Pass ONLY the 'params' to the command handler function
            result = self.commands[action](params)
            return result
        except Exception as e:
            print(f"Error executing command '{action}': {e}")
            return {
                "status": "error",
                "action": action,
                "message": str(e)
            }