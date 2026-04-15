python -m pip install PyQt6 pyqtgraph requests apscheduler keyring anthropic openai pyinstaller pyinstaller-hooks-contrib
python -m PyInstaller --noconfirm --distpath "C:\AI_Build" --workpath "C:\AI_Build\_work" build.spec
pause
