package org.morok.integration;

import org.telegram.messenger.LocaleController;

/** Own identity stays intact when Telegram downloads a new language pack. */
public final class MorokBrand {
    private MorokBrand() { }

    public static String localizedName() {
        LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
        return info != null && info.shortName != null && info.shortName.startsWith("ru") ? "Морок" : "MOROK";
    }
}
