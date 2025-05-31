# api.py
import requests
import json # For handling JSON data in requests/responses

class APICallCommands:
    def __init__(self):
        # You can add any global settings for API calls here, e.g., default timeout
        self.default_timeout = 10 # seconds

    def get_data_from_local_api(self, params):
        """
        Makes an HTTP GET request to a local API endpoint to retrieve data.
        params: {
            'url': 'http://localhost:8000/api/data', # The local API URL
            'headers': {'Authorization': 'Bearer ...'}, # Optional: HTTP headers
            'query_params': {'param1': 'value1'}, # Optional: Query parameters
            'timeout': 10 # Optional: Request timeout in seconds
        }
        """
        url = params.get('url')
        headers = params.get('headers', {})
        query_params = params.get('query_params', {})
        timeout = params.get('timeout', self.default_timeout)

        if not url:
            return {"status": "error", "message": "Missing 'url' parameter for get_data_from_local_api."}
        if not url.startswith(('http://localhost', 'https://localhost', 'http://127.0.0.1', 'https://127.0.0.1')):
             print(f"API: Warning: Attempting to access non-local URL: {url}")
             # You might want to raise an error here for security, or allow
             # but the command is named 'get_data_from_local_api' so it implies local.
        
        try:
            print(f"API: Sending GET request to {url} with query_params={query_params}")
            response = requests.get(url, headers=headers, params=query_params, timeout=timeout)
            response.raise_for_status() # Raise HTTPError for bad responses (4xx or 5xx)

            try:
                response_data = response.json()
                print(f"API: Received JSON response from {url}")
            except json.JSONDecodeError:
                response_data = response.text
                print(f"API: Received non-JSON text response from {url}")

            return {
                "status": "success",
                "message": f"Data retrieved successfully from {url}.",
                "http_status": response.status_code,
                "data": response_data
            }
        except requests.exceptions.Timeout:
            return {"status": "error", "message": f"Request to {url} timed out after {timeout} seconds."}
        except requests.exceptions.HTTPError as e:
            return {"status": "error", "message": f"HTTP Error accessing {url}: {e.response.status_code} - {e.response.text}"}
        except requests.exceptions.RequestException as e:
            return {"status": "error", "message": f"Network/Request Error accessing {url}: {str(e)}"}
        except Exception as e:
            return {"status": "error", "message": f"Unexpected error in get_data_from_local_api: {str(e)}"}

    def post_data_to_local_api(self, params):
        """
        Makes an HTTP POST request to a local API endpoint to send data.
        params: {
            'url': 'http://localhost:8000/api/submit', # The local API URL
            'headers': {'Content-Type': 'application/json'}, # Optional: HTTP headers
            'json_payload': {'key': 'value'}, # Optional: JSON data to send
            'form_data': {'field': 'value'}, # Optional: Form data to send (mutually exclusive with json_payload)
            'timeout': 10 # Optional: Request timeout in seconds
        }
        """
        url = params.get('url')
        headers = params.get('headers', {})
        json_payload = params.get('json_payload')
        form_data = params.get('form_data')
        timeout = params.get('timeout', self.default_timeout)

        if not url:
            return {"status": "error", "message": "Missing 'url' parameter for post_data_to_local_api."}
        if not url.startswith(('http://localhost', 'https://localhost', 'http://127.0.0.1', 'https://127.0.0.1')):
             print(f"API: Warning: Attempting to access non-local URL: {url}")
             # As above, consider security implications
        
        if json_payload and form_data:
            return {"status": "error", "message": "Cannot send both 'json_payload' and 'form_data' in a POST request."}

        try:
            print(f"API: Sending POST request to {url}")
            if json_payload:
                response = requests.post(url, headers=headers, json=json_payload, timeout=timeout)
            elif form_data:
                response = requests.post(url, headers=headers, data=form_data, timeout=timeout)
            else:
                response = requests.post(url, headers=headers, timeout=timeout) # Send empty POST

            response.raise_for_status() # Raise HTTPError for bad responses (4xx or 5xx)

            try:
                response_data = response.json()
                print(f"API: Received JSON response from {url}")
            except json.JSONDecodeError:
                response_data = response.text
                print(f"API: Received non-JSON text response from {url}")

            return {
                "status": "success",
                "message": f"Data sent successfully to {url}.",
                "http_status": response.status_code,
                "response_data": response_data
            }
        except requests.exceptions.Timeout:
            return {"status": "error", "message": f"Request to {url} timed out after {timeout} seconds."}
        except requests.exceptions.HTTPError as e:
            return {"status": "error", "message": f"HTTP Error sending data to {url}: {e.response.status_code} - {e.response.text}"}
        except requests.exceptions.RequestException as e:
            return {"status": "error", "message": f"Network/Request Error sending data to {url}: {str(e)}"}
        except Exception as e:
            return {"status": "error", "message": f"Unexpected error in post_data_to_local_api: {str(e)}"}