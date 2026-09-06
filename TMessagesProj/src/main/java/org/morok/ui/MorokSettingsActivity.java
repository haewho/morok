package org.morok.ui;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.morok.appearance.MorokAppearance;
import org.morok.settings.AppearanceSettings;
import org.morok.settings.MorokSettings;
import org.morok.settings.PrivacySettings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LiteModeSettingsActivity;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;
import java.util.Locale;

/** Native settings entry point. Controls are limited to wired, local behavior. */
public final class MorokSettingsActivity extends BaseFragment {
    private static final int GLASS = 1, REDUCED = 2, THEMES = 3, POWER = 4, RESET = 5, MEMORY = 6, PROXY = 7, PRIVACY = 8;
    private static final int HEADER = 0, CHECK = 1, ACTION = 2, INFO = 3;
    private final ArrayList<Row> rows = new ArrayList<>();
    private Adapter adapter;
    private String query = "";

    public MorokSettingsActivity(int account) {
        super();
        currentAccount = account;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        actionBar.createMenu().addItem(0, R.drawable.outline_header_search).setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onTextChanged(EditText editText) {
                        query = editText.getText().toString().trim().toLowerCase(Locale.ROOT);
                        rebuildRows();
                    }
                    @Override
                    public void onSearchCollapse() {
                        query = "";
                        rebuildRows();
                    }
                }).setSearchFieldHint(text(R.string.Search));

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
            AppearanceSettings settings = MorokSettings.appearance();
            switch (rows.get(position).id) {
                case GLASS:
                    apply(settings.withLiquidGlass(!settings.liquidGlass));
                    break;
                case REDUCED:
                    apply(settings.withReducedEffects(!settings.reducedEffects));
                    break;
                case THEMES:
                    presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                    break;
                case POWER:
                    presentFragment(new LiteModeSettingsActivity());
                    break;
                case PROXY:
                    presentFragment(new MorokProxyActivity(currentAccount));
                    break;
                case MEMORY:
                    presentFragment(new MorokMemoryActivity(currentAccount));
                    break;
                case PRIVACY:
                    presentFragment(new MorokPrivacyActivity(currentAccount));
                    break;
                case RESET:
                    showDialog(new AlertDialog.Builder(context)
                            .setTitle(text(R.string.MorokResetAppearance))
                            .setMessage(text(R.string.MorokResetAppearanceInfo))
                            .setPositiveButton(text(R.string.Reset), (dialog, which) -> apply(AppearanceSettings.DEFAULT))
                            .setNegativeButton(text(R.string.Cancel), null).create());
                    break;
            }
        });
        rebuildRows();
        return fragmentView;
    }

    private void apply(AppearanceSettings settings) {
        try {
            MorokAppearance.apply(settings, getParentActivity());
            rebuildRows();
        } catch (IllegalStateException e) {
            showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.MorokSettings))
                    .setMessage(text(R.string.MorokSettingsNewerVersion))
                    .setPositiveButton(text(R.string.OK), null).create());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (fragmentView != null) fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        if (query.isEmpty()) add(INFO, 0, R.string.MorokAboutClient);
        add(HEADER, 0, R.string.MorokAppearance);
        add(CHECK, GLASS, R.string.MorokLiquidGlass);
        if (query.isEmpty()) {
            add(INFO, 0, Build.VERSION.SDK_INT < 33 ? R.string.MorokLiquidGlassLegacyInfo :
                    LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? R.string.MorokLiquidGlassInfo :
                            R.string.MorokLiquidGlassUnavailableInfo);
        }
        add(CHECK, REDUCED, R.string.MorokReducedEffects);
        if (query.isEmpty()) add(INFO, 0, R.string.MorokReducedEffectsInfo);
        add(ACTION, THEMES, R.string.MorokThemes);
        add(ACTION, POWER, R.string.MorokPowerSettings);
        add(ACTION, RESET, R.string.MorokResetAppearance);
        if (query.isEmpty()) add(INFO, 0, R.string.MorokDeviceSettingsInfo);
        add(HEADER, 0, R.string.MorokPrivacyTitle);
        add(ACTION, PRIVACY, R.string.MorokPrivacyShortcut);
        if (query.isEmpty()) add(INFO, 0, R.string.MorokPrivacyShortcutInfo);
        add(HEADER, 0, R.string.MorokLocalTools);
        add(ACTION, MEMORY, R.string.MorokMemoryShortcut);
        add(ACTION, PROXY, R.string.MorokProxyTitle);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void add(int type, int id, int string) {
        String title = text(string);
        if (query.isEmpty() || (id != 0 && title.toLowerCase(Locale.ROOT).contains(query))) {
            rows.add(new Row(type, id, title));
        }
    }

    private static String text(int id) { return LocaleController.getString(id); }

    private static final class Row {
        final int type, id;
        final String title;
        Row(int type, int id, String title) { this.type = type; this.id = id; this.title = title; }
    }

    private final class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public int getItemCount() { return rows.size(); }
        @Override
        public int getItemViewType(int position) { return rows.get(position).type; }
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == CHECK || holder.getItemViewType() == ACTION;
        }
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View view;
            if (type == HEADER) view = new HeaderCell(parent.getContext());
            else if (type == CHECK) view = new TextCheckCell(parent.getContext());
            else if (type == INFO) view = new TextInfoPrivacyCell(parent.getContext());
            else view = new TextSettingsCell(parent.getContext());
            if (type != INFO) view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (row.type != INFO) holder.itemView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            if (row.type == HEADER) {
                ((HeaderCell) holder.itemView).setText(row.title);
                ((HeaderCell) holder.itemView).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            } else if (row.type == INFO) {
                ((TextInfoPrivacyCell) holder.itemView).setText(row.title);
                ((TextInfoPrivacyCell) holder.itemView).setTextColorByKey(Theme.key_windowBackgroundWhiteGrayText4);
            } else if (row.type == CHECK) {
                AppearanceSettings settings = MorokSettings.appearance();
                ((TextCheckCell) holder.itemView).setColors(Theme.key_windowBackgroundWhiteBlackText,
                        Theme.key_switchTrack, Theme.key_switchTrackChecked,
                        Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                ((TextCheckCell) holder.itemView).setTextAndCheck(row.title,
                        row.id == GLASS ? settings.liquidGlass : settings.reducedEffects, false);
            } else {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (row.id == PRIVACY) {
                    String value = text(R.string.MorokPrivacyLoginStatus);
                    if (UserConfig.getInstance(currentAccount).isClientActivated()) {
                        try {
                            PrivacySettings privacy = MorokSettings.privacy(currentAccount);
                            value = text(privacy.ghostPreset ? R.string.MorokGhostActiveStatus
                                    : privacy.hidesTyping() || privacy.hidesOnline() || privacy.hidesContentRead() || privacy.hidesRead()
                                            || privacy.hidesStoryViews() || privacy.markReadOnReply || !privacy.normalBehaviorChats.isEmpty()
                                            ? R.string.MorokPrivacyCustomStatus : R.string.MorokPrivacyOffStatus);
                        } catch (RuntimeException ignored) {}
                    }
                    cell.setTextAndValue(row.title, value, true);
                } else {
                    cell.setText(row.title, true);
                }
                cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
        }
    }
}
