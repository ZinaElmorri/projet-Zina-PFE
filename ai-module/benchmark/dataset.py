"""Jeu de données de paie réaliste, étiqueté, pour l'évaluation du module IA (rapport de PFE).

Le générateur simule 18 mois de paie pour 9 sociétés (≈ 4 000 salariés) : augmentations annuelles,
13e mois en décembre, primes ponctuelles et changements d'IBAN NORMAUX (cas difficiles à ne pas signaler),
entrées et sorties de salariés. Des anomalies étiquetées (≈ 2 % des fiches) sont ensuite injectées.

Convention sur l'historique : il représente les virements effectivement exécutés, supposés corrects
(une fiche anormale est corrigée avant exécution). Une anomalie d'un mois ne pollue donc pas l'historique
du mois suivant. Cette hypothèse est discutée dans les limites du rapport.

Usage : ``python -m benchmark.describe_dataset`` (depuis ai-module/).
"""
from __future__ import annotations

import dataclasses
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd
from faker import Faker

from app.montant_suspect.config import HISTORY_MONTHS

# --- Paramètres du jeu de données --------------------------------------------------------------

SEED_REFERENCE = 42
N_MOIS = 18
MOIS_DEBUT = (2024, 7)  # mois 1 = juillet 2024 ; mois 18 = décembre 2025 (13e mois dans le jeu de test)
DECOUPAGE = {"entrainement": range(1, 13), "validation": range(13, 16), "test": range(16, 19)}

TAILLES_SOCIETES = (1000, 800, 600, 500, 400, 300, 200, 120, 80)  # 4 000 salariés
SOCIETES_TREIZIEME_MOIS = (2, 5, 8)  # indices des sociétés qui versent un 13e mois en décembre

TAUX_EVENEMENTS = 0.0178  # événements d'anomalie par fiche ; ≈ 2 % de fiches anormales (une paire compte pour 2 fiches)
# Les types « en paire » (2 fiches étiquetées par événement) sont tirés moitié moins souvent pour équilibrer les effectifs.
POIDS_TYPES = {"doublon": 0.5, "iban_partage": 0.5}
P_PRIME = 0.01
P_CHANGEMENT_IBAN = 0.0025
P_PROMOTION = 0.004
P_DEPART = 0.008
P_EMBAUCHE = 0.009
BRUIT_MENSUEL = 0.012

TYPES_ANOMALIES: Dict[str, str] = {
    "montant_multiplie": "Montant multiplié par 3 à 10",
    "erreur_virgule": "Erreur de virgule (×100 ou ÷100)",
    "hausse_subtile": "Hausse subtile (+25 à +60 %)",
    "montant_nul_negatif": "Montant nul ou négatif",
    "doublon": "Doublon (salarié payé deux fois)",
    "iban_change_montant_eleve": "IBAN modifié et montant élevé",
    "iban_partage": "IBAN partagé entre deux salariés",
    "salarie_fantome": "Salarié fantôme (sans historique)",
    "salarie_sorti_paye": "Salarié sorti encore payé",
}
PROFILS = ("employé", "cadre", "dirigeant")

COLONNES = ["fiche_id", "mois_index", "mois", "societe_id", "salarie_id", "nom", "profil", "iban",
            "iban_change", "montant", "historique", "mois_depuis_dernier_paiement",
            "label", "type_anomalie", "evenement_normal", "montant_legitime"]

_BANQUES = ("30002", "30003", "30004", "30006", "20041", "10278", "12548", "18206", "14707", "13335",
            "11315", "17515")


# --- IBAN ------------------------------------------------------------------------------------------

def iban_valide(iban: str) -> bool:
    """Contrôle de la clé IBAN (modulo 97, ISO 13616)."""
    iban = (iban or "").replace(" ", "").upper()
    if len(iban) < 15 or not iban.isalnum():
        return False
    rearrange = iban[4:] + iban[:4]
    numerique = "".join(str(int(c, 36)) for c in rearrange)
    return int(numerique) % 97 == 1


def generer_iban(rng: np.random.Generator) -> str:
    """IBAN français valide (clé RIB et clé IBAN calculées)."""
    banque = _BANQUES[int(rng.integers(0, len(_BANQUES)))]
    guichet = f"{int(rng.integers(0, 100000)):05d}"
    compte = f"{int(rng.integers(0, 10 ** 11)):011d}"
    cle_rib = 97 - (89 * int(banque) + 15 * int(guichet) + 3 * int(compte)) % 97
    bban = f"{banque}{guichet}{compte}{cle_rib:02d}"
    cle_iban = 98 - int(bban + "152700") % 97
    return f"FR{cle_iban:02d}{bban}"


# --- Simulation ------------------------------------------------------------------------------------

@dataclasses.dataclass
class _Employe:
    salarie_id: str
    nom: str
    societe: int
    profil: str
    base: float
    iban: str
    historique: List[float]
    dernier_mois: Optional[int]
    actif: bool = True
    mois_sortie: Optional[int] = None
    reutilise: bool = False


class Simulateur:
    """Simule la paie mensuelle puis injecte les anomalies étiquetées."""

    def __init__(self, tailles=TAILLES_SOCIETES, seed: int = SEED_REFERENCE, n_mois: int = N_MOIS,
                 debut: Tuple[int, int] = MOIS_DEBUT, societes_treizieme=SOCIETES_TREIZIEME_MOIS,
                 p_depart: float = P_DEPART, p_embauche: float = P_EMBAUCHE,
                 departs_fixes: Optional[Dict[int, int]] = None):
        self.rng = np.random.default_rng(seed)
        self.fake = Faker("fr_FR")
        self.fake.seed_instance(seed)
        self.tailles = tuple(tailles)
        self.n_mois = n_mois
        self.debut = debut
        self.treizieme = set(societes_treizieme)
        self.p_depart, self.p_embauche = p_depart, p_embauche
        # Mode « scénario » : départs en nombre imposé, aucune embauche.
        self.departs_fixes = departs_fixes
        self.employes: List[_Employe] = []
        self.partis: List[_Employe] = []
        self._ibans: set = set()
        self._compteur_salarie = 0
        self._compteur_fiche = 0
        self._legit_du_mois: Dict[str, float] = {}
        self._parametres = [self._parametres_societe(i) for i in range(len(self.tailles))]
        for s, taille in enumerate(self.tailles):
            for _ in range(taille):
                self.employes.append(self._nouvel_employe(s, avec_historique=True))

    # --- création des salariés ---

    def _parametres_societe(self, s: int) -> Dict:
        r = self.rng
        part_dir = float(r.uniform(0.01, 0.035))
        part_cadre = float(r.uniform(0.17, 0.28))
        return {
            "id": f"SOC-{s + 1:02d}",
            "nom": self.fake.company(),
            "base_employe": float(r.uniform(1700, 2300)),
            "profils": [1 - part_cadre - part_dir, part_cadre, part_dir],
        }

    def _iban_unique(self) -> str:
        while True:
            iban = generer_iban(self.rng)
            if iban not in self._ibans:
                self._ibans.add(iban)
                return iban

    def _base_salaire(self, s: int, profil: str) -> float:
        r, p = self.rng, self._parametres[s]
        if profil == "employé":
            return max(1450.0, p["base_employe"] * float(r.lognormal(0, 0.10)))
        if profil == "cadre":
            return p["base_employe"] * float(r.uniform(1.6, 2.3)) * float(r.lognormal(0, 0.12))
        return float(r.uniform(6500, 12500)) * float(r.lognormal(0, 0.12))

    def _nouvel_employe(self, s: int, avec_historique: bool, profil: Optional[str] = None) -> _Employe:
        r = self.rng
        self._compteur_salarie += 1
        profil = profil or str(r.choice(PROFILS, p=self._parametres[s]["profils"]))
        base = round(self._base_salaire(s, profil), 2)
        historique = []
        if avec_historique:  # 6 mois de paie avant le début du jeu de données
            historique = [round(base * (1 + float(r.normal(0, BRUIT_MENSUEL))), 2) for _ in range(HISTORY_MONTHS)]
        return _Employe(
            salarie_id=f"EMP{self._compteur_salarie:05d}",
            nom=f"{self.fake.first_name()} {self.fake.last_name()}",
            societe=s, profil=profil, base=base, iban=self._iban_unique(),
            historique=historique, dernier_mois=0 if avec_historique else None)

    # --- calendrier ---

    def _mois_civil(self, m: int) -> Tuple[int, int]:
        n = self.debut[0] * 12 + (self.debut[1] - 1) + (m - 1)
        return n // 12, n % 12 + 1

    def libelle_mois(self, m: int) -> str:
        a, mm = self._mois_civil(m)
        return f"{a}-{mm:02d}"

    # --- boucle principale ---

    def executer(self, taux_evenements: float = TAUX_EVENEMENTS,
                 plan: Optional[Dict[int, List[Tuple[str, Dict]]]] = None) -> pd.DataFrame:
        """Retourne le jeu de données ; ``plan`` impose des événements précis par mois (scénario)."""
        lignes: List[Dict] = []
        for m in range(1, self.n_mois + 1):
            self._mouvements(m)
            self._evolutions_salaire(m)
            self._legit_du_mois = {}
            mois_lignes = [self._ligne_legitime(e, m) for e in self.employes if e.actif]
            extras: List[Dict] = []
            if plan is not None:
                evenements = plan.get(m, [])
            else:
                evenements = self._tirer_evenements(len(mois_lignes), taux_evenements)
            for type_anomalie, options in evenements:
                extras.extend(self._injecter(type_anomalie, options, mois_lignes, m))
            for ligne in mois_lignes + extras:
                self._compteur_fiche += 1
                ligne["fiche_id"] = self._compteur_fiche
                ligne["mois_index"] = m
                ligne["mois"] = self.libelle_mois(m)
                lignes.append(ligne)
            for e in self.employes:  # l'historique ne retient que les virements corrects
                if e.actif:
                    e.historique = (e.historique + [self._legit_du_mois[e.salarie_id]])[-HISTORY_MONTHS:]
                    e.dernier_mois = m
        df = pd.DataFrame(lignes)
        df["label"] = df["label"].astype(int)
        return df[COLONNES].reset_index(drop=True)

    def _mouvements(self, m: int) -> None:
        r = self.rng
        if self.departs_fixes is not None:
            actifs = [e for e in self.employes if e.actif]
            for idx in r.choice(len(actifs), size=self.departs_fixes.get(m, 0), replace=False):
                self._faire_partir(actifs[int(idx)], m)
            return
        for e in self.employes:
            if e.actif and r.random() < self.p_depart:
                self._faire_partir(e, m)
        for s, taille in enumerate(self.tailles):
            for _ in range(int(r.poisson(taille * self.p_embauche))):
                self.employes.append(self._nouvel_employe(s, avec_historique=False))

    def _faire_partir(self, e: _Employe, m: int) -> None:
        e.actif = False
        e.mois_sortie = m - 1  # dernier mois payé
        self.partis.append(e)

    def _evolutions_salaire(self, m: int) -> None:
        r = self.rng
        _, mois_civil = self._mois_civil(m)
        for e in self.employes:
            if not e.actif:
                continue
            if mois_civil == 1:  # augmentation annuelle
                e.base = round(e.base * (1 + float(r.uniform(0.01, 0.04))), 2)
            elif e.dernier_mois is not None and r.random() < P_PROMOTION:
                e.base = round(e.base * (1 + float(r.uniform(0.08, 0.15))), 2)

    def _ligne_legitime(self, e: _Employe, m: int) -> Dict:
        r = self.rng
        _, mois_civil = self._mois_civil(m)
        montant = e.base * (1 + float(r.normal(0, BRUIT_MENSUEL)))
        evenement = None
        if mois_civil == 12 and e.societe in self.treizieme:
            montant *= 2.0
            evenement = "treizieme_mois"
        elif r.random() < P_PRIME:
            montant *= 1 + float(r.uniform(0.08, 0.35))
            evenement = "prime"
        iban_change = False
        if e.dernier_mois is not None and r.random() < P_CHANGEMENT_IBAN:
            e.iban = self._iban_unique()
            iban_change = True
            evenement = evenement or "iban_change_legitime"
        montant = round(montant, 2)
        self._legit_du_mois[e.salarie_id] = montant
        return {
            "societe_id": self._parametres[e.societe]["id"], "salarie_id": e.salarie_id, "nom": e.nom,
            "profil": e.profil, "iban": e.iban, "iban_change": iban_change, "montant": montant,
            "historique": list(e.historique),
            "mois_depuis_dernier_paiement": None if e.dernier_mois is None else m - e.dernier_mois,
            "label": 0, "type_anomalie": None, "evenement_normal": evenement,
            "montant_legitime": montant,
        }

    # --- anomalies ---

    def _tirer_evenements(self, n_lignes: int, taux: float) -> List[Tuple[str, Dict]]:
        n = int(self.rng.binomial(n_lignes, taux))
        types = list(TYPES_ANOMALIES)
        poids = np.array([POIDS_TYPES.get(t, 1.0) for t in types])
        return [(str(self.rng.choice(types, p=poids / poids.sum())), {}) for _ in range(n)]

    def _choisir(self, lignes: List[Dict], tenure_min: int = 0, societe: Optional[str] = None) -> Optional[Dict]:
        candidats = [l for l in lignes
                     if not l.get("_utilisee") and len(l["historique"]) >= tenure_min
                     and (societe is None or l["societe_id"] == societe)]
        return candidats[int(self.rng.integers(0, len(candidats)))] if candidats else None

    @staticmethod
    def _marquer(ligne: Dict, type_anomalie: str) -> None:
        ligne["label"], ligne["type_anomalie"], ligne["_utilisee"] = 1, type_anomalie, True

    def _ligne_extra(self, e: _Employe, montant: float, historique: List[float],
                     depuis: Optional[int]) -> Dict:
        return {
            "societe_id": self._parametres[e.societe]["id"], "salarie_id": e.salarie_id, "nom": e.nom,
            "profil": e.profil, "iban": e.iban, "iban_change": False, "montant": montant,
            "historique": historique, "mois_depuis_dernier_paiement": depuis, "label": 0,
            "type_anomalie": None, "evenement_normal": None, "montant_legitime": 0.0,
        }

    def _injecter(self, type_anomalie: str, options: Dict, lignes: List[Dict], m: int) -> List[Dict]:
        r = self.rng
        extras: List[Dict] = []
        if type_anomalie in ("montant_multiplie", "erreur_virgule", "hausse_subtile", "montant_nul_negatif",
                             "iban_change_montant_eleve"):
            ligne = self._choisir(lignes, tenure_min=0 if type_anomalie == "montant_nul_negatif" else 3)
            if ligne is None:
                return extras
            legit = ligne["montant_legitime"]
            if type_anomalie == "montant_multiplie":
                ligne["montant"] = round(legit * float(options.get("facteur", r.uniform(3, 10))), 2)
            elif type_anomalie == "erreur_virgule":
                facteur = options.get("facteur", 100 if r.random() < 0.5 else 0.01)
                ligne["montant"] = round(legit * facteur, 2)
            elif type_anomalie == "hausse_subtile":
                ligne["montant"] = round(legit * float(options.get("facteur", r.uniform(1.25, 1.60))), 2)
            elif type_anomalie == "montant_nul_negatif":
                ligne["montant"] = 0.0 if r.random() < 0.5 else -legit
            else:
                ligne["montant"] = round(legit * float(options.get("facteur", r.uniform(1.5, 3.0))), 2)
                ligne["iban"], ligne["iban_change"] = self._iban_unique(), True
            self._marquer(ligne, type_anomalie)
        elif type_anomalie == "doublon":
            ligne = self._choisir(lignes, tenure_min=1)
            if ligne is not None:
                copie = dict(ligne)
                copie["historique"] = list(ligne["historique"])
                self._marquer(ligne, type_anomalie)
                self._marquer(copie, type_anomalie)
                extras.append(copie)
        elif type_anomalie == "iban_partage":
            a = self._choisir(lignes, tenure_min=1)
            b = None
            if a is not None:
                a["_utilisee"] = True  # évite de tirer le même salarié comme second
                b = self._choisir(lignes, tenure_min=1, societe=a["societe_id"])
                a["_utilisee"] = False
            if a is not None and b is not None:
                b["iban"], b["iban_change"] = a["iban"], True
                self._marquer(a, type_anomalie)
                self._marquer(b, type_anomalie)
        elif type_anomalie == "salarie_fantome":
            s = int(r.integers(0, len(self.tailles)))
            fantome = self._nouvel_employe(s, avec_historique=False, profil="employé" if r.random() < 0.8 else "cadre")
            montant = round(fantome.base * float(options.get("facteur", r.uniform(0.9, 1.5))), 2)
            ligne = self._ligne_extra(fantome, montant, [], None)
            self._marquer(ligne, type_anomalie)
            extras.append(ligne)
        elif type_anomalie == "salarie_sorti_paye":
            candidats = [e for e in self.partis if e.mois_sortie is not None and 2 <= m - e.mois_sortie <= 4
                         and not e.reutilise and e.historique]
            if candidats:
                e = candidats[int(r.integers(0, len(candidats)))]
                e.reutilise = True
                montant = round(e.base * (1 + float(r.normal(0, BRUIT_MENSUEL))), 2)
                ligne = self._ligne_extra(e, montant, list(e.historique), m - e.mois_sortie)
                self._marquer(ligne, type_anomalie)
                extras.append(ligne)
        return extras

    def societes(self) -> List[Dict]:
        return [{"id": p["id"], "nom": p["nom"]} for p in self._parametres]


# --- API publique ------------------------------------------------------------------------------------

def generer_jeu_de_donnees(seed: int = SEED_REFERENCE) -> pd.DataFrame:
    """Jeu de référence : 9 sociétés, ≈ 4 000 salariés, 18 mois, ≈ 2 % de fiches anormales."""
    return Simulateur(seed=seed).executer()


def decouper(df: pd.DataFrame) -> Dict[str, pd.DataFrame]:
    """Découpage chronologique (par mois de paie, jamais au hasard) : 1–12 / 13–15 / 16–18."""
    return {nom: df[df["mois_index"].isin(list(mois))] for nom, mois in DECOUPAGE.items()}


def vers_paiements(df_mois: pd.DataFrame) -> List[Dict]:
    """Convertit des fiches au format de l'API du module IA (``PaiementIn``)."""
    return [{
        "ficheId": int(r.fiche_id), "salarieId": r.salarie_id, "clientId": r.societe_id,
        "montant": float(r.montant), "iban": r.iban, "ibanChange": bool(r.iban_change),
        "historique": [float(x) for x in r.historique],
    } for r in df_mois.itertuples(index=False)]


def a_csv(df: pd.DataFrame, chemin: str) -> None:
    sortie = df.copy()
    sortie["historique"] = sortie["historique"].map(lambda h: "|".join(f"{x:.2f}" for x in h))
    sortie.to_csv(chemin, index=False, encoding="utf-8")


# --- Scénario de soutenance --------------------------------------------------------------------------

SCENARIO_SEED = 2026
SCENARIO_DEBUT = (2025, 4)  # mois 1..6 = historique, mois 7 = octobre 2025 (fichier à contrôler)
SCENARIO_PLAN: List[Tuple[str, Dict]] = [
    ("montant_multiplie", {}),
    ("erreur_virgule", {"facteur": 100}),
    ("erreur_virgule", {"facteur": 0.01}),
    ("hausse_subtile", {}),
    ("montant_nul_negatif", {}),
    ("iban_change_montant_eleve", {}),
    ("salarie_fantome", {}),
    ("salarie_sorti_paye", {}),
    ("doublon", {}),
    ("iban_partage", {}),
]


def construire_scenario(seed: int = SCENARIO_SEED) -> Dict[str, pd.DataFrame]:
    """Une société, un fichier de 1 100 fiches dont exactement 12 anomalies connues.

    Retourne ``historique`` (6 mois de virements corrects), ``fichier`` (mois à contrôler) et ``attendues``.
    """
    departs = {2: 6, 3: 6, 4: 6, 5: 6}
    n_depart = sum(departs.values())
    # 1 097 salariés présents + 3 lignes supplémentaires (fantôme, sorti payé, doublon) = 1 100 fiches.
    sim = Simulateur(tailles=(1097 + n_depart,), seed=seed, n_mois=7, debut=SCENARIO_DEBUT,
                     societes_treizieme=(), departs_fixes=departs)
    df = sim.executer(plan={7: SCENARIO_PLAN})
    historique = df[df["mois_index"] <= 6].reset_index(drop=True)
    fichier = df[df["mois_index"] == 7].reset_index(drop=True)
    attendues = fichier[fichier["label"] == 1].copy()
    attendues["description"] = attendues.apply(_description_attendue, axis=1)
    attendues = attendues[["fiche_id", "salarie_id", "nom", "type_anomalie", "montant", "montant_legitime",
                           "description"]].reset_index(drop=True)
    return {"historique": historique, "fichier": fichier, "attendues": attendues}


def _fr(x: float) -> str:
    return f"{x:,.2f}".replace(",", "\u202f").replace(".", ",")


def _description_attendue(l) -> str:
    t, montant, legit = l["type_anomalie"], l["montant"], l["montant_legitime"]
    if t == "montant_multiplie":
        rapport = f"{montant / legit:.1f}".replace(".", ",")
        return f"Montant {rapport}× le salaire habituel ({_fr(montant)} € au lieu de {_fr(legit)} €)"
    if t == "erreur_virgule":
        return f"Erreur de virgule : {_fr(montant)} € au lieu de {_fr(legit)} €"
    if t == "hausse_subtile":
        return f"Hausse de {(montant / legit - 1) * 100:.0f} % sans justification ({_fr(montant)} € au lieu de {_fr(legit)} €)"
    if t == "montant_nul_negatif":
        return f"Montant nul ou négatif : {_fr(montant)} €"
    if t == "doublon":
        return "Salarié présent deux fois dans le fichier (même montant, même IBAN)"
    if t == "iban_change_montant_eleve":
        return f"IBAN modifié et montant élevé ({_fr(montant)} € au lieu de {_fr(legit)} €)"
    if t == "iban_partage":
        return "IBAN identique à celui d'un autre salarié de la société"
    if t == "salarie_fantome":
        return "Salarié sans aucun historique de paie"
    return "Salarié sorti depuis plusieurs mois encore payé"
