package org.morok.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.morok.update.MorokUpdateManager;
import org.morok.update.SignedUpdateManifest;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;

/** User-triggered signed update flow. It performs no background checks or silent installs. */
public final class MorokUpdateActivity extends BaseFragment {
    private LinearLayout content;
    private MorokUpdateManager manager;
    private boolean destroyed;

    public MorokUpdateActivity(int account) { currentAccount = account; }

    @Override public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        manager = new MorokUpdateManager(org.telegram.messenger.ApplicationLoader.applicationContext);
        return true;
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokUpdateTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        fragmentView = scroll;
        rebuild();
        return fragmentView;
    }

    @Override public void onFragmentDestroy() {
        destroyed = true;
        if (manager != null) manager.destroy();
        super.onFragmentDestroy();
    }

    @Override public void onResume() { super.onResume(); rebuild(); }

    private void rebuild() {
        if (content == null || manager == null || destroyed) return;
        content.removeAllViews();
        header(R.string.MorokUpdateInstalledHeader);
        info(LocaleController.formatString(R.string.MorokUpdateInstalled,
                manager.installedVersionName(), manager.installedVersionCode()));
        info(text(R.string.MorokUpdateSecurityInfo));
        header(R.string.MorokUpdateCheckHeader);
        switch (manager.status()) {
            case UNCONFIGURED:
                info(text(R.string.MorokUpdateUnconfigured));
                break;
            case CHECKING:
                info(text(R.string.MorokUpdateChecking));
                break;
            case CURRENT:
                info(text(R.string.MorokUpdateCurrent));
                break;
            case DOWNLOADING:
                info(text(R.string.MorokUpdateDownloading));
                break;
            case ERROR:
                info(text(R.string.MorokUpdateError));
                break;
            case READY:
                info(text(R.string.MorokUpdateReady));
                break;
            default:
                break;
        }
        if (manager.usingCachedManifest()) info(text(R.string.MorokUpdateCached));
        boolean busy = manager.status() == MorokUpdateManager.Status.CHECKING
                || manager.status() == MorokUpdateManager.Status.DOWNLOADING;
        action(R.string.MorokUpdateCheck, !busy && manager.configured(), () -> {
            manager.check(this::rebuild);
            rebuild();
        });
        SignedUpdateManifest update = manager.manifest();
        if (update != null && update.isNewerThan(manager.installedVersionCode())) {
            header(R.string.MorokUpdateAvailableHeader);
            info(LocaleController.formatString(R.string.MorokUpdateAvailable,
                    update.versionName, update.versionCode, readableBytes(update.size), update.telegramBase,
                    update.commit.substring(0, Math.min(12, update.commit.length()))));
            if (!update.changelog.trim().isEmpty()) info(update.changelog.trim());
            if (manager.status() == MorokUpdateManager.Status.AVAILABLE) {
                action(R.string.MorokUpdateDownload, true, () -> {
                    manager.download(this::rebuild);
                    rebuild();
                });
            } else if (manager.status() == MorokUpdateManager.Status.READY) {
                action(R.string.MorokUpdateInstall, true, this::install);
            }
        }
        info(text(R.string.MorokUpdateExplicitInfo));
    }

    private void install() {
        if (getParentActivity() == null || manager == null) return;
        if (!manager.install(getParentActivity())) {
            showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle(text(R.string.MorokUpdateInstallPermissionTitle))
                    .setMessage(text(R.string.MorokUpdateInstallPermissionInfo))
                    .setPositiveButton(text(R.string.OK), null).create());
        }
    }

    private void header(int string) {
        HeaderCell cell = new HeaderCell(content.getContext());
        cell.setText(text(string));
        content.addView(cell);
    }

    private void action(int string, boolean enabled, Runnable action) {
        TextSettingsCell cell = new TextSettingsCell(content.getContext());
        cell.setText(text(string), true);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setEnabled(enabled);
        cell.setAlpha(enabled ? 1f : 0.5f);
        if (enabled) cell.setOnClickListener(view -> action.run());
        content.addView(cell);
    }

    private void info(String value) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(content.getContext());
        cell.setText(value);
        content.addView(cell);
    }

    private static String readableBytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / (1024d * 1024d));
    }

    private static String text(int id) { return LocaleController.getString(id); }
}
