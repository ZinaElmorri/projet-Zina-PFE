"""Étape 0 : génère le jeu de données de référence et les livrables « Description du jeu de données ».

Usage (depuis ai-module/) :  python -m benchmark.describe_dataset [--seed 42]

Produit dans benchmark/rapport/ : tableaux, figures, textes, resultats.json (section jeuDeDonnees),
section 02 du rapport ; dans benchmark/scenario_soutenance/ : les fichiers du scénario de soutenance ;
dans benchmark/data/ (non versionné) : le jeu de référence complet en CSV.
"""
import argparse
import os

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

from . import dataset as ds
from . import reporting as rp

PRINCIPE = {
    "montant_multiplie": "Montant légitime × tirage uniforme dans [3 ; 10]",
    "erreur_virgule": "Montant légitime × 100 ou ÷ 100 (une chance sur deux)",
    "hausse_subtile": "Montant légitime × tirage uniforme dans [1,25 ; 1,60]",
    "montant_nul_negatif": "Montant égal à 0 ou à l'opposé du montant légitime",
    "doublon": "Fiche recopiée à l'identique ; les deux fiches sont étiquetées",
    "iban_change_montant_eleve": "Nouvel IBAN valide et montant légitime × [1,5 ; 3]",
    "iban_partage": "Un salarié reprend l'IBAN d'un collègue ; les deux fiches sont étiquetées",
    "salarie_fantome": "Nouveau salarié sans historique, salaire plausible (× [0,9 ; 1,5] du salaire type)",
    "salarie_sorti_paye": "Salarié parti depuis 2 à 4 mois, payé à nouveau à son salaire habituel",
}
MOIS_FR = ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."]
NOMS_PARTITIONS = {"entrainement": "Entraînement", "validation": "Validation", "test": "Test"}
DOSSIER_SCENARIO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scenario_soutenance")
DOSSIER_DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")


def _mois_fr(mois_aaaa_mm: str) -> str:
    a, m = mois_aaaa_mm.split("-")
    return f"{MOIS_FR[int(m) - 1]} {a}"


def _statistiques(df: pd.DataFrame, sim_societes) -> dict:
    parts = ds.decouper(df)
    normaux = df[df["label"] == 0]
    sans_fantome = df[df["type_anomalie"] != "salarie_fantome"]
    par_salarie = sans_fantome.groupby("salarie_id")["mois_index"].agg(["min", "max"])
    types = list(ds.TYPES_ANOMALIES)

    def par_type(sous_df):
        return {t: int((sous_df["type_anomalie"] == t).sum()) for t in types}

    societes = []
    for s in sim_societes:
        d = normaux[normaux["societe_id"] == s["id"]]
        profils = d.drop_duplicates("salarie_id")["profil"].value_counts()
        tout = df[df["societe_id"] == s["id"]]
        societes.append({
            "id": s["id"], "nom": s["nom"], "salaries": int(d["salarie_id"].nunique()),
            "employes": int(profils.get("employé", 0)), "cadres": int(profils.get("cadre", 0)),
            "dirigeants": int(profils.get("dirigeant", 0)),
            "treizieme": bool((tout["evenement_normal"] == "treizieme_mois").any()),
            "fiches": int(len(tout)), "anomalies": int(tout["label"].sum()),
        })
    return {
        "graine": int(ds.SEED_REFERENCE),
        "nbSocietes": len(sim_societes),
        "nbSalaries": int(normaux["salarie_id"].nunique()),
        "effectifMensuelMoyen": round(float(normaux.groupby("mois_index").size().mean()), 1),
        "nbMois": int(df["mois_index"].nunique()),
        "periode": [df["mois"].iloc[0], df["mois"].iloc[-1]],
        "nbFiches": int(len(df)),
        "nbAnomalies": int(df["label"].sum()),
        "tauxAnomalies": round(float(df["label"].mean()), 4),
        "entrees": int((par_salarie["min"] > 1).sum()),
        "sorties": int((par_salarie["max"] < ds.N_MOIS).sum()),
        "evenementsNormaux": {k: int(v) for k, v in df["evenement_normal"].value_counts().items()},
        "anomaliesParType": par_type(df),
        "anomaliesParMois": [int(x) for x in df.groupby("mois_index")["label"].sum()],
        "partitions": {
            nom: {"mois": [int(min(ds.DECOUPAGE[nom])), int(max(ds.DECOUPAGE[nom]))], "fiches": int(len(p)),
                  "anomalies": int(p["label"].sum()), "taux": round(float(p["label"].mean()), 4),
                  "anomaliesParType": par_type(p)}
            for nom, p in parts.items()},
        "societes": societes,
        "salaireMedianParProfil": {
            p: round(float(normaux[(normaux["profil"] == p) & (normaux["evenement_normal"] != "treizieme_mois")]
                           ["montant"].median()), 2) for p in ds.PROFILS},
        "parametres": {
            "tauxEvenements": ds.TAUX_EVENEMENTS, "poidsTypes": ds.POIDS_TYPES, "probabilitePrime": ds.P_PRIME,
            "probabiliteChangementIbanLegitime": ds.P_CHANGEMENT_IBAN, "probabilitePromotion": ds.P_PROMOTION,
            "probabiliteDepart": ds.P_DEPART, "probabiliteEmbauche": ds.P_EMBAUCHE,
            "bruitMensuel": ds.BRUIT_MENSUEL, "societesTreiziemeMois": list(ds.SOCIETES_TREIZIEME_MOIS),
            "historiqueMois": ds.HISTORY_MONTHS,
        },
    }


# --- Tableaux ----------------------------------------------------------------------------------------

def _tableaux(st: dict) -> None:
    ev = st["evenementsNormaux"]
    lignes = [
        ("Sociétés", rp.fr(st["nbSocietes"])),
        ("Salariés distincts (hors fantômes)", rp.fr(st["nbSalaries"])),
        ("Effectif mensuel moyen", rp.fr(st["effectifMensuelMoyen"], 0)),
        ("Entrées / sorties sur la période", f"{rp.fr(st['entrees'])} / {rp.fr(st['sorties'])}"),
        ("Mois de paie", f"{st['nbMois']} ({_mois_fr(st['periode'][0])} – {_mois_fr(st['periode'][1])})"),
        ("Fiches de paie", rp.fr(st["nbFiches"])),
        ("Fiches anormales étiquetées", f"{rp.fr(st['nbAnomalies'])} ({rp.fr_pct(st['tauxAnomalies'], 2)})"),
        ("Types d'anomalies", str(len(ds.TYPES_ANOMALIES))),
        ("Fiches normales atypiques : primes", rp.fr(ev.get("prime", 0))),
        ("Fiches normales atypiques : 13e mois", rp.fr(ev.get("treizieme_mois", 0))),
        ("Fiches normales atypiques : changements d'IBAN légitimes", rp.fr(ev.get("iban_change_legitime", 0))),
    ]
    rp.ecrire_tableau("tab_description_jeu_donnees", ["Caractéristique", "Valeur"], lignes,
                      "Description du jeu de données synthétique de référence.", "lr")

    lignes = []
    for nom, p in st["partitions"].items():
        a, b = p["mois"]
        lignes.append((NOMS_PARTITIONS[nom], f"{a}–{b}", rp.fr(p["fiches"]), rp.fr(p["anomalies"]),
                       rp.fr_pct(p["taux"], 2)))
    rp.ecrire_tableau("tab_decoupage_chronologique", ["Jeu", "Mois", "Fiches", "Anomalies", "Taux d'anomalies"],
                      lignes, "Découpage chronologique du jeu de données par mois de paie.", "lrrrr")

    lignes = [(s["id"], s["nom"], rp.fr(s["salaries"]), f"{s['employes']} / {s['cadres']} / {s['dirigeants']}",
               "oui" if s["treizieme"] else "non", rp.fr(s["fiches"]), rp.fr(s["anomalies"])) for s in st["societes"]]
    rp.ecrire_tableau("tab_societes", ["Société", "Raison sociale (fictive)", "Salariés", "Employés / cadres / dirigeants",
                                       "13e mois", "Fiches", "Anomalies"], lignes,
                      "Sociétés simulées : effectifs, profils, 13e mois et anomalies injectées.", "llrrcrr")

    types = list(ds.TYPES_ANOMALIES)
    lignes = [(ds.TYPES_ANOMALIES[t], rp.fr(st["partitions"]["entrainement"]["anomaliesParType"][t]),
               rp.fr(st["partitions"]["validation"]["anomaliesParType"][t]),
               rp.fr(st["partitions"]["test"]["anomaliesParType"][t]), rp.fr(st["anomaliesParType"][t]),
               PRINCIPE[t]) for t in types]
    total = ["Total"] + [rp.fr(st["partitions"][n]["anomalies"]) for n in ("entrainement", "validation", "test")] \
        + [rp.fr(st["nbAnomalies"]), ""]
    rp.ecrire_tableau("tab_anomalies_par_type",
                      ["Type d'anomalie", "Entraînement", "Validation", "Test", "Total", "Principe d'injection"],
                      lignes + [total], "Anomalies étiquetées par type et par jeu (nombre de fiches).", "lrrrrl",
                      alignements_latex="lrrrrp{6cm}")


# --- Figures -----------------------------------------------------------------------------------------

def _figures(df: pd.DataFrame, st: dict) -> None:
    rp.style_figure()
    normaux = df[(df["label"] == 0) & (df["evenement_normal"] != "treizieme_mois")]

    fig, ax = plt.subplots(figsize=(8, 4.6))
    bornes = np.logspace(np.log10(1200), np.log10(25000), 70)
    for i, profil in enumerate(ds.PROFILS):
        valeurs = normaux.loc[normaux["profil"] == profil, "montant"]
        libelle = {"employé": "Employés", "cadre": "Cadres", "dirigeant": "Dirigeants"}[profil]
        ax.hist(valeurs, bins=bornes, alpha=0.75, color=rp.COULEURS[i],
                label=f"{libelle} (médiane {rp.fr(valeurs.median(), 0)} €)")
    ax.set_xscale("log")
    ax.xaxis.set_minor_formatter(plt.NullFormatter())
    ax.set_xticks([1500, 2000, 3000, 5000, 8000, 12000, 20000])
    ax.set_xticklabels(["1\u202f500", "2\u202f000", "3\u202f000", "5\u202f000", "8\u202f000", "12\u202f000", "20\u202f000"])
    ax.yaxis.set_major_formatter(plt.FuncFormatter(lambda v, _: rp.fr(v, 0)))
    ax.set_xlabel("Montant net mensuel (€, échelle logarithmique)")
    ax.set_ylabel("Nombre de fiches")
    ax.legend(frameon=False)
    rp.enregistrer_figure(fig, "fig_distribution_salaires_profil")

    fig, ax = plt.subplots(figsize=(8.4, 4.2))
    par_mois = df.groupby("mois_index")["label"].agg(["sum", "mean"])
    couleur_partition = {}
    for i, nom in enumerate(("entrainement", "validation", "test")):
        for m in ds.DECOUPAGE[nom]:
            couleur_partition[m] = rp.COULEURS[i]
    ax.bar(par_mois.index, par_mois["sum"], color=[couleur_partition[m] for m in par_mois.index])
    mois = df.drop_duplicates("mois_index").set_index("mois_index")["mois"]
    ax.set_xticks(par_mois.index)
    ax.set_xticklabels([f"{MOIS_FR[int(mois[m].split('-')[1]) - 1]}\n{mois[m][2:4]}" for m in par_mois.index], fontsize=8)
    ax.set_xlabel("Mois de paie")
    ax.set_ylabel("Fiches anormales")
    ax.set_ylim(0, par_mois["sum"].max() * 1.25)
    from matplotlib.patches import Patch
    ax.legend(handles=[Patch(color=rp.COULEURS[i], label=NOMS_PARTITIONS[nom])
                       for i, nom in enumerate(("entrainement", "validation", "test"))],
              frameon=False, loc="upper left", ncol=3)
    rp.enregistrer_figure(fig, "fig_anomalies_par_mois")


# --- Textes ------------------------------------------------------------------------------------------

def _textes(st: dict) -> None:
    p = st["partitions"]
    med = st["salaireMedianParProfil"]
    ev = st["evenementsNormaux"]
    pct = lambda x, d=1: rp.fr_pct(x, d)
    n = rp.fr

    rp.ecrire_texte(
        "tab_description_jeu_donnees",
        "Description du jeu de données synthétique de référence (graine 42).",
        f"Le jeu de référence simule {n(st['nbSocietes'])} sociétés et {n(st['nbSalaries'])} salariés distincts sur "
        f"{st['nbMois']} mois de paie, soit {n(st['nbFiches'])} fiches. Environ {pct(st['tauxAnomalies'], 1)} des fiches "
        f"({n(st['nbAnomalies'])}) portent une anomalie étiquetée, ce qui reproduit le fort déséquilibre de classes d'un "
        "contexte réel et justifie l'usage de la PR-AUC plutôt que de l'exactitude. Les mouvements de personnel "
        f"({n(st['entrees'])} entrées et {n(st['sorties'])} sorties) créent des salariés sans historique, y compris légitimes. "
        f"Le jeu contient aussi des fiches normales mais atypiques ({n(ev.get('prime', 0))} primes, "
        f"{n(ev.get('treizieme_mois', 0))} versements de 13e mois et {n(ev.get('iban_change_legitime', 0))} changements d'IBAN "
        "légitimes) qui servent de cas difficiles : un bon modèle doit les laisser passer.")

    rp.ecrire_texte(
        "tab_decoupage_chronologique",
        "Découpage chronologique : entraînement (mois 1 à 12), validation (13 à 15), test (16 à 18).",
        f"Le découpage suit l'ordre du temps et non un tirage aléatoire : le modèle est entraîné sur les mois 1 à 12, "
        f"le seuil de décision est choisi sur les mois 13 à 15 et les résultats finaux sont mesurés sur les mois 16 à 18, "
        "jamais vus. Ce protocole reproduit l'usage réel, où l'on contrôle la paie d'un mois à venir avec un modèle appris "
        f"sur le passé, et évite toute fuite d'information entre jeux. Les trois jeux ont des taux d'anomalies voisins "
        f"({pct(p['entrainement']['taux'], 2)}, {pct(p['validation']['taux'], 2)} et {pct(p['test']['taux'], 2)}), "
        f"avec {n(p['test']['anomalies'])} anomalies dans le jeu de test, effectif suffisant pour des taux de détection par type "
        "stables lorsqu'ils sont moyennés sur plusieurs graines. Le jeu de test se termine en décembre : il contient le 13e mois "
        "de trois sociétés, dont le modèle n'a vu qu'un seul exemple annuel (décembre 2024) à l'entraînement.")

    rp.ecrire_texte(
        "tab_societes",
        "Sociétés simulées : effectifs, répartition des profils, 13e mois et anomalies.",
        f"Les {st['nbSocietes']} sociétés couvrent des tailles très différentes, de {n(min(s['salaries'] for s in st['societes']))} à "
        f"{n(max(s['salaries'] for s in st['societes']))} salariés, afin que les statistiques de comparaison au sein d'une société "
        "(z-score) reposent sur des effectifs variés. Chaque société a son niveau de salaire de base et sa propre répartition "
        "employés, cadres et dirigeants. Trois sociétés versent un 13e mois en décembre, ce qui double leur fiche de paie ce mois-là. "
        "Les anomalies sont réparties proportionnellement aux effectifs. Les raisons sociales sont fictives, générées avec la "
        "bibliothèque Faker en localisation française.")

    rp.ecrire_texte(
        "tab_anomalies_par_type",
        "Anomalies étiquetées par type et par jeu ; la dernière colonne rappelle le principe d'injection.",
        "Neuf types d'anomalies sont injectés, des plus évidentes (montant multiplié, montant négatif ou nul) aux plus difficiles "
        "(hausse de 25 à 60 %, salarié fantôme, salarié sorti encore payé), qui ressemblent à des situations légitimes. "
        "Les doublons et les IBAN partagés étiquettent les deux fiches concernées, car l'Expert RH doit examiner la paire. "
        "Ces types sont tirés deux fois moins souvent que les autres pour que les effectifs par type restent comparables. "
        f"Le jeu de test contient entre {min(p['test']['anomaliesParType'].values())} et {max(p['test']['anomaliesParType'].values())} fiches par type, "
        "ce qui reste modeste : l'étape d'évaluation répète donc les mesures sur cinq graines et rapporte moyenne et écart-type.")

    rp.ecrire_texte(
        "fig_distribution_salaires_profil",
        "Distribution du montant net mensuel des fiches normales selon le profil du salarié (hors 13e mois).",
        f"Les trois profils occupent des plages de salaire nettement distinctes, avec des médianes de {n(med['employé'])} € pour les "
        f"employés, {n(med['cadre'])} € pour les cadres et {n(med['dirigeant'])} € pour les dirigeants. L'échelle logarithmique "
        "rend visible la queue des dirigeants sans écraser les employés. Un même montant peut être normal pour un dirigeant et "
        "suspect pour un employé : un seuil global sur le montant est donc inopérant. C'est la raison d'être des variables "
        "relatives du modèle (écart à l'historique du salarié, comparaison aux autres salariés de la société). "
        "Les distributions se recouvrent peu ; seuls les employés les mieux payés et les cadres les moins payés se rejoignent autour de 3 000 €.")

    rp.ecrire_texte(
        "fig_anomalies_par_mois",
        "Nombre de fiches anormales par mois de paie, coloré selon le jeu (entraînement, validation, test).",
        "Les anomalies sont réparties de façon régulière dans le temps, ce qui évite qu'un jeu ne soit artificiellement plus "
        f"facile qu'un autre : on compte de {n(min(st['anomaliesParMois']))} à {n(max(st['anomaliesParMois']))} fiches anormales par mois. "
        "Le découpage chronologique place l'entraînement sur les douze premiers mois, la validation sur les trois suivants et "
        "le test sur les trois derniers. Le bruit entre mois reflète "
        "le caractère aléatoire de l'injection ; il est absorbé par la répétition sur plusieurs graines.")

    rp.ecrire_texte(
        "methode_generation_et_limites",
        "Méthode de génération du jeu de données synthétique et limites.",
        "Le générateur simule la paie mois par mois. Chaque salarié a un salaire de base lié à son profil et à sa société, "
        "un bruit mensuel d'environ 1 %, une augmentation annuelle en janvier, des promotions occasionnelles, des primes "
        "ponctuelles normales, un 13e mois en décembre pour trois sociétés, des changements d'IBAN légitimes, des entrées et des "
        "sorties. Des anomalies de neuf types sont ensuite injectées à un taux d'environ 2 % des fiches et étiquetées. Les IBAN sont "
        "valides (clés RIB et IBAN calculées en modulo 97) et les noms viennent de Faker en localisation française, avec une graine "
        "fixée. Limites : les données sont synthétiques et les anomalies sont injectées par des règles que l'on connaît, ce qui "
        "peut favoriser les approches qui exploitent les mêmes signaux ; l'historique est supposé correct (les anomalies d'un mois "
        "ne contaminent pas le suivant) ; les salaires suivent des lois simples. Les résultats mesurent donc la capacité à "
        "retrouver des anomalies plausibles et non la performance sur de vraies données de paie, qui devra être confirmée par un "
        "pilote.")


# --- Section du rapport -------------------------------------------------------------------------------

def _section(st: dict) -> None:
    def inclure(nom_tableau: str, nom_texte: str) -> str:
        tableau = rp.lire_fichier(os.path.join(rp.DOSSIER_TABLEAUX, f"{nom_tableau}.md"))
        texte = rp.lire_fichier(os.path.join(rp.DOSSIER_TEXTES, f"{nom_texte}.md"))
        return f"{tableau}\n\n{texte}"

    def figure(nom: str) -> str:
        texte = rp.lire_fichier(os.path.join(rp.DOSSIER_TEXTES, f"{nom}.md"))
        return f"![{nom}](rapport/figures/{nom}.png)\n\n{texte}"

    methode = rp.lire_fichier(os.path.join(rp.DOSSIER_TEXTES, "methode_generation_et_limites.md"))
    rp.ecrire_section("02_jeu_de_donnees", f"""## 2. Jeu de données

### 2.1 Méthode de génération et limites

{methode}

### 2.2 Description du jeu de données

{inclure('tab_description_jeu_donnees', 'tab_description_jeu_donnees')}

### 2.3 Découpage chronologique

{inclure('tab_decoupage_chronologique', 'tab_decoupage_chronologique')}

{figure('fig_anomalies_par_mois')}

### 2.4 Sociétés simulées

{inclure('tab_societes', 'tab_societes')}

### 2.5 Salaires par profil

{figure('fig_distribution_salaires_profil')}

### 2.6 Anomalies injectées

{inclure('tab_anomalies_par_type', 'tab_anomalies_par_type')}""")
    rp.ecrire_section("00_entete", """# Évaluation du module IA de contrôle des fichiers de paie

Rapport d'évaluation généré par `ai-module/benchmark`. Reproduction : voir les commandes en fin de document.

## 1. Objectif et organisation

Le module IA contrôle les fichiers de paie avant la génération des virements SEPA : il attribue à chaque fiche un score
de suspicion et des raisons lisibles, à partir d'un modèle Isolation Forest et de règles métier. Ce document évalue ce
module de façon rigoureuse (découpage chronologique, seuil choisi sur la validation, résultats finaux sur le test,
cinq graines) en quatre étapes : 0 jeu de données, A évaluation du modèle, B comparaison des approches, C intégration
dans l'application. *Les sections correspondant aux étapes A, B et C seront ajoutées au fur et à mesure.*""")
    rp.ecrire_reproduction()


# --- Scénario de soutenance ---------------------------------------------------------------------------

def _scenario() -> dict:
    sc = ds.construire_scenario()
    os.makedirs(DOSSIER_SCENARIO, exist_ok=True)
    colonnes = {"mois": "mois", "societe_id": "societe", "salarie_id": "matricule", "nom": "nom",
                "iban": "iban", "montant": "montant"}
    for cle, fichier in (("historique", "historique_6_mois.csv"), ("fichier", "fichier_paie_mois.csv")):
        d = sc[cle][["fiche_id"] + list(colonnes)].rename(columns=colonnes)
        d.to_csv(os.path.join(DOSSIER_SCENARIO, fichier), index=False, encoding="utf-8")
    att = sc["attendues"].rename(columns={"salarie_id": "matricule", "montant_legitime": "montant_habituel"})
    att["type_libelle"] = att["type_anomalie"].map(ds.TYPES_ANOMALIES)
    att.to_csv(os.path.join(DOSSIER_SCENARIO, "anomalies_attendues.csv"), index=False, encoding="utf-8")
    benins = sc["fichier"][sc["fichier"]["evenement_normal"].isin(["prime", "iban_change_legitime"])]
    benins[["fiche_id", "salarie_id", "nom", "montant", "evenement_normal"]].rename(
        columns={"salarie_id": "matricule", "evenement_normal": "evenement"}
    ).to_csv(os.path.join(DOSSIER_SCENARIO, "cas_normaux_atypiques.csv"), index=False, encoding="utf-8")
    return {"graine": ds.SCENARIO_SEED, "societe": sc["fichier"]["societe_id"].iloc[0],
            "mois": sc["fichier"]["mois"].iloc[0], "fiches": int(len(sc["fichier"])),
            "anomalies": int(len(sc["attendues"])), "casNormauxAtypiques": int(len(benins)),
            "anomaliesParType": {t: int((sc["attendues"]["type_anomalie"] == t).sum()) for t in ds.TYPES_ANOMALIES
                                 if (sc["attendues"]["type_anomalie"] == t).any()}}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=ds.SEED_REFERENCE)
    args = parser.parse_args()

    sim = ds.Simulateur(seed=args.seed)
    df = sim.executer()
    os.makedirs(DOSSIER_DATA, exist_ok=True)
    ds.a_csv(df, os.path.join(DOSSIER_DATA, "jeu_reference.csv"))

    stats = _statistiques(df, sim.societes())
    stats["scenarioSoutenance"] = _scenario()
    rp.maj_resultats("jeuDeDonnees", stats)
    _tableaux(stats)
    _figures(df, stats)
    _textes(stats)
    _section(stats)
    print(f"Jeu de données : {stats['nbFiches']} fiches, {stats['nbAnomalies']} anomalies "
          f"({stats['tauxAnomalies']:.2%}), {stats['nbSalaries']} salariés.")
    print("Rapport assemblé :", rp.assembler_rapport())


if __name__ == "__main__":
    main()
