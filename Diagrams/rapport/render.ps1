# Génère les images PNG (dossier png/) et SVG (dossier svg/) de tous les diagrammes du rapport.
# Usage : powershell -ExecutionPolicy Bypass -File Diagrams\rapport\render.ps1 [nom_sans_extension]
param([string]$Nom = "*")

$jar = Join-Path $env:USERPROFILE ".vscode\extensions\jebbs.plantuml-2.18.1\plantuml.jar"
if (-not (Test-Path $jar)) { throw "plantuml.jar introuvable : $jar" }
# Graphviz portable (sans installation) : meilleure mise en page des diagrammes non séquentiels.
$dot = Get-ChildItem (Join-Path $env:LOCALAPPDATA "graphviz") -Recurse -Filter dot.exe -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName
if ($dot) { $env:GRAPHVIZ_DOT = $dot } else { Write-Warning "Graphviz absent : installez-le pour un rendu correct." }

Push-Location $PSScriptRoot
try {
    $fichiers = Get-ChildItem -Filter *.puml | Where-Object { $_.Name -like "$Nom.puml" } |
        Select-Object -ExpandProperty Name
    if (-not $fichiers) { throw "Aucun diagramme ne correspond a : $Nom" }
    java -DPLANTUML_LIMIT_SIZE=16384 -jar $jar -charset UTF-8 -tpng -o png $fichiers
    java -DPLANTUML_LIMIT_SIZE=16384 -jar $jar -charset UTF-8 -tsvg -o svg $fichiers
} finally {
    Pop-Location
}
