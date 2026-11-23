# GeoLite2 databases (login history location)

iam-service fills `city`, `country` and `isp` of the login history from **offline** MaxMind GeoLite2 files.
No IP address is ever sent to MaxMind or any other third party: only local `.mmdb` files are read.

Compose mounts this folder read-only at `/geoip` in `iam-service` and expects:

| File | Env var (compose value) | Fills |
|---|---|---|
| `GeoLite2-City.mmdb` | `GEOIP_CITY_DB=/geoip/GeoLite2-City.mmdb` | city, country (English name) |
| `GeoLite2-ASN.mmdb` | `GEOIP_ASN_DB=/geoip/GeoLite2-ASN.mmdb` | isp = autonomous-system organization |

GeoLite2 has no free ISP database; the ASN organization (e.g. the network operator) is the closest free source.

## Download

1. Create a free MaxMind account (https://www.maxmind.com/en/geolite2/signup) and generate a licence key.
2. Install `geoipupdate` (https://github.com/maxmind/geoipupdate) and write a `GeoIP.conf` **outside the repo**:

   ```
   AccountID <your account id>
   LicenseKey <your licence key>
   EditionIDs GeoLite2-City GeoLite2-ASN
   ```

3. `geoipupdate -f /path/to/GeoIP.conf -d deploy/geoip` (run weekly to stay current; MaxMind's licence requires
   updating or deleting outdated copies).
4. Restart iam-service: `docker compose -p erp up -d --no-deps iam-service`. The log shows
   `GeoIP city database loaded: /geoip/GeoLite2-City.mmdb …`.

The licence key and the `.mmdb` files must **not** be committed (`deploy/geoip/*.mmdb` is in `.gitignore`).

## Without the files

iam-service starts normally, logs a WARN per missing file, and the three fields stay `null`. Private, loopback,
link-local, unique-local and CGNAT addresses (e.g. logins through the local Docker gateway) are never looked up.
