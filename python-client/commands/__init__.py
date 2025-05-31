# __init__.py
from .input import InputCommands
from .system import SystemCommands
from . import email # Using the UI-based email commands now
from .api import APICallCommands # <--- ADD THIS NEW IMPORT

class CommandDispatcher:
    def __init__(self):
        self.input_cmds = InputCommands()
        self.system = SystemCommands()
        self.email_ui_cmds = email.EmailUICommands() # <--- CHANGED THIS LINE
        self.api_cmds = APICallCommands() # <--- INSTANTIATE THE NEW CLASS

        self.commands = {
            # Input commands (keyboard & mouse)
            'mouse_move': self.input_cmds.move,
            'mouse_click': self.input_cmds.click,
            'mouse_drag': self.input_cmds.drag,
            'mouse_scroll': self.input_cmds.scroll,
            'key_press': self.input_cmds.press_key,
            'key_combo': self.input_cmds.key_combo,
            'type_text': self.input_cmds.type_text,
            'combo_click': self.input_cmds.combo_click,

            # System commands
            'screenshot': self.system.screenshot,
            'get_screen_size': self.system.get_screen_size,
            'ping': self.system.ping,
            'open_url': self.system.open_url,
            'read_file': self.system.read_file,
            'write_file': self.system.write_file,
            'download_file': self.system.download_file,
            'upload_file': self.system.upload_file,
            'run_shell_command': self.system.run_shell_command,
            'launch_application': self.system.launch_application,

            # Email UI commands
            'activate_email_client': self.email_ui_cmds.activate_email_client,
            'compose_and_send_email_ui': self.email_ui_cmds.compose_and_send_email_ui,
            'read_latest_email_ui': self.email_ui_cmds.read_latest_email_ui,

            # NEW: Local API Call Commands
            'get_data_from_local_api': self.api_cmds.get_data_from_local_api, # <--- ADD THIS MAPPING
            'post_data_to_local_api': self.api_cmds.post_data_to_local_api,   # <--- ADD THIS MAPPING
        }

    def execute_command(self, command_data):
        # ... (rest of this method remains the same) ...
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