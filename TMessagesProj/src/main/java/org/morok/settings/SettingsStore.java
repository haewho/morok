package org.morok.settings;

/** Small storage boundary, also used by JVM-only migration and account-isolation tests. */
public interface SettingsStore {
    int getInt(String key, int fallback);
    boolean getBoolean(String key, boolean fallback);
    void saveBooleans(int schemaVersion, String[] keys, boolean[] values);
}
