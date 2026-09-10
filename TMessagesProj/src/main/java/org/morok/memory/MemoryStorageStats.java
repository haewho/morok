package org.morok.memory;

import java.util.HashSet;

/** Bounded account-local counters shown by the Memory storage screen. */
public final class MemoryStorageStats {
    public final long usedBytes;
    public int cards;
    public int automaticCards;
    public int versions;
    public int savedOriginals;
    public int uniqueBlobs;
    public int notDownloaded;
    public int tooLarge;
    public int unavailable;
    public int storageErrors;
    public int policyBlocked;
    private final HashSet<String> blobs = new HashSet<>();

    public MemoryStorageStats(long usedBytes) {
        this.usedBytes = Math.max(0, usedBytes);
    }

    public void addCard() {
        addCard(false);
    }

    public void addCard(boolean automatic) {
        cards++;
        if (automatic) automaticCards++;
    }

    public void addSnapshot(String state, String blob) {
        versions++;
        if ("saved".equals(state)) {
            savedOriginals++;
            if (blob != null && !blob.isEmpty() && blobs.add(blob)) uniqueBlobs++;
        } else if ("not_downloaded".equals(state)) {
            notDownloaded++;
        } else if ("too_large".equals(state)) {
            tooLarge++;
        } else if ("unavailable".equals(state)) {
            unavailable++;
        } else if ("storage_error".equals(state)) {
            storageErrors++;
        } else if ("policy_blocked".equals(state)) {
            policyBlocked++;
        }
    }
}
