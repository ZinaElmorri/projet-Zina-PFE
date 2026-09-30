-- ============================================================================
-- Données de démonstration pour le DASHBOARD KPI (PostgreSQL uniquement).
-- Optionnel, NON destructif (INSERT seulement), à lancer À LA MAIN :
--   docker exec -i sepa-postgres psql -U sepa -d sepa_db < backend/demo/demo_dashboard.sql
--
-- Résultat attendu pour le mois dernier :
--   1100 fiches, 15 clients, 2 / 1100 = 0,18 %
-- ============================================================================
BEGIN;

-- 1100 fiches de paie « générées » (APPROUVEE) le mois dernier, réparties sur 15 clients.
INSERT INTO transactions (employe_id, client_id, nom_beneficiaire, iban, bic, montant, devise, statut, cree_le, modifie_le)
SELECT
  'DEMO_EMP' || gs,
  'CLI' || (gs % 15 + 1),
  'Salarie ' || gs,
  'FR76' || lpad((30000000000 + gs)::text, 20, '0'),
  'AGRIFRPP',
  round((1500 + random() * 2500)::numeric, 2),
  'EUR',
  'APPROUVEE',
  date_trunc('month', now()) - interval '1 month' + (floor(random() * 27) || ' days')::interval,
  now()
FROM generate_series(1, 1100) gs;

-- Exactement 2 anomalies bloquantes (IBAN invalide) sur 2 de ces fiches du mois dernier.
INSERT INTO anomalies (signature, type, matricule, iban, montant, periode, details, transaction_id, statut, cree_le, modifie_le)
SELECT
  'DEMO_IBAN_' || t.id,
  'IBAN_INVALIDE',
  t.employe_id,
  t.iban,
  t.montant,
  to_char(date_trunc('month', now()) - interval '1 month', 'YYYY-MM'),
  'IBAN invalide (donnée de démonstration)',
  t.id,
  'OUVERTE',
  now(), now()
FROM transactions t
WHERE t.employe_id IN ('DEMO_EMP1', 'DEMO_EMP2');

-- Historique : ~600 fiches réparties sur les mois -2 à -4 (pour la courbe 12 mois).
INSERT INTO transactions (employe_id, client_id, nom_beneficiaire, iban, bic, montant, devise, statut, cree_le, modifie_le)
SELECT
  'DEMO_HIST' || gs,
  'CLI' || (gs % 15 + 1),
  'Salarie hist ' || gs,
  'FR76' || lpad((40000000000 + gs)::text, 20, '0'),
  'AGRIFRPP',
  round((1500 + random() * 2000)::numeric, 2),
  'EUR',
  'APPROUVEE',
  date_trunc('month', now()) - (interval '1 month' * ((gs % 3) + 2)) + (floor(random() * 20) || ' days')::interval,
  now()
FROM generate_series(1, 600) gs;

COMMIT;
