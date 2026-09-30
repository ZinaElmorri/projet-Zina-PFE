# Frontend — Application React

Interface de la plateforme SEPA : authentification, tableau de bord, gestion des transactions, import XSD, journal d'audit et gestion des utilisateurs.

## Prérequis
- Node.js 20+

## Installation & lancement

```bash
npm install
cp .env.example .env    # ajuster VITE_API_URL si besoin
npm run dev
```

Application disponible sur `http://localhost:5173`.

## Connexion par défaut
- Utilisateur : `admin`
- Mot de passe : `admin123`

## Structure

```
src/
├── api/client.js          # Instance Axios + intercepteurs JWT
├── context/AuthContext.jsx# Contexte d'authentification
├── components/            # Layout, route protégée
├── pages/                 # Login, Dashboard, Transactions, Xsd, Audit, Users
├── App.jsx                # Routage
├── main.jsx               # Point d'entrée
└── styles.css             # Styles globaux
```

## Pages

| Route           | Description                                      |
|-----------------|--------------------------------------------------|
| `/login`        | Connexion (mot de passe + reconnaissance faciale)|
| `/`             | Tableau de bord (statistiques)                   |
| `/transactions` | CRUD transactions, contrôles métier, analyse IA  |
| `/xsd`          | Import et analyse des schémas XSD                 |
| `/audit`        | Journal d'audit                                  |
| `/users`        | Gestion des utilisateurs (Administrateur)        |
