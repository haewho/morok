package org.morok.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.morok.settings.MorokSettings;
import org.morok.settings.PrivacySettings;
import org.telegram.messenger.LocaleController;
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
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/** Account-local experimental controls; only behavior with semantic hooks is exposed here. */
public final class MorokPrivacyActivity extends BaseFragment {
    private static final int GHOST = 1, HIDE_TYPING = 2, HIDE_ONLINE = 3, HIDE_CONTENT_READ = 4,
            HIDE_READ = 5, HIDE_STORY_VIEWS = 6, MARK_READ_ON_REPLY = 7, RESET = 8;
    private static final int HEADER = 0, CHECK = 1, ACTION = 2, INFO = 3;
    private final ArrayList<Row> rows = new ArrayList<>();
    private Adapter adapter;

    public MorokPrivacyActivity(int account) {
        currentAccount = account;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokPrivacyTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
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
            if (!isAvailable() || position < 0 || position >= rows.size()) return;
            PrivacySettings settings = settings();
            switch (rows.get(position).id) {
                case GHOST:
                    apply(settings.withGhostPreset(!settings.ghostPreset));
                    break;
                case HIDE_TYPING:
                    apply(settings.withHideTyping(!settings.hideTyping));
                    break;
                case HIDE_ONLINE:
                    apply(settings.withHideOnline(!settings.hideOnline));
                    break;
                case HIDE_CONTENT_READ:
                    apply(settings.withHideContentRead(!settings.hideContentRead));
                    break;
                case HIDE_READ:
                    apply(settings.withHideRead(!settings.hideRead));
                    break;
                case HIDE_STORY_VIEWS:
                    apply(settings.withHideStoryViews(!settings.hideStoryViews));
                    break;
                case MARK_READ_ON_REPLY:
                    apply(settings.withMarkReadOnReply(!settings.markReadOnReply));
                    break;
                case RESET:
                    showDialog(new AlertDialog.Builder(context)
                            .setTitle(text(R.string.MorokPrivacyReset))
                            .setMessage(text(R.string.MorokPrivacyResetInfo))
                            .setPositiveButton(text(R.string.Reset), (dialog, which) -> apply(PrivacySettings.DEFAULT))
                            .setNegativeButton(text(R.string.Cancel), null).create());
                    break;
            }
        });
        rebuildRows();
        return fragmentView;
    }

    private boolean isAvailable() {
        return currentAccount >= 0 && currentAccount < UserConfig.MAX_ACCOUNT_COUNT
                && UserConfig.getInstance(currentAccount).isClientActivated();
    }

    private PrivacySettings settings() {
        try {
            return MorokSettings.privacy(currentAccount);
        } catch (RuntimeException unavailableAccountOrSettings) {
            return PrivacySettings.DEFAULT;
        }
    }

    private void apply(PrivacySettings settings) {
        try {
            MorokSettings.setPrivacy(currentAccount, settings);
            rebuildRows();
        } catch (IllegalStateException error) {
            showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokPrivacyTitle))
                    .setMessage(text(R.string.MorokSettingsNewerVersion))
                    .setPositiveButton(text(R.string.OK), null).create());
        }
    }

    private void rebuildRows() {
        rows.clear();
        rows.add(new Row(INFO, 0, text(R.string.MorokPrivacyExperimentalInfo)));
        if (isAvailable()) {
            rows.add(new Row(HEADER, 0, text(R.string.MorokPrivacyActivityHeader)));
            rows.add(new Row(CHECK, GHOST, text(R.string.MorokGhostPreset)));
            rows.add(new Row(INFO, 0, text(R.string.MorokGhostPresetInfo)));
            rows.add(new Row(CHECK, HIDE_TYPING, text(R.string.MorokHideTyping)));
            rows.add(new Row(INFO, 0, text(R.string.MorokHideTypingInfo)));
            rows.add(new Row(INFO, 0, text(settings().hidesTyping()
                    ? R.string.MorokTypingSuppressedStatus : R.string.MorokTypingNormalStatus)));
            rows.add(new Row(CHECK, HIDE_ONLINE, text(R.string.MorokHideOnline)));
            rows.add(new Row(INFO, 0, text(R.string.MorokHideOnlineInfo)));
            rows.add(new Row(INFO, 0, text(settings().hidesOnline()
                    ? R.string.MorokOnlineSuppressedStatus : R.string.MorokOnlineNormalStatus)));
            rows.add(new Row(CHECK, HIDE_CONTENT_READ, text(R.string.MorokHideContentRead)));
            rows.add(new Row(INFO, 0, text(R.string.MorokHideContentReadInfo)));
            rows.add(new Row(INFO, 0, text(settings().hidesContentRead()
                    ? R.string.MorokContentReadSuppressedStatus : R.string.MorokContentReadNormalStatus)));
            rows.add(new Row(CHECK, HIDE_READ, text(R.string.MorokHideRead)));
            rows.add(new Row(INFO, 0, text(R.string.MorokHideReadInfo)));
            rows.add(new Row(INFO, 0, text(settings().hidesRead()
                    ? R.string.MorokReadSuppressedStatus : R.string.MorokReadNormalStatus)));
            rows.add(new Row(CHECK, HIDE_STORY_VIEWS, text(R.string.MorokHideStoryViews)));
            rows.add(new Row(INFO, 0, text(R.string.MorokHideStoryViewsInfo)));
            rows.add(new Row(INFO, 0, text(settings().hidesStoryViews()
                    ? R.string.MorokStoryViewsSuppressedStatus : R.string.MorokStoryViewsNormalStatus)));
            rows.add(new Row(CHECK, MARK_READ_ON_REPLY, text(R.string.MorokMarkReadOnReply)));
            rows.add(new Row(INFO, 0, text(R.string.MorokMarkReadOnReplyInfo)));
            rows.add(new Row(INFO, 0, text(R.string.MorokChatExceptionsInfo)));
            rows.add(new Row(ACTION, RESET, text(R.string.MorokPrivacyReset)));
        } else {
            rows.add(new Row(INFO, 0, text(R.string.MorokPrivacyLoginRequired)));
        }
        if (adapter != null) adapter.notifyDataSetChanged();
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
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return isAvailable() && (holder.getItemViewType() == CHECK || holder.getItemViewType() == ACTION);
        }
        @NonNull
        @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View view;
            if (type == HEADER) view = new HeaderCell(parent.getContext());
            else if (type == CHECK) view = new TextCheckCell(parent.getContext());
            else if (type == INFO) view = new TextInfoPrivacyCell(parent.getContext());
            else view = new TextSettingsCell(parent.getContext());
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
            } else if (row.type == CHECK) {
                PrivacySettings settings = settings();
                ((TextCheckCell) holder.itemView).setColors(Theme.key_windowBackgroundWhiteBlackText,
                        Theme.key_switchTrack, Theme.key_switchTrackChecked,
                        Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                boolean checked = row.id == GHOST ? settings.ghostPreset
                        : row.id == HIDE_TYPING ? settings.hideTyping
                        : row.id == HIDE_ONLINE ? settings.hideOnline
                        : row.id == HIDE_CONTENT_READ ? settings.hideContentRead
                        : row.id == HIDE_READ ? settings.hideRead
                        : row.id == HIDE_STORY_VIEWS ? settings.hideStoryViews : settings.markReadOnReply;
                ((TextCheckCell) holder.itemView).setTextAndCheck(row.title, checked, false);
            } else {
                ((TextSettingsCell) holder.itemView).setText(row.title, false);
                ((TextSettingsCell) holder.itemView).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
        }
    }
}
