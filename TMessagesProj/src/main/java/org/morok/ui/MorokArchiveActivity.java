package org.morok.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.morok.settings.ArchiveSettings;
import org.morok.history.LocalHistoryImporter;
import org.morok.memory.MemoryStorageStats;
import org.morok.memory.MorokMemoryStore;
import org.morok.settings.MorokSettings;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.Collections;

/** Account-local allowlist for automatic encrypted snapshots of newly received messages. */
public final class MorokArchiveActivity extends BaseFragment {
    private static final int ENABLED = 1, ADD_CHAT = 2, CHAT = 3, CLEAR = 4, IMPORT_HISTORY = 5;
    private static final int RETENTION = 6, STORAGE = 7, ATTACHMENT = 8, NETWORK = 9, CLEAN_POLICY = 10;
    private static final int HEADER = 0, CHECK = 1, ACTION = 2, INFO = 3;
    private final ArrayList<Row> rows = new ArrayList<>();
    private Adapter adapter;
    private boolean importing;
    private boolean cleaning;
    private boolean loadingStats;
    private MemoryStorageStats storageStats;

    public MorokArchiveActivity(int account) { currentAccount = account; }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokArchiveTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context); fragmentView = frame;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context)); list.setItemAnimator(null);
        list.setAdapter(adapter = new Adapter());
        frame.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        list.setOnItemClickListener((view, position) -> {
            if (!available() || position < 0 || position >= rows.size()) return;
            ArchiveSettings settings = settings(); Row row = rows.get(position);
            if (row.id == ENABLED) apply(settings.withEnabled(!settings.enabled));
            else if (row.id == ADD_CHAT) chooseChatKind(false);
            else if (row.id == IMPORT_HISTORY) chooseChatKind(true);
            else if (row.id == RETENTION) chooseRetention();
            else if (row.id == STORAGE) chooseStorage();
            else if (row.id == ATTACHMENT) chooseAttachment();
            else if (row.id == NETWORK) chooseNetwork();
            else if (row.id == CLEAN_POLICY) confirmCleanPolicy();
            else if (row.id == CHAT) showDialog(new AlertDialog.Builder(context)
                    .setTitle(text(R.string.MorokArchiveRemoveChat)).setMessage(row.title)
                    .setPositiveButton(text(R.string.Remove), (d, w) -> apply(settings.withChat(row.dialogId, false)))
                    .setNegativeButton(text(R.string.Cancel), null).create());
            else if (row.id == CLEAR) showDialog(new AlertDialog.Builder(context)
                    .setTitle(text(R.string.MorokArchiveClearChats)).setMessage(text(R.string.MorokArchiveClearChatsInfo))
                    .setPositiveButton(text(R.string.Remove), (d, w) -> apply(settings.withoutChats()))
                    .setNegativeButton(text(R.string.Cancel), null).create());
        });
        rebuildRows(); loadStats(); return fragmentView;
    }

    @Override public void onResume() {
        super.onResume();
        rebuildRows();
        loadStats();
    }

    private boolean available() {
        return currentAccount >= 0 && currentAccount < UserConfig.MAX_ACCOUNT_COUNT
                && UserConfig.getInstance(currentAccount).isClientActivated();
    }

    private ArchiveSettings settings() {
        try { return MorokSettings.archive(currentAccount); }
        catch (RuntimeException error) { return ArchiveSettings.DEFAULT; }
    }

    private boolean apply(ArchiveSettings value) {
        try {
            MorokSettings.setArchive(currentAccount, value);
            rebuildRows();
            loadStats();
            return true;
        }
        catch (IllegalStateException error) {
            showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveTitle))
                    .setMessage(text(R.string.MorokSettingsNewerVersion)).setPositiveButton(text(R.string.OK), null).create());
            return false;
        }
    }

    private void chooseRetention() {
        ArchiveSettings current = settings();
        int[] options = ArchiveSettings.retentionOptions();
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < labels.length; i++) labels[i] = LocaleController.formatString(
                R.string.MorokArchiveDays, options[i]);
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveRetention))
                .setItems(labels, (dialog, which) -> changePolicy(
                        current.withRetentionDays(options[which]), options[which] < current.retentionDays))
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void chooseStorage() {
        ArchiveSettings current = settings();
        int[] options = ArchiveSettings.storageOptions();
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < labels.length; i++) labels[i] = sizeLabel(options[i]);
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveStorageLimit))
                .setItems(labels, (dialog, which) -> changePolicy(
                        current.withStorageMib(options[which]), options[which] < current.storageMib))
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void chooseAttachment() {
        ArchiveSettings current = settings();
        int[] options = ArchiveSettings.attachmentOptions();
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < labels.length; i++) labels[i] = sizeLabel(options[i]);
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveAttachmentLimit))
                .setItems(labels, (dialog, which) -> apply(
                        current.withAttachmentMib(options[which])))
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void chooseNetwork() {
        ArchiveSettings current = settings();
        String[] values = {ArchiveSettings.ATTACHMENTS_NEVER, ArchiveSettings.ATTACHMENTS_WIFI,
                ArchiveSettings.ATTACHMENTS_ANY};
        CharSequence[] labels = {text(R.string.MorokArchiveNetworkNever),
                text(R.string.MorokArchiveNetworkWifi), text(R.string.MorokArchiveNetworkAny)};
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveAttachmentNetwork))
                .setItems(labels, (dialog, which) -> apply(current.withAttachmentPolicy(values[which])))
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void changePolicy(ArchiveSettings value, boolean removesExisting) {
        if (!removesExisting) { apply(value); return; }
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchivePolicyChangeTitle))
                .setMessage(text(R.string.MorokArchivePolicyChangeInfo))
                .setPositiveButton(text(R.string.MorokArchiveApply), (dialog, which) -> {
                    if (apply(value)) cleanPolicy();
                }).setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void confirmCleanPolicy() {
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveClean))
                .setMessage(text(R.string.MorokArchiveCleanInfo))
                .setPositiveButton(text(R.string.Remove), (dialog, which) -> cleanPolicy())
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void cleanPolicy() {
        if (cleaning || !available()) return;
        cleaning = true;
        rebuildRows();
        try {
            MorokMemoryStore.forAccount(currentAccount).cleanAutomaticArchive((result, error) -> {
                cleaning = false;
                storageStats = null;
                rebuildRows();
                loadStats();
                if (getContext() == null) return;
                String message = error != null || result == null ? text(R.string.MorokArchiveCleanError)
                        : LocaleController.formatString(R.string.MorokArchiveCleanResult,
                        result.removedCards, AndroidUtilities.formatFileSize(result.beforeBytes),
                        AndroidUtilities.formatFileSize(result.afterBytes));
                showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveClean))
                        .setMessage(message).setPositiveButton(text(R.string.OK), null).create());
            });
        } catch (RuntimeException error) {
            cleaning = false;
            rebuildRows();
            if (getContext() != null) {
                showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveClean))
                        .setMessage(text(R.string.MorokArchiveCleanError))
                        .setPositiveButton(text(R.string.OK), null).create());
            }
        }
    }

    private void loadStats() {
        if (!available() || loadingStats) return;
        loadingStats = true;
        try {
            MorokMemoryStore.forAccount(currentAccount).storageStats((stats, error) -> {
                loadingStats = false;
                if (error == null) storageStats = stats;
                rebuildRows();
            });
        } catch (RuntimeException error) {
            loadingStats = false;
        }
    }

    private void chooseChatKind(boolean forImport) {
        CharSequence[] kinds = {text(R.string.MorokArchivePrivateChat), text(R.string.MorokArchiveGroup),
                text(R.string.MorokArchiveChannel)};
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(forImport
                        ? R.string.MorokArchiveImportChooseChat : R.string.MorokArchiveAddChat))
                .setItems(kinds, (dialog, which) -> openPicker(which == 0 ? DialogsActivity.DIALOGS_TYPE_USERS_ONLY
                        : which == 1 ? DialogsActivity.DIALOGS_TYPE_GROUPS_ONLY : DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY,
                        forImport))
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void openPicker(int type, boolean forImport) {
        Bundle args = new Bundle(); args.putBoolean("onlySelect", true); args.putBoolean("checkCanWrite", false);
        args.putBoolean("allowGlobalSearch", false); args.putInt("dialogsType", type);
        DialogsActivity picker = new DialogsActivity(args); picker.setCurrentAccount(currentAccount);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids.isEmpty()) return false;
            if (forImport) confirmImport(dids.get(0).dialogId);
            else {
                ArchiveSettings updated = settings();
                for (int i = 0; i < dids.size(); i++) updated = updated.withChat(dids.get(i).dialogId, true);
                apply(updated);
            }
            picker.finishFragment(); return true;
        });
        presentFragment(picker);
    }

    private void confirmImport(long dialogId) {
        if (importing || getContext() == null) return;
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveImportTitle))
                .setMessage(LocaleController.formatString(R.string.MorokArchiveImportConfirm, dialogTitle(dialogId)))
                .setPositiveButton(text(R.string.MorokArchiveImportAction), (dialog, which) -> importHistory(dialogId))
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void importHistory(long dialogId) {
        if (importing || getContext() == null) return;
        importing = true;
        if (adapter != null) adapter.notifyDataSetChanged();
        AlertDialog progress = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        showDialog(progress);
        LocalHistoryImporter.importRecent(currentAccount, dialogId, (result, error) -> {
            importing = false;
            try { progress.dismiss(); } catch (RuntimeException ignored) { }
            if (adapter != null) adapter.notifyDataSetChanged();
            if (getContext() == null) return;
            String message = error != null ? text(R.string.MorokArchiveImportError)
                    : LocaleController.formatString(R.string.MorokArchiveImportResult,
                    result.retained, result.eligible, result.scanned);
            showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveImportTitle))
                    .setMessage(message).setPositiveButton(text(R.string.OK), null).create());
        });
    }

    private void rebuildRows() {
        rows.clear(); rows.add(new Row(INFO, 0, text(R.string.MorokArchiveInfo), 0));
        if (!available()) rows.add(new Row(INFO, 0, text(R.string.MorokMemorySignInRequired), 0));
        else {
            ArchiveSettings settings = settings();
            rows.add(new Row(HEADER, 0, text(R.string.MorokArchivePolicy), 0));
            rows.add(new Row(CHECK, ENABLED, text(R.string.MorokArchiveEnabled), 0));
            rows.add(new Row(INFO, 0, text(R.string.MorokArchiveEnabledInfo), 0));
            rows.add(new Row(ACTION, ADD_CHAT, text(R.string.MorokArchiveAddChat), 0));
            ArrayList<Long> chats = new ArrayList<>(settings.chats); Collections.sort(chats);
            if (chats.isEmpty()) rows.add(new Row(INFO, 0, text(R.string.MorokArchiveNoChats), 0));
            else {
                for (long dialogId : chats) rows.add(new Row(ACTION, CHAT, dialogTitle(dialogId), dialogId));
                rows.add(new Row(ACTION, CLEAR, text(R.string.MorokArchiveClearChats), 0));
            }
            rows.add(new Row(HEADER, 0, text(R.string.MorokArchiveLimitsHeader), 0));
            rows.add(new Row(ACTION, RETENTION, text(R.string.MorokArchiveRetention), 0,
                    LocaleController.formatString(R.string.MorokArchiveDays, settings.retentionDays)));
            rows.add(new Row(ACTION, STORAGE, text(R.string.MorokArchiveStorageLimit), 0,
                    sizeLabel(settings.storageMib)));
            rows.add(new Row(ACTION, ATTACHMENT, text(R.string.MorokArchiveAttachmentLimit), 0,
                    sizeLabel(settings.attachmentMib)));
            rows.add(new Row(ACTION, NETWORK, text(R.string.MorokArchiveAttachmentNetwork), 0,
                    networkLabel(settings.attachmentPolicy)));
            rows.add(new Row(INFO, 0, storageInfo(settings), 0));
            rows.add(new Row(ACTION, CLEAN_POLICY, text(R.string.MorokArchiveClean), 0));
            rows.add(new Row(INFO, 0, text(R.string.MorokArchiveLimits), 0));
            rows.add(new Row(HEADER, 0, text(R.string.MorokArchiveImportHeader), 0));
            rows.add(new Row(ACTION, IMPORT_HISTORY, text(R.string.MorokArchiveImportAction), 0));
            rows.add(new Row(INFO, 0, text(R.string.MorokArchiveImportInfo), 0));
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private String storageInfo(ArchiveSettings settings) {
        if (storageStats == null) return text(R.string.MorokArchiveStorageLoading);
        return LocaleController.formatString(R.string.MorokArchiveStorageInfo,
                AndroidUtilities.formatFileSize(storageStats.usedBytes), settings.storageMib,
                storageStats.automaticCards, storageStats.cards, storageStats.policyBlocked);
    }

    private String networkLabel(String policy) {
        if (ArchiveSettings.ATTACHMENTS_NEVER.equals(policy)) return text(R.string.MorokArchiveNetworkNever);
        if (ArchiveSettings.ATTACHMENTS_ANY.equals(policy)) return text(R.string.MorokArchiveNetworkAny);
        return text(R.string.MorokArchiveNetworkWifi);
    }

    private String sizeLabel(int mib) {
        return LocaleController.formatString(R.string.MorokArchiveMib, mib);
    }

    private String dialogTitle(long dialogId) {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = controller.getUser(dialogId); if (user != null) return UserObject.getUserName(user);
        } else if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = controller.getChat(-dialogId); if (chat != null && chat.title != null) return chat.title;
        }
        return LocaleController.formatString(R.string.MorokChatExceptionUnknown, dialogId);
    }

    private static String text(int id) { return LocaleController.getString(id); }
    private static final class Row {
        final int type, id; final String title; final long dialogId; final String value;
        Row(int type, int id, String title, long dialogId) { this(type, id, title, dialogId, null); }
        Row(int type, int id, String title, long dialogId, String value) {
            this.type = type; this.id = id; this.title = title; this.dialogId = dialogId; this.value = value;
        }
    }
    private final class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return rows.size(); }
        @Override public int getItemViewType(int position) { return rows.get(position).type; }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return available() && !importing && !cleaning
                    && (holder.getItemViewType() == CHECK || holder.getItemViewType() == ACTION);
        }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View view = type == HEADER ? new HeaderCell(parent.getContext()) : type == CHECK ? new TextCheckCell(parent.getContext())
                    : type == INFO ? new TextInfoPrivacyCell(parent.getContext()) : new TextSettingsCell(parent.getContext());
            if (type != INFO) view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(view);
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (row.type == HEADER) ((HeaderCell) holder.itemView).setText(row.title);
            else if (row.type == INFO) ((TextInfoPrivacyCell) holder.itemView).setText(row.title);
            else if (row.type == CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setColors(Theme.key_windowBackgroundWhiteBlackText, Theme.key_switchTrack, Theme.key_switchTrackChecked,
                        Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                cell.setTextAndCheck(row.title, settings().enabled, false);
            } else if (row.value == null) ((TextSettingsCell) holder.itemView).setText(row.title, false);
            else ((TextSettingsCell) holder.itemView).setTextAndValue(row.title, row.value, true);
        }
    }
}
