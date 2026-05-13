!include LogicLib.nsh
!include nsDialogs.nsh

!ifndef BUILD_UNINSTALLER
Var CreateDesktopShortcutCheckbox
Var CreateStartMenuShortcutCheckbox
Var ShouldCreateDesktopShortcut
Var ShouldCreateStartMenuShortcut

!macro customInit
  StrCpy $ShouldCreateDesktopShortcut ${BST_CHECKED}
  StrCpy $ShouldCreateStartMenuShortcut ${BST_CHECKED}
!macroend

!macro customWelcomePage
  !define MUI_WELCOMEPAGE_TITLE "Install EasyCodex Relay"
  !define MUI_WELCOMEPAGE_TEXT "This wizard will guide you through installing EasyCodex Relay.$\r$\n$\r$\nYou can choose the install location and shortcut options before installation begins."
  !insertmacro MUI_PAGE_WELCOME
!macroend

!macro customPageAfterChangeDir
  Page custom ShortcutOptionsPage ShortcutOptionsPageLeave
!macroend

Function ShortcutOptionsPage
  nsDialogs::Create 1018
  Pop $0
  ${If} $0 == error
    Abort
  ${EndIf}

  ${NSD_CreateLabel} 0 0 100% 24u "Select the shortcuts you want for EasyCodex Relay."
  Pop $0

  ${NSD_CreateCheckbox} 0 36u 100% 12u "Create a desktop shortcut"
  Pop $CreateDesktopShortcutCheckbox
  ${NSD_SetState} $CreateDesktopShortcutCheckbox $ShouldCreateDesktopShortcut

  ${NSD_CreateCheckbox} 0 56u 100% 12u "Create a Start Menu shortcut"
  Pop $CreateStartMenuShortcutCheckbox
  ${NSD_SetState} $CreateStartMenuShortcutCheckbox $ShouldCreateStartMenuShortcut

  nsDialogs::Show
FunctionEnd

Function ShortcutOptionsPageLeave
  ${NSD_GetState} $CreateDesktopShortcutCheckbox $ShouldCreateDesktopShortcut
  ${NSD_GetState} $CreateStartMenuShortcutCheckbox $ShouldCreateStartMenuShortcut
FunctionEnd

!macro customInstall
  ${ifNot} ${isUpdated}
    ${If} $ShouldCreateStartMenuShortcut == ${BST_CHECKED}
      !insertmacro createMenuDirectory
      CreateShortCut "$newStartMenuLink" "$appExe" "" "$appExe" 0 "" "" "${APP_DESCRIPTION}"
      ClearErrors
      WinShell::SetLnkAUMI "$newStartMenuLink" "${APP_ID}"
    ${EndIf}

    ${If} $ShouldCreateDesktopShortcut == ${BST_CHECKED}
      CreateShortCut "$newDesktopLink" "$appExe" "" "$appExe" 0 "" "" "${APP_DESCRIPTION}"
      ClearErrors
      WinShell::SetLnkAUMI "$newDesktopLink" "${APP_ID}"
    ${EndIf}

    System::Call 'Shell32::SHChangeNotify(i 0x8000000, i 0, i 0, i 0)'
  ${endIf}
!macroend
!endif
