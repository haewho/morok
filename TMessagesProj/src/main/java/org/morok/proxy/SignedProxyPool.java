package org.morok.proxy;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict, bounded data-only signed pool format. Independent of Android for testing. */
public final class SignedProxyPool {
    public static final int MAX_BYTES = 96 * 1024;
    public final long version, issued, expires;
    public final String digest;
    public final List<ProxyNode> nodes;

    private SignedProxyPool(long version, long issued, long expires, String digest, List<ProxyNode> nodes) {
        this.version = version; this.issued = issued; this.expires = expires; this.digest = digest;
        this.nodes = Collections.unmodifiableList(nodes);
    }

    public static SignedProxyPool verify(byte[] envelope, Map<String, String> pinnedKeys,
            long nowSeconds, long minimumVersion, String previousDigest) throws Exception {
        if (envelope.length == 0 || envelope.length > MAX_BYTES) throw new IllegalArgumentException("Pool size");
        String text = new String(envelope, StandardCharsets.US_ASCII);
        if (!java.util.Arrays.equals(text.getBytes(StandardCharsets.US_ASCII), envelope)) throw new IllegalArgumentException("Pool encoding");
        String[] lines = text.split("\n", -1);
        if (lines.length != 5 || !lines[4].isEmpty() || !"MOROK-SIGNED-POOL-1".equals(lines[0])
                || !lines[1].matches("[A-Za-z0-9_-]{1,48}")) throw new IllegalArgumentException("Pool envelope");
        String key = pinnedKeys.get(lines[1]);
        if (key == null) throw new IllegalArgumentException("Unknown signing key");
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)));
        if (!(publicKey instanceof RSAPublicKey) || ((RSAPublicKey) publicKey).getModulus().bitLength() < 2048)
            throw new IllegalArgumentException("Weak signing key");
        byte[] payload = Base64.getDecoder().decode(lines[3]);
        if (payload.length > 64 * 1024) throw new IllegalArgumentException("Pool payload size");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey); verifier.update(payload);
        if (!verifier.verify(Base64.getDecoder().decode(lines[2]))) throw new IllegalArgumentException("Pool signature");
        String body = new String(payload, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(body.getBytes(StandardCharsets.UTF_8), payload) || body.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Pool encoding");
        String[] entries = body.split("\n", -1);
        if (entries.length < 6 || entries.length > 55 || !entries[entries.length - 1].isEmpty()
                || !"MOROK-PROXY-POOL-1".equals(entries[0])) throw new IllegalArgumentException("Pool schema");
        long version = number(entries[1], "version"), issued = number(entries[2], "issued"), expires = number(entries[3], "expires");
        if (version < 1 || version < minimumVersion) throw new IllegalArgumentException("Pool rollback");
        if (nowSeconds < 1_700_000_000L || issued > nowSeconds + 300 || expires <= nowSeconds || expires <= issued
                || expires - issued > 31L * 86400) throw new IllegalArgumentException("Pool expired or clock incorrect");
        String digest = ProxyNode.hex(MessageDigest.getInstance("SHA-256").digest(payload));
        if (version == minimumVersion && previousDigest != null && !previousDigest.isEmpty() && !digest.equals(previousDigest))
            throw new IllegalArgumentException("Pool version reused");
        ArrayList<ProxyNode> nodes = new ArrayList<>();
        Set<String> ids = new HashSet<>(), addresses = new HashSet<>();
        for (int i = 4; i < entries.length - 1; i++) {
            String[] row = entries[i].split("\t", -1);
            if (row.length != 3 || !"node".equals(row[0]) || !row[1].matches("[A-Za-z0-9_-]{1,48}") || !ids.add(row[1]))
                throw new IllegalArgumentException("Pool node schema");
            ProxyNode node = ProxyNode.parse(row[2]);
            if (!addresses.add(node.host + ":" + node.port)) throw new IllegalArgumentException("Duplicate pool endpoint");
            nodes.add(node);
        }
        return new SignedProxyPool(version, issued, expires, digest, nodes);
    }

    private static long number(String line, String key) {
        if (!line.matches(key + "\t[0-9]{1,18}")) throw new IllegalArgumentException("Pool schema");
        return Long.parseLong(line.substring(key.length() + 1));
    }
}
