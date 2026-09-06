import org.morok.proxy.ProxyNode;
import org.morok.proxy.ProxyRetryPolicy;
import org.morok.proxy.SignedProxyPool;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Portable security/regression checks; makes no network calls. */
public final class ProxyCoreTest {
    private static int checks;
    interface Checked { void run() throws Exception; }
    private static void require(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    private static void rejects(Checked action) throws Exception {
        checks++;
        try { action.run(); } catch (IllegalArgumentException | java.security.GeneralSecurityException expected) { return; }
        throw new AssertionError("Expected rejection at check " + checks);
    }
    private static byte[] envelope(String payload, String keyId, KeyPair key) throws Exception {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(key.getPrivate()); signer.update(bytes);
        return ("MOROK-SIGNED-POOL-1\n" + keyId + "\n" + Base64.getEncoder().encodeToString(signer.sign()) + "\n"
                + Base64.getEncoder().encodeToString(bytes) + "\n").getBytes(StandardCharsets.US_ASCII);
    }
    public static void main(String[] args) throws Exception {
        String secret = "0123456789abcdef0123456789abcdef";
        require(ProxyNode.parse("tg://proxy?server=node.example&port=443&secret=" + secret).secret.equals(secret));
        require(ProxyNode.parse("https://t.me/socks?server=node.example&port=1080&user=a&pass=b%26c").password.equals("b&c"));
        require(ProxyNode.parse("socks5://a:b%40c@[::1]:1080").host.equals("::1"));
        require(ProxyNode.parse("socks5://a:p+ass@node.example:1080").password.equals("p+ass"));
        require(ProxyNode.parse("socks5://a:p%2Bass@node.example:1080").password.equals("p+ass"));
        require(ProxyNode.parse("tg://proxy?server=node.example&port=443&secret=dd" + secret).secret.startsWith("dd"));
        String tls = "ee" + secret + "6578616d706c652e636f6d";
        require(ProxyNode.parse("https://t.me/proxy?server=node.example&port=443&secret=" + tls).secret.equals(tls));
        rejects(() -> ProxyNode.parse("tg://proxy?server=node.example&port=0&secret=" + secret));
        rejects(() -> ProxyNode.parse("tg://proxy?server=node.example&server=evil.example&port=443&secret=" + secret));
        rejects(() -> ProxyNode.parse("https://t.me.evil.example/proxy?server=node.example&port=443&secret=" + secret));
        rejects(() -> ProxyNode.parse("tg://proxy?server=node.example&port=443&secret=abc"));
        rejects(() -> ProxyNode.parse("tg://socks?server=node.example&port=1080&script=x"));
        rejects(() -> ProxyNode.parse("socks5://node.example:1080/path"));
        rejects(() -> ProxyNode.parse("tg://proxy?server=node.example&port=443&secret=" + secret + "&pass=x"));
        rejects(() -> ProxyNode.parse("tg://socks?server=node.example&port=65536"));
        rejects(() -> ProxyNode.parse("tg://proxy:443?server=node.example&port=443&secret=" + secret));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair first = generator.generateKeyPair(), second = generator.generateKeyPair();
        Map<String, String> keys = new HashMap<>();
        keys.put("first", Base64.getEncoder().encodeToString(first.getPublic().getEncoded()));
        keys.put("second", Base64.getEncoder().encodeToString(second.getPublic().getEncoded()));
        long now = 1_800_000_000L;
        String payload = "MOROK-PROXY-POOL-1\nversion\t9\nissued\t1799999900\nexpires\t1800086400\n"
                + "node\tprimary\ttg://proxy?server=node.example&port=443&secret=" + secret + "\n";
        byte[] signed = envelope(payload, "first", first);
        SignedProxyPool accepted = SignedProxyPool.verify(signed, keys, now, 8, "");
        require(accepted.nodes.size() == 1 && accepted.version == 9);
        require(SignedProxyPool.verify(signed, keys, now, 9, accepted.digest).digest.equals(accepted.digest));
        require(SignedProxyPool.verify(envelope(payload, "second", second), keys, now, 8, "").version == 9);
        rejects(() -> SignedProxyPool.verify(envelope(payload, "unknown", first), keys, now, 8, ""));
        rejects(() -> SignedProxyPool.verify(envelope(payload, "first", second), keys, now, 8, ""));
        rejects(() -> SignedProxyPool.verify(signed, keys, now, 10, ""));
        rejects(() -> SignedProxyPool.verify(signed, keys, now, 9, "different"));
        rejects(() -> SignedProxyPool.verify(signed, keys, 1_900_000_000L, 8, ""));
        rejects(() -> SignedProxyPool.verify(signed, keys, 1_700_000_000L, 8, ""));
        rejects(() -> SignedProxyPool.verify(signed, keys, 0, 8, ""));
        rejects(() -> SignedProxyPool.verify(new byte[SignedProxyPool.MAX_BYTES + 1], keys, now, 0, ""));
        rejects(() -> SignedProxyPool.verify(envelope(payload.replace("node\tprimary", "script\tprimary"), "first", first), keys, now, 0, ""));
        rejects(() -> SignedProxyPool.verify(envelope(payload + "node\tduplicate\ttg://socks?server=node.example&port=443\n", "first", first), keys, now, 0, ""));
        byte[] changed = signed.clone(); changed[changed.length - 4] = changed[changed.length - 4] == 'A' ? (byte)'B' : (byte)'A';
        rejects(() -> SignedProxyPool.verify(changed, keys, now, 0, ""));
        require(ProxyRetryPolicy.delayMillis(0, 0) == 15000);
        require(ProxyRetryPolicy.delayMillis(1, 0) == 30000);
        require(ProxyRetryPolicy.delayMillis(Integer.MAX_VALUE, 1) == 360000);
        require(ProxyRetryPolicy.delayMillis(-1, -1) == 15000);
        System.out.println("Proxy core: " + checks + " checks passed (no network/device claims).");
    }
}
