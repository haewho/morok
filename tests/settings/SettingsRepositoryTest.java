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
        public String getString(String key, String fallback) { return (String) values.getOrDefault(key, fallback); }
        public void save(int version, String[] keys, boolean[] booleans, String[] stringKeys, String[] strings) {
            if (keys.length != booleans.length || stringKeys.length != strings.length) throw new IllegalArgumentException("Mismatched settings");
            writes++;
            values.put(SettingsRepository.SCHEMA_KEY, version);
            for (int i = 0; i < keys.length; i++) values.put(keys[i], booleans[i]);
            for (int i = 0; i < stringKeys.length; i++) values.put(stringKeys[i], strings[i]);
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
        check(device.getInt(SettingsRepository.SCHEMA_KEY, -1) == 7, "additive schema migration");
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
        check(!defaults.ghostPreset && !defaults.hideTyping && !defaults.hideOnline && !defaults.hideContentRead && !defaults.hideRead
                && !defaults.hideStoryViews && !defaults.markReadOnReply && !defaults.delayGhostSends
                && defaults.normalBehaviorChats.isEmpty()
                && !defaults.hidesTyping() && !defaults.hidesOnline() && !defaults.hidesContentRead()
                && !defaults.hidesRead() && !defaults.hidesStoryViews(), "privacy defaults preserve Telegram behavior");
        SettingsRepository firstAccount = new SettingsRepository(accounts.get(first));
        firstAccount.savePrivacy(PrivacySettings.DEFAULT.withGhostPreset(true));
        check(firstAccount.privacy().hidesTyping(), "ghost preset enables its documented typing policy");
        check(firstAccount.privacy().hidesOnline(), "ghost preset enables its documented online policy");
        check(firstAccount.privacy().hidesContentRead(), "ghost preset enables its documented content-read policy");
        check(firstAccount.privacy().hidesRead(), "ghost preset enables its documented message-read policy");
        check(firstAccount.privacy().hidesStoryViews(), "ghost preset enables its documented story-view policy");
        check(firstAccount.privacy().ghostSendDelaySeconds(99) == 0,
                "ghost delayed send remains off until separately enabled");
        check(!firstAccount.privacy().allowsTypingAction(0), "typing is suppressed by the preset");
        check(firstAccount.privacy().allowsTypingAction(PrivacySettings.ACTION_CANCEL), "typing cancellation remains allowed");
        firstAccount.savePrivacy(firstAccount.privacy().withGhostPreset(false).withHideTyping(true));
        check(firstAccount.privacy().hidesTyping(), "individual typing setting is independent of preset");
        check(!firstAccount.privacy().hidesOnline(), "disabling preset restores online when its individual flag is off");
        check(!firstAccount.privacy().hidesContentRead(), "disabling preset restores content-read when its individual flag is off");
        check(!firstAccount.privacy().hidesRead(), "disabling preset restores message-read when its individual flag is off");
        firstAccount.savePrivacy(firstAccount.privacy().withHideOnline(true));
        check(firstAccount.privacy().hidesOnline(), "individual online setting is independent of preset");
        firstAccount.savePrivacy(firstAccount.privacy().withHideContentRead(true));
        check(firstAccount.privacy().hidesContentRead(), "individual content-read setting is independent of preset");
        firstAccount.savePrivacy(firstAccount.privacy().withHideRead(true));
        check(firstAccount.privacy().hidesRead(), "individual message-read setting is independent of preset");
        firstAccount.savePrivacy(firstAccount.privacy().withHideStoryViews(true).withMarkReadOnReply(true));
        check(firstAccount.privacy().hidesStoryViews(), "individual story-view setting is independent of preset");
        check(firstAccount.privacy().shouldMarkReadOnReply(99), "read-on-reply is active only with hidden reads");
        firstAccount.savePrivacy(firstAccount.privacy().withGhostPreset(true).withDelayGhostSends(true));
        check(firstAccount.privacy().ghostSendDelaySeconds(99) == PrivacySettings.GHOST_SEND_DELAY_SECONDS,
                "explicit delayed send uses the documented Ghost delay");
        firstAccount.savePrivacy(firstAccount.privacy().withNormalBehaviorForChat(99, true).withNormalBehaviorForChat(-1001, true));
        PrivacySettings withExceptions = firstAccount.privacy();
        check(withExceptions.usesNormalBehavior(99) && withExceptions.usesNormalBehavior(-1001), "chat exceptions survive persistence");
        check(withExceptions.allowsTypingAction(99, 0) && withExceptions.allowsReadReceipt(99)
                && withExceptions.allowsContentRead(99) && withExceptions.allowsStoryViewReceipt(99),
                "chat exception restores supported normal behavior");
        check(!withExceptions.shouldMarkReadOnReply(99), "normal-behavior chat does not force a redundant reply read");
        check(withExceptions.ghostSendDelaySeconds(99) == 0,
                "normal-behavior chat bypasses the Ghost send delay");
        check(withExceptions.ghostSendDelaySeconds(100) == PrivacySettings.GHOST_SEND_DELAY_SECONDS,
                "Ghost send delay remains active outside an exception");
        check(!withExceptions.allowsReadReceipt(100), "privacy remains active outside an exception");
        firstAccount.savePrivacy(withExceptions.withNormalBehaviorForChat(99, false));
        check(!firstAccount.privacy().usesNormalBehavior(99) && firstAccount.privacy().usesNormalBehavior(-1001),
                "one chat exception can be removed independently");
        firstAccount.savePrivacy(firstAccount.privacy().withGhostPreset(false));
        check(firstAccount.privacy().ghostSendDelaySeconds(100) == 0,
                "delayed send cannot activate while the Ghost preset is off");
        PrivacySettings secondAccountPrivacy = new SettingsRepository(accounts.get(second)).privacy();
        check(!secondAccountPrivacy.hidesTyping() && !secondAccountPrivacy.hidesContentRead() && !secondAccountPrivacy.hidesRead(),
                "privacy is isolated by stable account");
        firstAccount.resetPrivacy();
        check(firstAccount.privacy().allowsTypingAction(0), "privacy reset restores normal Telegram behavior");
        for (long invalid : new long[] {0, -1, Long.MIN_VALUE}) {
            boolean rejected = false;
            try { SettingsRepository.accountNamespace(invalid); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid unauthenticated identity rejected: " + invalid);
        }

        device.values.put(SettingsRepository.SCHEMA_KEY, 8);
        int oldWrites = device.writes;
        boolean rejected = false;
        try { restarted.resetAppearance(); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected && device.writes == oldWrites, "newer schema cannot be downgraded by reset");
        check(device.getInt(SettingsRepository.SCHEMA_KEY, -1) == 8, "newer schema kept intact");
        System.out.println("PASS: settings defaults, additive migration, restart, resets, stable-account privacy isolation, ghost/story/reply/delay/chat policies, invalid IDs, downgrade refusal");
    }
}
