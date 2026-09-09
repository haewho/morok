package org.morok.ui;

import android.content.Context;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.morok.chatmeta.ChatMetadata;
import org.morok.chatmeta.MorokChatMetadataStore;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** Editor for one encrypted, account-local dialog alias and note. */
public final class MorokChatMetadataActivity extends BaseFragment {
    public interface AliasChanged { void changed(String alias); }

    private static final int SAVE = 1;
    private static final int CLEAR = 2;
    private final long dialogId;
    private final long expectedUserId;
    private final String sourceTitle;
    private final AliasChanged aliasChanged;
    private MorokChatMetadataStore store;
    private EditText alias;
    private EditText note;
    private TextView status;
    private boolean destroyed;
    private boolean busy;

    public MorokChatMetadataActivity(int account, long dialogId, String sourceTitle, AliasChanged aliasChanged) {
        setCurrentAccount(account);
        this.dialogId = dialogId;
        this.expectedUserId = UserConfig.getInstance(account).getClientUserId();
        this.sourceTitle = sourceTitle == null ? "" : sourceTitle;
        this.aliasChanged = aliasChanged;
    }

    @Override
    public boolean onFragmentCreate() {
        if (dialogId == 0 || expectedUserId <= 0 || Build.VERSION.SDK_INT < 23) {
            Toast.makeText(org.telegram.messenger.ApplicationLoader.applicationContext,
                    text(R.string.MorokChatMetadataUnavailable), Toast.LENGTH_LONG).show();
            return false;
        }
        try { store = MorokChatMetadataStore.forAccount(currentAccount); }
        catch (RuntimeException error) { return false; }
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokChatMetadataTitle));
        if (!sourceTitle.isEmpty()) actionBar.setSubtitle(sourceTitle);
        actionBar.createMenu().addItemWithWidth(SAVE, R.drawable.ic_ab_done, AndroidUtilities.dp(56), text(R.string.Save));
        actionBar.createMenu().addItemWithWidth(CLEAR, R.drawable.msg_delete, AndroidUtilities.dp(48), text(R.string.MorokChatMetadataClear));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == SAVE) save();
                else if (id == CLEAR) confirmClear();
            }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        fragmentView = scroll;

        content.addView(label(context, text(R.string.MorokChatMetadataAliasHeader), 14));
        alias = field(context, text(R.string.MorokChatMetadataAliasHint), ChatMetadata.MAX_ALIAS_LENGTH, true);
        content.addView(alias, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(54)));
        content.addView(info(context, text(R.string.MorokChatMetadataAliasInfo)));
        content.addView(label(context, text(R.string.MorokChatMetadataNoteHeader), 14));
        note = field(context, text(R.string.MorokChatMetadataNoteHint), ChatMetadata.MAX_NOTE_LENGTH, false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(180));
        noteParams.topMargin = AndroidUtilities.dp(6);
        content.addView(note, noteParams);
        content.addView(info(context, text(R.string.MorokChatMetadataPrivacyInfo)));
        status = info(context, text(R.string.MorokChatMetadataLoading));
        content.addView(status);
        setBusy(true);
        load();
        return fragmentView;
    }

    @Override public void onFragmentDestroy() { destroyed = true; super.onFragmentDestroy(); }

    private boolean active() {
        return !destroyed && store != null && store.isActive()
                && UserConfig.getInstance(currentAccount).getClientUserId() == expectedUserId;
    }

    private void load() {
        store.get(dialogId, (value, error) -> {
            if (!active() || alias == null || note == null) return;
            setBusy(false);
            if (error != null || value == null) {
                status.setText(text(R.string.MorokChatMetadataStorageError));
                return;
            }
            alias.setText(value.alias);
            alias.setSelection(alias.length());
            note.setText(value.note);
            status.setText(value.isEmpty() ? text(R.string.MorokChatMetadataEmpty) : text(R.string.MorokChatMetadataSavedLocal));
        });
    }

    private void save() {
        if (!active() || busy) return;
        setBusy(true);
        store.save(dialogId, alias.getText().toString(), note.getText().toString(), (value, error) -> {
            if (!active()) return;
            setBusy(false);
            if (error != null || value == null) {
                status.setText(text(R.string.MorokChatMetadataStorageError));
                toast(text(R.string.MorokChatMetadataStorageError));
                return;
            }
            if (aliasChanged != null) aliasChanged.changed(value.alias);
            toast(text(R.string.MorokChatMetadataSaved));
            finishFragment();
        });
    }

    private void confirmClear() {
        if (!active() || busy || getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(text(R.string.MorokChatMetadataClear))
                .setMessage(text(R.string.MorokChatMetadataClearInfo))
                .setNegativeButton(text(R.string.Cancel), null)
                .setPositiveButton(text(R.string.Delete), (dialog, which) -> {
                    alias.setText(""); note.setText(""); save();
                }).create());
    }

    private void setBusy(boolean value) {
        busy = value;
        if (alias != null) alias.setEnabled(!value);
        if (note != null) note.setEnabled(!value);
    }

    private static EditText field(Context context, String hint, int maxLength, boolean singleLine) {
        EditText field = new EditText(context);
        field.setTextSize(16);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        field.setHint(hint);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        field.setSaveEnabled(false);
        field.setSingleLine(singleLine);
        field.setGravity(singleLine ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (singleLine ? InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                : InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        if (Build.VERSION.SDK_INT >= 26) {
            field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            field.setImeOptions(field.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }
        return field;
    }

    private static TextView label(Context context, String value, int size) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        view.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(4));
        return view;
    }

    private static TextView info(Context context, String value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(13);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        view.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(16));
        return view;
    }

    private void toast(String value) {
        if (getParentActivity() != null) Toast.makeText(getParentActivity(), value, Toast.LENGTH_SHORT).show();
    }

    private static String text(int id) { return LocaleController.getString(id); }
}
