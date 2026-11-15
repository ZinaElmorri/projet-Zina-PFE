"""Livrables de l'étape C : légendes des captures, paragraphe d'intégration, résultat du scénario de soutenance.

Usage (depuis ai-module/) :  python -m benchmark.livrables_c

Les captures sont produites par ``capturer_ecrans.mjs`` (application lancée, scénario importé).
Les chiffres du scénario de soutenance ont été relevés avec le modèle en service sur le fichier SOC-DEMO du mois
courant (``MESURE`` ci-dessous) ; ils se vérifient en rejouant le scénario (voir scenario_soutenance/README.md).
"""
from __future__ import annotations

from . import reporting as rp

MESURE = {
    "date": "2026-11-15", "modele": "iforest-2026-09-25 (modèle en service)",
    "fiches": 1100, "anomaliesConnues": 12, "suspectsIa": 9, "detecteesParIa": 5, "fauxPositifsIa": 4,
    "fauxPositifsSurCasNormauxAtypiques": 4, "niveaux": {"eleve": 7, "moyen": 2, "faible": 0},
    "metier": {"erreurs": 1, "avertissements": 1},
}

# (type d'anomalie, matricule, détecté par l'IA, autre contrôle)
LIGNES_SCENARIO = [
    ("Montant multiplié par 3 à 10", "EMP00407", "Oui — élevé (0,75), montant très supérieur à l'habituel", "—"),
    ("Erreur de virgule (×100)", "EMP01027", "Oui — élevé (0,87), erreur de virgule probable", "—"),
    ("Erreur de virgule (÷100)", "EMP00941", "Non", "—"),
    ("Hausse subtile", "EMP00325", "Oui — moyen (0,54), hausse inhabituelle", "—"),
    ("IBAN modifié et montant élevé", "EMP00529", "Oui — élevé (0,60), IBAN modifié et montant élevé", "—"),
    ("IBAN partagé (1re fiche)", "EMP00525", "Oui — élevé (0,60), IBAN partagé", "—"),
    ("IBAN partagé (2e fiche)", "EMP00731", "Non", "—"),
    ("Montant nul ou négatif", "EMP00252", "Non", "Contrôle métier : erreur (montant nul)"),
    ("Doublon (2 fiches)", "EMP00284", "Non", "Contrôle métier : avertissement de doublon"),
    ("Salarié fantôme", "EMP01122", "Non", "—"),
    ("Salarié sorti encore payé", "EMP00129", "Non", "—"),
]


def _tableau() -> None:
    rp.ecrire_tableau(
        "tab_scenario_soutenance", ["Anomalie connue", "Matricule", "Contrôle IA", "Autre contrôle"],
        [list(l) for l in LIGNES_SCENARIO],
        "Scénario de soutenance : 12 fiches anormales connues dans un fichier de 1 100 fiches, et ce que l'application signale "
        "avec le modèle en service.", "llll", alignements_latex="p{3.8cm}lp{5cm}p{3.6cm}")
    m = MESURE
    rp.ecrire_texte(
        "tab_scenario_soutenance",
        "Résultat du scénario de soutenance avec le modèle en service (fichier de 1 100 fiches, 12 anomalies connues).",
        f"Sur le fichier de démonstration, le contrôle IA signale {m['suspectsIa']} fiches sur {m['fiches']} (0,8 %), dont "
        f"{m['detecteesParIa']} sont de vraies anomalies parmi les 12 connues : les erreurs qui déforment le montant ou touchent "
        "l'IBAN sont retrouvées, avec une explication lisible et un type probable. Les contrôles métier complètent ce résultat "
        "en signalant le montant nul (erreur) et le doublon (avertissement), que l'IA seule ne voit pas. Quatre fiches signalées "
        "par l'IA sont des fausses alertes : trois changements d'IBAN légitimes, que la règle « IBAN modifié » signale "
        "systématiquement pour vérification, et une fiche au profil inhabituel. Restent non détectés l'IBAN partagé de la seconde "
        "fiche, l'erreur de virgule vers le bas, le salarié fantôme et le salarié sorti encore payé, conformément aux limites "
        "mesurées dans l'évaluation (sections 3 et 4). Ce scénario illustre donc l'apport de l'IA, sans en cacher les limites.")


def _textes() -> None:
    rp.ecrire_texte(
        "capture_qualite_modele_ia",
        "Page « Qualité du modèle IA » (administrateur) : version, date d'entraînement, seuil, performances du banc d'essai, "
        "détection par type d'anomalie et courbe précision–rappel.",
        "Cette page de supervision technique est réservée à l'administrateur. Elle affiche la version du modèle en service, "
        "sa date d'entraînement, la taille de son jeu d'entraînement et le seuil effectivement appliqué, puis les résultats du "
        "dernier banc d'essai lus dans le fichier de résultats : précision, rappel, F1, PR-AUC, charge de faux positifs, "
        "taux de détection par type d'anomalie et courbe précision–rappel avec le point de fonctionnement actuel. Un bandeau "
        "rappelle que les mesures portent sur des données synthétiques. La page ne contient aucune donnée de salarié : elle "
        "répond au besoin de l'administrateur de superviser le modèle sans accéder à la paie.")
    rp.ecrire_texte(
        "capture_synthese_controle_ia",
        "Synthèse du résultat d'un fichier : carte « Contrôle IA » avec le nombre de fiches suspectes, leur part et la "
        "répartition par type probable.",
        "La synthèse du fichier regroupe quatre cartes cliquables : validation XSD, contrôles métier, contrôle IA et statut. "
        "La carte IA indique le nombre de fiches suspectes sur le nombre de fiches analysées, le pourcentage correspondant, la "
        "version du modèle et la répartition par type probable d'anomalie. L'Expert RH ou le Manager voit ainsi en un coup d'œil "
        "ce que l'IA a relevé, et peut ouvrir le détail d'un clic. Si le module IA est indisponible, la carte l'indique et le "
        "fichier reste exploitable avec les seuls contrôles métier et la validation XSD.")
    rp.ecrire_texte(
        "capture_sous_page_controle_ia",
        "Sous-page « Contrôle IA » : fiches suspectes triées par score, avec niveau, type probable, explication et recommandation.",
        "Pour chaque fiche suspecte, la sous-page affiche le score et son niveau (faible, moyen ou élevé), le type d'anomalie "
        "probable, une explication en langage clair (par exemple « Montant 3,1× la moyenne des 6 derniers mois » ou « IBAN modifié "
        "ce mois-ci ») et une recommandation de correction adaptée au type. Le tableau est trié par score décroissant et peut être "
        "filtré par niveau. L'explication traduit les variables qui ont conduit le modèle à signaler la fiche : l'utilisateur sait "
        "quoi vérifier et pourquoi, ce qui rend la décision (corriger, confirmer ou ignorer) rapide et traçable.")
    rp.ecrire_texte(
        "capture_kpi_ia",
        "Tableau de bord KPI : cartes « Anomalies détectées par l'IA » et « Part détectée par l'IA seule ».",
        "Deux indicateurs du tableau de bord KPI mesurent l'apport de l'IA sur la période choisie : le nombre de fiches signalées "
        "par l'IA et la part de celles que les contrôles métier ne signalent pas (IBAN ou BIC invalide, montant nul ou négatif, "
        "doublon). Cette seconde valeur chiffre la valeur ajoutée de l'IA face aux règles, comme l'analyse de la section 4. Les "
        "chiffres sont calculés sur la dernière analyse de chaque fiche ; en l'absence de fiche signalée, la part est indisponible "
        "(« — ») et non nulle.")
    rp.ecrire_texte(
        "integration_ia_parcours_utilisateur", "Intégration de l'IA dans le parcours utilisateur.",
        "L'IA est intégrée au parcours existant sans le bloquer ni le dupliquer. Lors du contrôle d'un fichier, l'Expert RH obtient, "
        "en plus des contrôles métier et de la validation XSD, une analyse IA de chaque fiche ; le résultat est conservé avec le "
        "fichier. La synthèse résume l'analyse, la sous-page « Contrôle IA » la détaille fiche par fiche avec une explication et une "
        "recommandation, et le Manager retrouve les mêmes informations lors de la validation, ce qui sert le principe des quatre yeux. "
        "Si l'IA est indisponible, le parcours continue et le signale. Les rôles sont respectés : l'administrateur supervise la qualité "
        "du modèle et voit des indicateurs agrégés, sans jamais accéder aux données des salariés, tandis que l'Expert RH et le Manager "
        "travaillent sur les fiches. L'IA reste une aide à la décision : elle signale, l'humain tranche.")


def _section() -> None:
    def fig(nom: str) -> str:
        return f"![{nom}](rapport/captures/{nom}.png)\n\n" + rp.lire_fichier(f"{rp.DOSSIER_TEXTES}/{nom}.md")

    tableau = rp.lire_fichier(f"{rp.DOSSIER_TABLEAUX}/tab_scenario_soutenance.md")
    texte_tableau = rp.lire_fichier(f"{rp.DOSSIER_TEXTES}/tab_scenario_soutenance.md")
    integration = rp.lire_fichier(f"{rp.DOSSIER_TEXTES}/integration_ia_parcours_utilisateur.md")
    rp.ecrire_section("05_integration_application", f"""## 5. Intégration de l'IA dans l'application (étape C)

### 5.1 Intégration de l'IA dans le parcours utilisateur

{integration}

### 5.2 Qualité du modèle (administrateur)

{fig('capture_qualite_modele_ia')}

### 5.3 Contrôle IA d'un fichier (Expert RH et Manager)

{fig('capture_synthese_controle_ia')}

{fig('capture_sous_page_controle_ia')}

### 5.4 Indicateurs KPI de l'apport de l'IA

{fig('capture_kpi_ia')}

### 5.5 Scénario de soutenance

{tableau}

{texte_tableau}""")


def main() -> None:
    rp.maj_resultats("scenarioSoutenanceMesure", MESURE)
    _tableau()
    _textes()
    _section()
    rp.ecrire_reproduction()
    print("Rapport assemblé :", rp.assembler_rapport())


if __name__ == "__main__":
    main()
