# Deployment: Ubuntu Server 24.04, Tomcat 10.1, PostgreSQL 16, Nginx

All four are in the Ubuntu 24.04 archive: `tomcat10` (10.1.x), `postgresql-16`, `nginx` and `openjdk-21-jre-headless`.

## 1. Packages

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless tomcat10 postgresql-16 nginx
java -version          # 21.x
```

## 2. Database

```bash
sudo -u postgres psql <<'SQL'
create role registry login password 'CHANGE-ME';
create database registry owner registry encoding 'UTF8' template template0;
SQL
```

Flyway creates and upgrades the schema when the application starts. No manual DDL is needed.

## 3. Configuration

All settings come from environment variables; secrets are never put in the WAR.

```bash
sudo install -d -m 750 -o root -g tomcat /etc/registry
sudo tee /etc/registry/registry.env >/dev/null <<'ENV'
DB_URL=jdbc:postgresql://127.0.0.1:5432/registry
DB_USER=registry
DB_PASSWORD=CHANGE-ME
JWT_SECRET=<output of: openssl rand -base64 48>
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=<initial admin password, change after first login>
SOAP_USERNAME=integration
SOAP_PASSWORD=<password given to the external system>
ONEID_MODE=http
ONEID_AUTHORIZE_URL=...
ONEID_TOKEN_URL=...
ONEID_USERINFO_URL=...
ONEID_CLIENT_ID=...
ONEID_CLIENT_SECRET=...
ONEID_REDIRECT_URI=https://registry.example/auth/callback
EXTERNAL_REGISTRY_MODE=soap
EXTERNAL_REGISTRY_URL=...
ENV
sudo chmod 640 /etc/registry/registry.env
```

Load the file into Tomcat's systemd unit:

```bash
sudo systemctl edit tomcat10
# add:
# [Service]
# EnvironmentFile=/etc/registry/registry.env
# Environment=TZ=Asia/Tashkent
```

## 4. Deploy the WAR

```bash
mvn -B package -DskipTests                    # builds target/registry.war
sudo systemctl stop tomcat10
sudo rm -rf /var/lib/tomcat10/webapps/ROOT /var/lib/tomcat10/webapps/ROOT.war
sudo cp target/registry.war /var/lib/tomcat10/webapps/ROOT.war
sudo systemctl start tomcat10
curl -s http://127.0.0.1:8080/actuator/health  # {"status":"UP",...}
```

To bind Tomcat to localhost only, set `address="127.0.0.1"` on the HTTP connector in `/etc/tomcat10/server.xml`. That way only Nginx is reachable from outside.

Logs go to `journalctl -u tomcat10` and `/var/log/tomcat10/`.

## 5. Nginx with TLS

Use `deploy/nginx/registry.conf`:
- Change the upstream to `127.0.0.1:8080`.
- Point `root` at the frontend build.
- Add TLS:

```nginx
server {
    listen 443 ssl http2;
    server_name registry.example;
    ssl_certificate     /etc/ssl/registry/fullchain.pem;
    ssl_certificate_key /etc/ssl/registry/privkey.pem;
    add_header Strict-Transport-Security "max-age=31536000" always;
    # ... locations from deploy/nginx/registry.conf ...
}
server { listen 80; server_name registry.example; return 301 https://$host$request_uri; }
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

For extra protection on the SOAP endpoint, restrict `/ws` to the external system's addresses with `allow`/`deny` on top of its HTTP Basic credentials.

## 6. Backups

Run a nightly logical backup and keep 30 days:

```bash
sudo tee /etc/cron.d/registry-backup >/dev/null <<'CRON'
30 1 * * * postgres pg_dump -Fc registry > /var/backups/registry-$(date +\%F).dump && find /var/backups -name 'registry-*.dump' -mtime +30 -delete
CRON
```

Restore with `pg_restore -d registry --clean /var/backups/registry-YYYY-MM-DD.dump`. Test a restore before go-live. For point-in-time recovery, add WAL archiving (`archive_mode=on`, plus pgBackRest or similar).

## 7. Upgrades

Stop Tomcat, replace `ROOT.war`, start Tomcat. Flyway applies new migrations on startup, and the application refuses to start if the schema does not match. Take a `pg_dump` before every upgrade.
