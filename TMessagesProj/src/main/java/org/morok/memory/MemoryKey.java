package org.morok.memory;

import java.util.Objects;

/** Stable identity, independent of Telegram's reusable account slot or cache row. */
public final class MemoryKey {
    public final long userId;
    public final String peerKind;
    public final long peerId;
    public final int messageId;
    public final long topicId;

    public MemoryKey(long userId, String peerKind, long peerId, int messageId, long topicId) {
        if (userId <= 0 || peerId <= 0 || messageId <= 0 || topicId < 0
                || !("user".equals(peerKind) || "group".equals(peerKind) || "channel".equals(peerKind))) {
            throw new IllegalArgumentException("Invalid Memory identity");
        }
        this.userId = userId;
        this.peerKind = peerKind;
        this.peerId = peerId;
        this.messageId = messageId;
        this.topicId = topicId;
    }

    public String canonical() {
        return userId + ":" + peerKind + ":" + peerId + ":" + messageId + ":" + topicId;
    }

    public long dialogId() {
        return "user".equals(peerKind) ? peerId : -peerId;
    }

    @Override public boolean equals(Object other) {
        return other instanceof MemoryKey && canonical().equals(((MemoryKey) other).canonical());
    }

    @Override public int hashCode() { return Objects.hash(userId, peerKind, peerId, messageId, topicId); }
}
