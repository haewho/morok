import org.morok.memory.MemoryKey;
import org.morok.memory.MemoryJournalPolicy;
import org.morok.memory.MemoryPolicy;

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
        expect(MemoryPolicy.AUTOMATIC_RETENTION_MILLIS == 90L * 24 * 60 * 60 * 1000,
                "Automatic archive retention has a deterministic bounded age");
        long now = 1_000_000_000_000L;
        expect(!MemoryPolicy.automaticExpired(now - MemoryPolicy.AUTOMATIC_RETENTION_MILLIS, now),
                "Automatic card remains available through the retention boundary");
        expect(MemoryPolicy.automaticExpired(now - MemoryPolicy.AUTOMATIC_RETENTION_MILLIS - 1, now),
                "Automatic card expires immediately after the retention boundary");
        for (long user : new long[]{0, -1}) {
            try { new MemoryKey(user, "user", 1, 1, 0); throw new AssertionError("Invalid account accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        System.out.println("Memory domain: " + checks + " checks passed");
    }
}
