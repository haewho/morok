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
import org.morok.settings.MorokSettings;
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
    private static final int ENABLED = 1, ADD_CHAT = 2, CHAT = 3, CLEAR = 4;
    private static final int HEADER = 0, CHECK = 1, ACTION = 2, INFO = 3;
    private final ArrayList<Row> rows = new ArrayList<>();
    private Adapter adapter;

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
            else if (row.id == ADD_CHAT) chooseChatKind();
            else if (row.id == CHAT) showDialog(new AlertDialog.Builder(context)
                    .setTitle(text(R.string.MorokArchiveRemoveChat)).setMessage(row.title)
                    .setPositiveButton(text(R.string.Remove), (d, w) -> apply(settings.withChat(row.dialogId, false)))
                    .setNegativeButton(text(R.string.Cancel), null).create());
            else if (row.id == CLEAR) showDialog(new AlertDialog.Builder(context)
                    .setTitle(text(R.string.MorokArchiveClearChats)).setMessage(text(R.string.MorokArchiveClearChatsInfo))
                    .setPositiveButton(text(R.string.Remove), (d, w) -> apply(settings.withoutChats()))
                    .setNegativeButton(text(R.string.Cancel), null).create());
        });
        rebuildRows(); return fragmentView;
    }

    private boolean available() {
        return currentAccount >= 0 && currentAccount < UserConfig.MAX_ACCOUNT_COUNT
                && UserConfig.getInstance(currentAccount).isClientActivated();
    }

    private ArchiveSettings settings() {
        try { return MorokSettings.archive(currentAccount); }
        catch (RuntimeException error) { return ArchiveSettings.DEFAULT; }
    }

    private void apply(ArchiveSettings value) {
        try { MorokSettings.setArchive(currentAccount, value); rebuildRows(); }
        catch (IllegalStateException error) {
            showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveTitle))
                    .setMessage(text(R.string.MorokSettingsNewerVersion)).setPositiveButton(text(R.string.OK), null).create());
        }
    }

    private void chooseChatKind() {
        CharSequence[] kinds = {text(R.string.MorokArchivePrivateChat), text(R.string.MorokArchiveGroup),
                text(R.string.MorokArchiveChannel)};
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokArchiveAddChat))
                .setItems(kinds, (dialog, which) -> openPicker(which == 0 ? DialogsActivity.DIALOGS_TYPE_USERS_ONLY
                        : which == 1 ? DialogsActivity.DIALOGS_TYPE_GROUPS_ONLY : DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY))
                .setNegativeButton(text(R.string.Cancel), null).create());
    }

    private void openPicker(int type) {
        Bundle args = new Bundle(); args.putBoolean("onlySelect", true); args.putBoolean("checkCanWrite", false);
        args.putBoolean("allowGlobalSearch", false); args.putInt("dialogsType", type);
        DialogsActivity picker = new DialogsActivity(args); picker.setCurrentAccount(currentAccount);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            ArchiveSettings updated = settings();
            for (int i = 0; i < dids.size(); i++) updated = updated.withChat(dids.get(i).dialogId, true);
            apply(updated); picker.finishFragment(); return true;
        });
        presentFragment(picker);
    }

    private void rebuildRows() {
        rows.clear(); rows.add(new Row(INFO, 0, text(R.string.MorokArchiveInfo), 0));
        if (!available()) rows.add(new Row(INFO, 0, text(R.string.MorokMemorySignInRequired), 0));
        else {
            rows.add(new Row(HEADER, 0, text(R.string.MorokArchivePolicy), 0));
            rows.add(new Row(CHECK, ENABLED, text(R.string.MorokArchiveEnabled), 0));
            rows.add(new Row(INFO, 0, text(R.string.MorokArchiveEnabledInfo), 0));
            rows.add(new Row(ACTION, ADD_CHAT, text(R.string.MorokArchiveAddChat), 0));
            ArrayList<Long> chats = new ArrayList<>(settings().chats); Collections.sort(chats);
            if (chats.isEmpty()) rows.add(new Row(INFO, 0, text(R.string.MorokArchiveNoChats), 0));
            else {
                for (long dialogId : chats) rows.add(new Row(ACTION, CHAT, dialogTitle(dialogId), dialogId));
                rows.add(new Row(ACTION, CLEAR, text(R.string.MorokArchiveClearChats), 0));
            }
            rows.add(new Row(INFO, 0, text(R.string.MorokArchiveLimits), 0));
        }
        if (adapter != null) adapter.notifyDataSetChanged();
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
        final int type, id; final String title; final long dialogId;
        Row(int type, int id, String title, long dialogId) { this.type = type; this.id = id; this.title = title; this.dialogId = dialogId; }
    }
    private final class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return rows.size(); }
        @Override public int getItemViewType(int position) { return rows.get(position).type; }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return available() && (holder.getItemViewType() == CHECK || holder.getItemViewType() == ACTION);
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
            } else ((TextSettingsCell) holder.itemView).setText(row.title, false);
        }
    }
}
