import java.util.HashMap;
import java.util.Map;
import org.morok.settings.AppearanceSettings;
import org.morok.settings.PrivacySettings;
import org.morok.settings.SettingsRepository;
import org.morok.settings.SettingsStore;

/** Run with plain javac/java; no Android SDK, Gradle, network, account or credentials required. */
public final class SettingsRepositoryTest {
    private static final class Store implements SettingsStore {
        final Map<String, Object> values = new HashMap<>();
        int writes;
        public int getInt(String key, int fallback) { return (int) values.getOrDefault(key, fallback); }
        public boolean getBoolean(String key, boolean fallback) { return (boolean) values.getOrDefault(key, fallback); }
        public void saveBooleans(int version, String[] keys, boolean[] booleans) {
            if (keys.length != booleans.length) throw new IllegalArgumentException("Mismatched settings");
            writes++;
            values.put(SettingsRepository.SCHEMA_KEY, version);
            for (int i = 0; i < keys.length; i++) values.put(keys[i], booleans[i]);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Store device = new Store();
        SettingsRepository repo = new SettingsRepository(device);
        check(repo.appearance().liquidGlass && !repo.appearance().reducedEffects, "fresh install defaults");
        check(device.writes == 0, "reading cannot rewrite settings");

        device.values.put("future.unrelated", "keep");
        repo.saveAppearance(new AppearanceSettings(false, true));
        SettingsRepository restarted = new SettingsRepository(device);
        check(!restarted.appearance().liquidGlass && restarted.appearance().reducedEffects, "restart persistence");
        check(device.getInt(SettingsRepository.SCHEMA_KEY, -1) == 2, "additive schema migration");
        restarted.resetAppearance();
        check(restarted.appearance().liquidGlass && !restarted.appearance().reducedEffects, "category reset defaults");
        check("keep".equals(device.values.get("future.unrelated")), "reset preserves other settings");

        Map<String, Store> accounts = new HashMap<>();
        String first = SettingsRepository.accountNamespace(9223372036854775806L);
        String second = SettingsRepository.accountNamespace(42L);
        accounts.put(first, new Store());
        accounts.put(second, new Store());
        new SettingsRepository(accounts.get(first)).saveAppearance(new AppearanceSettings(false, true));
        check(new SettingsRepository(accounts.get(second)).appearance().liquidGlass, "accounts do not share values");
        check(!new SettingsRepository(accounts.get(SettingsRepository.accountNamespace(9223372036854775806L)))
                .appearance().liquidGlass, "stable identity survives reassigned slots");

        PrivacySettings defaults = new SettingsRepository(accounts.get(second)).privacy();
        check(!defaults.ghostPreset && !defaults.hideTyping && !defaults.hidesTyping(), "privacy defaults preserve Telegram behavior");
        SettingsRepository firstAccount = new SettingsRepository(accounts.get(first));
        firstAccount.savePrivacy(new PrivacySettings(true, false));
        check(firstAccount.privacy().hidesTyping(), "ghost preset enables its documented typing policy");
        check(!firstAccount.privacy().allowsTypingAction(0), "typing is suppressed by the preset");
        check(firstAccount.privacy().allowsTypingAction(PrivacySettings.ACTION_CANCEL), "typing cancellation remains allowed");
        firstAccount.savePrivacy(firstAccount.privacy().withGhostPreset(false).withHideTyping(true));
        check(firstAccount.privacy().hidesTyping(), "individual typing setting is independent of preset");
        check(!new SettingsRepository(accounts.get(second)).privacy().hidesTyping(), "privacy is isolated by stable account");
        firstAccount.resetPrivacy();
        check(firstAccount.privacy().allowsTypingAction(0), "privacy reset restores normal Telegram behavior");
        for (long invalid : new long[] {0, -1, Long.MIN_VALUE}) {
            boolean rejected = false;
            try { SettingsRepository.accountNamespace(invalid); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid unauthenticated identity rejected: " + invalid);
        }

        device.values.put(SettingsRepository.SCHEMA_KEY, 3);
        int oldWrites = device.writes;
        boolean rejected = false;
        try { restarted.resetAppearance(); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected && device.writes == oldWrites, "newer schema cannot be downgraded by reset");
        check(device.getInt(SettingsRepository.SCHEMA_KEY, -1) == 3, "newer schema kept intact");
        System.out.println("PASS: settings defaults, additive migration, restart, resets, stable-account privacy isolation, ghost policy, invalid IDs, downgrade refusal");
    }
}
