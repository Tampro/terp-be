package com.terp.erp.iam.login;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Offline MaxMind GeoLite2 databases for the login history (manual §3.2 location). Both optional: an empty or
 * unreadable path leaves city / country / ISP null. Only local .mmdb files are read; no IP ever leaves the service.
 */
@ConfigurationProperties(prefix = "erp.geoip")
public record GeoIpProperties(String cityDb, String asnDb) {
}
