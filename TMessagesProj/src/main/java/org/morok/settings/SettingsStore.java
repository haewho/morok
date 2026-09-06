package org.morok.settings;

/** Small storage boundary, also used by JVM-only migration and account-isolation tests. */
public interface SettingsStore {
    int getInt(String key, int fallback);
    boolean getBoolean(String key, boolean fallback);
    String getString(String key, String fallback);
    void save(int schemaVersion, String[] booleanKeys, boolean[] booleanValues,
              String[] stringKeys, String[] stringValues);

    default void saveBooleans(int schemaVersion, String[] keys, boolean[] values) {
        save(schemaVersion, keys, values, new String[0], new String[0]);
    }
}
