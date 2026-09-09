package org.morok.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.morok.settings.InteractionSettings;
import org.morok.settings.MorokSettings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.SwipeGestureSettingsView;
import org.telegram.ui.ReactionsDoubleTapManageActivity;

/** Account-local gesture controls layered over Telegram's existing reaction picker. */
public final class MorokInteractionsActivity extends BaseFragment {
    private LinearLayout content;

    public MorokInteractionsActivity(int account) {
        currentAccount = account;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokInteractionsTitle));
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
        if (!isAvailable()) {
            info(R.string.MorokInteractionsLoginRequired);
            return;
        }
        InteractionSettings settings = settings();
        header(R.string.MorokInteractionsDoubleTapHeader);
        check(R.string.MorokInteractionsDoubleTapReactions, settings.doubleTapReactionsEnabled,
                () -> apply(settings().withDoubleTapReactionsEnabled(
                        !settings().doubleTapReactionsEnabled)));
        info(R.string.MorokInteractionsDoubleTapInfo);
        action(R.string.MorokInteractionsChooseReaction, quickReactionLabel(), this::openReactionPicker);
        info(R.string.MorokInteractionsChooseReactionInfo);
        header(R.string.MorokInteractionsChatListHeader);
        SwipeGestureSettingsView swipe = new SwipeGestureSettingsView(content.getContext(), currentAccount);
        swipe.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        content.addView(swipe, new LinearLayout.LayoutParams(-1, -2));
        info(R.string.MorokInteractionsChatListInfo);
        action(R.string.MorokInteractionsReset, null, this::confirmReset);
        info(R.string.MorokInteractionsFooter);
    }

    private boolean isAvailable() {
        return currentAccount >= 0 && currentAccount < UserConfig.MAX_ACCOUNT_COUNT
                && UserConfig.getInstance(currentAccount).isClientActivated();
    }

    private InteractionSettings settings() {
        try { return MorokSettings.interactions(currentAccount); }
        catch (RuntimeException unavailableSettings) { return InteractionSettings.DEFAULT; }
    }

    private void apply(InteractionSettings settings) {
        try {
            MorokSettings.setInteractions(currentAccount, settings);
            rebuild();
        } catch (RuntimeException error) {
            message(R.string.MorokInteractionsSaveError);
        }
    }

    private void openReactionPicker() {
        ReactionsDoubleTapManageActivity picker = new ReactionsDoubleTapManageActivity();
        picker.setCurrentAccount(currentAccount);
        presentFragment(picker);
    }

    private String quickReactionLabel() {
        String reaction = MediaDataController.getInstance(currentAccount).getDoubleTapReaction();
        if (reaction == null || reaction.isEmpty()) return text(R.string.MorokInteractionsTelegramDefault);
        return reaction.startsWith("animated_") ? text(R.string.MorokInteractionsCustomEmoji) : reaction;
    }

    private void confirmReset() {
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokInteractionsReset))
                .setMessage(text(R.string.MorokInteractionsResetInfo))
                .setPositiveButton(text(R.string.Reset), (dialog, which) -> apply(InteractionSettings.DEFAULT))
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void header(int id) {
        HeaderCell cell = new HeaderCell(content.getContext());
        cell.setText(text(id));
        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        content.addView(cell);
    }

    private void check(int id, boolean checked, Runnable callback) {
        TextCheckCell cell = new TextCheckCell(content.getContext());
        cell.setColors(Theme.key_windowBackgroundWhiteBlackText, Theme.key_switchTrack,
                Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        cell.setTextAndCheck(text(id), checked, false);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setOnClickListener(view -> callback.run());
        content.addView(cell);
    }

    private void action(int id, String value, Runnable callback) {
        TextSettingsCell cell = new TextSettingsCell(content.getContext());
        if (value == null) cell.setText(text(id), false);
        else cell.setTextAndValue(text(id), value, true);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setOnClickListener(view -> callback.run());
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
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokInteractionsTitle))
                .setMessage(text(id)).setPositiveButton(text(R.string.OK), null).create());
    }

    private static String text(int id) { return LocaleController.getString(id); }
}
