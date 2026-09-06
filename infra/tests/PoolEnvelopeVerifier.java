import org.morok.proxy.SignedProxyPool;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;

/** Cross-language signer check against the exact client verifier. */
public final class PoolEnvelopeVerifier {
    public static void main(String[] args) throws Exception {
        SignedProxyPool pool = SignedProxyPool.verify(Files.readAllBytes(Paths.get(args[0])),
                Collections.singletonMap("test", Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(args[1])))),
                System.currentTimeMillis() / 1000, 0, "");
        if (pool.nodes.size() != 2 || pool.version != 1) throw new AssertionError("Wrong signed payload");
        System.out.println("Python/OpenSSL signer -> Java client verification passed.");
    }
}
