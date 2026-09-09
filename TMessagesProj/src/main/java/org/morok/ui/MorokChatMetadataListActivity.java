package org.morok.ui;

import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.morok.chatmeta.ChatMetadata;
import org.morok.chatmeta.MorokChatMetadataStore;
import org.telegram.messenger.AndroidUtilities;
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
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Locale;

/** Account-local index for already saved chat aliases and notes. */
public final class MorokChatMetadataListActivity extends BaseFragment {
    private static final int CLEAR = 1;
    private final long expectedUserId;
    private MorokChatMetadataStore store;
    private final ArrayList<ChatMetadata> all = new ArrayList<>();
    private final ArrayList<ChatMetadata> visible = new ArrayList<>();
    private RecyclerListView list;
    private Adapter adapter;
    private EditText search;
    private TextView status;
    private boolean destroyed;

    public MorokChatMetadataListActivity(int account) {
        setCurrentAccount(account);
        expectedUserId = UserConfig.getInstance(account).getClientUserId();
    }

    @Override public boolean onFragmentCreate() {
        if (expectedUserId <= 0 || Build.VERSION.SDK_INT < 23) {
            Toast.makeText(org.telegram.messenger.ApplicationLoader.applicationContext,
                    text(R.string.MorokChatMetadataUnavailable), Toast.LENGTH_LONG).show();
            return false;
        }
        try { store = MorokChatMetadataStore.forAccount(currentAccount); }
        catch (RuntimeException error) { return false; }
        return super.onFragmentCreate();
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokChatMetadataListTitle));
        actionBar.createMenu().addItemWithWidth(CLEAR, R.drawable.msg_delete, AndroidUtilities.dp(48),
                text(R.string.MorokChatMetadataClearAll));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment(); else if (id == CLEAR) confirmClear();
            }
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;
        search = new EditText(context);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setHint(text(R.string.MorokChatMetadataSearch));
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        search.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        search.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        root.addView(search, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(52)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        status = new TextView(context);
        status.setText(text(R.string.MorokChatMetadataLoading));
        status.setTextSize(13);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        status.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
        root.addView(status);
        list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter = new Adapter());
        list.setItemAnimator(null);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= visible.size()) return;
            ChatMetadata metadata = visible.get(position);
            presentFragment(new MorokChatMetadataActivity(currentAccount, metadata.dialogId,
                    sourceTitle(metadata.dialogId), alias -> refresh()));
        });
        refresh();
        return fragmentView;
    }

    @Override public void onResume() { super.onResume(); if (list != null) refresh(); }
    @Override public void onFragmentDestroy() { destroyed = true; super.onFragmentDestroy(); }

    private boolean active() {
        return !destroyed && store != null && store.isActive()
                && UserConfig.getInstance(currentAccount).getClientUserId() == expectedUserId;
    }

    private void refresh() {
        if (!active()) { if (!destroyed) finishFragment(); return; }
        status.setText(text(R.string.MorokChatMetadataLoading));
        store.list((values, error) -> {
            if (!active() || status == null) return;
            if (error != null || values == null) {
                status.setText(text(R.string.MorokChatMetadataStorageError));
                return;
            }
            all.clear(); all.addAll(values); applyFilter();
        });
    }

    private void applyFilter() {
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (ChatMetadata metadata : all) {
            String source = sourceTitle(metadata.dialogId);
            if (query.isEmpty() || metadata.alias.toLowerCase(Locale.ROOT).contains(query)
                    || metadata.note.toLowerCase(Locale.ROOT).contains(query)
                    || source.toLowerCase(Locale.ROOT).contains(query)) visible.add(metadata);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (status != null) status.setText(visible.isEmpty() ? text(R.string.MorokChatMetadataListEmpty)
                : LocaleController.formatString(R.string.MorokChatMetadataAccountOnly, visible.size(), MorokChatMetadataStore.MAX_ENTRIES));
    }

    private String sourceTitle(long dialogId) {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        if (dialogId > 0) {
            TLRPC.User user = controller.getUser(dialogId);
            if (user != null) return UserObject.getUserName(user);
        } else {
            TLRPC.Chat chat = controller.getChat(-dialogId);
            if (chat != null && chat.title != null) return chat.title;
        }
        return text(R.string.MorokChatMetadataSourceUnavailable);
    }

    private void confirmClear() {
        if (!active() || all.isEmpty() || getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.MorokChatMetadataClearAll))
                .setMessage(text(R.string.MorokChatMetadataClearAllInfo))
                .setNegativeButton(text(R.string.Cancel), null)
                .setPositiveButton(text(R.string.Delete), (dialog, which) -> store.clear((value, error) -> {
                    if (!active()) return;
                    if (error != null && getParentActivity() != null) Toast.makeText(getParentActivity(), text(R.string.MorokChatMetadataStorageError), Toast.LENGTH_LONG).show();
                    else refresh();
                })).create());
    }

    private static String excerpt(String value, int limit) {
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= limit ? oneLine : oneLine.substring(0, limit) + "…";
    }

    private static String text(int id) { return LocaleController.getString(id); }

    private final class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public int getItemCount() { return visible.size(); }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
            TextView title = new TextView(parent.getContext());
            title.setTextSize(16); title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            TextView detail = new TextView(parent.getContext());
            detail.setTextSize(13); detail.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            detail.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            row.addView(title); row.addView(detail);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerListView.Holder(row);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ChatMetadata metadata = visible.get(position);
            LinearLayout row = (LinearLayout) holder.itemView;
            String source = sourceTitle(metadata.dialogId);
            ((TextView) row.getChildAt(0)).setText(metadata.alias.isEmpty() ? source : metadata.alias);
            String detail = metadata.alias.isEmpty() ? "" : source;
            if (!metadata.note.isEmpty()) detail += (detail.isEmpty() ? "" : "\n") + excerpt(metadata.note, 180);
            ((TextView) row.getChildAt(1)).setText(detail);
        }
    }
}
