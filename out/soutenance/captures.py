"""Captures d'écran de la plateforme (compte de démo admin) via Edge + Playwright."""
import os
import sys
from playwright.sync_api import sync_playwright

BASE = os.environ.get("FRONT_URL", "http://localhost:5173")
USER = os.environ.get("DEMO_USER", "admin")
PWD = os.environ.get("DEMO_PWD", "admin123")
OUT = os.path.join(os.path.dirname(__file__), "captures")
PAGES = [
    ("02_accueil", "/"),
    ("03_kpi", "/kpi"),
    ("04_transactions", "/transactions"),
    ("05_anomalies", "/anomalies"),
    ("06_virements_masse", "/virements-masse"),
    ("07_xsd", "/xsd"),
    ("08_audit", "/audit"),
    ("09_utilisateurs", "/users"),
]

os.makedirs(OUT, exist_ok=True)
only = set(sys.argv[1:])
with sync_playwright() as p:
    browser = p.chromium.launch(channel="msedge")
    page = browser.new_page(viewport={"width": 1600, "height": 900}, device_scale_factor=1.5)
    page.goto(f"{BASE}/login")
    page.wait_for_load_state("networkidle")
    if not only or "01_connexion" in only:
        page.screenshot(path=f"{OUT}/01_connexion.png")
    page.get_by_label("Utilisateur").fill(USER)
    page.get_by_label("Mot de passe").fill(PWD)
    page.get_by_role("button", name="Se connecter").click()
    page.wait_for_url(f"{BASE}/")
    for name, path in PAGES:
        if only and name not in only:
            continue
        page.goto(f"{BASE}{path}")
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(1200)
        page.screenshot(path=f"{OUT}/{name}.png")
        print("ok", name)
    # Aperçu XML d'un fichier de l'historique (consultation seule, rien n'est modifié)
    for name, row in (("06b_masse_conforme", 0), ("06c_masse_erreurs_xsd", 2)):
        if only and name not in only:
            continue
        page.goto(f"{BASE}/virements-masse")
        page.wait_for_load_state("networkidle")
        page.get_by_role("button", name="Revoir").nth(row).click()
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(1500)
        page.screenshot(path=f"{OUT}/{name}.png")
        print("ok", name)
    browser.close()
