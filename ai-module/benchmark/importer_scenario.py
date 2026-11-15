"""Importe le scénario de soutenance dans la base de l'application (données de démonstration).

Usage (depuis ai-module/, application lancée avec Docker) :

    python -m benchmark.importer_scenario                 # import (refusé si le scénario est déjà présent)
    python -m benchmark.importer_scenario --reinitialiser # supprime d'abord SOC-DEMO, puis réimporte
    python -m benchmark.importer_scenario --retirer       # supprime uniquement le scénario SOC-DEMO

Ce que fait l'import, SANS effacer les données existantes (seules les lignes de la société SOC-DEMO sont
ajoutées, ou supprimées avec --reinitialiser / --retirer) :
  - la société débitrice « SOC-DEMO » (si elle n'existe pas) ;
  - son affectation à l'Expert RH choisi (``--expert``, par défaut expert1), s'il n'en a pas déjà une ;
  - 6 mois de virements corrects (les 6 mois précédant le mois courant) et le fichier de paie du MOIS COURANT
    (1 100 fiches dont 12 anomalies connues), tous au statut APPROUVEE.

Les dates sont recalées sur le mois courant : le fichier à contrôler est toujours « ce mois-ci », donc visible
dans les tableaux de bord sans changer de période. Les anomalies attendues sont dans
benchmark/scenario_soutenance/anomalies_attendues.csv.
"""
from __future__ import annotations

import argparse
import csv
import datetime
import io
import os
import subprocess
import sys
from typing import List, Tuple

CODE_SOCIETE = "SOC-DEMO"
NOM_SOCIETE = "Société de démonstration SEPAPaie"
IBAN_DEBITEUR = "FR7630004000031234567890143"
BIC_DEBITEUR = "BNPAFRPP"
BIC_SALARIES = "BNPAFRPP"
DOSSIER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scenario_soutenance")


def _mois_decale(premier_du_mois: datetime.date, decalage: int) -> datetime.date:
    n = premier_du_mois.year * 12 + premier_du_mois.month - 1 + decalage
    return datetime.date(n // 12, n % 12 + 1, 1)


def _lire(nom: str) -> List[dict]:
    with open(os.path.join(DOSSIER, nom), encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def construire_lignes(aujourdhui: datetime.date) -> Tuple[List[list], dict]:
    """Lignes (employe, nom, iban, montant, cree_le) recalées sur le mois courant, et statistiques."""
    courant = aujourdhui.replace(day=1)
    historique = _lire("historique_6_mois.csv")
    fichier = _lire("fichier_paie_mois.csv")
    mois_historique = sorted({l["mois"] for l in historique})
    if len(mois_historique) != 6:
        raise SystemExit(f"L'historique du scénario doit couvrir 6 mois (trouvé : {len(mois_historique)}).")
    cible = {m: _mois_decale(courant, i - 6) for i, m in enumerate(mois_historique)}

    lignes: List[list] = []
    for l in historique:
        jour = cible[l["mois"]]
        lignes.append([l["matricule"], l["nom"], l["iban"], l["montant"], f"{jour.isoformat()} 08:00:00"])
    for l in fichier:
        lignes.append([l["matricule"], l["nom"], l["iban"], l["montant"], f"{courant.isoformat()} 00:00:30"])
    return lignes, {"historique": len(historique), "fichier": len(fichier), "mois": courant.strftime("%Y-%m")}


def _sql_import(lignes: List[list], expert: str) -> str:
    tampon = io.StringIO()
    ecrivain = csv.writer(tampon, lineterminator="\n")
    for employe, nom, iban, montant, cree_le in lignes:
        ecrivain.writerow([employe, CODE_SOCIETE, nom, iban, BIC_SALARIES, montant, "EUR", "Salaire", "APPROUVEE",
                           cree_le, cree_le])
    return f"""
BEGIN;
INSERT INTO societes (code, nom, iban, bic, actif, cree_le, modifie_le)
SELECT '{CODE_SOCIETE}', '{NOM_SOCIETE.replace("'", "''")}', '{IBAN_DEBITEUR}', '{BIC_DEBITEUR}', true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM societes WHERE code = '{CODE_SOCIETE}');
INSERT INTO affectations_societe (societe_code, expert_id, expert_username, affecte_par, debut_le)
SELECT '{CODE_SOCIETE}', u.id, u.username, 'systeme', now() FROM utilisateurs u
WHERE u.username = '{expert}'
  AND NOT EXISTS (SELECT 1 FROM affectations_societe a WHERE a.societe_code = '{CODE_SOCIETE}' AND a.fin_le IS NULL);
COPY transactions (employe_id, client_id, nom_beneficiaire, iban, bic, montant, devise, libelle, statut, cree_le, modifie_le)
FROM STDIN WITH (FORMAT csv);
{tampon.getvalue()}\\.
COMMIT;
"""


SQL_RETRAIT = f"""
BEGIN;
DELETE FROM anomalies WHERE transaction_id IN (SELECT id FROM transactions WHERE client_id = '{CODE_SOCIETE}');
DELETE FROM sepa_fichiers_generes WHERE client_id = '{CODE_SOCIETE}';
DELETE FROM transactions WHERE client_id = '{CODE_SOCIETE}';
DELETE FROM affectations_societe WHERE societe_code = '{CODE_SOCIETE}';
DELETE FROM societes WHERE code = '{CODE_SOCIETE}';
COMMIT;
"""


def _psql(conteneur: str, sql: str) -> str:
    resultat = subprocess.run(
        ["docker", "exec", "-i", conteneur, "psql", "-U", "sepa", "-d", "sepa_db", "-v", "ON_ERROR_STOP=1", "-At"],
        input=sql.encode("utf-8"), capture_output=True)
    if resultat.returncode != 0:
        raise SystemExit("Erreur PostgreSQL :\n" + resultat.stderr.decode("utf-8", "replace"))
    return resultat.stdout.decode("utf-8", "replace").strip()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--conteneur", default="sepa-postgres", help="Nom du conteneur PostgreSQL")
    parser.add_argument("--expert", default="expert1", help="Expert RH auquel affecter SOC-DEMO")
    parser.add_argument("--reinitialiser", action="store_true", help="Supprime SOC-DEMO puis réimporte")
    parser.add_argument("--retirer", action="store_true", help="Supprime uniquement le scénario SOC-DEMO")
    args = parser.parse_args()

    if args.retirer:
        _psql(args.conteneur, SQL_RETRAIT)
        print("Scénario SOC-DEMO retiré (les autres données n'ont pas été touchées).")
        return

    deja = int(_psql(args.conteneur, f"SELECT count(*) FROM transactions WHERE client_id = '{CODE_SOCIETE}';") or 0)
    if deja and not args.reinitialiser:
        raise SystemExit(f"Le scénario est déjà importé ({deja} fiches SOC-DEMO). "
                         "Utilisez --reinitialiser pour le recréer, ou --retirer pour le supprimer.")
    if deja:
        _psql(args.conteneur, SQL_RETRAIT)

    lignes, stats = construire_lignes(datetime.date.today())
    _psql(args.conteneur, _sql_import(lignes, args.expert))
    total = _psql(args.conteneur, f"SELECT count(*) FROM transactions WHERE client_id = '{CODE_SOCIETE}';")
    print(f"Import terminé : {total} fiches pour {CODE_SOCIETE} ({stats['historique']} d'historique sur 6 mois "
          f"+ {stats['fichier']} fiches de paie du mois {stats['mois']}).")
    print(f"Société affectée à {args.expert} (si l'utilisateur existe). Anomalies attendues : "
          "benchmark/scenario_soutenance/anomalies_attendues.csv")


if __name__ == "__main__":
    sys.exit(main())
