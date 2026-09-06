package org.morok.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.morok.proxy.MorokProxyManager;
import org.morok.settings.AppProfilePresets;
import org.morok.settings.AppProfileState;
import org.morok.settings.AppearanceMode;
import org.morok.settings.MorokAppProfiles;
import org.morok.settings.PrivacySettings;
import org.morok.settings.RoundVideoSettings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/** Reviewed local whole-app presets with an explicit preview before every apply. */
public final class MorokProfilesActivity extends BaseFragment {
    private static final int HEADER = 0, ACTION = 1, INFO = 2;
    private static final int NORMAL = 10, STEALTH = 11, WORK = 12, SAVER = 13;
    private static final int SAVE_CUSTOM = 20, APPLY_CUSTOM = 21, RESTORE_PREVIOUS = 22;

    private final ArrayList<Row> rows = new ArrayList<>();
    private Adapter adapter;

    public MorokProfilesActivity(int account) {
        super();
        currentAccount = account;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokAppProfilesTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        fragmentView = frame;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setVerticalScrollBarEnabled(false);
        list.setItemAnimator(null);
        list.setAdapter(adapter = new Adapter());
        frame.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        list.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= rows.size()) return;
            int id = rows.get(position).id;
            if (!isAvailable()) {
                message(R.string.MorokAppProfilesLoginRequired);
                return;
            }
            if (id >= NORMAL && id <= SAVER) {
                int preset = id - NORMAL;
                preview(profileName(preset), AppProfilePresets.create(preset, MorokAppProfiles.current(currentAccount)));
            } else if (id == SAVE_CUSTOM) {
                confirmSaveCustom();
            } else if (id == APPLY_CUSTOM) {
                AppProfileState custom = MorokAppProfiles.custom(currentAccount);
                if (custom == null) message(R.string.MorokAppProfilesNoCustom);
                else preview(text(R.string.MorokAppProfileCustom), custom);
            } else if (id == RESTORE_PREVIOUS) {
                AppProfileState previous = MorokAppProfiles.previous(currentAccount);
                if (previous == null) message(R.string.MorokAppProfilesNoPrevious);
                else preview(text(R.string.MorokAppProfilePrevious), previous);
            }
        });
        rebuildRows();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuildRows();
    }

    private boolean isAvailable() {
        return currentAccount >= 0 && currentAccount < UserConfig.MAX_ACCOUNT_COUNT
                && UserConfig.getInstance(currentAccount).isClientActivated();
    }

    private void rebuildRows() {
        rows.clear();
        if (isAvailable()) {
            AppProfileState current = null;
            try { current = MorokAppProfiles.current(currentAccount); } catch (RuntimeException ignored) {}
            rows.add(new Row(INFO, 0, current == null ? text(R.string.MorokAppProfilesLoginStatus)
                    : LocaleController.formatString(R.string.MorokAppProfilesCurrent,
                            profileName(AppProfilePresets.detect(current)))));
        } else {
            rows.add(new Row(INFO, 0, text(R.string.MorokAppProfilesLoginStatus)));
        }
        rows.add(new Row(HEADER, 0, text(R.string.MorokAppProfilesBuiltIn)));
        rows.add(new Row(ACTION, NORMAL, text(R.string.MorokAppProfileNormal)));
        rows.add(new Row(ACTION, STEALTH, text(R.string.MorokAppProfileStealth)));
        rows.add(new Row(ACTION, WORK, text(R.string.MorokAppProfileWork)));
        rows.add(new Row(ACTION, SAVER, text(R.string.MorokAppProfileSaver)));
        rows.add(new Row(HEADER, 0, text(R.string.MorokAppProfileCustom)));
        rows.add(new Row(ACTION, SAVE_CUSTOM, text(R.string.MorokAppProfilesSaveCustom)));
        rows.add(new Row(ACTION, APPLY_CUSTOM, text(R.string.MorokAppProfilesApplyCustom)));
        rows.add(new Row(ACTION, RESTORE_PREVIOUS, text(R.string.MorokAppProfilesRestorePrevious)));
        rows.add(new Row(INFO, 0, text(R.string.MorokAppProfilesLocalOnly)));
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void confirmSaveCustom() {
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokAppProfilesSaveCustom))
                .setMessage(text(R.string.MorokAppProfilesSaveCustomInfo))
                .setPositiveButton(text(R.string.Save), (dialog, which) -> {
                    try {
                        MorokAppProfiles.saveCustom(currentAccount);
                        message(R.string.MorokAppProfilesCustomSaved);
                        rebuildRows();
                    } catch (RuntimeException error) {
                        message(R.string.MorokAppProfilesApplyError);
                    }
                }).setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void preview(String name, AppProfileState state) {
        showDialog(new AlertDialog.Builder(getContext()).setTitle(name)
                .setMessage(previewText(state))
                .setPositiveButton(text(R.string.MorokAppProfilesApply), (dialog, which) -> apply(state))
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void apply(AppProfileState state) {
        try {
            MorokAppProfiles.apply(currentAccount, state, getParentActivity());
            message(R.string.MorokAppProfilesApplied);
            rebuildRows();
        } catch (RuntimeException error) {
            message(R.string.MorokAppProfilesApplyError);
        }
    }

    private String previewText(AppProfileState state) {
        StringBuilder result = new StringBuilder();
        line(result, R.string.MorokAppProfilesAppearance, appearanceName(AppearanceMode.detect(state.settings.appearance)));
        line(result, R.string.MorokLiquidGlass, enabled(state.settings.appearance.liquidGlass));
        line(result, R.string.MorokReducedEffects, enabled(state.settings.appearance.reducedEffects));
        line(result, R.string.MorokAppProfilesAutoplayVideo, enabled(state.autoplayVideos));
        line(result, R.string.MorokAppProfilesAutoplayGifs, enabled(state.autoplayGifs));
        line(result, R.string.MorokAppProfilesPrivacy, privacyName(state.settings.privacy));
        line(result, R.string.MorokAppProfilesNotificationContent,
                text(state.notificationContent ? R.string.MorokAppProfilesShown : R.string.MorokAppProfilesHidden));
        line(result, R.string.MorokRoundVideoTitle, roundVideoName(state.settings.roundVideo));
        line(result, R.string.MorokAppProfilesNetwork,
                LocaleController.formatString(R.string.MorokAppProfilesNetworkKept, networkMode()));
        result.append("\n\n").append(text(R.string.MorokAppProfilesPreviewFooter));
        return result.toString();
    }

    private static void line(StringBuilder output, int title, String value) {
        if (output.length() != 0) output.append('\n');
        output.append(LocaleController.formatString(R.string.MorokAppProfilesValue, text(title), value));
    }

    private static String profileName(int preset) {
        if (preset == AppProfilePresets.NORMAL) return text(R.string.MorokAppProfileNormal);
        if (preset == AppProfilePresets.STEALTH) return text(R.string.MorokAppProfileStealth);
        if (preset == AppProfilePresets.WORK) return text(R.string.MorokAppProfileWork);
        if (preset == AppProfilePresets.SAVER) return text(R.string.MorokAppProfileSaver);
        return text(R.string.MorokAppProfileCustom);
    }

    private static String appearanceName(int mode) {
        if (mode == AppearanceMode.TELEGRAM) return text(R.string.MorokAppearanceModeTelegram);
        if (mode == AppearanceMode.SOLID) return text(R.string.MorokAppearanceModeSolid);
        if (mode == AppearanceMode.MINIMAL) return text(R.string.MorokAppearanceModeMinimal);
        return text(R.string.MorokAppearanceModeCustom);
    }

    private static String privacyName(PrivacySettings privacy) {
        if (privacy.ghostPreset) return text(R.string.MorokGhostPreset);
        if (privacy.hidesTyping() || privacy.hidesOnline() || privacy.hidesContentRead()
                || privacy.hidesRead() || privacy.hidesStoryViews() || privacy.markReadOnReply
                || privacy.delayGhostSends) return text(R.string.MorokPrivacyCustomStatus);
        return text(R.string.MorokAppProfilesStandard);
    }

    private static String roundVideoName(RoundVideoSettings roundVideo) {
        if (!roundVideo.enhanced) return text(R.string.MorokAppProfilesTelegramDefault);
        return roundVideo.profile;
    }

    private static String networkMode() {
        MorokProxyManager.Mode mode = MorokProxyManager.getInstance().mode();
        if (mode == MorokProxyManager.Mode.AUTO) return text(R.string.MorokAppProfilesNetworkAuto);
        if (mode == MorokProxyManager.Mode.MANUAL) return text(R.string.MorokAppProfilesNetworkManual);
        return text(R.string.MorokAppProfilesNetworkDirect);
    }

    private static String enabled(boolean value) {
        return text(value ? R.string.MorokSettingsTransferEnabled : R.string.MorokSettingsTransferDisabled);
    }

    private void message(int id) {
        if (getContext() == null) return;
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokAppProfilesTitle))
                .setMessage(text(id)).setPositiveButton(text(R.string.OK), null).create());
    }

    private static String text(int id) { return LocaleController.getString(id); }

    private static final class Row {
        final int type, id;
        final String title;
        Row(int type, int id, String title) { this.type = type; this.id = id; this.title = title; }
    }

    private final class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return rows.size(); }
        @Override public int getItemViewType(int position) { return rows.get(position).type; }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return holder.getItemViewType() == ACTION; }

        @NonNull
        @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View view = type == HEADER ? new HeaderCell(parent.getContext())
                    : type == INFO ? new TextInfoPrivacyCell(parent.getContext())
                    : new TextSettingsCell(parent.getContext());
            if (type != INFO) view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(view);
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (row.type == HEADER) {
                ((HeaderCell) holder.itemView).setText(row.title);
                ((HeaderCell) holder.itemView).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            } else if (row.type == INFO) {
                ((TextInfoPrivacyCell) holder.itemView).setText(row.title);
                ((TextInfoPrivacyCell) holder.itemView).setTextColorByKey(Theme.key_windowBackgroundWhiteGrayText4);
            } else {
                ((TextSettingsCell) holder.itemView).setText(row.title, true);
                ((TextSettingsCell) holder.itemView).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
        }
    }
}
