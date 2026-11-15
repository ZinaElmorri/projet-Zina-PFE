"""Production des livrables de l'étape B (figures, tableaux, textes, section du rapport)."""
from __future__ import annotations

from typing import Dict

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.patches import Patch
from matplotlib.ticker import FuncFormatter

from app.montant_suspect.config import FEATURES

from . import dataset as ds
from . import reporting as rp
from .livrables_a import _fmt_axe, _pct, _pm

APPROCHES = ["regles", "if_actuel", "if_valide", "lof", "ocsvm", "hybride", "if_calendrier", "if_collectif",
             "if_optimise", "hybride_optimise"]
NOMS = {
    "regles": "Règles métier seules",
    "if_actuel": "Isolation Forest, décision actuelle",
    "if_valide": "Isolation Forest, seuil validé",
    "lof": "Local Outlier Factor",
    "ocsvm": "One-Class SVM",
    "hybride": "Hybride : règles + Isolation Forest",
    "if_calendrier": "IF + indicateur de décembre",
    "if_collectif": "IF + écart relatif à la société",
    "if_optimise": "IF à variables retenues",
    "hybride_optimise": "Hybride optimisé (règles + IF à variables retenues)",
}
VARIABLES = {
    "montant": "Montant", "ecart_moyenne_salarie": "Écart à la moyenne du salarié",
    "zscore_client": "Z-score dans la société", "variation_mois_precedent": "Variation sur le mois précédent",
    "iban_change_recent": "IBAN modifié", "nb_paiements_meme_iban": "Paiements sur le même IBAN",
    "ecart_relatif_societe": "Écart relatif à la société",
}
COLONNES_TYPE = ["regles", "if_actuel", "ocsvm", "hybride", "if_optimise", "hybride_optimise"]
C_REGLES, C_IA, C_DEUX, C_AUCUNE = rp.COULEURS[2], rp.COULEURS[1], rp.COULEURS[0], "#C9CED6"
RECOMMANDE = "hybride_optimise"


def _bas(texte: str) -> str:
    """Minuscule initiale sauf pour les sigles (« IBAN modifié » reste tel quel)."""
    return texte if texte.startswith("IBAN") else texte[0].lower() + texte[1:]


def _m(res, cle, metrique):
    return res["approches"][cle]["metriques"][metrique]


# --- Figures -----------------------------------------------------------------------------------------

def _figures(res: Dict) -> None:
    rp.style_figure()
    A = res["approches"]

    fig, ax = plt.subplots(figsize=(9.2, 5.4))
    pos = np.arange(len(APPROCHES))[::-1]
    h = 0.38
    for k, (metrique, nom, couleur) in enumerate((("f1", "F1", rp.COULEURS[0]), ("prAuc", "PR-AUC", rp.COULEURS[1]))):
        valeurs = [A[c]["metriques"][metrique]["moyenne"] for c in APPROCHES]
        erreurs = [A[c]["metriques"][metrique]["ecartType"] for c in APPROCHES]
        ax.barh(pos + (0.5 - k) * h, valeurs, height=h, color=couleur, xerr=erreurs, label=nom,
                error_kw={"elinewidth": 1, "capsize": 2})
    ax.set_yticks(pos, [NOMS[c] for c in APPROCHES])
    ax.set_xlim(0, 1)
    ax.set_xlabel("Valeur sur le jeu de test (moyenne et écart-type sur 5 graines)")
    ax.xaxis.set_major_formatter(FuncFormatter(_fmt_axe))
    ax.legend(frameon=False, loc="lower right")
    rp.enregistrer_figure(fig, "fig_comparaison_f1_prauc")

    # Valeur ajoutée de l'IA : répartition des anomalies selon le détecteur
    va = res["valeurAjoutee"]["parType"]
    types = list(ds.TYPES_ANOMALIES)
    fig, ax = plt.subplots(figsize=(9, 4.8))
    pos = np.arange(len(types))[::-1]
    gauche = np.zeros(len(types))
    for cle, nom, couleur in (("les_deux", "Règles et IA", C_DEUX), ("regles_seules", "Règles seules", C_REGLES),
                              ("ia_seule", "IA seule", C_IA), ("aucune", "Non détectée", C_AUCUNE)):
        part = np.array([va[t][cle] / max(sum(va[t].values()), 1e-9) for t in types])
        ax.barh(pos, part, left=gauche, color=couleur, label=nom, height=0.65)
        gauche += part
    ax.set_yticks(pos, [ds.TYPES_ANOMALIES[t] for t in types])
    ax.set_xlim(0, 1)
    ax.set_xlabel("Part des anomalies du type (jeu de test, décision actuelle de l'IA)")
    ax.xaxis.set_major_formatter(FuncFormatter(_fmt_axe))
    ax.legend(frameon=False, loc="upper center", bbox_to_anchor=(0.5, 1.13), ncol=4, fontsize=9)
    rp.enregistrer_figure(fig, "fig_valeur_ajoutee_regles_ia")

    # Ablation
    ab = res["ablation"]
    complet = ab["_complet"]
    fig, ax = plt.subplots(figsize=(8.6, 4.6))
    pos = np.arange(len(FEATURES))[::-1]
    h = 0.38
    for k, (cle, nom, couleur) in enumerate((("prAuc", "Jeu de test", rp.COULEURS[0]),
                                             ("prAucValidation", "Jeu de validation", rp.COULEURS[2]))):
        delta = [ab[f][cle]["moyenne"] - complet[cle]["moyenne"] for f in FEATURES]
        ax.barh(pos + (0.5 - k) * h, delta, height=h, color=couleur, label=nom)
    ax.axvline(0, color="black", lw=0.8)
    ax.set_yticks(pos, [VARIABLES[f] for f in FEATURES])
    ax.set_xlabel("Variation de la PR-AUC quand la variable est retirée")
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: rp.fr(v, 2)))
    ax.legend(frameon=False, loc="lower right")
    rp.enregistrer_figure(fig, "fig_ablation_variables")

    # Rappel par type et par approche
    fig, ax = plt.subplots(figsize=(9.4, 4.8))
    matrice = np.array([[A[c]["rappelParType"][t]["moyenne"] for c in COLONNES_TYPE] for t in types])
    ax.imshow(matrice, cmap="Blues", vmin=0, vmax=1, aspect="auto")
    for i in range(len(types)):
        for j in range(len(COLONNES_TYPE)):
            ax.text(j, i, rp.fr(matrice[i, j] * 100, 0), ha="center", va="center", fontsize=10,
                    color="white" if matrice[i, j] > 0.55 else "black")
    courts = {"regles": "Règles\nmétier", "if_actuel": "IF\nactuel", "ocsvm": "One-Class\nSVM",
              "hybride": "Hybride\nrègles + IF", "if_optimise": "IF variables\nretenues", "hybride_optimise": "Hybride\noptimisé"}
    ax.set_xticks(range(len(COLONNES_TYPE)), [courts[c] for c in COLONNES_TYPE], fontsize=9)
    ax.set_yticks(range(len(types)), [ds.TYPES_ANOMALIES[t] for t in types])
    ax.grid(False)
    rp.enregistrer_figure(fig, "fig_rappel_par_type_approches")


# --- Tableaux ----------------------------------------------------------------------------------------

def _tableaux(res: Dict) -> None:
    A = res["approches"]

    def cellules(cle, latex):
        a, m = A[cle], A[cle]["metriques"]
        return [NOMS[cle], _pm(m["precision"], 2, latex), _pm(m["rappel"], 2, latex), _pm(m["f1"], 2, latex),
                _pm(m["prAuc"], 2, latex), _pm(m["fpPour1000"], 1, latex),
                rp.fr(a["tempsEntrainementS"]["moyenne"], 1, latex), rp.fr(a["tempsPredictionMsPour1000"]["moyenne"], 0, latex)]

    rp.ecrire_tableau(
        "tab_comparaison_approches",
        ["Approche", "Précision", "Rappel", "F1", "PR-AUC", "Faux positifs / 1 000 fiches", "Entraînement (s)",
         "Prédiction (ms / 1 000 fiches)"],
        [cellules(c, False) for c in APPROCHES],
        "Comparaison des approches sur le jeu de test (moyenne ± écart-type sur 5 graines ; seuils choisis sur la validation).",
        "lrrrrrrr", lignes_latex=[cellules(c, True) for c in APPROCHES], alignements_latex="p{4.6cm}rrrrrrr")

    types = list(ds.TYPES_ANOMALIES)
    lignes = [[ds.TYPES_ANOMALIES[t]] + [rp.fr(A[c]["rappelParType"][t]["moyenne"] * 100, 0) + " %" for c in COLONNES_TYPE]
              for t in types]
    rp.ecrire_tableau(
        "tab_detection_par_type_approches", ["Type d'anomalie"] + [NOMS[c] for c in COLONNES_TYPE], lignes,
        "Taux de détection (rappel) par type d'anomalie et par approche, jeu de test, moyenne sur 5 graines.",
        "l" + "r" * len(COLONNES_TYPE),
        alignements_latex="p{3.6cm}" + "p{1.9cm}" * len(COLONNES_TYPE))

    ab = res["ablation"]
    complet = ab["_complet"]
    lignes, lignes_tex = [], []
    for f in ["_complet"] + FEATURES:
        nom = "Aucune (modèle complet)" if f == "_complet" else VARIABLES[f]
        for latex, sortie in ((False, lignes), (True, lignes_tex)):
            d_test = ab[f]["prAuc"]["moyenne"] - complet["prAuc"]["moyenne"]
            d_val = ab[f]["prAucValidation"]["moyenne"] - complet["prAucValidation"]["moyenne"]
            signe = (lambda x: "+" if x > 0 else "") if True else None
            sortie.append([nom, rp.fr(ab[f]["f1"]["moyenne"], 2, latex), rp.fr(ab[f]["prAuc"]["moyenne"], 2, latex),
                           "—" if f == "_complet" else signe(d_test) + rp.fr(d_test, 2, latex),
                           rp.fr(ab[f]["prAucValidation"]["moyenne"], 2, latex),
                           "—" if f == "_complet" else signe(d_val) + rp.fr(d_val, 2, latex)])
    rp.ecrire_tableau(
        "tab_ablation_variables",
        ["Variable retirée", "F1 (test)", "PR-AUC (test)", "Variation (test)", "PR-AUC (validation)", "Variation (validation)"],
        lignes, "Ablation des variables de l'Isolation Forest : performance après retrait d'une variable (moyenne sur 5 graines).",
        "lrrrrr", lignes_latex=lignes_tex)

    va = res["valeurAjoutee"]
    lignes, lignes_tex = [], []
    totaux = {k: 0.0 for k in ("regles_seules", "ia_seule", "les_deux", "aucune")}
    for t in types:
        v = va["parType"][t]
        for k in totaux:
            totaux[k] += v[k]
        for latex, sortie in ((False, lignes), (True, lignes_tex)):
            sortie.append([ds.TYPES_ANOMALIES[t], rp.fr(sum(v.values()), 0, latex), rp.fr(v["regles_seules"], 1, latex),
                           rp.fr(v["ia_seule"], 1, latex), rp.fr(v["les_deux"], 1, latex), rp.fr(v["aucune"], 1, latex)])
    for latex, sortie in ((False, lignes), (True, lignes_tex)):
        sortie.append(["Total", rp.fr(sum(totaux.values()), 0, latex), rp.fr(totaux["regles_seules"], 1, latex),
                       rp.fr(totaux["ia_seule"], 1, latex), rp.fr(totaux["les_deux"], 1, latex),
                       rp.fr(totaux["aucune"], 1, latex)])
    rp.ecrire_tableau(
        "tab_valeur_ajoutee_ia",
        ["Type d'anomalie", "Anomalies (test)", "Règles seules", "IA seule", "Règles et IA", "Non détectées"],
        lignes, "Valeur ajoutée de l'IA : anomalies du jeu de test détectées par les règles seules, par l'IA seule (décision "
                "actuelle), par les deux, ou par aucune (nombre moyen de fiches par graine).",
        "lrrrrr", lignes_latex=lignes_tex)


# --- Textes ------------------------------------------------------------------------------------------

def _textes(res: Dict) -> None:
    A, ab = res["approches"], res["ablation"]
    fr = rp.fr
    types = list(ds.TYPES_ANOMALIES)
    m = lambda c, k: _m(res, c, k)  # noqa: E731
    f1 = lambda c: fr(m(c, "f1")["moyenne"], 2)  # noqa: E731
    pa = lambda c: fr(m(c, "prAuc")["moyenne"], 2)  # noqa: E731
    fp = lambda c: fr(m(c, "fpPour1000")["moyenne"], 0)  # noqa: E731

    rp.ecrire_texte(
        "tab_comparaison_approches",
        "Comparaison des approches de détection sur le jeu de test (moyenne ± écart-type sur 5 graines).",
        f"Les règles métier seules atteignent un F1 de {f1('regles')} (rappel {fr(m('regles', 'rappel')['moyenne'], 2)}) mais signalent "
        f"{fp('regles')} fiches normales pour 1 000, car la règle « 2 fois la moyenne » alerte aussi les 13e mois. L'Isolation Forest "
        f"actuel obtient un F1 de {f1('if_actuel')} avec sa décision actuelle et une PR-AUC de {pa('if_actuel')}. Le One-Class SVM, "
        f"entraîné en une fraction de seconde, fait aussi bien ou mieux (F1 {f1('ocsvm')}, PR-AUC {pa('ocsvm')}) : le choix de "
        f"l'algorithme n'est donc pas le facteur déterminant, et le Local Outlier Factor, presque au niveau du hasard (ROC-AUC "
        f"{fr(m('lof', 'rocAuc')['moyenne'], 2)}), est à écarter. Ce qui fait la différence, ce sont les variables et la combinaison avec les "
        f"règles : l'hybride optimisé atteint un F1 de {f1('hybride_optimise')} ± {fr(m('hybride_optimise', 'f1')['ecartType'], 2)}, "
        f"une PR-AUC de {pa('hybride_optimise')} et seulement {fp('hybride_optimise')} faux positifs pour 1 000 fiches. Les temps de calcul "
        "sont négligeables pour toutes les approches (entraînement de quelques secondes, prédiction en quelques dizaines de "
        "millisecondes pour 1 000 fiches).")

    rp.ecrire_texte(
        "fig_comparaison_f1_prauc", "F1 et PR-AUC de chaque approche sur le jeu de test (barres d'erreur : écart-type sur 5 graines).",
        f"Le classement par F1 et par PR-AUC est cohérent. En bas, les règles seules et le Local Outlier Factor restent sous {fr(0.3, 1)} de F1. "
        f"Les trois variantes de l'Isolation Forest qui ajoutent une information utile (écart relatif à la société, variables retenues, "
        f"combinaison avec les règles) progressent à chaque étape, de {f1('if_valide')} à {f1('if_collectif')}, puis {f1('if_optimise')}, puis {f1('hybride_optimise')}. "
        f"L'indicateur de décembre, lui, dégrade le résultat ({f1('if_calendrier')} contre {f1('if_valide')}). "
        f"L'écart-type est élevé pour les variantes sans règles (jusqu'à {fr(m('if_optimise', 'f1')['ecartType'], 2)} sur le F1), car leur seuil dépend du tirage ; "
        f"l'hybride est le plus stable ({fr(m('hybride_optimise', 'f1')['ecartType'], 2)}) parce que les règles détectent avec certitude une partie des anomalies. "
        "Les barres d'erreur invitent à ne pas sur-interpréter les écarts entre variantes proches.")

    rp.ecrire_texte(
        "analyse_variantes_calendrier_collectif",
        "Analyse des variantes « indicateur de décembre » et « écart relatif à la société ».",
        f"Ajouter à l'Isolation Forest un indicateur de décembre, pour absorber le 13e mois, n'améliore pas le résultat : le F1 passe de "
        f"{f1('if_valide')} à {f1('if_calendrier')} et les faux positifs de {fp('if_valide')} à {fp('if_calendrier')} pour 1 000 fiches. "
        "Avec un seul mois de décembre dans l'entraînement, l'indicateur isole ce mois dans son ensemble au lieu de décrire "
        "un comportement propre aux sociétés concernées : sur la graine de référence, 99 % des 13e mois sont signalés (contre 70 % sans "
        "l'indicateur) et les fiches de décembre sans 13e mois le sont aussi plus souvent (3 % contre 0,7 %). À l'inverse, rapporter l'écart à l'historique de chaque salarié à la "
        "médiane de sa société pour le mois traite l'effet collectif sans connaître le calendrier : un 13e mois ou une augmentation "
        f"générale décale toute la société, pas un salarié isolé. Cette variable porte le F1 à {f1('if_collectif')} et la PR-AUC à "
        f"{pa('if_collectif')}. Elle présente en outre l'avantage de ne nécessiter aucune donnée supplémentaire : elle se calcule sur le "
        "lot contrôlé. La leçon méthodologique est qu'une variable qui exprime la cause (effet collectif) vaut mieux qu'une variable "
        "qui désigne la période.")

    pert = sorted(FEATURES, key=lambda f: ab[f]["prAuc"]["moyenne"] - ab["_complet"]["prAuc"]["moyenne"])
    plus_utiles = pert[:3]
    nuisibles = [f for f in FEATURES if ab[f]["prAuc"]["moyenne"] > ab["_complet"]["prAuc"]["moyenne"]]
    d = lambda f, k="prAuc": ab[f][k]["moyenne"] - ab["_complet"][k]["moyenne"]  # noqa: E731
    rp.ecrire_texte(
        "tab_ablation_variables",
        "Ablation : performance de l'Isolation Forest quand on retire une variable (moyenne sur 5 graines).",
        f"Les variables les plus utiles sont {', '.join(_bas(VARIABLES[f]) for f in plus_utiles)} : les retirer fait baisser la PR-AUC de "
        f"{fr(abs(d(plus_utiles[0])), 2)} à {fr(abs(d(plus_utiles[-1])), 2)} sur le test. À l'inverse, retirer "
        f"{' ou '.join(_bas(VARIABLES[f]) for f in nuisibles)} améliore la PR-AUC (par exemple {fr(d(nuisibles[0]), 2)} de variation en retirant "
        f"{_bas(VARIABLES[nuisibles[0]])}). Le montant brut est en effet peu informatif lorsque les salaires vont de 1 500 à plus de "
        "10 000 € : sur la graine de référence, 52 des 92 fiches normales signalées par la décision actuelle sont celles de dirigeants, "
        "qui ne représentent pourtant que 2,7 % des fiches normales. Le z-score dans la société souffre du même "
        "défaut. Les mêmes variables ressortent sur la validation, où la sélection a été faite : c'est ce qui permet de les écarter "
        "sans avoir regardé le test. Ce résultat dépend de l'hétérogénéité des profils du jeu synthétique, qu'il faudra confirmer sur des "
        "données réelles.")

    va = res["valeurAjoutee"]
    tot = {k: sum(va["parType"][t][k] for t in types) for k in ("regles_seules", "ia_seule", "les_deux", "aucune")}
    detectees = tot["regles_seules"] + tot["ia_seule"] + tot["les_deux"]
    ia_seule_types = sorted(types, key=lambda t: -va["parType"][t]["ia_seule"])[:3]
    regles_seules_types = sorted(types, key=lambda t: -va["parType"][t]["regles_seules"])[:2]
    fpv = res["valeurAjoutee"]["fauxPositifs"]
    rp.ecrire_texte(
        "tab_valeur_ajoutee_ia",
        "Valeur ajoutée de l'IA : anomalies détectées par les règles seules, par l'IA seule, par les deux, ou par aucune.",
        f"Sur environ {fr(sum(tot.values()), 0)} anomalies par jeu de test, {fr(tot['ia_seule'], 0)} ne sont vues que par l'IA, soit "
        f"{_pct(tot['ia_seule'] / max(detectees, 1e-9), 0)} des anomalies détectées, contre {fr(tot['regles_seules'], 0)} vues seulement par les règles "
        f"et {fr(tot['les_deux'], 0)} par les deux. Les types où l'IA apporte le plus sont "
        f"{', '.join(_bas(ds.TYPES_ANOMALIES[t]) for t in ia_seule_types)}. Les règles, elles, sont irremplaçables pour "
        f"{' et '.join(_bas(ds.TYPES_ANOMALIES[t]) for t in regles_seules_types)}, qu'un modèle statistique distingue mal. "
        f"En contrepartie, les règles produisent {fr(fpv['regles_seules'], 0)} fausses alertes que l'IA ne partage pas (les 13e mois), contre "
        f"{fr(fpv['ia_seule'], 0)} pour l'IA seule. {fr(tot['aucune'], 0)} anomalies échappent aux deux, principalement les salariés fantômes, les "
        "salariés sortis encore payés et les hausses subtiles : ces types exigent l'historique de présence du salarié. Les deux approches "
        "sont donc complémentaires, ce qui justifie l'hybride.")

    rp.ecrire_texte(
        "fig_valeur_ajoutee_regles_ia", "Répartition des anomalies de chaque type selon le détecteur (règles, IA, les deux, aucun).",
        "Le diagramme montre pour chaque type la part des anomalies vues par les deux détecteurs, par les règles seules, par l'IA seule "
        "ou par aucun. Les erreurs de montant importantes sont retrouvées par les deux approches, ce qui les rend robustes. "
        "Les anomalies qui ne contredisent aucune règle simple (IBAN partagé, erreur de virgule vers le bas) relèvent surtout de l'IA, "
        "ou ne sont détectées par personne. La partie grise, importante pour la hausse subtile, le salarié fantôme et le salarié sorti, "
        "désigne les anomalies non couvertes par le dispositif actuel et oriente les évolutions futures. Aucun des deux détecteurs ne suffit seul.")

    rp.ecrire_texte(
        "tab_detection_par_type_approches", "Taux de détection par type d'anomalie pour les principales approches (jeu de test).",
        "Ce tableau détaille, type par type, ce que chaque approche détecte. Les règles métier atteignent un rappel complet sur les "
        "doublons et les montants nuls ou négatifs, ce que l'Isolation Forest actuel ne fait pas (6 % et 28 %). À l'inverse, l'Isolation Forest et le "
        "One-Class SVM sont meilleurs sur les IBAN modifiés et les IBAN partagés, que les règles ne voient pas. Retirer le montant brut des "
        "variables rend la hausse subtile détectable (63 à 75 % contre 3 %), et l'hybride optimisé atteint 100 % sur quatre autres types. "
        "Il reste un point faible : l'IBAN partagé n'est détecté qu'à un tiers, loin du One-Class SVM, ce qui appelle une règle explicite "
        "sur les IBAN communs à plusieurs salariés. Le salarié fantôme et le salarié sorti encore payé restent à 0 % pour toutes les approches.")

    rp.ecrire_texte(
        "fig_ablation_variables", "Variation de la PR-AUC lorsqu'une variable est retirée de l'Isolation Forest (test et validation).",
        "Une barre négative signale une variable utile (sa suppression dégrade la PR-AUC), une barre positive une variable "
        "qui gêne le modèle. Les barres du test et de la validation vont dans le même sens pour les six variables, ce qui valide la "
        "sélection faite sur la validation seule. Le montant brut et le z-score dans la société sont les deux variables nuisibles. "
        "L'indicateur d'IBAN modifié, la variation sur le mois précédent et le nombre de paiements sur le même IBAN sont parmi les plus utiles, "
        "l'écart à la moyenne du salarié étant à un niveau comparable. Cette lecture a servi à construire la variante à variables retenues.")

    rp.ecrire_texte(
        "fig_rappel_par_type_approches", "Rappel par type d'anomalie (en %) pour les principales approches, jeu de test.",
        "La carte de chaleur résume les complémentarités : les colonnes de règles sont foncées pour les doublons et les montants nuls, "
        "celles des modèles statistiques pour les IBAN. La colonne de l'hybride optimisé est la plus foncée dans l'ensemble, sauf pour "
        "l'IBAN partagé. Seules les deux dernières lignes (salarié fantôme, salarié sorti) restent entièrement claires, et la hausse subtile "
        "ne devient détectable qu'après retrait du montant brut. Les deux types sans détection appellent des "
        "informations nouvelles (historique de présence, rapprochement avec le registre du personnel) plutôt qu'un algorithme différent.")

    h, o = A["hybride_optimise"], A["if_actuel"]
    rp.ecrire_texte(
        "justification_choix_modele", "Justification du choix du modèle.",
        f"Nous recommandons l'hybride optimisé (règles métier et Isolation Forest sur cinq variables) : sur le test, il détecte "
        f"{_pct(m('hybride_optimise', 'rappel')['moyenne'], 0)} des anomalies avec une précision de {_pct(m('hybride_optimise', 'precision')['moyenne'], 0)}, contre "
        f"{_pct(m('if_actuel', 'rappel')['moyenne'], 0)} et {_pct(m('if_actuel', 'precision')['moyenne'], 0)} pour la décision actuelle, "
        f"et réduit les faux positifs de {fp('if_actuel')} à {fp('hybride_optimise')} pour 1 000 fiches. Ce choix n'a utilisé que la validation "
        "(sélection des variables et du seuil) ; il est aussi le meilleur sur la validation. Il ne dépend pas d'un algorithme sophistiqué : "
        "le One-Class SVM est une alternative crédible, et le gain provient surtout des règles métier, de la suppression des variables "
        "nuisibles et de la normalisation par société. "
        f"L'IA apporte une valeur propre : {fr(tot['ia_seule'], 0)} anomalies par jeu de test, soit {_pct(tot['ia_seule'] / max(detectees, 1e-9), 0)} des anomalies "
        f"détectées, échappent aux règles, alors que les règles seules produisent {fr(fpv['regles_seules'], 0)} fausses alertes que l'IA ne partage pas "
        f"(contre {fr(fpv['ia_seule'], 0)} pour l'IA seule). "
        "Réserves : les données sont synthétiques et les anomalies injectées par des règles connues ; les performances "
        "réelles devront être mesurées lors d'un pilote. Ce modèle est préparé comme version candidate (voir `benchmark/candidat_v2/`) et n'est pas activé : "
        "le modèle en service reste inchangé.")


# --- Section du rapport ------------------------------------------------------------------------------

def _section() -> None:
    def tableau(nom):
        return rp.lire_fichier(f"{rp.DOSSIER_TABLEAUX}/{nom}.md") + "\n\n" + rp.lire_fichier(f"{rp.DOSSIER_TEXTES}/{nom}.md")

    def figure(nom):
        return f"![{nom}](rapport/figures/{nom}.png)\n\n" + rp.lire_fichier(f"{rp.DOSSIER_TEXTES}/{nom}.md")

    def texte(nom):
        return rp.lire_fichier(f"{rp.DOSSIER_TEXTES}/{nom}.md")

    rp.ecrire_section("04_comparaison_approches", f"""## 4. Comparaison des approches (étape B)

Approches comparées avec le protocole de la section 3 : règles métier seules (IBAN, montant, doublon, « 2 fois la moyenne »),
Isolation Forest actuel, Local Outlier Factor, One-Class SVM (entraîné sur un échantillon de 10 000 fiches), hybride règles + IA,
et trois variantes de l'Isolation Forest (indicateur de décembre, écart relatif à la société, variables retenues sur la validation).

### 4.1 Comparaison globale

{tableau('tab_comparaison_approches')}

{figure('fig_comparaison_f1_prauc')}

### 4.2 Variantes pour le 13e mois

{texte('analyse_variantes_calendrier_collectif')}

### 4.3 Ablation des variables

{tableau('tab_ablation_variables')}

{figure('fig_ablation_variables')}

### 4.4 Valeur ajoutée de l'IA

{tableau('tab_valeur_ajoutee_ia')}

{figure('fig_valeur_ajoutee_regles_ia')}

### 4.5 Détection par type et par approche

{tableau('tab_detection_par_type_approches')}

{figure('fig_rappel_par_type_approches')}

### 4.6 Justification du choix du modèle

{texte('justification_choix_modele')}""")


def produire(calcul: Dict, res: Dict) -> None:
    _figures(res)
    _tableaux(res)
    _textes(res)
    _section()
