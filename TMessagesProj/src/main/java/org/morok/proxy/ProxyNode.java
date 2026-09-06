package org.morok.proxy;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Validated connection data. Never log this object or the original link. */
public final class ProxyNode {
    public final String host, username, password, secret;
    public final int port;

    public ProxyNode(String host, int port, String username, String password, String secret) {
        if (host == null || host.isEmpty() || host.length() > 253 || !host.matches("[A-Za-z0-9.:%_-]+")
                || port < 1 || port > 65535) throw new IllegalArgumentException("Invalid proxy address");
        this.host = host.toLowerCase(Locale.ROOT);
        this.port = port;
        this.username = credential(username);
        this.password = credential(password);
        this.secret = normalizeSecret(secret == null ? "" : secret);
        if (!this.secret.isEmpty() && (!this.username.isEmpty() || !this.password.isEmpty()))
            throw new IllegalArgumentException("Mixed proxy authentication");
        if (this.username.isEmpty() && !this.password.isEmpty()) throw new IllegalArgumentException("Missing SOCKS username");
    }

    private static String credential(String value) {
        if (value == null) return "";
        if (value.getBytes(StandardCharsets.UTF_8).length > 255 || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid proxy credential");
        return value;
    }

    private static String normalizeSecret(String value) {
        if (value.isEmpty()) return "";
        if (value.length() > 1024) throw new IllegalArgumentException("Invalid MTProxy secret");
        byte[] raw;
        if (value.matches("[0-9A-Fa-f]+")) {
            if (value.length() % 2 != 0) throw new IllegalArgumentException("Invalid MTProxy secret");
            raw = new byte[value.length() / 2];
            for (int i = 0; i < raw.length; i++) raw[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        } else raw = Base64.getUrlDecoder().decode(value);
        boolean valid = raw.length == 16 || raw.length == 17 && raw[0] == (byte) 0xdd;
        if (raw.length > 17 && raw.length <= 270 && raw[0] == (byte) 0xee) {
            String domain = new String(raw, 17, raw.length - 17, StandardCharsets.US_ASCII);
            valid = domain.matches("[A-Za-z0-9.-]{1,253}") && domain.indexOf('.') > 0;
        }
        if (!valid) throw new IllegalArgumentException("Invalid MTProxy secret");
        return hex(raw);
    }

    public static ProxyNode parse(String input) {
        if (input == null || input.length() > 4096) throw new IllegalArgumentException("Invalid proxy link");
        try {
            URI uri = new URI(input.trim());
            if (uri.getFragment() != null) throw new IllegalArgumentException();
            if ("socks5".equalsIgnoreCase(uri.getScheme())) {
                if (uri.getRawQuery() != null || uri.getPath() != null && !uri.getPath().isEmpty()) throw new IllegalArgumentException();
                String[] auth = uri.getRawUserInfo() == null ? new String[]{"", ""} : uri.getRawUserInfo().split(":", 2);
                String host = uri.getHost();
                if (host != null && host.startsWith("[")) host = host.substring(1, host.length() - 1);
                return new ProxyNode(host, uri.getPort(), decode(auth[0].replace("+", "%2B")),
                        auth.length == 2 ? decode(auth[1].replace("+", "%2B")) : "", "");
            }
            String type;
            if ("tg".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == -1 && (uri.getPath() == null || uri.getPath().isEmpty())) {
                type = uri.getHost();
            } else if ("https".equalsIgnoreCase(uri.getScheme()) && "t.me".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1) {
                type = uri.getPath().startsWith("/") ? uri.getPath().substring(1) : "";
            } else throw new IllegalArgumentException();
            if (!"proxy".equals(type) && !"socks".equals(type) || uri.getUserInfo() != null) throw new IllegalArgumentException();
            Map<String, String> fields = new HashMap<>();
            if (uri.getRawQuery() == null) throw new IllegalArgumentException();
            for (String pair : uri.getRawQuery().split("&", -1)) {
                String[] parts = pair.split("=", 2);
                if (parts.length != 2) throw new IllegalArgumentException();
                String key = decode(parts[0]);
                if (!key.matches("server|port|user|pass|secret") || fields.put(key, decode(parts[1])) != null) throw new IllegalArgumentException();
            }
            if ("proxy".equals(type) && (!fields.containsKey("secret") || fields.get("secret").isEmpty())
                    || "socks".equals(type) && fields.containsKey("secret")) throw new IllegalArgumentException();
            return new ProxyNode(fields.get("server"), Integer.parseInt(fields.get("port")), fields.get("user"), fields.get("pass"), fields.get("secret"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid proxy link");
        }
    }

    private static String decode(String value) throws Exception { return URLDecoder.decode(value, "UTF-8"); }

    public String id() {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest((host + "\0" + port + "\0" + username + "\0" + password + "\0" + secret).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(Character.forDigit((value & 255) >> 4, 16)).append(Character.forDigit(value & 15, 16));
        return out.toString();
    }
}
