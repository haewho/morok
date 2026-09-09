package org.morok.safety;

import org.morok.settings.MorokSettings;

/** Read-only boundary used by upstream window code. */
public final class MorokScreenPrivacy {
    private MorokScreenPrivacy() {}

    public static boolean enabled() {
        return MorokSettings.safety().protectScreen;
    }
}
