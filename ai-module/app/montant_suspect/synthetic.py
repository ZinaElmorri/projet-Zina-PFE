"""Génération de données synthétiques réalistes pour l'entraînement / l'évaluation.

Produit des salaires par client + des anomalies injectées de trois types :
montant x3 à x5, IBAN changé (montant normal), montant très éloigné des autres salariés du client.
"""
from typing import Dict, List, Optional, Tuple

import numpy as np

from .config import HISTORY_MONTHS

TYPES_ANOMALIES = {
    "montant_multiplie": "Montant x3 à x5 du salaire habituel",
    "iban_change": "IBAN changé, montant habituel",
    "eloigne_client": "Montant très éloigné des autres salariés du client",
}
_TYPES = list(TYPES_ANOMALIES)


def generate_with_types(
    n_clients: int = 8,
    salaries_par_client: int = 30,
    anomaly_rate: float = 0.01,
    seed: int = 42,
) -> Tuple[List[Dict], List[int], List[Optional[str]]]:
    """Retourne (paiements, labels, types) ; type = None pour un paiement normal."""
    rng = np.random.default_rng(seed)
    paiements: List[Dict] = []
    labels: List[int] = []
    types: List[Optional[str]] = []
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
            type_anomalie = None

            if rng.random() < anomaly_rate:
                type_anomalie = _TYPES[int(rng.integers(0, 3))]
                if type_anomalie == "montant_multiplie":
                    montant = round(salaire * rng.uniform(3.0, 5.0), 2)
                    iban_change = False
                elif type_anomalie == "iban_change":
                    montant = round(salaire, 2)
                    iban_change = True
                else:
                    montant = round(base_client * rng.uniform(4.0, 6.0), 2)
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
            labels.append(1 if type_anomalie else 0)
            types.append(type_anomalie)
            fiche_id += 1

    return paiements, labels, types


def generate(
    n_clients: int = 8,
    salaries_par_client: int = 30,
    anomaly_rate: float = 0.01,
    seed: int = 42,
) -> Tuple[List[Dict], List[int]]:
    """Retourne (paiements, labels) où label=1 signale une anomalie injectée."""
    paiements, labels, _ = generate_with_types(n_clients, salaries_par_client, anomaly_rate, seed)
    return paiements, labels
