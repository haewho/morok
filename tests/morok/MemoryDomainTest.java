import org.morok.memory.MemoryKey;
import org.morok.memory.MemoryJournalPolicy;
import org.morok.memory.MemoryExportPolicy;
import org.morok.memory.MemoryPolicy;
import org.morok.memory.MemoryStorageStats;
import org.morok.memory.MemoryTrackingIndex;

import java.util.HashSet;

/** Executable JVM regression checks for account/peer identity and retention boundaries. */
public final class MemoryDomainTest {
    private static int checks;
    private static void expect(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        MemoryKey first = new MemoryKey(100, "channel", 33, 7, 19);
        HashSet<MemoryKey> snapshots = new HashSet<>();
        snapshots.add(first);
        snapshots.add(new MemoryKey(100, "channel", 33, 7, 19));
        expect(snapshots.size() == 1, "Repeated delivery must identify the same saved message");
        snapshots.add(new MemoryKey(200, "channel", 33, 7, 19));
        expect(snapshots.size() == 2, "Reusing an account slot for another user must not match previous cards");
        snapshots.add(new MemoryKey(100, "group", 33, 7, 19));
        snapshots.add(new MemoryKey(100, "channel", 34, 7, 19));
        snapshots.add(new MemoryKey(100, "channel", 33, 7, 20));
        expect(snapshots.size() == 5, "Peer type, peer ID and topic prevent namespace collisions");
        expect(first.dialogId() == -33, "Channel route must use negative dialog ID");
        expect(new MemoryKey(100, "user", 33, 7, 0).dialogId() == 33, "User route must retain positive dialog ID");
        expect(!MemoryPolicy.becomesLatest(10, 20), "A reordered old edit must not overwrite the latest revision");
        expect(MemoryPolicy.becomesLatest(21, 20), "A newer edit becomes the current local version");
        HashSet<String> tombstones = new HashSet<>(); tombstones.add(first.canonical());
        expect(tombstones.contains(new MemoryKey(100, "channel", 33, 7, 19).canonical()), "Replay must find a durable removal identity");
        expect(!tombstones.contains(new MemoryKey(200, "channel", 33, 7, 19).canonical()), "Removal must not cross account boundaries");
        expect(MemoryPolicy.canCopy(MemoryPolicy.MAX_ATTACHMENT_BYTES, 0, 100 * 1024 * 1024), "Attachment at limit is allowed with room");
        expect(!MemoryPolicy.canCopy(MemoryPolicy.MAX_ATTACHMENT_BYTES + 1, 0, 100 * 1024 * 1024), "Oversized original is refused");
        expect(!MemoryPolicy.canCopy(0, 0, 100 * 1024 * 1024), "Empty/truncated original is not retained as a valid file");
        expect(!MemoryPolicy.canCopy(1024, MemoryPolicy.MAX_ACCOUNT_BYTES, 100 * 1024 * 1024), "Account quota prevents disk filling");
        expect(!MemoryPolicy.canCopy(1024, 0, MemoryPolicy.MIN_FREE_BYTES + 1024), "Encryption overhead must preserve free-space reserve");
        expect(MemoryPolicy.canCopy(4L * 1024 * 1024, 32L * 1024 * 1024, 100L * 1024 * 1024,
                        4L * 1024 * 1024, 64L * 1024 * 1024),
                "Configured attachment and automatic-account boundaries allow an exact fit");
        expect(!MemoryPolicy.canCopy(4L * 1024 * 1024 + 1, 0, 100L * 1024 * 1024,
                        4L * 1024 * 1024, 64L * 1024 * 1024),
                "Configured attachment ceiling rejects a larger cached original");
        expect(!MemoryPolicy.canCopy(1024, 64L * 1024 * 1024, 100L * 1024 * 1024,
                        4L * 1024 * 1024, 64L * 1024 * 1024),
                "Configured automatic storage budget refuses additional bytes");
        String editEvent = MemoryJournalPolicy.eventId("edit", first.canonical() + ":revision");
        expect(editEvent.equals(MemoryJournalPolicy.eventId("edit", first.canonical() + ":revision")),
                "Journal replay identity must be deterministic");
        expect(!editEvent.equals(MemoryJournalPolicy.eventId("delete", first.canonical() + ":revision")),
                "Different journal event types cannot collide by construction");
        expect(MemoryJournalPolicy.safeEventId(editEvent), "Journal event identity must have a bounded validated form");
        expect(MemoryJournalPolicy.canAppend(MemoryJournalPolicy.MAX_EVENTS - 1, 128, 256),
                "Last bounded journal slot is accepted");
        expect(!MemoryJournalPolicy.canAppend(MemoryJournalPolicy.MAX_EVENTS, 128, 256),
                "Journal event count cannot grow without a ceiling");
        expect(!MemoryJournalPolicy.canAppend(0, 0, MemoryJournalPolicy.MAX_EVENT_BYTES + 1),
                "Oversized journal event is refused before disk write");
        expect(!MemoryJournalPolicy.canAppend(0, MemoryJournalPolicy.MAX_BYTES, 1),
                "Journal byte budget cannot be exceeded");
        expect(MemoryPolicy.MAX_AUTOMATIC_CARDS < MemoryPolicy.MAX_CARDS,
                "Automatic retention must leave capacity for manual Memory cards");
        expect(MemoryPolicy.MAX_LOCAL_HISTORY_IMPORT > 0
                        && MemoryPolicy.MAX_LOCAL_HISTORY_IMPORT <= MemoryPolicy.MAX_AUTOMATIC_CARDS,
                "One local-history import is positive and bounded by automatic retention");
        MemoryTrackingIndex tracking = new MemoryTrackingIndex();
        expect(!tracking.hasAny(), "An empty Memory index does not claim tracked cards");
        tracking.addPending(java.util.Collections.singleton(first.canonical()));
        expect(tracking.hasAny() && tracking.tracks(first.canonical()),
                "A durably journaled new card is visible to an immediate edit/delete");
        tracking.replacePersisted(java.util.Collections.emptySet());
        expect(tracking.tracks(first.canonical()), "An index read cannot hide a pending journal card");
        tracking.replacePersisted(java.util.Collections.singleton(first.canonical()));
        tracking.completePending(first.canonical());
        expect(tracking.tracks(first.canonical()), "Completed replay remains visible through the persisted index");
        expect(MemoryPolicy.AUTOMATIC_RETENTION_MILLIS == 90L * 24 * 60 * 60 * 1000,
                "Automatic archive retention has a deterministic bounded age");
        long now = 1_000_000_000_000L;
        expect(!MemoryPolicy.automaticExpired(now - MemoryPolicy.AUTOMATIC_RETENTION_MILLIS, now),
                "Automatic card remains available through the retention boundary");
        expect(MemoryPolicy.automaticExpired(now - MemoryPolicy.AUTOMATIC_RETENTION_MILLIS - 1, now),
                "Automatic card expires immediately after the retention boundary");
        long thirtyDays = 30L * 24 * 60 * 60 * 1000;
        expect(!MemoryPolicy.automaticExpired(now - thirtyDays, now, thirtyDays)
                        && MemoryPolicy.automaticExpired(now - thirtyDays - 1, now, thirtyDays),
                "Selected automatic retention has a precise inclusive boundary");
        expect(MemoryExportPolicy.validSelectionSize(1)
                        && MemoryExportPolicy.validSelectionSize(MemoryPolicy.MAX_CARDS)
                        && !MemoryExportPolicy.validSelectionSize(0)
                        && !MemoryExportPolicy.validSelectionSize(MemoryPolicy.MAX_CARDS + 1),
                "Memory export selection stays within the card bound");
        String safeExportName = MemoryExportPolicy.safeFileName("../secret?.txt");
        expect(!safeExportName.contains("/") && !safeExportName.contains("\\") && !safeExportName.startsWith("."),
                "Memory export removes path traversal characters from file names");
        expect(("attachments/1-1-" + safeExportName).equals(
                        MemoryExportPolicy.attachmentEntry(0, 0, "../secret?.txt")),
                "Memory export uses a bounded relative attachment path");
        expect(MemoryExportPolicy.safeFileName("x".repeat(200)).length() == MemoryExportPolicy.MAX_FILE_NAME,
                "Memory export file names are bounded");
        MemoryStorageStats stats = new MemoryStorageStats(1234);
        stats.addCard(true); stats.addCard(false);
        stats.addSnapshot("saved", "shared"); stats.addSnapshot("saved", "shared");
        stats.addSnapshot("not_downloaded", ""); stats.addSnapshot("too_large", "");
        stats.addSnapshot("unavailable", "lost"); stats.addSnapshot("storage_error", "");
        stats.addSnapshot("policy_blocked", ""); stats.addSnapshot("none", "");
        expect(stats.usedBytes == 1234 && stats.cards == 2 && stats.automaticCards == 1 && stats.versions == 8,
                "Storage diagnostics count account bytes, cards and every received version");
        expect(stats.savedOriginals == 2 && stats.uniqueBlobs == 1,
                "Shared encrypted originals are counted once while retaining both references");
        expect(stats.notDownloaded == 1 && stats.tooLarge == 1 && stats.unavailable == 1
                        && stats.storageErrors == 1 && stats.policyBlocked == 1,
                "Storage diagnostics preserve actionable attachment states");
        for (long user : new long[]{0, -1}) {
            try { new MemoryKey(user, "user", 1, 1, 0); throw new AssertionError("Invalid account accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        System.out.println("Memory domain: " + checks + " checks passed");
    }
}
