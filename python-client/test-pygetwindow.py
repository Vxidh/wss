import pygetwindow as gw
import time
import sys
import traceback

print("--- Starting pygetwindow isolation test ---")
try:
    # Step 1: List all windows to confirm basic functionality
    print("Attempting to get all window titles...")
    all_windows = gw.getAllTitles()
    print(f"Found {len(all_windows)} total windows.")
    # print(f"All window titles: {all_windows}") # Uncomment this line for verbose output if needed

    # Step 2: Try to find a known window (e.g., your browser after open_url)
    # Make sure Chrome or Edge (or whatever you use for open_url) is open
    # with "Google" in its title when you run this test.
    target_substring = "Google"
    print(f"Searching for window with title containing '{target_substring}'...")
    windows_found = gw.getWindowsWithTitle(target_substring)

    if windows_found:
        print(f"Found {len(windows_found)} window(s) matching '{target_substring}'.")
        target_window = windows_found[0]
        print(f"Attempting to activate window: '{target_window.title}'")

        # Additional checks: print properties before activating
        print(f"Is maximized: {target_window.maximize}") # <-- Corrected: 'maximized' (lowercase 'm')
        print(f"Is minimized: {target_window.minimize}") # <-- Already correct from previous
        print(f"Is active: {target_window.activate}")       # <-- Corrected: 'active' (lowercase 'a')
        print(f"Is visible: {target_window.visible}")

        if not target_window.isActive:
            target_window.activate() # This is the line that's likely crashing
            print(f"Attempted to activate '{target_window.title}'. Waiting 1 second...")
            time.sleep(1)
            print(f"Is now active: {target_window.isActive}")
        else:
            print(f"Window '{target_window.title}' is already active.")
        print("Activation attempt finished.")
    else:
        print(f"No window found with title containing '{target_substring}'. Ensure browser is open.")

except Exception as e:
    print(f"\n*** CRITICAL UNEXPECTED ERROR IN ISOLATED TEST ***")
    print(f"Error type: {type(e).__name__}")
    print(f"Error message: {e}")
    traceback.print_exc()
    sys.exit(1) # Exit with error code to signify failure

print("--- pygetwindow isolation test finished successfully (no crash detected) ---")