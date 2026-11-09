# ProxGuard

**Supervision Proxmox VE et alertes sur ton téléphone.**

ProxGuard est une application Android autonome qui se connecte à l'API de ton hôte Proxmox VE
pour afficher l'état de l'infrastructure en un coup d'œil et t'alerter avant la panne. Aucun
serveur intermédiaire, aucun compte : la configuration, les mesures et les incidents restent sur
l'appareil.

## Fonctionnalités

### Connexion sécurisée
- URL de l'API, identifiant de jeton (`user@realm!tokenid`) et secret.
- Bouton **Tester** qui interroge `/nodes` et affiche la version Proxmox, le nœud et l'empreinte
  SHA-256 du certificat.
- Option **Accepter le certificat auto-signé**, limitée à l'hôte configuré : le `TrustManager`
  personnalisé n'est actif que pour ce client, jamais globalement. L'empreinte affichée est
  épinglée après validation.
- Stockage de la configuration dans **DataStore**, secret masqué dans l'interface, jamais journalisé.
- Configuration effaçable depuis les réglages.

### Tableau de bord
- Bandeau d'état global : opérationnel, attention, incident ou hôte injoignable.
- Carte hôte : jauges circulaires CPU / RAM / disque racine, swap, charge 1/5/15 min, uptime,
  version PVE, modèle CPU, noyau et mémoire utilisée.
- Sparklines sur 1 h issues de `rrddata` (CPU, mémoire, disque).
- Résumé des invités, des mises à jour APT et du stockage, dernières tâches.
- Courbe locale des dernières 24 h et dernière synchronisation.

### Invités
- Liste unifiée des VMs et conteneurs avec pastille de statut, type, VMID, nœud, CPU, RAM,
  disque, uptime et tags.
- Recherche par nom, VMID, nœud ou tag.
- Filtres par type (VM / conteneur) et par statut (actif / arrêté), tri par statut, nom, CPU,
  RAM ou VMID.
- Détail : statut, jauges, courbes 1 h, configuration résumée, disques, réseau et tâches
  récentes filtrées sur la ressource.

### Alertes
- Règles activables : CPU hôte, RAM hôte, disque racine, invité arrêté, hôte injoignable.
- Seuils réglables de 50 à 99 %.
- Vérification périodique avec **WorkManager** toutes les 15 minutes minimum.
- Notifications locales sur un canal dédié, avec action **Ouvrir** vers l'écran des alertes.
- **Anti-spam** : une alerte par règle et par franchissement. L'état actif est persisté, une
  condition déjà signalée ne redéclenche rien tant qu'elle n'est pas revenue à la normale.
- Historique local des 200 derniers incidents (gravité, date, message).

### Historique local
- Un échantillon JSON par rafraîchissement et par vérification en arrière-plan.
- Fenêtre glissante de 24 h, purge automatique à chaque écriture/lecture.
- Courbes CPU, mémoire, swap, disque, charge et réseau, avec minimum, moyenne et maximum.
- Aucune base de données : fichier `metrics_history.json` dans le stockage privé de l'app.

### Réglages
- Intervalle de rafraîchissement : 15 s, 30 s, 1 min ou 5 min.
- Thème sombre (par défaut), clair ou système.
- Test de connexion et modification du serveur.
- Accès aux règles d'alertes, purge des données locales, réinitialisation complète.
- À propos, version et mentions de confidentialité.

## Architecture

```
android/app/src/main/java/dev/proxboss/proxguard/
├── MainActivity.kt              activité unique, thème dynamique, routage des notifications
├── ProxGuardApp.kt              Application + conteneur de dépendances manuel
├── data/
│   ├── config/                  AppConfig, validation, ConfigStore DataStore
│   ├── api/                     PveClient OkHttp, TrustManager épinglé, ApiException, parsers org.json
│   ├── model/                   modèles immuables (hôte, invités, tâches, disques, stockage…)
│   ├── repo/                    PveRepository (agrégation des endpoints)
│   ├── history/                 MetricStore + codec JSON, purge 24 h
│   └── alerts/                  AlertEvaluator pur, AlertStore persistant
├── work/                        AlertCheckWorker + AlertScheduler (WorkManager)
├── notify/                      canal de notifications et action Ouvrir
├── ui/
│   ├── MainViewModel.kt         état, rafraîchissement, alertes de premier plan
│   ├── theme/                   palette sombre/clair, typographie, formes
│   ├── components/              jauges, sparklines, cartes, bannières, états vides
│   ├── nav/                     NavHost, barre inférieure, transitions
│   └── screens/                 connexion, tableau de bord, invités, détail, alertes, incidents,
│                                historique 24 h, réglages
└── util/                        formats français (tailles, durées, pourcentages, versions, tâches)
```

### Flux de données
1. `ConfigStore` expose un `StateFlow<AppConfig>` depuis DataStore.
2. `MainViewModel` crée un `PveRepository` à partir de la configuration et orchestre les appels.
3. `PveClient` ajoute le jeton `PVEAPIToken=...` et convertit les réponses JSON en modèles via
   `PveParsers` (tolérant aux champs absents).
4. Chaque rafraîchissement alimente `MetricStore` (24 h) et `AlertStore` (évaluation + anti-spam).
5. En arrière-plan, `AlertCheckWorker` applique les mêmes règles et émet les notifications.

### Endpoints Proxmox utilisés
- `/nodes`, `/nodes/{node}/status`
- `/nodes/{node}/rrddata?timeframe=hour&cf=AVERAGE`
- `/cluster/resources?type=vm`
- `/nodes/{node}/qemu`, `/nodes/{node}/lxc` (via les ressources cluster)
- `/nodes/{node}/tasks?limit=20` et `?vmid=…`
- `/nodes/{node}/{type}/{vmid}/config` et `rrddata`
- `/nodes/{node}/apt/update`, `/nodes/{node}/storage`, `/nodes/{node}/disks/list`

Le nœud réel renvoyé par l'API (`pve`) est utilisé partout ; aucun nom n'est codé en dur dans
les requêtes.

## Sécurité et vie privée
- Auth uniquement par jeton d'API ; aucun mot de passe Proxmox n'est demandé ni stocké.
- Secret conservé dans DataStore privé, non inclus dans `toString()`, aucun log de configuration.
- `android:allowBackup="false"` : pas de sauvegarde cloud des identifiants.
- Certificat auto-signé accepté uniquement après activation explicite, pour l'hôte configuré,
  avec vérification d'empreinte SHA-256 et épinglage. Aucun « trust all » global.
- Aucune télémétrie, aucun service externe. Les notifications sont générées localement.

## Stack et versions
- AGP 8.7.3, Kotlin 2.0.21, plugin Compose 2.0.21
- Compose BOM 2024.12.01, Material 3, Navigation Compose
- minSdk 30, compileSdk/targetSdk 35, Java 17
- OkHttp 4.12.0, kotlinx-coroutines 1.9.0, DataStore Preferences 1.1.1
- WorkManager 2.10.0
- Pas de Hilt, Room, Firebase ni autre dépendance lourde.

## Build et tests

Les builds doivent être sérialisés sur cette machine :

```bash
flock /tmp/aoe-build.lock -c "cd /home/dev/projects/proxguard/android && ANDROID_HOME=/home/dev/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug --no-daemon"

flock /tmp/aoe-build.lock -c "cd /home/dev/projects/proxguard/android && ANDROID_HOME=/home/dev/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --no-daemon"
```

APK de debug : `android/app/build/outputs/apk/debug/app-debug.apk`.

Les tests unitaires s'appuient sur de vraies réponses JSON enregistrées avec `curl` dans
`android/app/src/test/resources/` (nœuds, statut, rrddata, ressources cluster, configuration LXC,
tâches, disques, APT, stockage, erreurs 401/500).

Couverture :
- parsing des réponses et résumé de configuration ;
- calcul des seuils, gravités, anti-spam par franchissement et par invité ;
- persistance de l'état d'alerte et résistance à un fichier corrompu ;
- formats français (octets, durées, pourcentages), normalisation d'URL et de jeton ;
- codec JSON des métriques, purge 24 h, déduplication des échantillons ;
- client HTTP : en-tête d'authentification, réponses 401 et 500.

### Icône et visuels
- Icône adaptative générée par `tools/generate_icon.py` (foreground, background, monochrome,
  variantes legacy et ronde) dans `android/app/src/main/res/`.
- Visuels de fiche store 1080×1920 générés par `tools/generate_store_visuals.py` dans `store/`.

```bash
/opt/iconvenv/bin/python tools/generate_icon.py
/opt/iconvenv/bin/python tools/generate_store_visuals.py
```

## Publication locale

```bash
cd /home/dev && /home/dev/.local/bin/store-publish --server http://127.0.0.1:8099 \
  --slug proxguard --name "ProxGuard" \
  --tagline "Supervision Proxmox VE et alertes sur ton téléphone" \
  --category "Outils" --developer Proxboss --accent-color "#22D3EE" \
  --apk /home/dev/projects/proxguard/android/app/build/outputs/apk/debug/app-debug.apk \
  --description "..." --changelog "Version initiale" \
  --screenshot /home/dev/projects/proxguard/store/screenshot-dashboard.png \
  --screenshot /home/dev/projects/proxguard/store/screenshot-guests.png \
  --screenshot /home/dev/projects/proxguard/store/screenshot-alerts.png
```

## À valider sur un téléphone réel
- Rendu et ergonomie des jauges, sparklines et transitions sur différentes tailles d'écran.
- Demande d'autorisation `POST_NOTIFICATIONS` et affichage du canal selon le constructeur.
- Exécution réelle de WorkManager en veille/Doze (certains OEM retardent les tâches périodiques).
- Connexion à un certificat auto-signé réel, vérification de l'empreinte et comportement en cas
  de changement de certificat.
- Rotation d'écran, mode sombre/clair système, police et taille d'affichage importantes.
- Comportement réseau instable, VPN, IPv6 et reverse proxy devant l'API Proxmox.
- Environnement multi-nœuds (l'application supervise actuellement le premier nœud en ligne).
