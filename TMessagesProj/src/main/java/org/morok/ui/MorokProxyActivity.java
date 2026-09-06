package org.morok.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.morok.proxy.MorokProxyManager;
import org.morok.proxy.ProxyNode;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProxyListActivity;

import java.text.DateFormat;
import java.util.Date;

/** Available before login; imports require an explicit user action. */
public final class MorokProxyActivity extends BaseFragment {
    private LinearLayout content;
    private final MorokProxyManager manager = MorokProxyManager.getInstance();
    private final Runnable listener = this::rebuild;

    public MorokProxyActivity(int account) { super(); currentAccount = account; }

    @Override public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        manager.start(); manager.addListener(listener);
        return true;
    }

    @Override public void onFragmentDestroy() {
        manager.removeListener(listener);
        super.onFragmentDestroy();
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.MorokProxyTitle));
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

    @Override public void onResume() { super.onResume(); rebuild(); }

    private void rebuild() {
        if (content == null) return;
        Context context = content.getContext();
        content.removeAllViews();
        TextCheckCell automatic = new TextCheckCell(context);
        automatic.setTextAndCheck(text(R.string.MorokProxyAutomatic), manager.mode() == MorokProxyManager.Mode.AUTO, true);
        automatic.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        automatic.setOnClickListener(v -> {
            if (manager.mode() == MorokProxyManager.Mode.AUTO) manager.setDirect();
            else if (!manager.setAutomatic()) message(manager.status() == MorokProxyManager.Status.STORAGE_ERROR
                    ? R.string.MorokProxyStatusStorageError : R.string.MorokProxyNoNodes);
        });
        content.addView(automatic);
        action(R.string.MorokProxyDirect, () -> manager.setDirect());
        info(statusText());
        if (!manager.infrastructureConfigured()) info(text(R.string.MorokProxyInfrastructureMissing));
        else if (!manager.hasValidPool()) info(text(R.string.MorokProxyStatusPoolError));
        info(text(R.string.MorokProxyTrafficScope));
        action(R.string.MorokProxyAnother, () -> {
            if (manager.mode() == MorokProxyManager.Mode.AUTO) manager.another();
            else message(R.string.MorokProxyEnableFirst);
        });
        action(R.string.MorokProxyImport, () -> importDialog(null));
        action(R.string.MorokProxyScan, () -> CameraScanActivity.showAsSheet(this, true, CameraScanActivity.TYPE_QR,
                new CameraScanActivity.CameraScanActivityDelegate() {
                    @Override public void didFindQr(String value) { importDialog(value); }
                }));
        action(R.string.MorokProxyRefresh, () -> manager.refreshPool());
        action(R.string.MorokProxyManage, () -> presentFragment(new ProxyListActivity()));
        HeaderCell header = new HeaderCell(context);
        header.setText(text(R.string.MorokProxyNodes)); content.addView(header);
        if (manager.nodes().isEmpty()) info(text(R.string.MorokProxyNoNodes));
        for (ProxyNode node : manager.nodes()) {
            TextSettingsCell row = new TextSettingsCell(context);
            String label = (node.secret.isEmpty() ? "SOCKS5" : "MTProxy") + " · " + node.host + ":" + node.port;
            row.setTextAndValue(label, node.id().equals(manager.selectedId()) && manager.mode() != MorokProxyManager.Mode.DIRECT
                    ? text(R.string.MorokProxySelected) : "", true);
            row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            row.setOnClickListener(v -> manager.setManual(node));
            content.addView(row);
        }
    }

    private String statusText() {
        int id;
        switch (manager.status()) {
            case MANUAL: id = R.string.MorokProxyStatusManual; break;
            case CHECKING: id = R.string.MorokProxyStatusChecking; break;
            case CONNECTED: id = R.string.MorokProxyStatusConnected; break;
            case RETRY: id = R.string.MorokProxyStatusRetry; break;
            case OFFLINE: id = R.string.MorokProxyStatusOffline; break;
            case EMPTY: id = R.string.MorokProxyNoNodes; break;
            case POOL_ERROR: id = R.string.MorokProxyStatusPoolError; break;
            case STORAGE_ERROR: id = R.string.MorokProxyStatusStorageError; break;
            default: id = R.string.MorokProxyStatusDirect;
        }
        String result = text(id);
        if (manager.lastCheckWallTime() != 0) result += "\n" + text(R.string.MorokProxyLastCheck) + " "
                + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(manager.lastCheckWallTime()));
        return result;
    }

    private void action(int title, Runnable action) {
        TextSettingsCell cell = new TextSettingsCell(content.getContext());
        cell.setText(text(title), true);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setOnClickListener(v -> action.run()); content.addView(cell);
    }
    private void info(String text) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(content.getContext());
        cell.setText(text); content.addView(cell);
    }
    private void message(int id) {
        if (getParentActivity() != null) showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.MorokProxyTitle))
                .setMessage(text(id)).setPositiveButton(text(R.string.OK), null).create());
    }

    private void importDialog(String scanned) {
        if (getParentActivity() == null) return;
        EditTextBoldCursor input = new EditTextBoldCursor(getParentActivity());
        input.setTextSize(16);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint("tg://proxy · https://t.me/socks · socks5://");
        input.setSingleLine(false);
        input.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        if (scanned != null) input.setText(scanned);
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.MorokProxyImport))
                .setMessage(text(R.string.MorokProxyImportInfo)).setView(input)
                .setPositiveButton(text(R.string.Add), (dialog, which) -> {
                    try { manager.importLink(input.getText().toString()); }
                    catch (IllegalArgumentException e) { message(R.string.MorokProxyInvalidLink); }
                }).setNegativeButton(text(R.string.Cancel), null).create());
    }

    private static String text(int id) { return LocaleController.getString(id); }
}
