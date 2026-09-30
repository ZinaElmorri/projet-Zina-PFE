"""Génération de données synthétiques réalistes pour l'entraînement / l'évaluation.

Produit des salaires par client + ~1 % d'anomalies injectées (montant x3,
IBAN changé, montant très éloigné des autres salariés du client).
"""
from typing import Dict, List, Tuple

import numpy as np

from .config import HISTORY_MONTHS


def generate(
    n_clients: int = 8,
    salaries_par_client: int = 30,
    anomaly_rate: float = 0.01,
    seed: int = 42,
) -> Tuple[List[Dict], List[int]]:
    """Retourne (paiements, labels) où label=1 signale une anomalie injectée."""
    rng = np.random.default_rng(seed)
    paiements: List[Dict] = []
    labels: List[int] = []
    fiche_id = 1

    for client in range(n_clients):
        # Salaire moyen du client entre 1800 et 4200 €.
        base_client = rng.uniform(1800, 4200)
        for _ in range(salaries_par_client):
            salaire = max(1200.0, rng.normal(base_client, base_client * 0.08))
            historique = [
                round(max(1000.0, rng.normal(salaire, salaire * 0.03)), 2)
                for _ in range(HISTORY_MONTHS)
            ]
            iban = f"FR76{rng.integers(10**10, 10**11)}"
            est_anomalie = rng.random() < anomaly_rate

            if est_anomalie:
                type_anomalie = rng.integers(0, 3)
                if type_anomalie == 0:
                    montant = round(salaire * rng.uniform(3.0, 5.0), 2)  # montant x3+
                    iban_change = False
                elif type_anomalie == 1:
                    montant = round(salaire, 2)
                    iban_change = True  # IBAN changé
                else:
                    montant = round(base_client * rng.uniform(4.0, 6.0), 2)  # très éloigné
                    iban_change = False
            else:
                montant = round(max(1000.0, rng.normal(salaire, salaire * 0.03)), 2)
                iban_change = False

            paiements.append({
                "ficheId": fiche_id,
                "salarieId": str(fiche_id),
                "clientId": str(client),
                "montant": montant,
                "iban": iban,
                "ibanChange": iban_change,
                "historique": historique,
            })
            labels.append(1 if est_anomalie else 0)
            fiche_id += 1

    return paiements, labels
