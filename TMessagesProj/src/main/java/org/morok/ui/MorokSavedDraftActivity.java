package org.morok.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.morok.drafts.MorokSavedDraftStore;
import org.morok.drafts.SavedDraft;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** Explicit save/restore screen for one encrypted plain-text chat draft snapshot. */
public final class MorokSavedDraftActivity extends BaseFragment {
    public interface Selection { void selected(String text); }

    private final long expectedUserId;
    private final long dialogId;
    private final long topicId;
    private final String label;
    private final String currentText;
    private final Selection selection;
    private MorokSavedDraftStore store;
    private SavedDraft saved;
    private TextView savedPreview;
    private TextView savedStatus;
    private TextView saveAction;
    private TextView restoreAction;
    private TextView deleteAction;
    private boolean destroyed;

    public MorokSavedDraftActivity(int account, long dialogId, long topicId, String label,
                                   String currentText, Selection selection) {
        setCurrentAccount(account);
        this.expectedUserId = UserConfig.getInstance(account).getClientUserId();
        this.dialogId = dialogId;
        this.topicId = Math.max(0, topicId);
        String cleanLabel = label == null ? "" : label.trim().replace('\n', ' ').replace('\r', ' ');
        this.label = cleanLabel.length() <= SavedDraft.MAX_LABEL_LENGTH
                ? cleanLabel : cleanLabel.substring(0, SavedDraft.MAX_LABEL_LENGTH);
        this.currentText = currentText == null ? "" : currentText;
        this.selection = selection;
    }

    @Override public boolean onFragmentCreate() {
        if (expectedUserId <= 0 || dialogId == 0 || Build.VERSION.SDK_INT < 23) {
            toastGlobal(text(R.string.MorokDraftUnavailable));
            return false;
        }
        try { store = MorokSavedDraftStore.forAccount(currentAccount); }
        catch (RuntimeException error) {
            toastGlobal(text(R.string.MorokDraftUnavailable));
            return false;
        }
        return super.onFragmentCreate();
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokDraftTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        fragmentView = scroll;

        root.addView(header(context, text(R.string.MorokDraftCurrent)));
        root.addView(preview(context, currentText.trim().isEmpty()
                ? text(R.string.MorokDraftCurrentEmpty) : currentText));
        saveAction = action(context, text(R.string.MorokDraftSave), this::saveCurrent);
        root.addView(saveAction);
        root.addView(info(context, text(R.string.MorokDraftSaveInfo)));

        root.addView(header(context, text(R.string.MorokDraftSaved)));
        savedStatus = info(context, text(R.string.MorokDraftLoading));
        root.addView(savedStatus);
        savedPreview = preview(context, "");
        savedPreview.setVisibility(View.GONE);
        root.addView(savedPreview);
        restoreAction = action(context, text(R.string.MorokDraftRestore), this::restore);
        deleteAction = action(context, text(R.string.MorokDraftDelete), this::confirmDelete);
        restoreAction.setVisibility(View.GONE);
        deleteAction.setVisibility(View.GONE);
        root.addView(restoreAction);
        root.addView(deleteAction);
        root.addView(info(context, text(R.string.MorokDraftRestoreInfo)));
        refresh();
        return fragmentView;
    }

    @Override public void onResume() { super.onResume(); if (savedStatus != null) refresh(); }
    @Override public void onFragmentDestroy() { destroyed = true; super.onFragmentDestroy(); }

    private boolean active() {
        return !destroyed && store != null && store.isActive()
                && UserConfig.getInstance(currentAccount).getClientUserId() == expectedUserId;
    }

    private void refresh() {
        if (!active()) { if (!destroyed) finishFragment(); return; }
        savedStatus.setText(text(R.string.MorokDraftLoading));
        store.get(dialogId, topicId, (value, error) -> {
            if (!active() || savedStatus == null) return;
            if (error != null) {
                saved = null;
                savedStatus.setText(text(R.string.MorokDraftStorageError));
                showSaved(false);
                return;
            }
            saved = value;
            savedStatus.setText(text(value == null ? R.string.MorokDraftNone : R.string.MorokDraftSavedReady));
            if (value != null) savedPreview.setText(value.text);
            showSaved(value != null);
        });
    }

    private void showSaved(boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        savedPreview.setVisibility(state);
        restoreAction.setVisibility(state);
        deleteAction.setVisibility(state);
    }

    private void saveCurrent() {
        if (!active()) return;
        if (currentText.trim().isEmpty()) {
            toast(text(R.string.MorokDraftNothingToSave));
            return;
        }
        if (currentText.length() > SavedDraft.MAX_TEXT_LENGTH) {
            toast(text(R.string.MorokDraftTooLong));
            return;
        }
        if (saved == null) persist();
        else if (getParentActivity() != null) {
            showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle(text(R.string.MorokDraftReplaceTitle))
                    .setMessage(text(R.string.MorokDraftReplaceSavedInfo))
                    .setNegativeButton(text(R.string.Cancel), null)
                    .setPositiveButton(text(R.string.Replace), (dialog, which) -> persist())
                    .create());
        }
    }

    private void persist() {
        if (!active()) return;
        saveAction.setEnabled(false);
        store.save(dialogId, topicId, label, currentText, (value, error) -> {
            if (!active()) return;
            saveAction.setEnabled(true);
            if (error != null || value == null) toast(text(R.string.MorokDraftStorageError));
            else {
                saved = value;
                toast(text(R.string.MorokDraftSavedToast));
                refresh();
            }
        });
    }

    private void restore() {
        if (!active() || saved == null || selection == null) return;
        selection.selected(saved.text);
        finishFragment();
    }

    private void confirmDelete() {
        if (!active() || saved == null || getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(text(R.string.MorokDraftDelete))
                .setMessage(text(R.string.MorokDraftDeleteInfo))
                .setNegativeButton(text(R.string.Cancel), null)
                .setPositiveButton(text(R.string.Delete), (dialog, which) -> store.remove(dialogId, topicId,
                        (value, error) -> {
                            if (!active()) return;
                            if (error != null) toast(text(R.string.MorokDraftStorageError));
                            else { saved = null; refresh(); }
                        })).create());
    }

    private static TextView header(Context context, String value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        view.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(18), AndroidUtilities.dp(18), AndroidUtilities.dp(8));
        return view;
    }

    private static TextView preview(Context context, String value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(16);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        view.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(16), AndroidUtilities.dp(18), AndroidUtilities.dp(16));
        view.setTextIsSelectable(true);
        view.setSaveEnabled(false);
        return view;
    }

    private static TextView action(Context context, String value, Runnable runnable) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(16);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        view.setBackground(Theme.getSelectorDrawable(false));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        view.setOnClickListener(ignored -> runnable.run());
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(54)));
        return view;
    }

    private static TextView info(Context context, String value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(13);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        view.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(16));
        return view;
    }

    private void toast(String message) {
        if (getParentActivity() != null) Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
    }

    private static void toastGlobal(String message) {
        Toast.makeText(org.telegram.messenger.ApplicationLoader.applicationContext, message, Toast.LENGTH_LONG).show();
    }

    private static String text(int id) { return LocaleController.getString(id); }
}
