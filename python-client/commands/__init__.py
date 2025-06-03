# commands/__init__.py

from .input import InputCommands
from .system import SystemCommands
from .email import EmailCommands
from .api import APICallCommands
import traceback
import logging
log = logging.getLogger(__name__)

class CommandDispatcher:
    # MODIFIED: Accept node_client_ref here
    def __init__(self, node_client_ref=None):
        self.node_client_ref = node_client_ref # Store it

        self.input_cmds = InputCommands()
        # MODIFIED: Pass node_client_ref to SystemCommands
        self.system = SystemCommands(node_client_ref=self.node_client_ref)
        self.email_cmds = EmailCommands()
        self.api_cmds = APICallCommands()

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
            'activate_window': self.system.activate_window,
            'wait': self.system.wait,
            'send_email': self.email_cmds.send_email,
            'read_latest_email': self.email_cmds.read_latest_email,

            # Local API Call Commands
            'get_data_from_local_api': self.api_cmds.get_data_from_local_api,
            'post_data_to_local_api': self.api_cmds.post_data_to_local_api,
        }

    def execute_command(self, command_data):
        # *** --- MODIFIED LINE BELOW --- ***
        # Change 'action' to 'commandType' to match the Batch Server's existing protocol
        action = command_data.get('commandType')
        # *** --- END MODIFIED LINE --- ***

        request_id = command_data.get('requestId')
        params = command_data.get('params', {}) # Define params here

        if not action:
            return {
                "status": "error",
                # *** --- MODIFIED MESSAGE BELOW --- ***
                "message": "Missing 'commandType' field", # Updated error message for clarity
                # *** --- END MODIFIED MESSAGE --- ***
                "requestId": request_id
            }

        if action not in self.commands:
            return {
                "status": "error",
                "message": f"Unknown command: {action}",
                "requestId": request_id
            }

        try:
            log.info(f"[Worker] Processing command: {action}")
            result = self.commands[action](params)

            if request_id and isinstance(result, dict) and 'requestId' not in result:
                result['requestId'] = request_id

            log.info(f"[Worker] Sent response for {action}: success")
            return {"status": "success", "response": result}

        except Exception as e:
            log.error(f"Error processing command {action}: {e}")
            traceback.print_exc()
            log.info(f"[Worker] Sent response for {action}: error")
            return {"status": "error", "message": str(e), "traceback": traceback.format_exc()}