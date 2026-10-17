# Backend — API REST Spring Boot

API de la plateforme SEPA : authentification, gestion des utilisateurs, audit trail, XSD, génération/validation XML, contrôles métier et pont vers le module IA.

## Prérequis
- Java 17+
- Maven 3.9+ (ou `./mvnw`)
- PostgreSQL 16 accessible (voir `docker-compose.yml` à la racine)

## Lancer

```bash
mvn spring-boot:run
```

L'API démarre sur `http://localhost:8080`.
Documentation Swagger : `http://localhost:8080/swagger-ui.html`.

## Compte par défaut
Créé automatiquement au premier démarrage :
- **Utilisateur** : `admin`
- **Mot de passe** : `admin123`

> À changer impérativement en production (`DataSeeder`).

## Principaux endpoints

| Méthode | Chemin                          | Rôle requis                    | Description                    |
|---------|---------------------------------|--------------------------------|--------------------------------|
| POST    | `/api/auth/login`               | public                         | Authentification (JWT)         |
| GET     | `/api/users`                    | ADMINISTRATEUR                 | Liste des utilisateurs         |
| POST    | `/api/users`                    | ADMINISTRATEUR                 | Créer un utilisateur           |
| GET     | `/api/audit`                    | ADMINISTRATEUR, AUDITEUR       | Journal d'audit                |
| POST    | `/api/xsd/import`               | ADMINISTRATEUR, GESTIONNAIRE_RH| Importer un XSD                |
| GET     | `/api/xsd/{id}/elements`        | authentifié                    | Analyser la structure XSD      |
| POST    | `/api/transactions`             | ADMINISTRATEUR, GESTIONNAIRE_RH| Créer une transaction          |
| POST    | `/api/transactions/controler`   | authentifié                    | Contrôles métier RH            |
| POST    | `/api/ai/detect`                | ADMIN, GESTIONNAIRE_RH, VALIDATEUR | Détection IA des montants suspects |
| POST    | `/api/masse/analyse-ia`         | ADMIN, GESTIONNAIRE_RH, VALIDATEUR | Contrôle IA d'une sélection de virements de masse |
| GET     | `/api/admin/ai/health`, `/metrics` | ADMIN, GESTIONNAIRE_RH, VALIDATEUR | État du module IA, métriques, retours utilisateurs |
| POST    | `/api/admin/ai/train?source=`   | ADMINISTRATEUR                 | Réentraîner le modèle (synthetique / reel) |
| POST    | `/api/transactions/generer`     | ADMINISTRATEUR, GESTIONNAIRE_RH| Générer le fichier SEPA XML    |
| GET     | `/api/dashboard/stats`          | authentifié                    | Statistiques du tableau de bord|

## Structure

```
src/main/java/com/zina/sepa/
├── config/       # Sécurité, CORS, OpenAPI, seeder
├── security/     # JWT (service, filtre, userdetails)
├── domain/       # Entités JPA + enums
├── repository/   # Spring Data JPA
├── dto/          # Objets de transfert
├── service/      # Logique métier (audit, XSD, XML, IA…)
└── controller/   # Endpoints REST
```
