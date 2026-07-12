' Double-click to start price-watch with no console window.
' Requires run-local-cloud.ps1 next to this file (gitignored secrets).
Option Explicit
Dim sh, dir, cmd
Set sh = CreateObject("WScript.Shell")
dir = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
cmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & dir & "\run-local-cloud.ps1"" -Hidden"
sh.Run cmd, 0, False
