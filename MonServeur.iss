; Script d'installation pour l'application web PHP
[Setup]
; Nom de l'installateur
AppName=Mon Serveur PHP
AppVersion=1.0
DefaultDirName={pf}\MonServeur
DefaultGroupName=MonServeur
OutputDir=.
OutputBaseFilename=MonServeurInstaller
Compression=lzma
SolidCompression=yes

[Files]
; Spécifiez ici les fichiers à inclure dans l'installateur
Source: "C:\Users\asus\Documents\ProjetHTTP\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs

[Icons]
; Crée un raccourci sur le bureau pour l'application
Name: "{userdesktop}\Mon Serveur"; Filename: "{app}\mon_serveur.exe"

[Run]
; Vous pouvez ajouter des actions à exécuter après l'installation
; Exemple : lancer un serveur PHP ou Apache si nécessaire
; Exemple : Exécuter un script pour configurer le serveur
Filename: "{app}\mon_serveur\start.bat"; StatusMsg: "Lancement de l'application..."; Flags: runhidden
