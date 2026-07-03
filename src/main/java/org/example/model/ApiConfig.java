package org.example.model;

/** Per-platform credentials, stored locally in SQLite. */
public class ApiConfig {

    private Platform platform;
    private String publicKey;
    private String secretKey;
    private String jwtToken;
    private boolean enabled = true;

    public ApiConfig() {}

    public ApiConfig(Platform platform, String publicKey, String secretKey, String jwtToken, boolean enabled) {
        this.platform = platform;
        this.publicKey = publicKey;
        this.secretKey = secretKey;
        this.jwtToken = jwtToken;
        this.enabled = enabled;
    }

    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getJwtToken() { return jwtToken; }
    public void setJwtToken(String jwtToken) { this.jwtToken = jwtToken; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
