import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.morok.update.SignedUpdateManifest;

public final class SignedUpdateManifestTest {
    private static final long NOW = 1_800_000_000L;
    private static KeyPair keyPair;
    private static Map<String, String> keys;

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        keys = new HashMap<>();
        keys.put("release-1", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        byte[] envelope = envelope(payload(70399, "io.github.haewho.morok", "https://updates.example/morok.apk",
                NOW - 60, NOW + 86400, "Fixed preview\nImproved updater"));
        SignedUpdateManifest manifest = verify(envelope, 0, "", "io.github.haewho.morok");
        check(manifest.versionCode == 70399 && manifest.isNewerThan(70389), "version");
        check("Fixed preview\nImproved updater".equals(manifest.changelog), "changelog");
        check(manifest.sha256.length() == 64 && manifest.certificateSha256.length() == 64, "digests");
        check(verify(envelope, manifest.versionCode, manifest.digest, manifest.packageName).versionCode == 70399,
                "same version and digest replay");

        byte[] tampered = envelope.clone();
        tampered[tampered.length - 8] ^= 1;
        rejects(() -> verify(tampered, 0, "", "io.github.haewho.morok"));
        rejects(() -> verify(envelope, 70400, "", "io.github.haewho.morok"));
        rejects(() -> verify(envelope, 70399, repeat('0', 64), "io.github.haewho.morok"));
        rejects(() -> verify(envelope, 0, "", "io.github.other"));
        rejects(() -> verify(envelope(payload(70399, "io.github.haewho.morok", "http://updates.example/app.apk",
                NOW - 60, NOW + 86400, "x")), 0, "", "io.github.haewho.morok"));
        rejects(() -> verify(envelope(payload(70399, "io.github.haewho.morok", "https://updates.example/app.apk",
                NOW - 86400, NOW - 1, "x")), 0, "", "io.github.haewho.morok"));
        System.out.println("PASS: signed update manifest signature, schema, target, expiry and anti-rollback");
    }

    private static SignedUpdateManifest verify(byte[] envelope, long minimum, String digest, String packageName)
            throws Exception {
        return SignedUpdateManifest.verify(envelope, keys, NOW, minimum, digest, packageName, "arm64-v8a");
    }

    private static String payload(long version, String packageName, String url, long issued, long expires,
            String changelog) {
        String hash = repeat('a', 64), certificate = repeat('b', 64);
        return "MOROK-UPDATE-1\n"
                + "version_code\t" + version + "\n"
                + "version_name\t12.10.2\n"
                + "package\t" + packageName + "\n"
                + "abi\tarm64-v8a\n"
                + "issued\t" + issued + "\n"
                + "expires\t" + expires + "\n"
                + "size\t80707402\n"
                + "sha256\t" + hash + "\n"
                + "certificate_sha256\t" + certificate + "\n"
                + "url\t" + url + "\n"
                + "telegram_base\t12.10.1-7038\n"
                + "commit\t6a63205a86ea3d04ab4191479a93bfd6a5458294\n"
                + "changelog_b64\t" + Base64.getEncoder().encodeToString(changelog.getBytes(StandardCharsets.UTF_8)) + "\n";
    }

    private static byte[] envelope(String payload) throws Exception {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(bytes);
        String result = "MOROK-SIGNED-UPDATE-1\nrelease-1\n"
                + Base64.getEncoder().encodeToString(signer.sign()) + "\n"
                + Base64.getEncoder().encodeToString(bytes) + "\n";
        return result.getBytes(StandardCharsets.US_ASCII);
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void rejects(Throwing action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalArgumentException expected) { }
    }

    private interface Throwing { void run() throws Exception; }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
