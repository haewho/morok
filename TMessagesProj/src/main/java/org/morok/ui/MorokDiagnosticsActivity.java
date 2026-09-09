package org.morok.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.morok.camera.RoundVideoDiagnostics;
import org.morok.diagnostics.MorokDiagnosticReport;
import org.morok.memory.MemoryStorageStats;
import org.morok.memory.MorokMemoryStore;
import org.morok.proxy.MorokProxyManager;
import org.morok.settings.ArchiveSettings;
import org.morok.settings.MorokSettings;
import org.morok.settings.RoundVideoSettings;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;

/** Read-only dashboard. Copied reports contain counts and states, never account/chat/proxy identities. */
public final class MorokDiagnosticsActivity extends BaseFragment {
    private final MorokProxyManager proxy = MorokProxyManager.getInstance();
    private final Runnable proxyListener = this::rebuild;
    private LinearLayout content;
    private MemoryStorageStats memoryStats;
    private String memoryState = "sign_in";
    private boolean memoryCaptureGap;
    private boolean destroyed;
    private int memoryGeneration;

    public MorokDiagnosticsActivity(int account) {
        currentAccount = account;
    }

    @Override public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        proxy.start();
        proxy.addListener(proxyListener);
        return true;
    }

    @Override public void onFragmentDestroy() {
        destroyed = true;
        memoryGeneration++;
        proxy.removeListener(proxyListener);
        super.onFragmentDestroy();
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokDiagnosticsTitle));
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

    @Override public void onResume() {
        super.onResume();
        rebuild();
        loadMemory();
    }

    private boolean accountAvailable() {
        return currentAccount >= 0 && currentAccount < UserConfig.MAX_ACCOUNT_COUNT
                && UserConfig.getInstance(currentAccount).isClientActivated();
    }

    private void loadMemory() {
        int generation = ++memoryGeneration;
        memoryStats = null;
        memoryCaptureGap = false;
        if (!accountAvailable()) {
            memoryState = "sign_in";
            rebuild();
            return;
        }
        memoryState = "loading";
        rebuild();
        final long userId = UserConfig.getInstance(currentAccount).getClientUserId();
        try {
            MorokMemoryStore store = MorokMemoryStore.forAccount(currentAccount);
            memoryCaptureGap = store.hasCaptureGap();
            store.storageStats((stats, error) -> {
                if (destroyed || generation != memoryGeneration || !accountAvailable()
                        || UserConfig.getInstance(currentAccount).getClientUserId() != userId) return;
                memoryStats = stats;
                memoryState = error == null && stats != null ? "ready" : "error";
                rebuild();
            });
        } catch (RuntimeException error) {
            memoryState = "error";
            rebuild();
        }
    }

    private void rebuild() {
        if (content == null || destroyed) return;
        content.removeAllViews();
        header(R.string.MorokDiagnosticsBuildHeader);
        info(LocaleController.formatString(R.string.MorokDiagnosticsBuildInfo,
                BuildVars.BUILD_VERSION_STRING, Build.VERSION.SDK_INT,
                Build.MANUFACTURER + " " + Build.MODEL));

        header(R.string.MorokDiagnosticsConnectionHeader);
        action(R.string.MorokProxyTitle, proxySummary(), () -> presentFragment(new MorokProxyActivity(currentAccount)));
        info(LocaleController.formatString(R.string.MorokDiagnosticsProxyInfo, proxy.nodes().size(),
                text(proxy.infrastructureConfigured() ? R.string.MorokDiagnosticsConfigured
                        : R.string.MorokDiagnosticsNotConfigured),
                text(proxy.hasValidPool() ? R.string.MorokDiagnosticsValid : R.string.MorokDiagnosticsUnavailable)));

        header(R.string.MorokDiagnosticsLocalHeader);
        if (accountAvailable()) {
            action(R.string.MorokArchiveTitle, archiveSummary(),
                    () -> presentFragment(new MorokArchiveActivity(currentAccount)));
            action(R.string.MorokMemoryShortcut, memorySummary(),
                    () -> presentFragment(new MorokMemoryActivity(currentAccount)));
        } else {
            info(text(R.string.MorokDiagnosticsSignInInfo));
        }

        header(R.string.MorokDiagnosticsRoundHeader);
        action(R.string.MorokRoundVideoTitle, roundSummary(), () -> presentFragment(new MorokRoundVideoActivity()));

        header(R.string.MorokDiagnosticsReportHeader);
        action(R.string.MorokDiagnosticsCopy, null, this::copyReport);
        info(text(R.string.MorokDiagnosticsReportInfo));
    }

    private String proxySummary() {
        return proxyMode() + " · " + proxyStatus();
    }

    private String proxyMode() {
        switch (proxy.mode()) {
            case MANUAL: return text(R.string.MorokDiagnosticsProxyManual);
            case AUTO: return text(R.string.MorokDiagnosticsProxyAuto);
            default: return text(R.string.MorokDiagnosticsProxyDirect);
        }
    }

    private String proxyStatus() {
        switch (proxy.status()) {
            case MANUAL: return text(R.string.MorokDiagnosticsStatusManual);
            case CHECKING: return text(R.string.MorokDiagnosticsStatusChecking);
            case CONNECTED: return text(R.string.MorokDiagnosticsStatusConnected);
            case RETRY: return text(R.string.MorokDiagnosticsStatusRetry);
            case OFFLINE: return text(R.string.MorokDiagnosticsStatusOffline);
            case EMPTY: return text(R.string.MorokDiagnosticsStatusEmpty);
            case POOL_ERROR: return text(R.string.MorokDiagnosticsStatusPoolError);
            case STORAGE_ERROR: return text(R.string.MorokDiagnosticsStatusStorageError);
            default: return text(R.string.MorokDiagnosticsStatusDirect);
        }
    }

    private ArchiveSettings archive() {
        try { return MorokSettings.archive(currentAccount); }
        catch (RuntimeException error) { return ArchiveSettings.DEFAULT; }
    }

    private String archiveSummary() {
        ArchiveSettings archive = archive();
        return LocaleController.formatString(R.string.MorokDiagnosticsArchiveValue,
                text(archive.enabled ? R.string.MorokInteractionsEnabled : R.string.MorokInteractionsDisabled),
                archive.chats.size());
    }

    private String memorySummary() {
        if ("loading".equals(memoryState)) return text(R.string.MorokDiagnosticsLoading);
        if ("error".equals(memoryState)) return text(R.string.MorokDiagnosticsError);
        if (memoryStats == null) return text(R.string.MorokDiagnosticsUnavailable);
        return LocaleController.formatString(R.string.MorokDiagnosticsMemoryValue, memoryStats.cards,
                AndroidUtilities.formatFileSize(memoryStats.usedBytes));
    }

    private RoundVideoSettings roundVideo() {
        try { return MorokSettings.roundVideo(); }
        catch (RuntimeException error) { return RoundVideoSettings.DEFAULT; }
    }

    private String roundSummary() {
        RoundVideoSettings settings = roundVideo();
        String profile = settings.enhanced ? settings.profile : text(R.string.MorokPrivacyOffStatus);
        return LocaleController.formatString(R.string.MorokDiagnosticsRoundValue, profile,
                RoundVideoDiagnostics.count());
    }

    private MorokDiagnosticReport report() {
        boolean available = accountAvailable();
        ArchiveSettings archive = available ? archive() : ArchiveSettings.DEFAULT;
        RoundVideoSettings round = roundVideo();
        MemoryStorageStats stats = memoryStats;
        return new MorokDiagnosticReport(BuildVars.BUILD_VERSION_STRING,
                ApplicationLoader.applicationContext.getPackageName(), Build.VERSION.SDK_INT,
                Build.MANUFACTURER + " " + Build.MODEL, available, proxy.mode().name(),
                proxy.status().name(), proxy.nodes().size(), proxy.infrastructureConfigured(),
                proxy.hasValidPool(), proxy.lastCheckWallTime(), archive.enabled, archive.chats.size(),
                available ? memoryState : "sign_in", stats == null ? 0 : stats.usedBytes,
                stats == null ? 0 : stats.cards, stats == null ? 0 : stats.versions,
                stats == null ? 0 : stats.savedOriginals, stats == null ? 0 : stats.storageErrors,
                memoryCaptureGap, round.enhanced, round.profile, RoundVideoDiagnostics.enabled(),
                RoundVideoDiagnostics.count());
    }

    private void copyReport() {
        if (getContext() == null) return;
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("MOROK diagnostics", report().render()));
        Toast.makeText(getContext(), text(R.string.MorokDiagnosticsCopied), Toast.LENGTH_SHORT).show();
    }

    private void header(int id) {
        HeaderCell cell = new HeaderCell(content.getContext());
        cell.setText(text(id));
        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        content.addView(cell);
    }

    private void action(int id, String value, Runnable callback) {
        TextSettingsCell cell = new TextSettingsCell(content.getContext());
        if (value == null) cell.setText(text(id), true);
        else cell.setTextAndValue(text(id), value, true);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setOnClickListener(view -> callback.run());
        content.addView(cell);
    }

    private void info(String value) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(content.getContext());
        cell.setText(value);
        cell.setTextColorByKey(Theme.key_windowBackgroundWhiteGrayText4);
        content.addView(cell);
    }

    private static String text(int id) { return LocaleController.getString(id); }
}
