"""Paramètres configurables par l'administrateur système (via variables d'environnement)."""
import os

CONTAMINATION = float(os.getenv("AI_CONTAMINATION", "0.01"))
SCORE_THRESHOLD = float(os.getenv("AI_SCORE_THRESHOLD", "0.6"))
HISTORY_MONTHS = int(os.getenv("AI_HISTORY_MONTHS", "6"))
N_ESTIMATORS = int(os.getenv("AI_N_ESTIMATORS", "200"))
RANDOM_STATE = 42
# Règle métier complémentaire : un IBAN modifié est toujours signalé pour vérification.
REGLE_IBAN_CHANGE = os.getenv("AI_REGLE_IBAN_CHANGE", "true").lower() == "true"

# Ordre des features attendu par le modèle.
FEATURES = [
    "montant",
    "ecart_moyenne_salarie",
    "zscore_client",
    "variation_mois_precedent",
    "iban_change_recent",
    "nb_paiements_meme_iban",
]
