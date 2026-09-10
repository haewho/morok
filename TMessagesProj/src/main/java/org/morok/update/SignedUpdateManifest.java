package org.morok.update;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

/** Strict signed metadata for one MOROK APK. Independent of Android for testing. */
public final class SignedUpdateManifest {
    public static final int MAX_ENVELOPE_BYTES = 48 * 1024;
    public static final long MAX_APK_BYTES = 512L * 1024 * 1024;

    public final long versionCode, issued, expires, size;
    public final String versionName, packageName, abi, sha256, certificateSha256;
    public final String url, telegramBase, commit, changelog, digest;

    private SignedUpdateManifest(long versionCode, String versionName, String packageName, String abi,
            long issued, long expires, long size, String sha256, String certificateSha256, String url,
            String telegramBase, String commit, String changelog, String digest) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.packageName = packageName;
        this.abi = abi;
        this.issued = issued;
        this.expires = expires;
        this.size = size;
        this.sha256 = sha256;
        this.certificateSha256 = certificateSha256;
        this.url = url;
        this.telegramBase = telegramBase;
        this.commit = commit;
        this.changelog = changelog;
        this.digest = digest;
    }

    public static SignedUpdateManifest verify(byte[] envelope, Map<String, String> pinnedKeys,
            long nowSeconds, long minimumVersion, String previousDigest, String expectedPackage,
            String expectedAbi) throws Exception {
        if (envelope == null || envelope.length == 0 || envelope.length > MAX_ENVELOPE_BYTES)
            throw new IllegalArgumentException("Update envelope size");
        String text = new String(envelope, StandardCharsets.US_ASCII);
        if (!java.util.Arrays.equals(text.getBytes(StandardCharsets.US_ASCII), envelope))
            throw new IllegalArgumentException("Update envelope encoding");
        String[] lines = text.split("\n", -1);
        if (lines.length != 5 || !lines[4].isEmpty() || !"MOROK-SIGNED-UPDATE-1".equals(lines[0])
                || !lines[1].matches("[A-Za-z0-9_-]{1,48}"))
            throw new IllegalArgumentException("Update envelope");
        String encodedKey = pinnedKeys == null ? null : pinnedKeys.get(lines[1]);
        if (encodedKey == null) throw new IllegalArgumentException("Unknown update signing key");
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey)));
        if (!(publicKey instanceof RSAPublicKey)
                || ((RSAPublicKey) publicKey).getModulus().bitLength() < 2048)
            throw new IllegalArgumentException("Weak update signing key");
        byte[] payload = Base64.getDecoder().decode(lines[3]);
        if (payload.length == 0 || payload.length > 32 * 1024)
            throw new IllegalArgumentException("Update payload size");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(payload);
        if (!verifier.verify(Base64.getDecoder().decode(lines[2])))
            throw new IllegalArgumentException("Update signature");

        String body = new String(payload, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(body.getBytes(StandardCharsets.UTF_8), payload) || body.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Update payload encoding");
        String[] fields = body.split("\n", -1);
        if (fields.length != 15 || !fields[14].isEmpty() || !"MOROK-UPDATE-1".equals(fields[0]))
            throw new IllegalArgumentException("Update schema");
        long versionCode = number(fields[1], "version_code");
        String versionName = value(fields[2], "version_name", "[A-Za-z0-9._+-]{1,48}");
        String packageName = value(fields[3], "package", "[a-zA-Z][a-zA-Z0-9_.]{2,159}");
        String abi = value(fields[4], "abi", "arm64-v8a");
        long issued = number(fields[5], "issued");
        long expires = number(fields[6], "expires");
        long size = number(fields[7], "size");
        String sha256 = value(fields[8], "sha256", "[0-9a-f]{64}");
        String certificateSha256 = value(fields[9], "certificate_sha256", "[0-9a-f]{64}");
        String url = value(fields[10], "url", ".{1,2048}");
        String telegramBase = value(fields[11], "telegram_base", "[A-Za-z0-9._+-]{1,64}");
        String commit = value(fields[12], "commit", "[0-9a-f]{7,40}");
        String changelogEncoded = value(fields[13], "changelog_b64", "[A-Za-z0-9+/=]{0,16384}");

        if (versionCode < 1 || versionCode < minimumVersion)
            throw new IllegalArgumentException("Update rollback");
        if (!packageName.equals(expectedPackage) || !abi.equals(expectedAbi))
            throw new IllegalArgumentException("Update target mismatch");
        if (nowSeconds < 1_700_000_000L || issued > nowSeconds + 300 || expires <= nowSeconds
                || expires <= issued || expires - issued > 31L * 86400)
            throw new IllegalArgumentException("Update expired or clock incorrect");
        if (size < 1024 * 1024 || size > MAX_APK_BYTES)
            throw new IllegalArgumentException("Update APK size");
        URI parsedUrl = new URI(url);
        if (!"https".equals(parsedUrl.getScheme()) || parsedUrl.getHost() == null || parsedUrl.getHost().isEmpty()
                || parsedUrl.getRawUserInfo() != null || parsedUrl.getRawFragment() != null)
            throw new IllegalArgumentException("Update URL");
        byte[] changelogBytes = Base64.getDecoder().decode(changelogEncoded);
        if (changelogBytes.length > 8192)
            throw new IllegalArgumentException("Update changelog size");
        String changelog = new String(changelogBytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(changelog.getBytes(StandardCharsets.UTF_8), changelogBytes)
                || changelog.indexOf('\u0000') >= 0)
            throw new IllegalArgumentException("Update changelog encoding");
        String digest = hex(MessageDigest.getInstance("SHA-256").digest(payload));
        if (versionCode == minimumVersion && previousDigest != null && !previousDigest.isEmpty()
                && !digest.equals(previousDigest))
            throw new IllegalArgumentException("Update version reused");
        return new SignedUpdateManifest(versionCode, versionName, packageName, abi, issued, expires,
                size, sha256, certificateSha256, url, telegramBase, commit, changelog, digest);
    }

    public boolean isNewerThan(long installedVersionCode) {
        return versionCode > installedVersionCode;
    }

    public static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static long number(String line, String key) {
        if (!line.matches(key + "\\t[0-9]{1,18}")) throw new IllegalArgumentException("Update schema");
        try { return Long.parseLong(line.substring(key.length() + 1)); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Update number", error); }
    }

    private static String value(String line, String key, String pattern) {
        String prefix = key + "\t";
        if (!line.startsWith(prefix)) throw new IllegalArgumentException("Update schema");
        String result = line.substring(prefix.length());
        if (!result.matches(pattern)) throw new IllegalArgumentException("Update field");
        return result;
    }
}
