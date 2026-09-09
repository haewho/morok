package org.morok.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.morok.settings.MorokAppProfiles;
import org.morok.settings.MorokSettings;
import org.morok.settings.SafetySettings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.PasscodeActivity;

/** Explicit local safety controls. Every switch defaults to upstream behavior. */
public final class MorokSafetyActivity extends BaseFragment {
    private LinearLayout content;

    public MorokSafetyActivity(int account) {
        super();
        currentAccount = account;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokSafetyTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        fragmentView = scroll;
        rebuild();
        return fragmentView;
    }

    @Override public void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        if (content == null) return;
        content.removeAllViews();
        SafetySettings safety = MorokSettings.safety();
        header(R.string.MorokSafetyScreenHeader);
        check(R.string.MorokSafetyScreenPrivacy, safety.protectScreen, () -> applySafety(
                MorokSettings.safety().withProtectScreen(!MorokSettings.safety().protectScreen)));
        info(R.string.MorokSafetyScreenPrivacyInfo);
        header(R.string.MorokSafetyActionsHeader);
        check(R.string.MorokSafetyConfirmCalls, safety.confirmOutgoingCalls, () -> applySafety(
                MorokSettings.safety().withConfirmOutgoingCalls(!MorokSettings.safety().confirmOutgoingCalls)));
        info(R.string.MorokSafetyConfirmCallsInfo);
        boolean activated = isAvailable();
        check(R.string.MorokSafetyNotificationContent,
                !activated || MorokAppProfiles.showsNotificationContent(currentAccount), () -> {
                    if (!isAvailable()) {
                        message(R.string.MorokSafetyLoginRequired);
                        return;
                    }
                    try {
                        MorokAppProfiles.setNotificationContent(currentAccount,
                                !MorokAppProfiles.showsNotificationContent(currentAccount));
                        rebuild();
                    } catch (RuntimeException error) {
                        message(R.string.MorokSafetySaveError);
                    }
                });
        info(activated ? R.string.MorokSafetyNotificationContentInfo : R.string.MorokSafetyLoginRequired);
        header(R.string.MorokSafetyAccessHeader);
        action(R.string.MorokSafetyAppLock, () -> presentFragment(new PasscodeActivity(
                SharedConfig.passcodeHash.isEmpty() ? PasscodeActivity.TYPE_SETUP_CODE
                        : PasscodeActivity.TYPE_ENTER_CODE_TO_MANAGE_SETTINGS)));
        info(R.string.MorokSafetyAppLockInfo);
        action(R.string.MorokSafetyReset, this::confirmReset);
        info(R.string.MorokSafetyFooter);
    }

    private boolean isAvailable() {
        return currentAccount >= 0 && currentAccount < UserConfig.MAX_ACCOUNT_COUNT
                && UserConfig.getInstance(currentAccount).isClientActivated();
    }

    private void applySafety(SafetySettings settings) {
        try {
            MorokSettings.setSafety(settings);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.morokScreenPrivacyChanged);
            rebuild();
        } catch (RuntimeException error) {
            message(R.string.MorokSafetySaveError);
        }
    }

    private void confirmReset() {
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokSafetyReset))
                .setMessage(text(R.string.MorokSafetyResetInfo))
                .setPositiveButton(text(R.string.Reset), (dialog, which) -> applySafety(SafetySettings.DEFAULT))
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void header(int id) {
        HeaderCell cell = new HeaderCell(content.getContext());
        cell.setText(text(id));
        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        content.addView(cell);
    }

    private void check(int id, boolean checked, Runnable action) {
        TextCheckCell cell = new TextCheckCell(content.getContext());
        cell.setColors(Theme.key_windowBackgroundWhiteBlackText, Theme.key_switchTrack,
                Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        cell.setTextAndCheck(text(id), checked, false);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setOnClickListener(view -> action.run());
        content.addView(cell);
    }

    private void action(int id, Runnable action) {
        TextSettingsCell cell = new TextSettingsCell(content.getContext());
        cell.setText(text(id), false);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setOnClickListener(view -> action.run());
        content.addView(cell);
    }

    private void info(int id) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(content.getContext());
        cell.setText(text(id));
        cell.setTextColorByKey(Theme.key_windowBackgroundWhiteGrayText4);
        content.addView(cell);
    }

    private void message(int id) {
        if (getContext() == null) return;
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokSafetyTitle))
                .setMessage(text(id)).setPositiveButton(text(R.string.OK), null).create());
    }

    private static String text(int id) { return LocaleController.getString(id); }
}
