import PyInstaller.__main__
import os
import sys

def build_exe():
    PyInstaller.__main__.run([
        'main.py',
        '--onefile',
        '--name=NodeClient',
        '--console',
        # Removed the problematic --add-data line
        '--hidden-import=websocket',
        '--hidden-import=pyautogui',
        '--hidden-import=PIL',
        '--hidden-import=commands',
        '--hidden-import=commands.mouse',
        '--hidden-import=commands.keyboard',
        '--hidden-import=commands.system',
        '--distpath=dist',
        '--workpath=build',
        '--specpath=build'
    ])

if __name__ == "__main__":
    build_exe()