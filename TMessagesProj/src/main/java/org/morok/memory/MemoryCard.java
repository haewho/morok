package org.morok.memory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/** Local data only; the TL snapshots retain entities, sender, reply and media metadata. */
public final class MemoryCard {
    public final String id;
    public final MemoryKey key;
    public String source;
    public String sender;
    public String note = "";
    public String tags = "";
    public boolean needsReply;
    public boolean completed;
    public boolean deletedInTelegram;
    public long createdAt;
    public long reminderAt;
    public long reminderDeliveredAt;
    public final ArrayList<Snapshot> versions = new ArrayList<>();

    MemoryCard(String id, MemoryKey key) { this.id = id; this.key = key; }

    public Snapshot latest() { return versions.get(versions.size() - 1); }

    public boolean matches(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return true;
        if ((source + "\n" + sender + "\n" + note + "\n" + tags).toLowerCase(Locale.ROOT).contains(needle)) return true;
        for (Snapshot version : versions) {
            if ((version.text + "\n" + version.fileName).toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }

    public boolean hasFile() {
        for (Snapshot snapshot : versions) if (!"none".equals(snapshot.fileState)) return true;
        return false;
    }

    JSONObject toJson() throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", id).put("user", key.userId).put("kind", key.peerKind).put("peer", key.peerId)
                .put("message", key.messageId).put("topic", key.topicId).put("source", source).put("sender", sender)
                .put("note", note).put("tags", tags).put("needsReply", needsReply).put("completed", completed)
                .put("deleted", deletedInTelegram).put("created", createdAt).put("reminder", reminderAt)
                .put("delivered", reminderDeliveredAt);
        JSONArray snapshots = new JSONArray();
        for (Snapshot snapshot : versions) snapshots.put(snapshot.toJson());
        return value.put("versions", snapshots);
    }

    static MemoryCard fromJson(JSONObject value) throws JSONException {
        MemoryCard card = new MemoryCard(value.getString("id"), new MemoryKey(value.getLong("user"),
                value.getString("kind"), value.getLong("peer"), value.getInt("message"), value.getLong("topic")));
        card.source = value.getString("source"); card.sender = value.getString("sender");
        card.note = value.optString("note"); card.tags = value.optString("tags");
        card.needsReply = value.optBoolean("needsReply"); card.completed = value.optBoolean("completed");
        card.deletedInTelegram = value.optBoolean("deleted"); card.createdAt = value.getLong("created");
        card.reminderAt = value.optLong("reminder"); card.reminderDeliveredAt = value.optLong("delivered");
        JSONArray versions = value.getJSONArray("versions");
        for (int i = 0; i < versions.length(); i++) card.versions.add(Snapshot.fromJson(versions.getJSONObject(i)));
        if (card.versions.isEmpty()) throw new JSONException("Memory card has no snapshot");
        return card;
    }

    public static final class Snapshot {
        public String text = "";
        public String serializedMessage = "";
        public String fingerprint = "";
        public long receivedAt;
        public int editedAt;
        public String fileState = "none";
        public String blob = "";
        public String fileName = "";
        public String mime = "application/octet-stream";
        public String sha256 = "";
        public long fileSize;

        public JSONObject toJson() throws JSONException {
            return new JSONObject().put("text", text).put("tl", serializedMessage).put("fingerprint", fingerprint)
                    .put("received", receivedAt).put("edited", editedAt).put("fileState", fileState)
                    .put("blob", blob).put("fileName", fileName).put("mime", mime).put("sha256", sha256).put("size", fileSize);
        }

        public static Snapshot fromJson(JSONObject value) throws JSONException {
            Snapshot snapshot = new Snapshot();
            snapshot.text = value.getString("text"); snapshot.serializedMessage = value.getString("tl");
            snapshot.fingerprint = value.getString("fingerprint"); snapshot.receivedAt = value.getLong("received");
            snapshot.editedAt = value.optInt("edited"); snapshot.fileState = value.getString("fileState");
            snapshot.blob = value.optString("blob"); snapshot.fileName = value.optString("fileName");
            snapshot.mime = value.optString("mime", "application/octet-stream");
            snapshot.sha256 = value.optString("sha256"); snapshot.fileSize = value.optLong("size");
            return snapshot;
        }
    }
}
