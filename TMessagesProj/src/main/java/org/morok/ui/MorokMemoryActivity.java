package org.morok.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.morok.history.MemoryCapture;
import org.morok.memory.MemoryCard;
import org.morok.memory.MemoryPolicy;
import org.morok.memory.MemoryStorageStats;
import org.morok.memory.MorokMemoryFileProvider;
import org.morok.memory.MorokMemoryReminderReceiver;
import org.morok.memory.MorokMemoryStore;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.RecyclerListView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/** Account-scoped native screen: every action on a card is local until Open source is explicitly tapped. */
public class MorokMemoryActivity extends BaseFragment {
    private final long expectedUserId;
    private String openCardId;
    private MorokMemoryStore store;
    private ArrayList<MemoryCard> all = new ArrayList<>();
    private ArrayList<MemoryCard> visible = new ArrayList<>();
    private RecyclerListView list;
    private CardsAdapter adapter;
    private TextView status;
    private EditText search;
    private int filter;
    private int queryGeneration;
    private boolean destroyed;

    public MorokMemoryActivity(int account) { this(account, null); }
    public MorokMemoryActivity(int account, String cardId) {
        super(); setCurrentAccount(account);
        expectedUserId = UserConfig.getInstance(account).getClientUserId(); openCardId = cardId;
    }

    @Override public boolean onFragmentCreate() {
        if (expectedUserId <= 0) {
            Toast.makeText(org.telegram.messenger.ApplicationLoader.applicationContext,
                    t(R.string.MorokMemorySignInRequired), Toast.LENGTH_LONG).show();
            return false;
        }
        if (Build.VERSION.SDK_INT < 23) {
            Toast.makeText(org.telegram.messenger.ApplicationLoader.applicationContext,
                    t(R.string.MorokMemoryUnsupported), Toast.LENGTH_LONG).show();
            return false;
        }
        store = MorokMemoryStore.forAccount(currentAccount);
        return super.onFragmentCreate();
    }

    @Override public View createView(Context context) {
        actionBar.setTitle(t(R.string.MorokMemoryTitle));
        actionBar.setSubtitle(t(R.string.MorokMemoryAccountOnly));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.createMenu().addItem(1, R.drawable.msg_settings);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); else if (id == 1) storageInfo(); }
        });
        LinearLayout root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;
        search = field(context, t(R.string.MorokMemorySearch), "", 512);
        search.setSingleLine(true); root.addView(search, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(52)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable value) { }
        });
        HorizontalScrollView filters = new HorizontalScrollView(context); filters.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(context);
        int[] labels = {R.string.MorokMemoryAll, R.string.MorokMemoryNeedsReply, R.string.MorokMemoryLater, R.string.MorokMemoryWithFiles, R.string.MorokMemoryCompleted};
        for (int i = 0; i < labels.length; i++) {
            final int selected = i;
            Button tab = button(context, t(labels[i]));
            tab.setOnClickListener(v -> { filter = selected; applyFilter(); }); tabs.addView(tab);
        }
        filters.addView(tabs); root.addView(filters);
        status = text(context, t(R.string.MorokMemoryLoading), 13);
        status.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8)); root.addView(status);
        list = new RecyclerListView(context); list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter = new CardsAdapter());
        list.setOnItemClickListener((view, position) -> { if (position < visible.size()) editCard(visible.get(position)); });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        refresh(); return root;
    }

    @Override public void onResume() { super.onResume(); if (list != null) refresh(); }
    @Override public void onFragmentDestroy() { destroyed = true; queryGeneration++; super.onFragmentDestroy(); }

    private boolean accountValid() {
        return !destroyed && UserConfig.getInstance(currentAccount).getClientUserId() == expectedUserId && store != null && store.isActive();
    }

    private void refresh() {
        if (!accountValid()) { if (!destroyed) finishFragment(); return; }
        store.list((cards, error) -> {
            if (!accountValid() || status == null) return;
            if (error != null) { status.setText(t(R.string.MorokMemoryStorageError)); return; }
            all = cards; applyFilter();
            if (openCardId != null) {
                String id = openCardId; openCardId = null;
                for (MemoryCard card : cards) if (card.id.equals(id)) { editCard(card); return; }
                toast(t(R.string.MorokMemoryCardMissing));
            }
        });
    }

    private void applyFilter() {
        final int generation = ++queryGeneration, selected = filter;
        final String query = search == null ? "" : search.getText().toString();
        final ArrayList<MemoryCard> cards = new ArrayList<>(all);
        Utilities.searchQueue.postRunnable(() -> {
            ArrayList<MemoryCard> result = new ArrayList<>();
            for (MemoryCard card : cards) {
                boolean matches = selected == 0 || selected == 1 && card.needsReply && !card.completed
                        || selected == 2 && card.reminderAt > 0 && !card.completed
                        || selected == 3 && card.hasFile() || selected == 4 && card.completed;
                if (matches && card.matches(query)) result.add(card);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (!accountValid() || generation != queryGeneration || adapter == null) return;
                visible = result; adapter.notifyDataSetChanged();
                status.setText((store.hasCaptureGap() ? t(R.string.MorokMemoryCaptureGap) + "\n" : "")
                        + (result.isEmpty() ? t(R.string.MorokMemoryEmpty) :
                        t(R.string.MorokMemoryAccountOnly) + " · " + result.size() + " / " + MemoryPolicy.MAX_CARDS));
            });
        }, 120);
    }

    public static void remember(BaseFragment fragment, MessageObject message) {
        if (fragment.getParentActivity() == null) return;
        try {
            MemoryCapture capture = MemoryCapture.take(message);
            MorokMemoryStore.forAccount(message.currentAccount).save(capture, true, (card, error) -> {
                if (fragment.getParentActivity() == null) return;
                if (error != null || card == null) {
                    Toast.makeText(fragment.getParentActivity(), t(R.string.MorokMemoryStorageError), Toast.LENGTH_LONG).show();
                } else fragment.presentFragment(new MorokMemoryActivity(message.currentAccount, card.id));
            });
        } catch (Exception error) {
            Toast.makeText(fragment.getParentActivity(), t(R.string.MorokMemoryUnsupported), Toast.LENGTH_LONG).show();
        }
    }

    private void editCard(MemoryCard card) {
        if (!accountValid() || getParentActivity() == null) return;
        Context context = getParentActivity();
        LinearLayout box = new LinearLayout(context); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), AndroidUtilities.dp(12));
        box.addView(text(context, card.source + " · " + card.sender, 14));
        TextView quote = text(context, card.latest().text, 16); quote.setTextIsSelectable(true); box.addView(quote);
        TextView state = text(context, snapshotStatus(card, card.latest()), 13); box.addView(state);
        EditText note = field(context, t(R.string.MorokMemoryNote), card.note, 8192); box.addView(note);
        EditText tags = field(context, t(R.string.MorokMemoryTags), card.tags, 512); tags.setSingleLine(true); box.addView(tags);
        CheckBox reply = check(context, t(R.string.MorokMemoryNeedsReply), card.needsReply); box.addView(reply);
        CheckBox completed = check(context, t(R.string.MorokMemoryCompleted), card.completed); box.addView(completed);
        long[] reminderAt = {card.reminderAt};
        Button reminder = button(context, reminderLabel(reminderAt[0])); box.addView(reminder);
        reminder.setOnClickListener(v -> chooseReminder(reminderAt, reminder));
        Button cancelReminder = button(context, t(R.string.MorokMemoryCancelReminder)); box.addView(cancelReminder);
        cancelReminder.setOnClickListener(v -> { reminderAt[0] = 0; reminder.setText(reminderLabel(0)); });
        box.addView(text(context, t(R.string.MorokMemoryReminderAccuracy), 12));
        Button versions = button(context, t(R.string.MorokMemoryVersions) + " (" + card.versions.size() + ")"); box.addView(versions);
        versions.setOnClickListener(v -> showVersions(card));
        final Button retry;
        if ("saved".equals(card.latest().fileState)) {
            Button file = button(context, t(R.string.MorokMemoryOpenFile)); box.addView(file);
            file.setOnClickListener(v -> openFile(card, card.latest()));
            retry = null;
        } else if (canRetry(card.latest())) {
            retry = button(context, t(R.string.MorokMemoryRetryFile)); box.addView(retry);
            box.addView(text(context, t(R.string.MorokMemoryRetryFileInfo), 12));
        } else {
            retry = null;
        }
        Button source = button(context, t(R.string.MorokMemoryOpenSource)); box.addView(source);
        source.setOnClickListener(v -> openSource(card));
        box.addView(text(context, t(R.string.MorokMemoryOpenSourceInfo), 12));
        Button remove = button(context, t(R.string.MorokMemoryRemove)); box.addView(remove);
        ScrollView scroll = new ScrollView(context); scroll.addView(box);
        AlertDialog editor = new AlertDialog.Builder(context).setTitle(t(R.string.MorokMemoryTitle)).setView(scroll)
                .setPositiveButton(t(R.string.Save), (dialog, which) -> {
                    store.update(card.id, note.getText().toString(), tags.getText().toString(), reply.isChecked(),
                            completed.isChecked(), reminderAt[0], (saved, error) -> {
                                if (error != null) toast(t(R.string.MorokMemoryStorageError));
                                refresh();
                            });
                }).setNegativeButton(t(R.string.Cancel), null).create();
        if (retry != null) retry.setOnClickListener(v -> {
            retry.setEnabled(false);
            retryAttachment(card, card.latest(), editor::dismiss, () -> retry.setEnabled(true));
        });
        remove.setOnClickListener(v -> showDialog(new AlertDialog.Builder(context)
                .setTitle(t(R.string.MorokMemoryRemove)).setMessage(t(R.string.MorokMemoryRemoveConfirm))
                .setNegativeButton(t(R.string.Cancel), null).setPositiveButton(t(R.string.Delete), (dialog, which) -> {
                    store.remove(card.id, (done, error) -> {
                        if (error != null) toast(t(R.string.MorokMemoryStorageError)); else editor.dismiss(); refresh();
                    });
                }).create()));
        showDialog(editor);
    }

    private void chooseReminder(long[] value, Button button) {
        if (!MorokMemoryReminderReceiver.notificationsAvailable(getParentActivity())) {
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(t(R.string.MorokMemoryRemind))
                    .setMessage(t(R.string.MorokMemoryNotificationsBlocked))
                    .setPositiveButton(t(R.string.Settings), (dialog, which) -> {
                        if (Build.VERSION.SDK_INT >= 33 && getParentActivity().checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            getParentActivity().requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 452);
                        } else {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getParentActivity().getPackageName()));
                            getParentActivity().startActivity(intent);
                        }
                    }).setNegativeButton(t(R.string.Cancel), null).create());
            return;
        }
        Calendar chosen = Calendar.getInstance(); chosen.setTimeInMillis(Math.max(System.currentTimeMillis() + 3600000, value[0]));
        DatePickerDialog date = new DatePickerDialog(getParentActivity(), (picker, year, month, day) -> {
            chosen.set(Calendar.YEAR, year); chosen.set(Calendar.MONTH, month); chosen.set(Calendar.DAY_OF_MONTH, day);
            TimePickerDialog time = new TimePickerDialog(getParentActivity(), (clock, hour, minute) -> {
                chosen.set(Calendar.HOUR_OF_DAY, hour); chosen.set(Calendar.MINUTE, minute); chosen.set(Calendar.SECOND, 0); chosen.set(Calendar.MILLISECOND, 0);
                if (chosen.getTimeInMillis() <= System.currentTimeMillis()) { toast(t(R.string.MorokMemoryFutureTime)); return; }
                value[0] = chosen.getTimeInMillis(); button.setText(reminderLabel(value[0]));
            }, chosen.get(Calendar.HOUR_OF_DAY), chosen.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(getParentActivity()));
            time.show();
        }, chosen.get(Calendar.YEAR), chosen.get(Calendar.MONTH), chosen.get(Calendar.DAY_OF_MONTH));
        date.getDatePicker().setMinDate(System.currentTimeMillis()); date.show();
    }

    private void showVersions(MemoryCard card) {
        CharSequence[] labels = new CharSequence[card.versions.size()];
        for (int i = 0; i < labels.length; i++) {
            MemoryCard.Snapshot snapshot = card.versions.get(i);
            labels[i] = date(snapshot.receivedAt) + "\n" + excerpt(snapshot.text, 90);
        }
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(t(R.string.MorokMemoryVersions))
                .setItems(labels, (dialog, which) -> {
                    MemoryCard.Snapshot snapshot = card.versions.get(which);
                    AlertDialog.Builder detail = new AlertDialog.Builder(getParentActivity()).setTitle(date(snapshot.receivedAt))
                            .setMessage(snapshot.text + "\n\n" + snapshotStatus(card, snapshot)).setNegativeButton(t(R.string.Close), null);
                    if ("saved".equals(snapshot.fileState)) {
                        detail.setPositiveButton(t(R.string.MorokMemoryOpenFile), (d, w) -> openFile(card, snapshot));
                    } else if (canRetry(snapshot)) {
                        detail.setPositiveButton(t(R.string.MorokMemoryRetryFile), (d, w) -> retryAttachment(card, snapshot, null, null));
                    }
                    showDialog(detail.create());
                }).create());
    }

    private static boolean canRetry(MemoryCard.Snapshot snapshot) {
        return "not_downloaded".equals(snapshot.fileState) || "storage_error".equals(snapshot.fileState)
                || "unavailable".equals(snapshot.fileState);
    }

    private void retryAttachment(MemoryCard card, MemoryCard.Snapshot snapshot, Runnable success, Runnable failure) {
        if (!accountValid()) return;
        store.retryAttachment(card.id, snapshot.fingerprint, (updated, error) -> {
            if (error != null || updated == null) {
                if (failure != null) failure.run();
                toast(t(R.string.MorokMemoryRetryFileFailed));
                return;
            }
            MemoryCard.Snapshot result = null;
            for (MemoryCard.Snapshot version : updated.versions) {
                if (version.fingerprint.equals(snapshot.fingerprint)) { result = version; break; }
            }
            if (success != null) success.run();
            if (result != null) {
                int label = fileStateLabel(result.fileState);
                toast(label == 0 ? t(R.string.MorokMemoryRetryFileFailed) : t(label));
            }
            refresh();
        });
    }

    private void openFile(MemoryCard card, MemoryCard.Snapshot snapshot) {
        if (!accountValid()) return;
        try {
            Uri uri = MorokMemoryFileProvider.grant(getParentActivity(), card, snapshot);
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, snapshot.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("Memory attachment", uri));
            getParentActivity().startActivity(Intent.createChooser(intent, t(R.string.MorokMemoryOpenFile)));
        } catch (Exception error) { toast(t(R.string.MorokMemoryViewerUnavailable)); }
    }

    private void openSource(MemoryCard card) {
        int account = MorokMemoryStore.resolveAccount(card.key.userId);
        if (account < 0 || account != currentAccount || !accountValid()) { toast(t(R.string.MorokMemoryAccountMissing)); return; }
        Bundle args = new Bundle();
        args.putLong("user".equals(card.key.peerKind) ? "user_id" : "chat_id", card.key.peerId);
        args.putInt("message_id", card.key.messageId);
        if (!MessagesController.getInstance(account).checkCanOpenChat(args, this)) return;
        if (getParentActivity() instanceof LaunchActivity && UserConfig.selectedAccount != account) {
            ((LaunchActivity) getParentActivity()).switchToAccount(account, true);
        }
        ChatActivity chat = new ChatActivity(args); chat.setCurrentAccount(account);
        if (card.key.topicId != 0) {
            if (MessagesController.getInstance(account).getTopicsController().findTopic(-card.key.dialogId(), card.key.topicId) == null) {
                toast(t(R.string.MorokMemoryTopicUnavailable)); return;
            }
            ForumUtilities.applyTopic(chat, MessagesStorage.TopicKey.of(card.key.dialogId(), card.key.topicId));
        }
        presentFragment(chat);
    }

    private void storageInfo() {
        if (!accountValid()) return;
        store.storageStats((stats, error) -> {
            if (error != null) { toast(t(R.string.MorokMemoryStorageError)); return; }
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(t(R.string.MorokMemoryStorage))
                    .setMessage(AndroidUtilities.formatFileSize(stats.usedBytes) + " / " + AndroidUtilities.formatFileSize(MemoryPolicy.MAX_ACCOUNT_BYTES)
                            + "\n" + LocaleController.formatString(R.string.MorokMemoryStorageCards, stats.cards, stats.versions)
                            + "\n" + LocaleController.formatString(R.string.MorokMemoryStorageFiles, stats.savedOriginals, stats.uniqueBlobs)
                            + "\n" + LocaleController.formatString(R.string.MorokMemoryStoragePending, stats.notDownloaded, stats.tooLarge)
                            + "\n" + LocaleController.formatString(R.string.MorokMemoryStorageProblems, stats.unavailable, stats.storageErrors)
                            + "\n\n" + t(R.string.MorokMemoryLimits)
                            + (store.hasCaptureGap() ? "\n\n" + t(R.string.MorokMemoryCaptureGap) : ""))
                    .setNegativeButton(t(R.string.Close), null)
                    .setPositiveButton(t(R.string.MorokMemoryClear), (dialog, which) -> showDialog(new AlertDialog.Builder(getParentActivity())
                            .setTitle(t(R.string.MorokMemoryClear)).setMessage(t(R.string.MorokMemoryClearConfirm))
                            .setNegativeButton(t(R.string.Cancel), null).setPositiveButton(t(R.string.Delete), (d, w) ->
                                    store.clear((done, failure) -> { if (failure != null) toast(t(R.string.MorokMemoryStorageError)); refresh(); })).create())).create());
        });
    }

    private static String snapshotStatus(MemoryCard card, MemoryCard.Snapshot snapshot) {
        String value = (card.imported ? t(R.string.MorokMemoryImported) + " · "
                : card.automatic ? t(R.string.MorokMemoryAutomatic) + " · " : "")
                + (card.deletedInTelegram ? t(R.string.MorokMemoryDeleted) : t(R.string.MorokMemoryLocalSnapshot));
        int label = fileStateLabel(snapshot.fileState);
        if (label == 0) return value;
        return value + "\n" + t(label) + (snapshot.fileName.isEmpty() ? "" : " · " + snapshot.fileName);
    }

    private static int fileStateLabel(String state) {
        switch (state) {
            case "saved": return R.string.MorokMemoryFileSaved;
            case "not_downloaded": return R.string.MorokMemoryFileNotDownloaded;
            case "too_large": return R.string.MorokMemoryFileTooLarge;
            case "storage_error": return R.string.MorokMemoryFileError;
            case "unavailable": return R.string.MorokMemoryFileUnavailable;
            default: return 0;
        }
    }

    private static String date(long time) { return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(time)); }
    private static String reminderLabel(long time) { return time > 0 ? t(R.string.MorokMemoryRemind) + " · " + date(time) : t(R.string.MorokMemoryRemind); }
    private static String excerpt(String text, int max) { return text.length() <= max ? text : text.substring(0, max) + "…"; }
    private static String t(int resource) { return LocaleController.getString(resource); }
    private void toast(String message) { if (getParentActivity() != null) Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show(); }

    private static TextView text(Context context, String value, int size) {
        TextView view = new TextView(context); view.setText(value); view.setTextSize(size);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8)); return view;
    }
    private static EditText field(Context context, String hint, String value, int limit) {
        EditText view = new EditText(context); view.setTextSize(16); view.setHint(hint); view.setText(value);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        view.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)}); return view;
    }
    private static Button button(Context context, String label) {
        Button button = new Button(context); button.setText(label); button.setAllCaps(false);
        button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)); return button;
    }
    private static CheckBox check(Context context, String label, boolean checked) {
        CheckBox box = new CheckBox(context); box.setText(label); box.setChecked(checked);
        box.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)); return box;
    }

    private final class CardsAdapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public int getItemCount() { return visible.size(); }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext()); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(8), AndroidUtilities.dp(18), AndroidUtilities.dp(8));
            row.addView(text(parent.getContext(), "", 16)); row.addView(text(parent.getContext(), "", 13));
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2)); return new RecyclerListView.Holder(row);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            MemoryCard card = visible.get(position); LinearLayout row = (LinearLayout) holder.itemView;
            ((TextView) row.getChildAt(0)).setText((card.completed ? "✓ " : "") + card.source + "\n" + excerpt(card.latest().text, 240));
            ((TextView) row.getChildAt(1)).setText((card.tags.isEmpty() ? "" : card.tags + " · ")
                    + (card.reminderAt > 0 ? date(card.reminderAt) + "\n" : "") + snapshotStatus(card, card.latest()));
        }
    }
}
