package com.minerva.core.journiv;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.ZoneId;

/** Validated configuration for the optional Journiv integration. */
@Component
public class JournivSettings {
    private final String url;
    private final String email;
    private final String password;
    private final ZoneId zone;

    /**
     * Creates settings from the application environment.
     *
     * @param url Journiv server base URL, or blank to disable integration
     * @param email dedicated account email
     * @param password dedicated account password
     * @param zone journal date time zone
     */
    public JournivSettings(
            @Value("${minerva.journiv.url:}") String url,
            @Value("${minerva.journiv.email:}") String email,
            @Value("${minerva.journiv.password:}") String password,
            @Value("${minerva.journiv.zone:UTC}") String zone
    ) {
        this.url = url.strip().replaceAll("/+$", "");
        this.email = email.strip();
        this.password = password;
        this.zone = ZoneId.of(zone);
        if (enabled()) {
            URI uri = URI.create(this.url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Journiv URL must be an HTTP(S) base URL without credentials");
            }
            if (this.email.isBlank() || this.password.isBlank()) {
                throw new IllegalArgumentException("Journiv email and password are required when a URL is configured");
            }
        }
    }

    /** @return whether the integration is configured */
    public boolean enabled() { return !url.isBlank(); }

    /** @return configured server base URL */
    public String url() { return url; }

    /** @return dedicated account email */
    public String email() { return email; }

    /** @return dedicated account password */
    public String password() { return password; }

    /** @return time zone used for daily entries */
    public ZoneId zone() { return zone; }

    /** @return stable server and account identity, without the password */
    public String account() { return url + "\n" + email; }
}
