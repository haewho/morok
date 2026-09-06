import java.util.HashMap;
import java.util.Map;
import org.morok.settings.AppearanceSettings;
import org.morok.settings.SettingsRepository;
import org.morok.settings.SettingsStore;

/** Run with plain javac/java; no Android SDK, Gradle, network, account or credentials required. */
public final class SettingsRepositoryTest {
    private static final class Store implements SettingsStore {
        final Map<String, Object> values = new HashMap<>();
        int writes;
        public int getInt(String key, int fallback) { return (int) values.getOrDefault(key, fallback); }
        public boolean getBoolean(String key, boolean fallback) { return (boolean) values.getOrDefault(key, fallback); }
        public void save(int version, String a, boolean av, String b, boolean bv) {
            writes++;
            values.put(SettingsRepository.SCHEMA_KEY, version);
            values.put(a, av);
            values.put(b, bv);
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
        check(device.getInt(SettingsRepository.SCHEMA_KEY, -1) == 1, "additive v0 to v1 migration");
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
        for (long invalid : new long[] {0, -1, Long.MIN_VALUE}) {
            boolean rejected = false;
            try { SettingsRepository.accountNamespace(invalid); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid unauthenticated identity rejected: " + invalid);
        }

        device.values.put(SettingsRepository.SCHEMA_KEY, 2);
        int oldWrites = device.writes;
        boolean rejected = false;
        try { restarted.resetAppearance(); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected && device.writes == oldWrites, "newer schema cannot be downgraded by reset");
        check(device.getInt(SettingsRepository.SCHEMA_KEY, -1) == 2, "newer schema kept intact");
        System.out.println("PASS: settings defaults, additive migration, restart, category reset, stable-account isolation, invalid IDs, downgrade refusal");
    }
}
