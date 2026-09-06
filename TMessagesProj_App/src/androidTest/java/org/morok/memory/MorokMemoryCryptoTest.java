package org.morok.memory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.UUID;

/** Run on an API 26+ device; exercises the actual Android Keystore and Tink primitive used by Memory. */
@RunWith(AndroidJUnit4.class)
public final class MorokMemoryCryptoTest {
    @Test public void encryptionRejectsTamperingAndCrossAccountReplay() throws Exception {
        String alias = "morok.test." + UUID.randomUUID();
        String uri = "android-keystore://" + alias;
        AndroidKeystoreKmsClient.generateNewAeadKey(uri);
        try {
            Aead cipher = new AndroidKeystoreKmsClient().getAead(uri);
            byte[] plain = "a private retained quote".getBytes(StandardCharsets.UTF_8);
            byte[] owner = "morok-memory/v1/100/index".getBytes(StandardCharsets.UTF_8);
            byte[] other = "morok-memory/v1/200/index".getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.encrypt(plain, owner);
            assertFalse(Arrays.equals(plain, ciphertext));
            assertArrayEquals(plain, cipher.decrypt(ciphertext, owner));
            try { cipher.decrypt(ciphertext, other); fail("Cross-account substitution accepted"); }
            catch (java.security.GeneralSecurityException expected) { }
            ciphertext[ciphertext.length - 1] ^= 1;
            try { cipher.decrypt(ciphertext, owner); fail("Tampered ciphertext accepted"); }
            catch (java.security.GeneralSecurityException expected) { }
        } finally {
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null); keys.deleteEntry(alias);
        }
    }
}
