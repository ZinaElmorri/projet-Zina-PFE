"""Production des livrables de l'étape A (figures, tableaux, textes, section du rapport)."""
from __future__ import annotations

from typing import Dict

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.ticker import FuncFormatter

from . import dataset as ds
from . import reporting as rp

PROD, VALIDE = "en_service", "if_seul"
SEUIL_EFFECTIF = 0.5
C_PROD, C_VALIDE, C_ALEA = rp.COULEURS[0], rp.COULEURS[2], rp.COULEURS[5]
NOMS_COURTS = {"en_service": "Décision actuelle (seuil effectif 0,5)", "if_seul": "Isolation Forest, seuil validé",
               "if_regle_iban": "IF + règle IBAN, seuil validé"}
CAUSES = {"treizieme_mois": "13e mois", "prime": "Primes", "iban_change_legitime": "Changements d'IBAN légitimes",
          "autre": "Autres fiches normales"}

# Pourquoi un type est bien ou mal détecté (variables du modèle : écart à l'historique, z-score société,
# variation mensuelle, IBAN modifié, IBAN partagé dans le lot).
POURQUOI = {
    "montant_multiplie": "l'écart à l'historique du salarié et le z-score dans la société deviennent extrêmes",
    "erreur_virgule": "le montant est sans commune mesure avec l'historique, dans un sens comme dans l'autre",
    "hausse_subtile": "une hausse de 25 à 60 % recouvre celle des primes et promotions normales : le signal se confond avec le bruit légitime",
    "montant_nul_negatif": "le montant sort de la plage habituelle, mais un score d'isolement peu élevé le laisse passer au seuil fixe ; une règle « montant ≤ 0 » le traite sans IA",
    "doublon": "chaque fiche prise isolément est normale ; seul le nombre de paiements sur le même IBAN les distingue, variable peu pesée par l'Isolation Forest",
    "iban_change_montant_eleve": "l'IBAN modifié et le montant multiplié par 1,5 à 3 se cumulent ; la règle IBAN la signale à coup sûr",
    "iban_partage": "le nombre de paiements sur le même IBAN vaut 2, ce qui isole imparfaitement ces fiches",
    "salarie_fantome": "sans historique, les variables prennent des valeurs neutres et la fiche paraît banale",
    "salarie_sorti_paye": "le modèle ne voit que les montants passés, pas l'absence de paiement le mois précédent",
}


def _pct(x: float, d: int = 0) -> str:
    return rp.fr_pct(x, d)


def _pm(stat: Dict, d: int = 2, latex: bool = False) -> str:
    return rp.fr_pm(stat["moyenne"], stat["ecartType"], d, latex)


def _fmt_axe(x, _pos=None):
    return rp.fr(x, 1)


# --- Figures -----------------------------------------------------------------------------------------

def _figures(calcul: Dict, res: Dict) -> None:
    rp.style_figure()
    brut = calcul["brut"]
    cfg = res["configurations"]
    prevalence = res["protocole"]["prevalenceTest"]
    grille = np.linspace(0, 1, 201)

    # Courbe précision–rappel
    fig, ax = plt.subplots(figsize=(6.4, 4.8))
    for c in brut[PROD]["pr"]:
        ax.plot(grille, c, color=C_PROD, alpha=0.18, lw=1)
    moyenne = np.mean(brut[PROD]["pr"], axis=0)
    ax.plot(grille, moyenne, color=C_PROD, lw=2.4,
            label=f"Modèle en service (PR-AUC {rp.fr(cfg[PROD]['metriques']['prAuc']['moyenne'], 2)})")
    ax.axhline(prevalence, color=C_ALEA, ls=":", lw=1.4, label=f"Classement au hasard ({_pct(prevalence, 1)})")
    m = cfg[PROD]["metriques"]
    ax.scatter([m["rappel"]["moyenne"]], [m["precision"]["moyenne"]], marker="*", s=170, color=C_PROD, zorder=5,
               edgecolor="white", label="Seuil actuel (0,5)")
    m2 = cfg[VALIDE]["metriques"]
    ax.scatter([m2["rappel"]["moyenne"]], [m2["precision"]["moyenne"]], marker="D", s=55, color=C_VALIDE, zorder=5,
               edgecolor="white", label="Seuil choisi sur la validation")
    ax.set_xlabel("Rappel")
    ax.set_ylabel("Précision")
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1.02)
    for axe in (ax.xaxis, ax.yaxis):
        axe.set_major_formatter(FuncFormatter(_fmt_axe))
    ax.legend(frameon=False, loc="upper right")
    rp.enregistrer_figure(fig, "fig_courbe_precision_rappel")

    # Courbe ROC
    fig, ax = plt.subplots(figsize=(6.0, 4.8))
    for c in brut[PROD]["roc"]:
        ax.plot(grille, c, color=C_PROD, alpha=0.18, lw=1)
    ax.plot(grille, np.mean(brut[PROD]["roc"], axis=0), color=C_PROD, lw=2.4,
            label=f"Modèle en service (AUC {rp.fr(cfg[PROD]['metriques']['rocAuc']['moyenne'], 2)})")
    ax.plot([0, 1], [0, 1], color=C_ALEA, ls=":", lw=1.4, label="Classement au hasard")
    ax.set_xlabel("Taux de faux positifs")
    ax.set_ylabel("Taux de vrais positifs (rappel)")
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1.02)
    for axe in (ax.xaxis, ax.yaxis):
        axe.set_major_formatter(FuncFormatter(_fmt_axe))
    ax.legend(frameon=False, loc="lower right")
    rp.enregistrer_figure(fig, "fig_courbe_roc")

    # Matrices de confusion (moyenne par graine)
    fig, axes = plt.subplots(1, 2, figsize=(9.6, 4.2))
    for ax, cle in zip(axes, (PROD, "if_regle_iban")):
        mc = cfg[cle]["matriceConfusionMoyenne"]
        valeurs = np.array([[mc["vraisNegatifs"], mc["fauxPositifs"]], [mc["fauxNegatifs"], mc["vraisPositifs"]]])
        lignes = valeurs / valeurs.sum(axis=1, keepdims=True)
        ax.imshow(lignes, cmap="Blues", vmin=0, vmax=1)
        for i in range(2):
            for j in range(2):
                ax.text(j, i, f"{rp.fr(valeurs[i, j], 0)}\n({_pct(lignes[i, j], 1)})", ha="center", va="center",
                        color="white" if lignes[i, j] > 0.55 else "black", fontsize=11)
        ax.set_xticks([0, 1], ["Normale", "Anomalie"])
        ax.set_yticks([0, 1], ["Normale", "Anomalie"])
        ax.set_xlabel("Fiche signalée par le modèle")
        ax.set_ylabel("Fiche réelle")
        ax.set_xlabel(f"Prédit\n{NOMS_COURTS[cle]}")
        ax.grid(False)
    rp.enregistrer_figure(fig, "fig_matrice_confusion")

    # Distribution des scores (score brut Isolation Forest, toutes graines, jeu de test)
    scores = np.concatenate(brut[VALIDE]["score_test"])
    y = np.concatenate(brut[VALIDE]["y_test"]).astype(bool)
    fig, ax = plt.subplots(figsize=(8, 4.4))
    bornes = np.linspace(0, 1, 51)
    ax.hist(scores[~y], bins=bornes, weights=np.full((~y).sum(), 1 / (~y).sum()), color=rp.COULEURS[1], alpha=0.75,
            label="Fiches normales")
    ax.hist(scores[y], bins=bornes, weights=np.full(y.sum(), 1 / y.sum()), color=rp.COULEURS[3], alpha=0.7,
            label="Fiches anormales")
    ax.set_yscale("log")
    ax.axvline(0.5, color=C_PROD, lw=2, label="Seuil actuel (0,5)")
    ax.axvline(cfg[VALIDE]["seuil"]["moyenne"], color=C_VALIDE, lw=2, ls="--",
               label=f"Seuil choisi sur la validation ({rp.fr(cfg[VALIDE]['seuil']['moyenne'], 2)})")
    ax.set_xlabel("Score de suspicion de l'Isolation Forest (0 à 1)")
    ax.set_ylabel("Proportion de la classe (échelle log.)")
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: rp.fr(v, 1)))
    ax.legend(frameon=False, loc="upper right")
    rp.enregistrer_figure(fig, "fig_distribution_scores")

    # F1 en fonction du seuil
    from .protocole import GRILLE_SEUILS
    fig, ax = plt.subplots(figsize=(7.4, 4.6))
    fv, ft = np.array(brut[VALIDE]["f1_val"]), np.array(brut[VALIDE]["f1_test"])
    ax.plot(GRILLE_SEUILS, fv.mean(0), color=C_PROD, lw=2.4, label="Validation (mois 13 à 15)")
    ax.fill_between(GRILLE_SEUILS, fv.mean(0) - fv.std(0), fv.mean(0) + fv.std(0), color=C_PROD, alpha=0.15)
    ax.plot(GRILLE_SEUILS, ft.mean(0), color=rp.COULEURS[3], lw=2, ls="--", label="Test (mois 16 à 18, non utilisé pour choisir)")
    ax.fill_between(GRILLE_SEUILS, ft.mean(0) - ft.std(0), ft.mean(0) + ft.std(0), color=rp.COULEURS[3], alpha=0.12)
    ax.axvline(cfg[VALIDE]["seuil"]["moyenne"], color=C_VALIDE, lw=1.8, label="Seuil choisi sur la validation")
    ax.axvline(0.5, color=C_ALEA, lw=1.8, ls=":", label="Seuil actuel (0,5)")
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 0.8)
    ax.set_xlabel("Seuil de décision sur le score")
    ax.set_ylabel("F1")
    for axe in (ax.xaxis, ax.yaxis):
        axe.set_major_formatter(FuncFormatter(_fmt_axe))
    ax.legend(frameon=False, loc="upper right", fontsize=9)
    rp.enregistrer_figure(fig, "fig_f1_en_fonction_du_seuil")

    # Rappel par type
    types = list(ds.TYPES_ANOMALIES)
    ordre = sorted(types, key=lambda t: next(r for r in cfg[PROD]["rappelParType"] if r["type"] == t)["rappel"]["moyenne"])
    fig, ax = plt.subplots(figsize=(8.4, 4.8))
    hauteur = 0.38
    for k, (cle, couleur) in enumerate(((PROD, C_PROD), (VALIDE, C_VALIDE))):
        par_type = {r["type"]: r["rappel"] for r in cfg[cle]["rappelParType"]}
        pos = np.arange(len(ordre)) + (k - 0.5) * hauteur
        ax.barh(pos, [par_type[t]["moyenne"] for t in ordre], height=hauteur, color=couleur,
                xerr=[par_type[t]["ecartType"] for t in ordre], error_kw={"elinewidth": 1, "capsize": 2},
                label=NOMS_COURTS[cle])
    ax.set_yticks(np.arange(len(ordre)), [ds.TYPES_ANOMALIES[t] for t in ordre])
    ax.set_xlim(0, 1.05)
    ax.set_xlabel("Rappel (part des anomalies du type détectées)")
    ax.xaxis.set_major_formatter(FuncFormatter(_fmt_axe))
    ax.legend(frameon=False, loc="lower right")
    rp.enregistrer_figure(fig, "fig_rappel_par_type")


# --- Tableaux ----------------------------------------------------------------------------------------

def _tableaux(res: Dict) -> None:
    cfg = res["configurations"]
    entetes = ["Configuration", "Précision", "Rappel", "F1", "ROC-AUC", "PR-AUC", "Faux positifs / 1 000 fiches", "Seuil"]

    def ligne(cle, latex=False):
        m = cfg[cle]["metriques"]
        seuil = cfg[cle]["seuil"]
        return [cfg[cle]["nom"], _pm(m["precision"], 2, latex), _pm(m["rappel"], 2, latex), _pm(m["f1"], 2, latex),
                _pm(m["rocAuc"], 2, latex), _pm(m["prAuc"], 2, latex), _pm(m["fpPour1000"], 1, latex),
                rp.fr(seuil["moyenne"], 2, latex) if cle == PROD else _pm(seuil, 2, latex)]

    cles = list(cfg)
    legende = ("Performances sur le jeu de test (mois 16 à 18), moyenne ± écart-type sur 5 graines. "
               f"Référence au hasard : PR-AUC = taux d'anomalies ({rp.fr_pct(res['protocole']['prevalenceTest'], 1)}), ROC-AUC = 0,50.")
    rp.ecrire_tableau("tab_performances_test", entetes, [ligne(c) for c in cles], legende, "lrrrrrrr",
                      lignes_latex=[ligne(c, True) for c in cles], alignements_latex="p{4.6cm}rrrrrrr")

    def cellule(stat, latex=False):
        return rp.fr_pm(stat["moyenne"] * 100, stat["ecartType"] * 100, 0, latex) + ("\\,\\%" if latex else " %")

    lignes, lignes_tex = [], []
    for k, t in enumerate(ds.TYPES_ANOMALIES):
        a, b = cfg[PROD]["rappelParType"][k], cfg[VALIDE]["rappelParType"][k]
        lignes.append([ds.TYPES_ANOMALIES[t], rp.fr(a["nbTestMoyen"], 0), cellule(a["rappel"]), cellule(b["rappel"])])
        lignes_tex.append([ds.TYPES_ANOMALIES[t], rp.fr(a["nbTestMoyen"], 0, True), cellule(a["rappel"], True),
                           cellule(b["rappel"], True)])
    rp.ecrire_tableau(
        "tab_detection_par_type",
        ["Type d'anomalie", "Fiches (test)", NOMS_COURTS[PROD], NOMS_COURTS[VALIDE]], lignes,
        "Taux de détection (rappel) par type d'anomalie sur le jeu de test, moyenne ± écart-type sur 5 graines.",
        "lrrr", lignes_latex=lignes_tex, alignements_latex="p{5cm}rp{3.2cm}p{3.2cm}")

    # Sensibilité saisonnière et causes des faux positifs
    lignes, lignes_tex = [], []
    for cle in (PROD, VALIDE):
        for periode, nom in (("oct_nov", "Octobre – novembre"), ("dec", "Décembre (13e mois)")):
            p = cfg[cle]["parPeriode"][periode]
            for latex, sortie in ((False, lignes), (True, lignes_tex)):
                sortie.append([NOMS_COURTS[cle], nom, rp.fr(p["precision"]["moyenne"], 2, latex),
                               rp.fr(p["rappel"]["moyenne"], 2, latex), rp.fr(p["f1"]["moyenne"], 2, latex),
                               rp.fr(p["fp_pour_1000"]["moyenne"], 1, latex)])
    rp.ecrire_tableau("tab_sensibilite_saisonniere",
                      ["Configuration", "Période du test", "Précision", "Rappel", "F1", "Faux positifs / 1 000 fiches"],
                      lignes, "Performances avant et pendant décembre, où trois sociétés versent un 13e mois (moyenne sur 5 graines).",
                      "llrrrr", lignes_latex=lignes_tex)

    lignes, lignes_tex = [], []
    for cle in (PROD, VALIDE):
        f = cfg[cle]["fauxPositifsParCause"]
        total = sum(f.values())
        for latex, sortie in ((False, lignes), (True, lignes_tex)):
            sortie.append([NOMS_COURTS[cle], rp.fr(total, 0, latex)] + [rp.fr(f[k], 0, latex) for k in CAUSES])
    rp.ecrire_tableau("tab_causes_faux_positifs", ["Configuration", "Faux positifs"] + list(CAUSES.values()), lignes,
                      "Causes des faux positifs sur le jeu de test (nombre moyen de fiches par graine).",
                      "lrrrrr", lignes_latex=lignes_tex, alignements_latex="p{4.2cm}rrrrr")


# --- Textes ------------------------------------------------------------------------------------------

def _textes(calcul: Dict, res: Dict) -> None:
    cfg = res["configurations"]
    P, V = cfg[PROD], cfg[VALIDE]
    mp, mv = P["metriques"], V["metriques"]
    prev = res["protocole"]["prevalenceTest"]
    fr = rp.fr
    seuil_v = V["seuil"]
    brut = calcul["brut"]
    grille_rappel = np.linspace(0, 1, 201)
    pr_moy = np.mean(brut[PROD]["pr"], axis=0)
    rappel_precision_95 = float(grille_rappel[pr_moy >= 0.95].max())
    precision_palier = float(pr_moy[int(0.7 * 200)])
    from .protocole import GRILLE_SEUILS
    f1_val_moy, f1_test_moy = np.mean(brut[VALIDE]["f1_val"], axis=0), np.mean(brut[VALIDE]["f1_test"], axis=0)
    i_val = int(np.argmin(np.abs(GRILLE_SEUILS - seuil_v["moyenne"])))
    i_eff = int(np.argmin(np.abs(GRILLE_SEUILS - SEUIL_EFFECTIF)))
    f1_val_max = float(f1_val_moy.max())
    seuil_test_max, f1_test_max = float(GRILLE_SEUILS[int(f1_test_moy.argmax())]), float(f1_test_moy.max())
    f1_test_au_seuil_valide, f1_test_au_seuil_actuel = float(f1_test_moy[i_val]), float(f1_test_moy[i_eff])
    par_type = {r["type"]: r for r in P["rappelParType"]}
    par_type_v = {r["type"]: r for r in V["rappelParType"]}
    bien = [ds.TYPES_ANOMALIES[t] for t, r in par_type.items() if r["rappel"]["moyenne"] >= 0.8]
    mal = [ds.TYPES_ANOMALIES[t] for t, r in par_type.items() if r["rappel"]["moyenne"] <= 0.2]
    fpc = P["fauxPositifsParCause"]
    fpc_v = V["fauxPositifsParCause"]
    dec_p, nov_p = P["parPeriode"]["dec"], P["parPeriode"]["oct_nov"]
    dec_v, nov_v = V["parPeriode"]["dec"], V["parPeriode"]["oct_nov"]
    part13 = fpc["treizieme_mois"] / max(sum(fpc.values()), 1)
    part13_v = fpc_v["treizieme_mois"] / max(sum(fpc_v.values()), 1)

    rp.ecrire_texte(
        "protocole_evaluation", "Protocole d'évaluation du modèle de détection.",
        "Le jeu de données est découpé selon l'ordre chronologique des mois de paie : les mois 1 à 12 servent à entraîner le "
        "modèle, les mois 13 à 15 à choisir le seuil de décision, et les mois 16 à 18, jamais utilisés auparavant, à mesurer les "
        "performances finales. Ce découpage reproduit l'usage réel (contrôler un mois futur avec un modèle appris sur le passé) et "
        "interdit toute fuite d'information. Le modèle évalué est celui de l'application, reconstruit avec ses hyperparamètres "
        "(200 arbres, contamination de 1 %). Sa décision actuelle (prédiction native de l'Isolation Forest, ou score d'au moins 0,6, "
        "ou IBAN modifié) équivaut en pratique à « score d'au moins 0,5 ou IBAN modifié » : le seuil configuré à 0,6 est sans effet "
        "puisque la prédiction native signale déjà tout score supérieur à 0,5. Cette décision, fixée avant l'évaluation, est mesurée "
        "telle quelle, puis comparée à des variantes dont le seuil est choisi sur la validation (maximisation du F1) ; aucun paramètre "
        "n'est ajusté en regardant le test. Les mesures retenues sont la précision, le rappel, le F1, la ROC-AUC et la PR-AUC, ainsi que le "
        "nombre de faux positifs pour 1 000 fiches, qui traduit la charge de vérification de l'Expert RH. Comme les anomalies "
        f"représentent environ {_pct(prev, 0)} des fiches, la ROC-AUC reste flatteuse (le grand nombre de fiches normales gonfle le "
        "taux de vrais négatifs) : la PR-AUC, dont la valeur de référence est le taux d'anomalies, est la mesure la plus pertinente. "
        "Chaque expérience est répétée sur cinq graines (jeu de données et modèle) et rapportée en moyenne ± écart-type.")

    rp.ecrire_texte(
        "tab_performances_test",
        "Performances du modèle sur le jeu de test (mois 16 à 18), moyenne ± écart-type sur 5 graines.",
        f"Avec sa décision actuelle (en pratique un seuil de 0,5 sur le score), le modèle en service atteint une précision de {_pm(mp['precision'])}, "
        f"un rappel de {_pm(mp['rappel'])} et un F1 de {_pm(mp['f1'])}, soit environ {fr(mp['fpPour1000']['moyenne'], 0)} faux "
        f"positifs pour 1 000 fiches. Sa PR-AUC de {_pm(mp['prAuc'])} est environ {fr(mp['prAuc']['moyenne'] / prev, 0)} fois supérieure à celle d'un classement au hasard "
        f"({_pct(prev, 1)}), ce qui prouve que le score contient une vraie information, mais elle reste loin de 1 : le modèle "
        f"détecte environ {_pct(mp['rappel']['moyenne'], 0)} des anomalies, pas toutes. Recalibrer le seuil sur la validation donne "
        f"un seuil de {_pm(seuil_v)} et un rappel plus élevé ({_pm(mv['rappel'])}), mais au prix d'une précision de {_pm(mv['precision'])} "
        f"et de {fr(mv['fpPour1000']['moyenne'], 0)} faux positifs pour 1 000 fiches : le seuil appris sur des mois sans 13e mois ne se "
        "transfère pas au mois de décembre. Ajouter la règle IBAN dégrade la PR-AUC "
        f"({_pm(cfg['if_regle_iban']['metriques']['prAuc'])} contre {_pm(mv['prAuc'])} sans elle) car elle signale tous les "
        "changements d'IBAN légitimes, alors que le modèle repère déjà seul les changements d'IBAN accompagnés d'un montant élevé.")

    rp.ecrire_texte(
        "tab_detection_par_type",
        "Taux de détection par type d'anomalie sur le jeu de test (rappel, moyenne ± écart-type sur 5 graines).",
        f"Le modèle détecte bien les anomalies qui déforment fortement le montant : {', '.join(bien) if bien else 'aucun type'} "
        f"(rappel d'au moins {_pct(0.8)} au seuil actuel), parce que l'écart à l'historique et le z-score dans la société deviennent "
        f"extrêmes. Il détecte très mal {', '.join(mal)} (rappel d'au plus {_pct(0.2)}). "
        "Pour la hausse subtile, la cause est de fond : une hausse de 25 à 60 % recouvre celle des primes et promotions normales, "
        "aucun modèle fondé sur le seul montant ne peut la distinguer sans faux positifs. Pour le salarié fantôme et le salarié "
        "sorti encore payé, l'information décisive (l'absence d'historique, l'absence de paiement le mois précédent) n'est pas "
        "dans les variables actuelles ; elle peut être ajoutée (la colonne correspondante est déjà fournie par le jeu de données). Le doublon demande "
        "une règle explicite sur l'identité des fiches, plus fiable que l'isolement statistique. Abaisser le seuil augmente "
        f"le rappel de plusieurs types (par exemple {ds.TYPES_ANOMALIES['montant_nul_negatif'].lower()} : "
        f"{_pct(par_type['montant_nul_negatif']['rappel']['moyenne'])} puis {_pct(par_type_v['montant_nul_negatif']['rappel']['moyenne'])}) "
        "mais au prix de nombreux faux positifs.")

    rp.ecrire_texte(
        "tab_sensibilite_saisonniere",
        "Performances avant et pendant décembre, mois du 13e mois de trois sociétés (moyenne sur 5 graines).",
        f"Les résultats se dégradent nettement en décembre : au seuil actuel, la précision passe de {fr(nov_p['precision']['moyenne'], 2)} "
        f"(octobre et novembre) à {fr(dec_p['precision']['moyenne'], 2)} et les faux positifs pour 1 000 fiches de "
        f"{fr(nov_p['fp_pour_1000']['moyenne'], 1)} à {fr(dec_p['fp_pour_1000']['moyenne'], 1)}. Pour le seuil choisi sur la validation, "
        f"l'écart est bien plus grand : {fr(nov_v['fp_pour_1000']['moyenne'], 0)} contre {fr(dec_v['fp_pour_1000']['moyenne'], 0)} faux positifs "
        "pour 1 000 fiches. Le 13e mois double le montant de la fiche de trois sociétés : pour le modèle, qui ne connaît pas le "
        "calendrier, c'est un montant deux fois supérieur à l'historique. Le seuil choisi sur la validation (juillet à septembre) ne "
        "pouvait pas anticiper ce phénomène. Il s'agit d'une limite réelle du modèle, qui justifie d'ajouter une variable de "
        "calendrier ou un indicateur de paiement exceptionnel déclaré.")

    rp.ecrire_texte(
        "tab_causes_faux_positifs", "Causes des faux positifs sur le jeu de test (nombre moyen de fiches par graine).",
        f"Au seuil actuel, {_pct(part13)} des faux positifs sont des 13e mois, "
        f"{fr(fpc['iban_change_legitime'], 0)} des changements d'IBAN légitimes (signalés d'office par la règle IBAN) et "
        f"{fr(fpc['autre'], 0)} d'autres fiches normales. Pour le seuil choisi sur la validation, les 13e mois représentent "
        f"{_pct(part13_v)} des faux positifs. Les primes ponctuelles, pourtant conçues comme des cas difficiles, génèrent très peu "
        "de fausses alertes : leur amplitude (jusqu'à +35 %) reste dans la tolérance du modèle. Presque toutes les fausses alertes "
        "proviennent donc de situations légitimes mais prévisibles que le modèle ignore, ce qui indique les améliorations les plus rentables.")

    rp.ecrire_texte(
        "fig_courbe_precision_rappel",
        "Courbe précision–rappel du modèle en service sur le jeu de test (trait fin : chaque graine ; trait épais : moyenne).",
        f"La courbe précision–rappel décrit le compromis offert par le score, indépendamment du seuil. Elle reste nettement au-dessus "
        f"du niveau du hasard ({_pct(prev, 1)}), avec une PR-AUC de {_pm(mp['prAuc'])}. Les anomalies les mieux classées (jusqu'à un "
        f"rappel d'environ {fr(rappel_precision_95, 2)}) sont retrouvées avec une précision supérieure à 0,95, puis la précision décroît à "
        f"mesure que l'on accepte de signaler davantage de fiches, jusqu'à un long palier d'environ {fr(precision_palier, 2)} pour les rappels élevés, "
        "dû au bloc des 13e mois classés parmi les fiches suspectes. "
        f"Le seuil actuel (étoile) offre une précision de {fr(mp['precision']['moyenne'], 2)} pour un rappel de {fr(mp['rappel']['moyenne'], 2)} ; "
        f"le seuil recalibré sur la validation (losange) gagne en rappel ({fr(mv['rappel']['moyenne'], 2)}) en perdant beaucoup de précision "
        f"({fr(mv['precision']['moyenne'], 2)}). La dispersion entre graines est modérée, signe que le résultat ne dépend pas d'un tirage favorable.")

    rp.ecrire_texte(
        "fig_courbe_roc", "Courbe ROC du modèle en service sur le jeu de test (trait fin : chaque graine ; trait épais : moyenne).",
        f"La courbe ROC s'écarte nettement de la diagonale, avec une aire de {_pm(mp['rocAuc'])}. Autrement dit, une fiche anormale "
        f"tirée au hasard reçoit un score plus élevé qu'une fiche normale dans environ {_pct(mp['rocAuc']['moyenne'], 0)} des cas. "
        f"Cette mesure doit toutefois être lue avec prudence : comme les fiches normales sont environ {fr((1 - prev) / prev, 0)} fois plus nombreuses, un taux de "
        "faux positifs de quelques pourcents correspond déjà à beaucoup de fausses alertes en valeur absolue. C'est pourquoi la "
        "courbe précision–rappel, plus sensible au déséquilibre, est la référence de ce rapport. La ROC confirme néanmoins que le "
        "score ordonne correctement les fiches.")

    rp.ecrire_texte(
        "fig_matrice_confusion", "Matrices de confusion moyennes sur le jeu de test (effectifs par graine et part de la ligne réelle).",
        f"À gauche, la décision actuelle signale en moyenne {fr(P['matriceConfusionMoyenne']['vraisPositifs'], 0)} anomalies vraies sur "
        f"{fr(P['matriceConfusionMoyenne']['vraisPositifs'] + P['matriceConfusionMoyenne']['fauxNegatifs'], 0)}, et laisse passer "
        f"{fr(P['matriceConfusionMoyenne']['fauxNegatifs'], 0)} anomalies (faux négatifs). Les faux positifs sont peu nombreux en proportion "
        f"({fr(P['matriceConfusionMoyenne']['fauxPositifs'], 0)} fiches sur plus de 12 000), ce qui ménage l'Expert RH. À droite, le "
        f"seuil recalibré avec la règle IBAN détecte davantage d'anomalies mais produit {fr(cfg['if_regle_iban']['matriceConfusionMoyenne']['fauxPositifs'], 0)} "
        "faux positifs en moyenne, soit une charge de vérification nettement supérieure. Le choix du seuil traduit donc un arbitrage "
        "entre anomalies manquées et temps de vérification, à fixer avec les utilisateurs.")

    rp.ecrire_texte(
        "fig_distribution_scores", "Distribution des scores de l'Isolation Forest sur le jeu de test, selon la classe réelle.",
        "La grande majorité des fiches normales a un score faible, avec toutefois un second regroupement autour de 0,4, qui correspond "
        "aux 13e mois (ces fiches représentent l'essentiel des faux positifs au seuil recalibré). Les anomalies s'étalent sur toute la "
        "plage, avec une queue marquée vers les scores élevés. Les deux distributions se recouvrent largement entre 0,2 et 0,6 : c'est la "
        "zone où se jouent les faux positifs et les faux négatifs. Au seuil actuel (0,5), peu de fiches normales sont signalées, mais "
        "une bonne part des anomalies, dont le score reste bas, passe inaperçue. Le seuil choisi sur la validation est plus bas et "
        "capte plus d'anomalies, mais il se place avant le regroupement des 13e mois et en signale donc presque toutes les fiches. L'échelle "
        "logarithmique rend visibles ces queues, invisibles sur une échelle linéaire.")

    rp.ecrire_texte(
        "fig_f1_en_fonction_du_seuil", "F1 en fonction du seuil de décision, sur la validation (trait plein) et sur le test (tirets).",
        f"Sur la validation, le F1 atteint son maximum ({fr(f1_val_max, 2)}) pour un seuil d'environ {fr(seuil_v['moyenne'], 2)}, valeur retenue par le protocole. "
        f"La courbe de test, tracée à titre de contrôle et jamais utilisée pour choisir, culmine à un seuil plus élevé (environ {fr(seuil_test_max, 2)}, F1 de "
        f"{fr(f1_test_max, 2)}) et ne donne que {fr(f1_test_au_seuil_valide, 2)} au seuil choisi sur la validation : la validation (juillet à septembre) "
        "ne contient pas de 13e mois, alors que le test (octobre à décembre) en contient un. "
        "Le décalage entre les deux courbes est donc d'origine saisonnière et non un défaut de "
        "protocole. Il montre qu'un seuil unique appris sur peu de mois n'est pas robuste, et qu'il vaut mieux corriger la cause (variable "
        f"de calendrier) que recalibrer en permanence. Le seuil actuel de 0,5 donne un F1 de {fr(f1_test_au_seuil_actuel, 2)} sur le test, proche de ce maximum, "
        "sans l'avoir été choisi sur ces données : ce bon résultat tient à un seuil prudent, pas à un réglage fin.")

    rp.ecrire_texte(
        "fig_rappel_par_type", "Rappel par type d'anomalie sur le jeu de test (barres d'erreur : écart-type sur 5 graines).",
        "Les types sont triés du moins bien au mieux détecté au seuil actuel. Le contraste est net : les erreurs qui défigurent le "
        "montant (multiplication, IBAN modifié avec montant élevé) sont presque toutes repérées, alors que le salarié fantôme, le "
        "salarié sorti encore payé, la hausse subtile et le doublon passent presque toujours inaperçus. Un seuil plus bas améliore le rappel "
        "de la plupart des types « montant » mais ne change rien aux deux types qui échappent à l'analyse des montants. Cela "
        "oriente les améliorations : des règles de cohérence pour les doublons et les montants nuls, de nouvelles variables pour "
        "les salariés fantômes et sortis, et un traitement spécifique de la saisonnalité.")


# --- Section du rapport ------------------------------------------------------------------------------

def _section() -> None:
    def tableau(nom):
        return (rp.lire_fichier(f"{rp.DOSSIER_TABLEAUX}/{nom}.md") + "\n\n" + rp.lire_fichier(f"{rp.DOSSIER_TEXTES}/{nom}.md"))

    def figure(nom):
        return f"![{nom}](rapport/figures/{nom}.png)\n\n" + rp.lire_fichier(f"{rp.DOSSIER_TEXTES}/{nom}.md")

    protocole = rp.lire_fichier(f"{rp.DOSSIER_TEXTES}/protocole_evaluation.md")
    rp.ecrire_section("03_evaluation_modele", f"""## 3. Évaluation du modèle (étape A)

### 3.1 Protocole d'évaluation

{protocole}

### 3.2 Performances sur le jeu de test

{tableau('tab_performances_test')}

{figure('fig_courbe_precision_rappel')}

{figure('fig_courbe_roc')}

{figure('fig_matrice_confusion')}

### 3.3 Choix du seuil et distribution des scores

{figure('fig_distribution_scores')}

{figure('fig_f1_en_fonction_du_seuil')}

### 3.4 Détection par type d'anomalie

{tableau('tab_detection_par_type')}

{figure('fig_rappel_par_type')}

### 3.5 Sensibilité à la saisonnalité et faux positifs

{tableau('tab_sensibilite_saisonniere')}

{tableau('tab_causes_faux_positifs')}""")


def produire(calcul: Dict, res: Dict) -> None:
    _figures(calcul, res)
    _tableaux(res)
    _textes(calcul, res)
    _section()
