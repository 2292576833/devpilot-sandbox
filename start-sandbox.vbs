' Dynamic path VBS - gets directory from script location
Set WshShell = CreateObject("WScript.Shell")
strPath = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
WshShell.CurrentDirectory = strPath
' Start via WMI for true independence
Set objWMIService = GetObject("winmgmts:\\.\root\cimv2")
Set objProcess = objWMIService.Get("Win32_Process")
strCommand = "java -jar """ & strPath & "\target\devpilot-sandbox-0.1.0.jar"" --server.port=9091"
objProcess.Create strCommand, strPath, null, intProcessID