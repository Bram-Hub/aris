# This installs two files, app.exe and logo.ico, creates a start menu shortcut, builds an uninstaller, and
# adds uninstall information to the registry for Add/Remove Programs
 
# To get started, put this script into a folder with the two files (app.exe, logo.ico, and license.rtf -
# You'll have to create these yourself) and run makensis on it
 
# If you change the names "app.exe", "logo.ico", or "license.rtf" you should do a search and replace - they
# show up in a few places.
# All the other settings can be tweaked by editing the !defines at the top of this script
!define APPNAME "Aris"
# These will be displayed by the "Click here for support information" link in "Add/Remove Programs"
# It is possible to use "mailto:" links in here to open the email client
!define HELPURL "http://github.com/cicchr/ARIS-Java" # "Support Information" link
 
RequestExecutionLevel admin ;Require admin rights on NT6+ (When UAC is turned on)
 
InstallDir "$PROGRAMFILES\${APPNAME}"
 
# rtf or txt file - remember if it is txt, it must be in the DOS text format (\r\n)
LicenseData "LICENSE.txt"
# This will be in the installer/uninstaller's title bar
Name "${APPNAME}"
Icon "logo.ico"
outFile "aris-install.exe"
 
!include LogicLib.nsh
 
# Just three pages - license agreement, install location, and installation
page license
page directory
Page instfiles
 
!macro VerifyUserIsAdmin
UserInfo::GetAccountType
pop $0
${If} $0 != "admin" ;Require admin rights on NT4+
        messageBox mb_iconstop "Administrator rights required!"
        setErrorLevel 740 ;ERROR_ELEVATION_REQUIRED
        quit
${EndIf}
!macroend
 
function .onInit
	setShellVarContext all
	!insertmacro VerifyUserIsAdmin
functionEnd
 
section "install"
	# Files for the install directory - to build the installer, these should be in the same directory as the install script (this file)
	setOutPath $INSTDIR
	# Files added here should be removed by the uninstaller (see section "uninstall")
	file "aris.exe"
	file "logo.ico"
    file "LICENSE.txt"
    file "aris-client.jar"
    createDirectory "$INSTDIR\lib"
    setOutPath $INSTDIR\lib
    file "lib\bcpkix-jdk15on-1.59.jar"
    file "lib\bcprov-jdk15on-1.59.jar"
    file "lib\bctls-jdk15on-1.59.jar"
    file "lib\commons-cli-1.4.jar"
    file "lib\commons-collections4-4.1.jar"
    file "lib\commons-io-2.6.jar"
    file "lib\commons-lang3-3.7.jar"
    file "lib\gson-2.8.2.jar"
    file "lib\log4j-api-2.10.0.jar"
    file "lib\log4j-core-2.10.0.jar"

	# Uninstaller - See function un.onInit and section "uninstall" for configuration
	writeUninstaller "$INSTDIR\uninstall.exe"
 
	# Start Menu
	createShortCut "$SMPROGRAMS\${APPNAME}.lnk" "$INSTDIR\aris.exe" "" "$INSTDIR\logo.ico"
 
	# Registry information for add/remove programs
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "DisplayName" "${APPNAME}"
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "UninstallString" "$\"$INSTDIR\uninstall.exe$\""
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "QuietUninstallString" "$\"$INSTDIR\uninstall.exe$\" /S"
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "InstallLocation" "$\"$INSTDIR$\""
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "DisplayIcon" "$\"$INSTDIR\logo.ico$\""
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "HelpLink" "$\"${HELPURL}$\""
	# There is no option for modifying or repairing the install
	WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "NoModify" 1
	WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}" "NoRepair" 1
	# Set the INSTALLSIZE constant (!defined at the top of this script) so Add/Remove Programs can accurately report the size
sectionEnd
 
# Uninstaller
 
function un.onInit
	SetShellVarContext all
 
	#Verify the uninstaller - last chance to back out
	MessageBox MB_OKCANCEL "Permanantly remove ${APPNAME}?" IDOK next
		Abort
	next:
	!insertmacro VerifyUserIsAdmin
functionEnd
 
section "uninstall"
 
	# Remove Start Menu launcher
	delete "$SMPROGRAMS\${APPNAME}.lnk"
 
	# Remove files

    delete "$INSTDIR\lib\bcpkix-jdk15on-1.59.jar"
    delete "$INSTDIR\lib\bcprov-jdk15on-1.59.jar"
    delete "$INSTDIR\lib\bctls-jdk15on-1.59.jar"
    delete "$INSTDIR\lib\commons-cli-1.4.jar"
    delete "$INSTDIR\lib\commons-collections4-4.1.jar"
    delete "$INSTDIR\lib\commons-io-2.6.jar"
    delete "$INSTDIR\lib\commons-lang3-3.7.jar"
    delete "$INSTDIR\lib\gson-2.8.2.jar"
    delete "$INSTDIR\lib\log4j-api-2.10.0.jar"
    delete "$INSTDIR\lib\log4j-core-2.10.0.jar"

    rmDir "$INSTDIR\lib"

	delete "$INSTDIR\aris.exe"
	delete "$INSTDIR\logo.ico"
    delete "$INSTDIR\LICENSE.txt"
    delete "$INSTDIR\aris-client.jar"
 
	# Always delete uninstaller as the last action
	delete "$INSTDIR\uninstall.exe"
 
	# Try to remove the install directory - this will only happen if it is empty
	rmDir "$INSTDIR"
 
	# Remove uninstaller information from the registry
	DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}"
sectionEnd
