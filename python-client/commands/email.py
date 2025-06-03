import smtplib
import imaplib
import email
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from email.mime.base import MIMEBase
from email import encoders
import ssl
import logging
import traceback
import os # Make sure os is imported for file operations

log = logging.getLogger(__name__)

class EmailCommands:
    def __init__(self):
        # No hardcoded server details here. They will be resolved dynamically
        # either from command parameters or based on a 'service_provider' parameter.
        pass

    def _get_email_config(self, params):
        """
        Extracts email configuration from command parameters, with defaults for common providers.
        
        Parameters:
            params (dict): The command parameters dictionary, e.g., from the incoming WebSocket command.
            
        Returns:
            dict: A dictionary containing email configuration (user, password, smtp_server, smtp_port, imap_server, imap_port).
            
        Raises:
            ValueError: If essential configuration is missing or if an unknown service_provider is specified.
        """
        
        email_user = params.get("email_user")
        # IMPORTANT: email_password should be an App Password for Gmail/Outlook if 2FA is enabled.
        email_password = params.get("email_password") 
        
        smtp_server = params.get("smtp_server")
        smtp_port = params.get("smtp_port")
        imap_server = params.get("imap_server")
        imap_port = params.get("imap_port")
        
        service_provider = params.get("service_provider", "").lower() # e.g., 'gmail', 'outlook', 'office365'
        
        if service_provider:
            if service_provider == "gmail":
                smtp_server = smtp_server or "smtp.gmail.com"
                smtp_port = smtp_port or 587 # TLS
                imap_server = imap_server or "imap.gmail.com"
                imap_port = imap_port or 993 # SSL
            elif service_provider in ["outlook", "office365"]:
                smtp_server = smtp_server or "smtp.office365.com"
                smtp_port = smtp_port or 587 # TLS
                imap_server = imap_server or "outlook.office365.com"
                imap_port = imap_port or 993 # SSL
            else:
                raise ValueError(f"Unknown email service provider: '{service_provider}'. Please provide full server details (smtp_server, smtp_port, imap_server, imap_port) explicitly.")

        # Final check to ensure all necessary details are present
        if not all([email_user, email_password, smtp_server, smtp_port, imap_server, imap_port]):
            missing_params = []
            if not email_user: missing_params.append('email_user')
            if not email_password: missing_params.append('email_password')
            if not smtp_server: missing_params.append('smtp_server')
            if not smtp_port: missing_params.append('smtp_port')
            if not imap_server: missing_params.append('imap_server')
            if not imap_port: missing_params.append('imap_port')
            raise ValueError(f"Email configuration is incomplete. Missing: {', '.join(missing_params)}. "
                             f"Ensure 'service_provider' is valid or all server details are explicitly provided.")

        return {
            "user": email_user,
            "password": email_password,
            "smtp_server": smtp_server,
            "smtp_port": int(smtp_port), # Ensure port is integer
            "imap_server": imap_server,
            "imap_port": int(imap_port), # Ensure port is integer
        }

    def send_email(self, params):
        """
        Sends an email using SMTP.
        Params:
            to_email (str): Recipient email address.
            subject (str): Email subject.
            body (str): Email body (plain text or HTML).
            attachments (list, optional): List of file paths to attach.
            email_user (str): Sender email address.
            email_password (str): Sender email password (use App Password for Gmail/Outlook).
            service_provider (str, optional): 'gmail', 'outlook', 'office365' to auto-configure servers.
            smtp_server (str, optional): Explicit SMTP server host.
            smtp_port (int, optional): Explicit SMTP server port.
            is_html (bool, optional): True if body is HTML, False for plain text. Defaults to False.
        """
        try:
            config = self._get_email_config(params) # Get config from parameters
            
            to_email = params.get("to_email")
            subject = params.get("subject")
            body = params.get("body")
            attachments = params.get("attachments", [])
            is_html = params.get("is_html", False)

            if not all([to_email, subject, body]):
                raise ValueError("Missing 'to_email', 'subject', or 'body' parameters for send_email.")

            msg = MIMEMultipart()
            msg['From'] = config["user"]
            msg['To'] = to_email
            msg['Subject'] = subject

            # Attach body
            if is_html:
                msg.attach(MIMEText(body, 'html'))
            else:
                msg.attach(MIMEText(body, 'plain'))

            # Attach files
            for filepath in attachments:
                if not os.path.exists(filepath):
                    log.warning(f"Attachment file not found: {filepath}. Skipping.")
                    continue
                try:
                    part = MIMEBase('application', 'octet-stream')
                    with open(filepath, "rb") as file:
                        part.set_payload(file.read())
                    encoders.encode_base64(part)
                    part.add_header('Content-Disposition', f"attachment; filename= {os.path.basename(filepath)}")
                    msg.attach(part)
                except Exception as e:
                    log.error(f"Failed to attach file {filepath}: {e}")
                    # Decide whether to raise or continue based on strictness

            context = ssl.create_default_context()
            with smtplib.SMTP(config["smtp_server"], config["smtp_port"]) as server:
                server.starttls(context=context) # Secure the connection
                server.login(config["user"], config["password"])
                server.send_message(msg)
            
            log.info(f"Email sent successfully to {to_email} from {config['user']}")
            return {"status": "success", "message": f"Email sent to {to_email}."}

        except ValueError as ve:
            log.error(f"send_email parameter error: {ve}")
            return {"status": "error", "message": str(ve)}
        except smtplib.SMTPAuthenticationError:
            log.error(f"send_email: Authentication failed for {config['user']}. Check username/password/App Password.")
            return {"status": "error", "message": "Email authentication failed."}
        except smtplib.SMTPConnectError as sce:
            log.error(f"send_email: Could not connect to SMTP server {config['smtp_server']}:{config['smtp_port']}. Error: {sce}")
            return {"status": "error", "message": f"SMTP connection error: {sce}"}
        except Exception as e:
            log.error(f"Error sending email: {e}")
            traceback.print_exc()
            return {"status": "error", "message": f"Error sending email: {e}", "traceback": traceback.format_exc()}

    def read_latest_email(self, params):
        """
        Reads the latest email (or latest unread) from the inbox.
        Params:
            email_user (str): Email address to login as.
            email_password (str): Email password (use App Password for Gmail/Outlook).
            service_provider (str, optional): 'gmail', 'outlook', 'office365' to auto-configure servers.
            imap_server (str, optional): Explicit IMAP server host.
            imap_port (int, optional): Explicit IMAP server port.
            from_email (str, optional): Filter by sender email.
            subject_substring (str, optional): Filter by subject substring.
            unread_only (bool, optional): Only read unread emails. Defaults to True.
        Returns:
            dict: Email details (sender, subject, body, date) or error.
        """
        try:
            config = self._get_email_config(params) # Get config from parameters

            from_email_filter = params.get("from_email")
            subject_substring_filter = params.get("subject_substring")
            unread_only = params.get("unread_only", True)

            mail = imaplib.IMAP4_SSL(config["imap_server"], config["imap_port"])
            mail.login(config["user"], config["password"])
            mail.select('inbox')

            search_criteria = []
            if unread_only:
                search_criteria.append('UNSEEN')
            
            # Add FROM/SUBJECT filters if provided. IMAP search allows literal strings.
            # Example: mail.search(None, 'FROM', 'example@domain.com', 'SUBJECT', 'Hello')
            if from_email_filter:
                search_criteria.extend(['FROM', from_email_filter])
            if subject_substring_filter:
                search_criteria.extend(['SUBJECT', subject_substring_filter])

            # If no specific search criteria other than unread_only, search all
            if not search_criteria and not unread_only: # If no filter at all, means search ALL
                search_criteria.append('ALL')
            elif not search_criteria and unread_only: # If only unread_only was initially added
                pass # search_criteria already has 'UNSEEN'

            status, email_ids = mail.search(None, *search_criteria)
            email_id_list = email_ids[0].split()

            if not email_id_list:
                log.info("No emails found matching criteria.")
                mail.logout()
                return {"status": "success", "message": "No emails found.", "email_data": None}

            # Get the latest email ID (highest number)
            latest_email_id = email_id_list[-1] 
            
            status, msg_data = mail.fetch(latest_email_id, '(RFC822)') # Fetch the entire email content
            raw_email = msg_data[0][1]
            msg = email.message_from_bytes(raw_email)

            sender = msg['from']
            subject = msg['subject']
            date = msg['date']
            body = ""
            html_body = ""

            if msg.is_multipart():
                for part in msg.walk():
                    ctype = part.get_content_type()
                    cdisp = str(part.get('Content-Disposition'))

                    if ctype == 'text/plain' and 'attachment' not in cdisp:
                        body = part.get_payload(decode=True).decode()
                        # Prefer plain text, but store HTML if available for more comprehensive return
                    elif ctype == 'text/html' and 'attachment' not in cdisp:
                        html_body = part.get_payload(decode=True).decode()
            else:
                body = msg.get_payload(decode=True).decode()

            # Mark as seen (optional, uncomment if desired)
            # mail.store(latest_email_id, '+FLAGS', '\\Seen') 

            mail.logout()
            
            email_data = {
                "sender": sender,
                "subject": subject,
                "body": body, # Plain text body
                "html_body": html_body, # HTML body if present
                "date": date
            }
            log.info(f"Successfully read latest email from '{sender}' with subject '{subject}'.")
            return {"status": "success", "message": "Email read successfully.", "email_data": email_data}

        except ValueError as ve:
            log.error(f"read_latest_email parameter error: {ve}")
            return {"status": "error", "message": str(ve)}
        except imaplib.IMAP4.error as ie:
            log.error(f"IMAP error: {ie}. Check server, port, and credentials for {config['user']}.")
            return {"status": "error", "message": f"IMAP connection or login failed: {ie}"}
        except Exception as e:
            log.error(f"Error reading email: {e}")
            traceback.print_exc()
            return {"status": "error", "message": f"Error reading email: {e}", "traceback": traceback.format_exc()}