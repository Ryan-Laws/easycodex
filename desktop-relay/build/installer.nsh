!include LogicLib.nsh
!include nsDialogs.nsh
!include nsProcess.nsh

ManifestDPIAware true

!ifndef BUILD_UNINSTALLER
Var CreateDesktopShortcutCheckbox
Var CreateStartMenuShortcutCheckbox
Var ShouldCreateDesktopShortcut
Var ShouldCreateStartMenuShortcut

LangString EasyCodexWelcomeTitle 1033 "Install EasyCodex Relay"
LangString EasyCodexWelcomeTitle 2052 "安装 EasyCodex 中继"
LangString EasyCodexWelcomeTitle 1028 "安裝 EasyCodex 中繼"
LangString EasyCodexWelcomeText 1033 "This wizard will guide you through installing EasyCodex Relay.$\r$\n$\r$\nYou can choose the install location and shortcut options before installation begins."
LangString EasyCodexWelcomeText 2052 "此向导将引导你安装 EasyCodex 中继。$\r$\n$\r$\n安装开始前，你可以选择安装位置和快捷方式选项。"
LangString EasyCodexWelcomeText 1028 "此精靈將引導你安裝 EasyCodex 中繼。$\r$\n$\r$\n安裝開始前，你可以選擇安裝位置和捷徑選項。"
LangString EasyCodexShortcutIntro 1033 "Select the shortcuts you want for EasyCodex Relay."
LangString EasyCodexShortcutIntro 2052 "选择要为 EasyCodex 中继创建的快捷方式。"
LangString EasyCodexShortcutIntro 1028 "選擇要為 EasyCodex 中繼建立的捷徑。"
LangString EasyCodexDesktopShortcut 1033 "Create a desktop shortcut"
LangString EasyCodexDesktopShortcut 2052 "创建桌面快捷方式"
LangString EasyCodexDesktopShortcut 1028 "建立桌面捷徑"
LangString EasyCodexUnsafeInstallDir 1033 "The selected folder looks like a source code or project folder. Choose a dedicated install folder such as Program Files\EasyCodex Relay to avoid data loss when uninstalling."
LangString EasyCodexUnsafeInstallDir 2052 "所选文件夹看起来像源码或项目目录。请选择专用安装目录，例如 Program Files\EasyCodex Relay，避免卸载时误删文件。"
LangString EasyCodexUnsafeInstallDir 1028 "所選資料夾看起來像原始碼或專案目錄。請選擇專用安裝目錄，例如 Program Files\EasyCodex Relay，避免解除安裝時誤刪檔案。"
LangString EasyCodexStartMenuShortcut 1033 "Create a Start Menu shortcut"
LangString EasyCodexStartMenuShortcut 2052 "创建开始菜单快捷方式"
LangString EasyCodexStartMenuShortcut 1028 "建立開始功能表捷徑"
LangString EasyCodexCloseBeforeInstall 1033 "EasyCodex Relay is still running. Quit EasyCodex Relay before installing this update, then run the installer again."
LangString EasyCodexCloseBeforeInstall 2052 "EasyCodex 中继仍在运行。请先退出 EasyCodex 中继，再重新运行安装包。"
LangString EasyCodexCloseBeforeInstall 1028 "EasyCodex 中繼仍在執行。請先退出 EasyCodex 中繼，再重新執行安裝程式。"

!macro customInit
  StrCpy $ShouldCreateDesktopShortcut ${BST_CHECKED}
  StrCpy $ShouldCreateStartMenuShortcut ${BST_CHECKED}
  ${nsProcess::FindProcess} "${APP_EXECUTABLE_FILENAME}" $0
  ${If} $0 == 0
    MessageBox MB_ICONSTOP|MB_OK "$(EasyCodexCloseBeforeInstall)"
    Abort
  ${EndIf}
!macroend

!macro customWelcomePage
  !define MUI_WELCOMEPAGE_TITLE "$(EasyCodexWelcomeTitle)"
  !define MUI_WELCOMEPAGE_TEXT "$(EasyCodexWelcomeText)"
  !insertmacro MUI_PAGE_WELCOME
!macroend

!macro customPageAfterChangeDir
  Page custom ShortcutOptionsPage ShortcutOptionsPageLeave
!macroend

Function ShortcutOptionsPage
  ${If} ${FileExists} "$INSTDIR\.git\*.*"
  ${OrIf} ${FileExists} "$INSTDIR\AGENTS.md"
  ${OrIf} ${FileExists} "$INSTDIR\mobile\settings.gradle.kts"
  ${OrIf} ${FileExists} "$INSTDIR\agent-relay\package.json"
    MessageBox MB_ICONSTOP|MB_OK "$(EasyCodexUnsafeInstallDir)"
    Abort
  ${EndIf}

  nsDialogs::Create 1018
  Pop $0
  ${If} $0 == error
    Abort
  ${EndIf}

  ${NSD_CreateLabel} 0 0 100% 24u "$(EasyCodexShortcutIntro)"
  Pop $0

  ${NSD_CreateCheckbox} 0 36u 100% 12u "$(EasyCodexDesktopShortcut)"
  Pop $CreateDesktopShortcutCheckbox
  ${NSD_SetState} $CreateDesktopShortcutCheckbox $ShouldCreateDesktopShortcut

  ${NSD_CreateCheckbox} 0 56u 100% 12u "$(EasyCodexStartMenuShortcut)"
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
