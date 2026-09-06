package org.morok.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.morok.camera.RoundVideoDiagnostics;
import org.morok.settings.MorokSettings;
import org.morok.settings.RoundVideoSettings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/** Device-local opt-in controls for the existing Telegram instant-camera encoder. */
public final class MorokRoundVideoActivity extends BaseFragment {
    private static final int ENHANCED = 1, PROFILE = 2, RESET = 3, DIAGNOSTICS = 4, VIEW_DIAGNOSTICS = 5,
            CLEAR_DIAGNOSTICS = 6;
    private static final int HEADER = 0, CHECK = 1, ACTION = 2, INFO = 3;
    private final ArrayList<Row> rows = new ArrayList<>();
    private Adapter adapter;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokRoundVideoTitle));
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
            RoundVideoSettings settings = MorokSettings.roundVideo();
            if (rows.get(position).id == ENHANCED) {
                apply(settings.withEnhanced(!settings.enhanced));
            } else if (rows.get(position).id == PROFILE) {
                CharSequence[] labels = {text(R.string.MorokRoundVideoAuto),
                        text(R.string.MorokRoundVideoSaver), text(R.string.MorokRoundVideoHigh)};
                String[] values = {RoundVideoSettings.PROFILE_AUTO,
                        RoundVideoSettings.PROFILE_SAVER, RoundVideoSettings.PROFILE_HIGH};
                showDialog(new AlertDialog.Builder(context).setTitle(text(R.string.MorokRoundVideoQuality))
                        .setItems(labels, (dialog, which) -> apply(settings.withProfile(values[which]))).create());
            } else if (rows.get(position).id == RESET) {
                showDialog(new AlertDialog.Builder(context).setTitle(text(R.string.MorokRoundVideoReset))
                        .setMessage(text(R.string.MorokRoundVideoResetInfo))
                        .setPositiveButton(text(R.string.Reset), (dialog, which) -> apply(RoundVideoSettings.DEFAULT))
                        .setNegativeButton(text(R.string.Cancel), null).create());
            } else if (rows.get(position).id == DIAGNOSTICS) {
                boolean enabled = !RoundVideoDiagnostics.enabled();
                RoundVideoDiagnostics.setEnabled(enabled);
                if (enabled) RoundVideoDiagnostics.record("diagnostics", "enabled=true");
                rebuildRows();
            } else if (rows.get(position).id == VIEW_DIAGNOSTICS) {
                showDiagnostics(context);
            } else if (rows.get(position).id == CLEAR_DIAGNOSTICS) {
                showDialog(new AlertDialog.Builder(context).setTitle(text(R.string.MorokRoundVideoDiagnosticsClear))
                        .setMessage(text(R.string.MorokRoundVideoDiagnosticsClearInfo))
                        .setPositiveButton(text(R.string.Clear), (dialog, which) -> {
                            RoundVideoDiagnostics.clear(); rebuildRows();
                        }).setNegativeButton(text(R.string.Cancel), null).create());
            }
        });
        rebuildRows();
        return fragmentView;
    }

    private void apply(RoundVideoSettings settings) {
        try {
            MorokSettings.setRoundVideo(settings);
            rebuildRows();
        } catch (IllegalStateException newerSchema) {
            showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokRoundVideoTitle))
                    .setMessage(text(R.string.MorokSettingsNewerVersion))
                    .setPositiveButton(text(R.string.OK), null).create());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        rows.add(new Row(INFO, 0, text(R.string.MorokRoundVideoExperimentalInfo)));
        rows.add(new Row(HEADER, 0, text(R.string.MorokRoundVideoCameraHeader)));
        rows.add(new Row(CHECK, ENHANCED, text(R.string.MorokRoundVideoEnhanced)));
        rows.add(new Row(INFO, 0, text(R.string.MorokRoundVideoEnhancedInfo)));
        rows.add(new Row(ACTION, PROFILE, text(R.string.MorokRoundVideoQuality)));
        rows.add(new Row(INFO, 0, text(R.string.MorokRoundVideoProfileInfo)));
        rows.add(new Row(INFO, 0, text(R.string.MorokRoundVideoStabilizationInfo)));
        rows.add(new Row(ACTION, RESET, text(R.string.MorokRoundVideoReset)));
        rows.add(new Row(HEADER, 0, text(R.string.MorokRoundVideoDiagnosticsHeader)));
        rows.add(new Row(CHECK, DIAGNOSTICS, text(R.string.MorokRoundVideoDiagnosticsEnabled)));
        rows.add(new Row(INFO, 0, text(R.string.MorokRoundVideoDiagnosticsInfo)));
        rows.add(new Row(ACTION, VIEW_DIAGNOSTICS, text(R.string.MorokRoundVideoDiagnosticsView)));
        rows.add(new Row(ACTION, CLEAR_DIAGNOSTICS, text(R.string.MorokRoundVideoDiagnosticsClear)));
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showDiagnostics(Context context) {
        String report = RoundVideoDiagnostics.report();
        TextView content = new TextView(context);
        content.setText(report); content.setTextIsSelectable(true); content.setTextSize(13);
        content.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        int padding = org.telegram.messenger.AndroidUtilities.dp(18);
        content.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(context); scroll.addView(content);
        showDialog(new AlertDialog.Builder(context).setTitle(text(R.string.MorokRoundVideoDiagnosticsView))
                .setView(scroll).setNegativeButton(text(R.string.Close), null)
                .setPositiveButton(text(R.string.Copy), (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("MOROK round-video diagnostics", report));
                        Toast.makeText(context, text(R.string.TextCopied), Toast.LENGTH_SHORT).show();
                    }
                }).create());
    }

    private static String profileLabel(RoundVideoSettings settings) {
        if (RoundVideoSettings.PROFILE_HIGH.equals(settings.profile)) return text(R.string.MorokRoundVideoHigh);
        if (RoundVideoSettings.PROFILE_SAVER.equals(settings.profile)) return text(R.string.MorokRoundVideoSaver);
        return text(R.string.MorokRoundVideoAuto);
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
            return holder.getItemViewType() == CHECK || holder.getItemViewType() == ACTION;
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
            } else if (row.type == INFO) {
                ((TextInfoPrivacyCell) holder.itemView).setText(row.title);
                ((TextInfoPrivacyCell) holder.itemView).setTextColorByKey(Theme.key_windowBackgroundWhiteGrayText4);
            } else if (row.type == CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setColors(Theme.key_windowBackgroundWhiteBlackText, Theme.key_switchTrack,
                        Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                cell.setTextAndCheck(row.title, row.id == ENHANCED
                        ? MorokSettings.roundVideo().enhanced : RoundVideoDiagnostics.enabled(), false);
            } else {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (row.id == PROFILE) cell.setTextAndValue(row.title, profileLabel(MorokSettings.roundVideo()), true);
                else if (row.id == VIEW_DIAGNOSTICS) cell.setTextAndValue(row.title,
                        LocaleController.formatString(R.string.MorokRoundVideoDiagnosticsEvents,
                                RoundVideoDiagnostics.count()), true);
                else cell.setText(row.title, false);
                cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
        }
    }
}
