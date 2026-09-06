package org.morok.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.morok.appearance.MorokAppearance;
import org.morok.settings.AppearanceSettings;
import org.morok.settings.MorokSettings;
import org.morok.settings.PrivacySettings;
import org.morok.settings.RoundVideoSettings;
import org.morok.settings.SettingsProfile;
import org.morok.settings.SettingsProfileCodec;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Explicit Storage Access Framework transfer for validated, secret-free MOROK settings profiles. */
public final class MorokSettingsTransferActivity extends BaseFragment {
    private static final int EXPORT = 1, IMPORT = 2;
    private static final int REQUEST_EXPORT = 7301, REQUEST_IMPORT = 7302;
    private static final int HEADER = 0, ACTION = 1, INFO = 2;
    private final ArrayList<Row> rows = new ArrayList<>();

    public MorokSettingsTransferActivity(int account) {
        super();
        currentAccount = account;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokSettingsTransfer));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        rows.add(new Row(INFO, 0, text(R.string.MorokSettingsTransferInfo)));
        rows.add(new Row(HEADER, 0, text(R.string.MorokSettingsTransferHeader)));
        rows.add(new Row(ACTION, EXPORT, text(R.string.MorokSettingsTransferExport)));
        rows.add(new Row(ACTION, IMPORT, text(R.string.MorokSettingsTransferImport)));
        rows.add(new Row(INFO, 0, text(R.string.MorokSettingsTransferExcluded)));

        FrameLayout frame = new FrameLayout(context);
        fragmentView = frame;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setVerticalScrollBarEnabled(false);
        list.setItemAnimator(null);
        list.setAdapter(new Adapter());
        frame.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        list.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= rows.size()) return;
            int id = rows.get(position).id;
            if (id != EXPORT && id != IMPORT) return;
            if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
                showMessage(R.string.MorokSettingsTransferLoginRequired);
                return;
            }
            if (id == EXPORT) chooseExportDestination();
            else chooseImportSource();
        });
        return fragmentView;
    }

    private void chooseExportDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "MOROK-settings-v1.morok-settings");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void chooseImportSource() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) exportTo(uri);
        else if (requestCode == REQUEST_IMPORT) importFrom(uri);
    }

    private void exportTo(Uri uri) {
        final SettingsProfile profile;
        try {
            profile = MorokSettings.exportProfile(currentAccount);
        } catch (RuntimeException error) {
            showMessage(R.string.MorokSettingsTransferLoginRequired);
            return;
        }
        Context context = getContext();
        if (context == null) return;
        Utilities.globalQueue.postRunnable(() -> {
            boolean success = false;
            try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IOException("No settings output stream");
                output.write(SettingsProfileCodec.encode(profile).getBytes(StandardCharsets.UTF_8));
                output.flush();
                success = true;
            } catch (Exception error) {
                FileLog.e(error);
            }
            boolean exported = success;
            AndroidUtilities.runOnUIThread(() -> showMessage(exported
                    ? R.string.MorokSettingsTransferExported : R.string.MorokSettingsTransferFileError));
        });
    }

    private void importFrom(Uri uri) {
        Context context = getContext();
        if (context == null) return;
        Utilities.globalQueue.postRunnable(() -> {
            SettingsProfile profile = null;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("No settings input stream");
                profile = SettingsProfileCodec.decode(readLimited(input));
            } catch (Exception error) {
                FileLog.e(error);
            }
            SettingsProfile decoded = profile;
            AndroidUtilities.runOnUIThread(() -> {
                if (decoded == null) showMessage(R.string.MorokSettingsTransferInvalid);
                else showImportPreview(decoded);
            });
        });
    }

    private void showImportPreview(SettingsProfile profile) {
        if (getParentActivity() == null) return;
        if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
            showMessage(R.string.MorokSettingsTransferLoginRequired);
            return;
        }
        AppearanceSettings currentAppearance;
        RoundVideoSettings currentRound;
        PrivacySettings currentPrivacy;
        try {
            currentAppearance = MorokSettings.appearance();
            currentRound = MorokSettings.roundVideo();
            currentPrivacy = MorokSettings.privacy(currentAccount);
        } catch (RuntimeException error) {
            showMessage(R.string.MorokSettingsTransferLoginRequired);
            return;
        }
        ArrayList<String> changes = new ArrayList<>();
        addBooleanChange(changes, R.string.MorokLiquidGlass, currentAppearance.liquidGlass, profile.appearance.liquidGlass);
        addBooleanChange(changes, R.string.MorokReducedEffects, currentAppearance.reducedEffects, profile.appearance.reducedEffects);
        addBooleanChange(changes, R.string.MorokRoundVideoEnhanced, currentRound.enhanced, profile.roundVideo.enhanced);
        if (!currentRound.profile.equals(profile.roundVideo.profile)) {
            addChange(changes, text(R.string.MorokRoundVideoQuality), profileLabel(currentRound), profileLabel(profile.roundVideo));
        }
        addBooleanChange(changes, R.string.MorokGhostPreset, currentPrivacy.ghostPreset, profile.privacy.ghostPreset);
        addBooleanChange(changes, R.string.MorokHideTyping, currentPrivacy.hideTyping, profile.privacy.hideTyping);
        addBooleanChange(changes, R.string.MorokHideOnline, currentPrivacy.hideOnline, profile.privacy.hideOnline);
        addBooleanChange(changes, R.string.MorokHideContentRead, currentPrivacy.hideContentRead, profile.privacy.hideContentRead);
        addBooleanChange(changes, R.string.MorokHideRead, currentPrivacy.hideRead, profile.privacy.hideRead);
        addBooleanChange(changes, R.string.MorokHideStoryViews, currentPrivacy.hideStoryViews, profile.privacy.hideStoryViews);
        addBooleanChange(changes, R.string.MorokMarkReadOnReply, currentPrivacy.markReadOnReply, profile.privacy.markReadOnReply);
        addBooleanChange(changes, R.string.MorokDelayGhostSends, currentPrivacy.delayGhostSends, profile.privacy.delayGhostSends);
        if (changes.isEmpty()) {
            showMessage(R.string.MorokSettingsTransferNoChanges);
            return;
        }
        StringBuilder preview = new StringBuilder();
        for (String change : changes) {
            if (preview.length() != 0) preview.append('\n');
            preview.append("• ").append(change);
        }
        AppearanceSettings previousAppearance = currentAppearance;
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(text(R.string.MorokSettingsTransferPreview))
                .setMessage(preview.toString())
                .setPositiveButton(text(R.string.MorokSettingsTransferApply), (dialog, which) -> {
                    try {
                        MorokSettings.applyProfile(currentAccount, profile);
                        MorokAppearance.refreshAfterImport(previousAppearance, profile.appearance, getParentActivity());
                        showMessage(R.string.MorokSettingsTransferImported);
                    } catch (RuntimeException error) {
                        FileLog.e(error);
                        showMessage(R.string.MorokSettingsTransferApplyError);
                    }
                })
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > SettingsProfileCodec.MAX_CHARACTERS) {
                throw new IOException("Settings profile is too large");
            }
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private void addBooleanChange(ArrayList<String> changes, int label, boolean before, boolean after) {
        if (before != after) addChange(changes, text(label), state(before), state(after));
    }

    private void addChange(ArrayList<String> changes, String label, String before, String after) {
        changes.add(LocaleController.formatString(R.string.MorokSettingsTransferChange, label, before, after));
    }

    private String state(boolean value) {
        return text(value ? R.string.MorokSettingsTransferEnabled : R.string.MorokSettingsTransferDisabled);
    }

    private static String profileLabel(RoundVideoSettings settings) {
        if (RoundVideoSettings.PROFILE_HIGH.equals(settings.profile)) return text(R.string.MorokRoundVideoHigh);
        if (RoundVideoSettings.PROFILE_SAVER.equals(settings.profile)) return text(R.string.MorokRoundVideoSaver);
        return text(R.string.MorokRoundVideoAuto);
    }

    private void showMessage(int message) {
        if (getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.MorokSettingsTransfer))
                .setMessage(text(message)).setPositiveButton(text(R.string.OK), null).create());
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
            View view;
            if (type == HEADER) view = new HeaderCell(parent.getContext());
            else if (type == INFO) view = new TextInfoPrivacyCell(parent.getContext());
            else view = new TextSettingsCell(parent.getContext());
            if (type != INFO) view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(view);
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (row.type == HEADER) {
                ((HeaderCell) holder.itemView).setText(row.title);
            } else if (row.type == INFO) {
                ((TextInfoPrivacyCell) holder.itemView).setText(row.title);
                ((TextInfoPrivacyCell) holder.itemView).setTextColorByKey(Theme.key_windowBackgroundWhiteGrayText4);
            } else {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setText(row.title, row.id != IMPORT);
                cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
        }
    }
}
