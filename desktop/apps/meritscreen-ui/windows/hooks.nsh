; Watching NSIS hooks: register the Guardian service shipped as a sidecar.
; Tauri installs sidecars next to Watching.exe in $INSTDIR.
!macro NSIS_HOOK_POSTINSTALL
  IfFileExists "$INSTDIR\meritscreen-guardian.exe" 0 +3
    DetailPrint "Installing Watching Guardian service..."
    nsExec::ExecToLog '"$INSTDIR\meritscreen-guardian.exe" --install-service'
!macroend
!macro NSIS_HOOK_PREUNINSTALL
  IfFileExists "$INSTDIR\meritscreen-guardian.exe" 0 +2
    nsExec::ExecToLog '"$INSTDIR\meritscreen-guardian.exe" --uninstall-service'
!macroend
