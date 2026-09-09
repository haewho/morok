package org.morok.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.morok.templates.MorokReplyTemplateStore;
import org.morok.templates.ReplyTemplate;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/** Manages encrypted reply templates and optionally returns plain text to a chat composer. */
public final class MorokReplyTemplatesActivity extends BaseFragment {
    public interface Selection { void selected(String text); }

    private static final int ADD = 1;
    private static final int CLEAR = 2;
    private final long expectedUserId;
    private final Selection selection;
    private MorokReplyTemplateStore store;
    private final ArrayList<ReplyTemplate> all = new ArrayList<>();
    private final ArrayList<ReplyTemplate> visible = new ArrayList<>();
    private RecyclerListView list;
    private Adapter adapter;
    private EditText search;
    private TextView status;
    private boolean destroyed;

    public MorokReplyTemplatesActivity(int account) { this(account, null); }

    public MorokReplyTemplatesActivity(int account, Selection selection) {
        setCurrentAccount(account);
        expectedUserId = UserConfig.getInstance(account).getClientUserId();
        this.selection = selection;
    }

    @Override public boolean onFragmentCreate() {
        if (expectedUserId <= 0 || Build.VERSION.SDK_INT < 23) {
            Toast.makeText(org.telegram.messenger.ApplicationLoader.applicationContext,
                    text(R.string.MorokTemplatesUnavailable), Toast.LENGTH_LONG).show();
            return false;
        }
        try { store = MorokReplyTemplateStore.forAccount(currentAccount); }
        catch (RuntimeException error) { return false; }
        return super.onFragmentCreate();
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(selection == null ? R.string.MorokTemplatesTitle : R.string.MorokTemplatesInsertTitle));
        actionBar.createMenu().addItemWithWidth(ADD, R.drawable.msg_add, AndroidUtilities.dp(48), text(R.string.Add));
        if (selection == null) {
            actionBar.createMenu().addItemWithWidth(CLEAR, R.drawable.msg_delete, AndroidUtilities.dp(48),
                    text(R.string.MorokTemplatesClearAll));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == ADD) edit(null);
                else if (id == CLEAR) confirmClear();
            }
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;
        search = field(context, text(R.string.MorokTemplatesSearch), 256, true);
        search.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        root.addView(search, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(52)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        status = new TextView(context);
        status.setText(text(R.string.MorokTemplatesLoading));
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
            ReplyTemplate template = visible.get(position);
            if (selection != null) {
                selection.selected(template.body);
                finishFragment();
            } else {
                edit(template);
            }
        });
        list.setOnItemLongClickListener((view, position) -> {
            if (selection != null) return false;
            if (position < 0 || position >= visible.size()) return false;
            confirmRemove(visible.get(position));
            return true;
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
        status.setText(text(R.string.MorokTemplatesLoading));
        store.list((values, error) -> {
            if (!active() || status == null) return;
            if (error != null || values == null) {
                status.setText(text(R.string.MorokTemplatesStorageError));
                return;
            }
            all.clear(); all.addAll(values); applyFilter();
        });
    }

    private void applyFilter() {
        String query = search == null ? "" : search.getText().toString();
        visible.clear();
        for (ReplyTemplate template : all) if (template.matches(query)) visible.add(template);
        if (adapter != null) adapter.notifyDataSetChanged();
        if (status != null) {
            if (visible.isEmpty()) status.setText(text(R.string.MorokTemplatesEmpty));
            else status.setText(LocaleController.formatString(R.string.MorokTemplatesCount,
                    visible.size(), MorokReplyTemplateStore.MAX_TEMPLATES));
        }
    }

    private void edit(ReplyTemplate template) {
        if (!active() || getParentActivity() == null) return;
        Context context = getParentActivity();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), AndroidUtilities.dp(10));
        EditText title = field(context, text(R.string.MorokTemplatesNameHint), ReplyTemplate.MAX_TITLE_LENGTH, true);
        EditText body = field(context, text(R.string.MorokTemplatesBodyHint), ReplyTemplate.MAX_BODY_LENGTH, false);
        if (template != null) { title.setText(template.title); body.setText(template.body); }
        box.addView(title, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(54)));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(180));
        bodyParams.topMargin = AndroidUtilities.dp(8);
        box.addView(body, bodyParams);
        box.addView(info(context, text(R.string.MorokTemplatesEditorInfo)));
        AlertDialog editor = new AlertDialog.Builder(context)
                .setTitle(text(template == null ? R.string.MorokTemplatesAdd : R.string.MorokTemplatesEdit))
                .setView(box)
                .setNegativeButton(text(R.string.Cancel), null)
                .setPositiveButton(text(R.string.Save), null)
                .create();
        editor.setOnShowListener(ignored -> editor.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String nextTitle = title.getText().toString().trim();
                    String nextBody = body.getText().toString().trim();
                    if (TextUtils.isEmpty(nextTitle) || TextUtils.isEmpty(nextBody)) {
                        toast(text(R.string.MorokTemplatesRequired));
                        return;
                    }
                    view.setEnabled(false);
                    MorokReplyTemplateStore.Callback<ReplyTemplate> callback = (value, error) -> {
                        if (!active()) return;
                        if (error != null || value == null) {
                            view.setEnabled(true);
                            toast(text(R.string.MorokTemplatesStorageError));
                        } else {
                            editor.dismiss();
                            refresh();
                        }
                    };
                    if (template == null) store.create(nextTitle, nextBody, callback);
                    else store.update(template.id, nextTitle, nextBody, callback);
                }));
        showDialog(editor);
    }

    private void confirmRemove(ReplyTemplate template) {
        if (!active() || getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.MorokTemplatesDelete))
                .setMessage(template.title)
                .setNegativeButton(text(R.string.Cancel), null)
                .setPositiveButton(text(R.string.Delete), (dialog, which) -> store.remove(template.id, (value, error) -> {
                    if (!active()) return;
                    if (error != null) toast(text(R.string.MorokTemplatesStorageError)); else refresh();
                })).create());
    }

    private void confirmClear() {
        if (!active() || all.isEmpty() || getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.MorokTemplatesClearAll))
                .setMessage(text(R.string.MorokTemplatesClearAllInfo))
                .setNegativeButton(text(R.string.Cancel), null)
                .setPositiveButton(text(R.string.Delete), (dialog, which) -> store.clear((value, error) -> {
                    if (!active()) return;
                    if (error != null) toast(text(R.string.MorokTemplatesStorageError)); else refresh();
                })).create());
    }

    private static EditText field(Context context, String hint, int limit, boolean singleLine) {
        EditText field = new EditText(context);
        field.setTextSize(16);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        field.setHint(hint);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
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

    private static TextView info(Context context, String value) {
        TextView view = new TextView(context);
        view.setText(value); view.setTextSize(13);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        view.setPadding(0, AndroidUtilities.dp(8), 0, 0);
        return view;
    }

    private static String excerpt(String value, int limit) {
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= limit ? oneLine : oneLine.substring(0, limit) + "…";
    }

    private void toast(String message) {
        if (getParentActivity() != null) Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
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
            TextView body = new TextView(parent.getContext());
            body.setTextSize(13); body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            body.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            row.addView(title); row.addView(body);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerListView.Holder(row);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ReplyTemplate template = visible.get(position);
            LinearLayout row = (LinearLayout) holder.itemView;
            ((TextView) row.getChildAt(0)).setText(template.title);
            ((TextView) row.getChildAt(1)).setText(excerpt(template.body, 220));
        }
    }
}
