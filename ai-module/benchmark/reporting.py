"""Outils communs de production des livrables du rapport : tableaux, figures, textes, resultats.json.

Tous les livrables sont écrits dans ``benchmark/rapport/`` : ``figures/`` (PNG 300 dpi + PDF vectoriel),
``tableaux/`` (Markdown + LaTeX booktabs, format français), ``textes/`` (légende + interprétation),
``resultats.json`` (tous les chiffres, relus par l'application à l'étape C).
"""
from __future__ import annotations

import json
import os
from typing import Dict, Iterable, List, Optional, Sequence

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "rapport")
DOSSIER_FIGURES = os.path.join(RACINE, "figures")
DOSSIER_TABLEAUX = os.path.join(RACINE, "tableaux")
DOSSIER_TEXTES = os.path.join(RACINE, "textes")
CHEMIN_RESULTATS = os.path.join(RACINE, "resultats.json")

# Palette lisible en noir et blanc comme en couleur (charte de l'application : bleu nuit / turquoise).
COULEURS = ["#1F3A5F", "#2A9D8F", "#E9A23B", "#C8553D", "#8A6BBE", "#6C7A89"]
ESPACE_FIN = "\u202f"


# --- Formats français ---------------------------------------------------------------------------------

def fr(valeur: float, decimales: int = 0, latex: bool = False) -> str:
    """12345.6 -> « 12 345,6 » (virgule décimale, espace fine comme séparateur de milliers)."""
    texte = f"{valeur:,.{decimales}f}"
    separateur = "\\," if latex else ESPACE_FIN
    return texte.replace(",", "§").replace(".", ",").replace("§", separateur)


def fr_pm(moyenne: float, ecart_type: float, decimales: int = 3, latex: bool = False) -> str:
    """Moyenne ± écart-type : « 0,912 ± 0,014 »."""
    signe = "$\\pm$" if latex else "±"
    return f"{fr(moyenne, decimales, latex)} {signe} {fr(ecart_type, decimales, latex)}"


def fr_pct(valeur: float, decimales: int = 1, latex: bool = False) -> str:
    """0.0203 -> « 2,0 % » (la valeur est une proportion)."""
    if latex:
        return f"{fr(valeur * 100, decimales, True)}\\,\\%"
    return f"{fr(valeur * 100, decimales)}\u00a0%"


def _echapper_latex(texte: str) -> str:
    """Échappe les caractères spéciaux LaTeX d'un texte brut (&, %, _, #) et convertit les espaces insécables."""
    for a, b in (("&", "\\&"), ("%", "\\%"), ("_", "\\_"), ("#", "\\#"), ("\u202f", "\\,"), ("\u00a0", "~")):
        texte = texte.replace(a, b)
    return texte


# --- Fichiers ------------------------------------------------------------------------------------------

def _dossier(chemin: str) -> str:
    os.makedirs(chemin, exist_ok=True)
    return chemin


def ecrire_tableau(nom: str, entetes: Sequence[str], lignes: Sequence[Sequence], legende: str,
                   alignements: Optional[str] = None, lignes_latex: Optional[Sequence[Sequence]] = None,
                   alignements_latex: Optional[str] = None) -> None:
    """Écrit ``tableaux/<nom>.md`` et ``tableaux/<nom>.tex`` (booktabs).

    ``lignes`` : cellules déjà formatées pour Markdown ; ``lignes_latex`` : variante LaTeX (sinon les mêmes,
    avec échappement des caractères spéciaux).
    """
    dossier = _dossier(DOSSIER_TABLEAUX)
    alignements = alignements or "l" + "r" * (len(entetes) - 1)

    def ligne_md(cellules: Iterable) -> str:
        return "| " + " | ".join(str(c).replace("|", "\\|") for c in cellules) + " |"

    separateur = "| " + " | ".join(
        ":---" if a == "l" else ("---:" if a == "r" else ":---:") for a in alignements) + " |"
    md = [ligne_md(entetes), separateur] + [ligne_md(l) for l in lignes] + ["", f"*{legende}*", ""]
    with open(os.path.join(dossier, f"{nom}.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(md))

    source = lignes_latex if lignes_latex is not None else [[_echapper_latex(str(c)) for c in l] for l in lignes]
    tex = [
        "\\begin{table}[htbp]", "\\centering", f"\\caption{{{_echapper_latex(legende)}}}",
        f"\\label{{tab:{nom.removeprefix('tab_')}}}",
        "\\small", f"\\begin{{tabular}}{{{alignements_latex or alignements}}}", "\\toprule",
        " & ".join(f"\\textbf{{{_echapper_latex(e)}}}" for e in entetes) + " \\\\", "\\midrule",
    ]
    tex += [" & ".join(str(c) for c in l) + " \\\\" for l in source]
    tex += ["\\bottomrule", "\\end{tabular}", "\\end{table}", ""]
    with open(os.path.join(dossier, f"{nom}.tex"), "w", encoding="utf-8") as f:
        f.write("\n".join(tex))


def style_figure() -> None:
    plt.rcParams.update({
        "font.size": 11, "axes.labelsize": 12, "xtick.labelsize": 10, "ytick.labelsize": 10,
        "legend.fontsize": 10, "axes.spines.top": False, "axes.spines.right": False,
        "axes.grid": True, "grid.alpha": 0.25, "figure.dpi": 100, "pdf.fonttype": 42, "ps.fonttype": 42,
        "axes.prop_cycle": matplotlib.cycler(color=COULEURS),
    })


def enregistrer_figure(fig, nom: str) -> None:
    """PNG 300 dpi + PDF vectoriel ; pas de titre dans l'image (il ira dans la légende du rapport)."""
    dossier = _dossier(DOSSIER_FIGURES)
    fig.tight_layout()
    fig.savefig(os.path.join(dossier, f"{nom}.png"), dpi=300)
    fig.savefig(os.path.join(dossier, f"{nom}.pdf"))
    plt.close(fig)


def ecrire_texte(nom: str, legende: str, paragraphe: str) -> None:
    """Légende proposée + paragraphe d'interprétation pour une figure ou un tableau."""
    dossier = _dossier(DOSSIER_TEXTES)
    with open(os.path.join(dossier, f"{nom}.md"), "w", encoding="utf-8") as f:
        f.write(f"**Légende proposée** : {legende}\n\n**Interprétation** : {paragraphe}\n")


def maj_resultats(section: str, donnees: Dict) -> None:
    """Fusionne ``donnees`` dans ``resultats.json`` sous la clé ``section`` (les autres sections sont conservées)."""
    _dossier(RACINE)
    courant: Dict = {}
    if os.path.exists(CHEMIN_RESULTATS):
        with open(CHEMIN_RESULTATS, encoding="utf-8") as f:
            courant = json.load(f)
    courant[section] = donnees
    with open(CHEMIN_RESULTATS, "w", encoding="utf-8") as f:
        json.dump(courant, f, ensure_ascii=False, indent=2)


def lire_resultats() -> Dict:
    if not os.path.exists(CHEMIN_RESULTATS):
        return {}
    with open(CHEMIN_RESULTATS, encoding="utf-8") as f:
        return json.load(f)


# --- Rapport assemblé ----------------------------------------------------------------------------------

DOSSIER_SECTIONS = os.path.join(RACINE, "sections")
CHEMIN_RAPPORT = os.path.join(RACINE, "..", "rapport_evaluation.md")


def ecrire_section(nom: str, markdown: str) -> None:
    """Écrit une section du rapport (``sections/<nom>.md``, triées par nom pour l'assemblage)."""
    with open(os.path.join(_dossier(DOSSIER_SECTIONS), f"{nom}.md"), "w", encoding="utf-8") as f:
        f.write(markdown.strip() + "\n")


def assembler_rapport() -> str:
    """Concatène les sections existantes dans ``benchmark/rapport_evaluation.md``."""
    dossier = _dossier(DOSSIER_SECTIONS)
    morceaux = []
    for fichier in sorted(os.listdir(dossier)):
        if fichier.endswith(".md"):
            with open(os.path.join(dossier, fichier), encoding="utf-8") as f:
                morceaux.append(f.read().strip())
    chemin = os.path.normpath(CHEMIN_RAPPORT)
    with open(chemin, "w", encoding="utf-8") as f:
        f.write("\n\n".join(morceaux) + "\n")
    return chemin


def ecrire_reproduction() -> None:
    """Section « Reproduction » du rapport : une commande par étape."""
    lignes = "\n".join(f"{commande:<46}# {role}" for commande, role in COMMANDES_REPRODUCTION)
    ecrire_section("99_reproduction", f"""## Reproduction

Depuis le dossier `ai-module/` (environnement virtuel avec `pip install -r benchmark/requirements-benchmark.txt`) :

```
{lignes}
```""")


COMMANDES_REPRODUCTION = [
    ("python -m benchmark.describe_dataset", "étape 0 : jeu de données, scénario de soutenance"),
    ("python -m benchmark.evaluate_full", "étape A : évaluation du modèle (5 graines, ~1 min)"),
    ("python -m benchmark.compare", "étape B : comparaison des approches (~8 min ; --cache pour les livrables seuls)"),
    ("python -m benchmark.exporter_candidat", "étape B : version candidate (non activée)"),
]


def lire_fichier(chemin: str) -> str:
    with open(chemin, encoding="utf-8") as f:
        return f.read().strip()
