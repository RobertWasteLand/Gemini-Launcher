!define GL_Old_Name "Gemini Launcher"

!macro NSIS_HOOK_POSTINSTALL
  ${If} $UpdateMode = 1
    ${If} ${FileExists} "$SMPROGRAMS\${GL_Old_Name}.lnk"
    ${AndIfNot} ${FileExists} "$SMPROGRAMS\${PRODUCTNAME}.lnk"
      CreateShortcut "$SMPROGRAMS\${PRODUCTNAME}.lnk" "$INSTDIR\${MAINBINARYNAME}.exe"
    ${EndIf}
    ${If} ${FileExists} "$DESKTOP\${GL_Old_Name}.lnk"
    ${AndIfNot} ${FileExists} "$DESKTOP\${PRODUCTNAME}.lnk"
      CreateShortcut "$DESKTOP\${PRODUCTNAME}.lnk" "$INSTDIR\${MAINBINARYNAME}.exe"
    ${EndIf}
  ${EndIf}
!macroend

!macro NSIS_HOOK_PREUNINSTALL
  ${If} $UpdateMode <> 1
    ExecWait '"$INSTDIR\${MAINBINARYNAME}.exe" --GL_Cleanup'
  ${EndIf}
!macroend
