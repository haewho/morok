import java.util.HashMap;
import java.util.Map;
import org.morok.settings.AppProfilePresets;
import org.morok.settings.AppProfileState;
import org.morok.settings.AppProfileStateCodec;
import org.morok.settings.AppearanceMode;
import org.morok.settings.AppearanceSettings;
import org.morok.settings.ArchiveSettings;
import org.morok.settings.PrivacySettings;
import org.morok.settings.RoundVideoSettings;
import org.morok.settings.SafetySettings;
import org.morok.settings.SettingsRepository;
import org.morok.settings.SettingsProfile;
import org.morok.settings.SettingsProfileCodec;
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
        check(AppearanceMode.detect(repo.appearance()) == AppearanceMode.TELEGRAM,
                "default settings are the named Telegram mode");
        AppearanceSettings solidMode = AppearanceMode.settings(AppearanceMode.SOLID);
        check(!solidMode.liquidGlass && !solidMode.reducedEffects
                        && AppearanceMode.detect(solidMode) == AppearanceMode.SOLID,
                "solid mode disables glass without changing the animation policy");
        AppearanceSettings minimalMode = AppearanceMode.settings(AppearanceMode.MINIMAL);
        check(!minimalMode.liquidGlass && minimalMode.reducedEffects
                        && AppearanceMode.detect(minimalMode) == AppearanceMode.MINIMAL,
                "minimum-effects mode disables glass and reduces supported animations");
        check(AppearanceMode.detect(new AppearanceSettings(true, true)) == AppearanceMode.CUSTOM,
                "independent switches remain representable as a custom mode");
        boolean invalidModeRejected = false;
        try { AppearanceMode.settings(AppearanceMode.CUSTOM); }
        catch (IllegalArgumentException expected) { invalidModeRejected = true; }
        check(invalidModeRejected, "custom state cannot be applied as an ambiguous named preset");

        device.values.put("future.unrelated", "keep");
        repo.saveAppearance(new AppearanceSettings(false, true));
        SettingsRepository restarted = new SettingsRepository(device);
        check(!restarted.appearance().liquidGlass && restarted.appearance().reducedEffects, "restart persistence");
        check(device.getInt(SettingsRepository.SCHEMA_KEY, -1) == 10, "additive schema migration");
        restarted.resetAppearance();
        check(restarted.appearance().liquidGlass && !restarted.appearance().reducedEffects, "category reset defaults");
        check("keep".equals(device.values.get("future.unrelated")), "reset preserves other settings");

        RoundVideoSettings roundDefaults = restarted.roundVideo();
        check(!roundDefaults.enhanced && RoundVideoSettings.PROFILE_AUTO.equals(roundDefaults.profile),
                "round-video defaults preserve upstream behavior");
        RoundVideoSettings high = roundDefaults.withEnhanced(true).withProfile(RoundVideoSettings.PROFILE_HIGH);
        check(high.desiredResolution(384) == 720 && high.desiredBitrateKbps(1000, 720) == 4000,
                "high round-video profile requests its bounded experiment");
        restarted.saveRoundVideo(high);
        check(new SettingsRepository(device).roundVideo().enhanced
                        && RoundVideoSettings.PROFILE_HIGH.equals(new SettingsRepository(device).roundVideo().profile),
                "round-video profile survives restart");
        restarted.saveRoundVideo(new RoundVideoSettings(true, "future-invalid"));
        check(RoundVideoSettings.PROFILE_AUTO.equals(restarted.roundVideo().profile),
                "invalid round-video profile safely maps to auto");
        restarted.resetRoundVideo();
        check(!restarted.roundVideo().enhanced, "round-video reset restores upstream behavior");

        SafetySettings safetyDefaults = restarted.safety();
        check(!safetyDefaults.protectScreen && !safetyDefaults.confirmOutgoingCalls,
                "safety controls default to upstream screen and call behavior");
        restarted.saveSafety(safetyDefaults.withProtectScreen(true).withConfirmOutgoingCalls(true));
        SafetySettings restartedSafety = new SettingsRepository(device).safety();
        check(restartedSafety.protectScreen && restartedSafety.confirmOutgoingCalls,
                "device safety controls survive restart together");
        restarted.resetSafety();
        check(!restarted.safety().protectScreen && !restarted.safety().confirmOutgoingCalls,
                "safety reset restores upstream behavior");
        check("keep".equals(device.values.get("future.unrelated")),
                "safety save and reset preserve unrelated settings");

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
        ArchiveSettings archiveDefaults = firstAccount.archive();
        check(!archiveDefaults.enabled && archiveDefaults.chats.isEmpty(), "archive defaults to off with an empty allowlist");
        ArchiveSettings archive = archiveDefaults.withEnabled(true).withChat(99, true).withChat(-1001, true);
        firstAccount.saveArchive(archive);
        check(firstAccount.archive().archives(99) && firstAccount.archive().archives(-1001),
                "archive allowlist survives persistence for both peer namespaces");
        check(!firstAccount.archive().archives(100), "archive does not capture a chat outside its allowlist");
        firstAccount.saveArchive(firstAccount.archive().withEnabled(false));
        check(!firstAccount.archive().archives(99) && firstAccount.archive().chats.contains(99L),
                "disabling capture preserves the user's chat selection");
        ArchiveSettings archiveCapped = ArchiveSettings.DEFAULT;
        for (int i = 1; i <= 300; i++) archiveCapped = archiveCapped.withChat(i, true);
        check(archiveCapped.chats.size() == ArchiveSettings.MAX_CHATS && !archiveCapped.chats.contains(300L),
                "archive allowlist remains bounded");
        check(new SettingsRepository(accounts.get(second)).archive().chats.isEmpty(),
                "archive selection is isolated by stable account");
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
        firstAccount.savePrivacy(firstAccount.privacy().withoutNormalBehaviorChats());
        check(firstAccount.privacy().normalBehaviorChats.isEmpty()
                        && firstAccount.privacy().ghostPreset && firstAccount.privacy().hideTyping,
                "all chat exceptions can be cleared without changing privacy controls");
        PrivacySettings capped = PrivacySettings.DEFAULT;
        for (int i = 1; i <= 300; i++) capped = capped.withNormalBehaviorForChat(i, true);
        check(capped.normalBehaviorChats.size() == 256 && !capped.usesNormalBehavior(300),
                "chat exception list remains bounded");
        firstAccount.savePrivacy(firstAccount.privacy().withGhostPreset(false));
        check(firstAccount.privacy().ghostSendDelaySeconds(100) == 0,
                "delayed send cannot activate while the Ghost preset is off");
        PrivacySettings secondAccountPrivacy = new SettingsRepository(accounts.get(second)).privacy();
        check(!secondAccountPrivacy.hidesTyping() && !secondAccountPrivacy.hidesContentRead() && !secondAccountPrivacy.hidesRead(),
                "privacy is isolated by stable account");
        firstAccount.resetPrivacy();
        check(firstAccount.privacy().allowsTypingAction(0), "privacy reset restores normal Telegram behavior");

        PrivacySettings transferablePrivacy = PrivacySettings.DEFAULT.withGhostPreset(true)
                .withHideTyping(true).withHideOnline(true).withHideContentRead(true).withHideRead(true)
                .withHideStoryViews(true).withMarkReadOnReply(true).withDelayGhostSends(true)
                .withNormalBehaviorForChat(123456789, true);
        SettingsProfile profile = new SettingsProfile(new AppearanceSettings(false, true),
                new RoundVideoSettings(true, RoundVideoSettings.PROFILE_HIGH), transferablePrivacy);
        String encodedProfile = SettingsProfileCodec.encode(profile);
        SettingsProfile decodedProfile = SettingsProfileCodec.decode(encodedProfile);
        check(!decodedProfile.appearance.liquidGlass && decodedProfile.appearance.reducedEffects,
                "settings profile round-trips appearance");
        check(decodedProfile.roundVideo.enhanced && RoundVideoSettings.PROFILE_HIGH.equals(decodedProfile.roundVideo.profile),
                "settings profile round-trips round-video policy");
        check(decodedProfile.privacy.ghostPreset && decodedProfile.privacy.hideTyping && decodedProfile.privacy.hideOnline
                        && decodedProfile.privacy.hideContentRead && decodedProfile.privacy.hideRead
                        && decodedProfile.privacy.hideStoryViews && decodedProfile.privacy.markReadOnReply
                        && decodedProfile.privacy.delayGhostSends,
                "settings profile round-trips privacy policy");
        check(decodedProfile.privacy.normalBehaviorChats.isEmpty()
                        && !encodedProfile.contains("123456789") && !encodedProfile.contains("proxy")
                        && !encodedProfile.contains("archive") && !encodedProfile.contains("secret"),
                "transfer file omits account-scoped IDs, archives and connection secrets");
        PrivacySettings destinationPrivacy = PrivacySettings.DEFAULT.withNormalBehaviorForChat(-777, true);
        PrivacySettings appliedPrivacy = decodedProfile.applyPrivacyTo(destinationPrivacy);
        check(appliedPrivacy.usesNormalBehavior(-777) && appliedPrivacy.ghostPreset && appliedPrivacy.hideRead,
                "profile import preserves destination chat exceptions while applying global policy");
        check(encodedProfile.equals(SettingsProfileCodec.encode(decodedProfile)),
                "settings transfer format has deterministic canonical output");

        AppProfileState profileCurrent = new AppProfileState(profile, true, false, true,
                AppProfileState.NETWORK_KEEP);
        AppProfileState normal = AppProfilePresets.create(AppProfilePresets.NORMAL, profileCurrent);
        check(normal.settings.appearance.liquidGlass && !normal.settings.appearance.reducedEffects
                        && normal.autoplayVideos && normal.autoplayGifs && normal.notificationContent
                        && !normal.settings.privacy.ghostPreset,
                "Normal restores Telegram-like appearance, autoplay and standard privacy");
        check(normal.settings.roundVideo.enhanced
                        && RoundVideoSettings.PROFILE_HIGH.equals(normal.settings.roundVideo.profile),
                "Normal preserves the current round-video experiment");
        AppProfileState stealth = AppProfilePresets.create(AppProfilePresets.STEALTH, profileCurrent);
        check(!stealth.settings.appearance.liquidGlass && !stealth.settings.appearance.reducedEffects
                        && stealth.settings.privacy.ghostPreset && !stealth.autoplayVideos
                        && !stealth.autoplayGifs && !stealth.notificationContent,
                "Stealth enables the documented local privacy set and hides notification content");
        AppProfileState work = AppProfilePresets.create(AppProfilePresets.WORK, profileCurrent);
        check(work.settings.appearance.liquidGlass && !work.autoplayVideos && !work.autoplayGifs
                        && work.notificationContent && !work.settings.privacy.ghostPreset,
                "Work keeps normal appearance and notification content while stopping autoplay");
        AppProfileState saver = AppProfilePresets.create(AppProfilePresets.SAVER, profileCurrent);
        check(!saver.settings.appearance.liquidGlass && saver.settings.appearance.reducedEffects
                        && !saver.autoplayVideos && !saver.autoplayGifs && saver.notificationContent,
                "Saver combines minimum effects with disabled autoplay");
        check(AppProfileState.NETWORK_KEEP.equals(normal.networkPolicy)
                        && AppProfileState.NETWORK_KEEP.equals(stealth.networkPolicy)
                        && AppProfileState.NETWORK_KEEP.equals(work.networkPolicy)
                        && AppProfileState.NETWORK_KEEP.equals(saver.networkPolicy),
                "every built-in profile preserves the current network route");
        check(AppProfilePresets.detect(normal) == AppProfilePresets.NORMAL
                        && AppProfilePresets.detect(stealth) == AppProfilePresets.STEALTH
                        && AppProfilePresets.detect(work) == AppProfilePresets.WORK
                        && AppProfilePresets.detect(saver) == AppProfilePresets.SAVER,
                "built-in profiles are detected from their managed state");
        check(AppProfilePresets.detect(profileCurrent) == AppProfilePresets.CUSTOM,
                "a mixed autoplay state is detected as Custom");
        String encodedAppProfile = AppProfileStateCodec.encode(stealth);
        AppProfileState decodedAppProfile = AppProfileStateCodec.decode(encodedAppProfile);
        check(AppProfilePresets.detect(decodedAppProfile) == AppProfilePresets.STEALTH
                        && decodedAppProfile.settings.roundVideo.enhanced
                        && AppProfileState.NETWORK_KEEP.equals(decodedAppProfile.networkPolicy),
                "local app profile round-trips all managed policy");
        check(encodedAppProfile.equals(AppProfileStateCodec.encode(decodedAppProfile)),
                "local app profile format has deterministic canonical output");
        String[] invalidAppProfiles = {
                encodedAppProfile.replace("format=1", "format=2"),
                encodedAppProfile.replace("autoplay.videos=false\n", ""),
                encodedAppProfile.replace("network.policy=keep\n\n", "network.policy=keep\nunknown=x\n\n"),
                encodedAppProfile.replace("autoplay.gifs=false", "autoplay.gifs=1"),
                encodedAppProfile.replace("network.policy=keep", "network.policy=proxy"),
                encodedAppProfile.replace("notifications.content=false\n", "notifications.content=false\nnotifications.content=false\n"),
                "NOT_MOROK\n" + encodedAppProfile
        };
        for (String invalidAppProfile : invalidAppProfiles) {
            boolean invalidRejected = false;
            try { AppProfileStateCodec.decode(invalidAppProfile); }
            catch (IllegalArgumentException expected) { invalidRejected = true; }
            check(invalidRejected, "malformed, incomplete, duplicate, unknown and future app profiles are rejected");
        }
        boolean oversizedAppProfileRejected = false;
        try {
            StringBuilder oversized = new StringBuilder();
            while (oversized.length() <= AppProfileStateCodec.MAX_CHARACTERS) oversized.append('x');
            AppProfileStateCodec.decode(oversized.toString());
        } catch (IllegalArgumentException expected) { oversizedAppProfileRejected = true; }
        check(oversizedAppProfileRejected, "oversized local app profile is rejected before parsing");

        String[] invalidProfiles = {
                encodedProfile.replace("format=1", "format=2"),
                encodedProfile.replace("appearance.liquid_glass=false\n", ""),
                encodedProfile + "privacy.hide_read=true\n",
                encodedProfile.replace("privacy.hide_read=true", "privacy.hide_read=1"),
                encodedProfile.replace("camera.round_video_profile=high", "camera.round_video_profile=ultra"),
                encodedProfile + "account.user_id=42\n",
                "NOT_MOROK\n" + encodedProfile
        };
        for (String invalidProfile : invalidProfiles) {
            boolean invalidRejected = false;
            try { SettingsProfileCodec.decode(invalidProfile); }
            catch (IllegalArgumentException expected) { invalidRejected = true; }
            check(invalidRejected, "malformed, incomplete, duplicate, unknown and future profiles are rejected");
        }
        boolean oversizedRejected = false;
        try {
            StringBuilder oversized = new StringBuilder();
            while (oversized.length() <= SettingsProfileCodec.MAX_CHARACTERS) oversized.append('x');
            SettingsProfileCodec.decode(oversized.toString());
        } catch (IllegalArgumentException expected) { oversizedRejected = true; }
        check(oversizedRejected, "oversized settings transfer is rejected before parsing");

        for (long invalid : new long[] {0, -1, Long.MIN_VALUE}) {
            boolean rejected = false;
            try { SettingsRepository.accountNamespace(invalid); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid unauthenticated identity rejected: " + invalid);
        }

        device.values.put(SettingsRepository.SCHEMA_KEY, 11);
        int oldWrites = device.writes;
        boolean rejected = false;
        try { restarted.resetAppearance(); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected && device.writes == oldWrites, "newer schema cannot be downgraded by reset");
        check(device.getInt(SettingsRepository.SCHEMA_KEY, -1) == 11, "newer schema kept intact");
        System.out.println("PASS: settings defaults, migration, isolation, archive, round-video, safety, privacy, strict transfer, reviewed app profiles, invalid IDs, downgrade refusal");
    }
}
