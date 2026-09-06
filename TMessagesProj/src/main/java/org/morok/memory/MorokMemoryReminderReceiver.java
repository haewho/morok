package org.morok.memory;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Local, inexact reminders; no network request and no implicit chat read. */
public final class MorokMemoryReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "morok_memory_v1";
    private static final String ACTION = "org.morok.MEMORY_REMINDER";
    public static final String OPEN_ACTION = "org.morok.OPEN_MEMORY";

    private static PendingIntent alarm(long userId) {
        Context context = ApplicationLoader.applicationContext;
        Intent intent = new Intent(context, MorokMemoryReminderReceiver.class).setAction(ACTION)
                .setData(Uri.parse("morok-memory://reminder/" + userId)).putExtra("morok_user_id", userId);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean notificationsAvailable(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
            return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    static void scheduleNext(long userId, ArrayList<MemoryCard> cards) {
        if (Build.VERSION.SDK_INT < 23) return;
        Context context = ApplicationLoader.applicationContext;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long next = Long.MAX_VALUE;
        for (MemoryCard card : cards) {
            if (!card.completed && card.reminderAt > 0 && card.reminderDeliveredAt < card.reminderAt) next = Math.min(next, card.reminderAt);
        }
        try {
            if (next == Long.MAX_VALUE || MorokMemoryStore.resolveAccount(userId) < 0) manager.cancel(alarm(userId));
            else {
                long now = System.currentTimeMillis();
                if (!notificationsAvailable(context)) next = Math.max(next, now + 6 * 60 * 60 * 1000L);
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.max(now + 1000, next), alarm(userId));
            }
        } catch (RuntimeException ignored) { /* Store retains pending reminders if OS scheduling is unavailable. */ }
    }

    static boolean notifyCard(long userId, MemoryCard card) {
        Context context = ApplicationLoader.applicationContext;
        if (MorokMemoryStore.resolveAccount(userId) < 0 || !notificationsAvailable(context)) return false;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    LocaleController.getString(R.string.MorokMemoryTitle), NotificationManager.IMPORTANCE_DEFAULT));
        }
        // Generic lock-screen text: quotes, tags and sender names remain behind the app lock.
        Intent intent = new Intent(context, LaunchActivity.class).setAction(OPEN_ACTION)
                .setData(Uri.parse("morok-memory://card/" + userId + "/" + card.id))
                .putExtra("morok_user_id", userId).putExtra("morok_card_id", card.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.notification).setContentTitle(LocaleController.getString(R.string.MorokMemoryTitle))
                .setContentText(LocaleController.getString(R.string.MorokMemoryReminderNotification))
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_REMINDER);
        try { manager.notify("morok.memory." + userId + "." + card.id, 1, builder.build()); return true; }
        catch (RuntimeException ignored) { return false; }
    }

    static void cancelCard(long userId, String cardId) {
        NotificationManager manager = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel("morok.memory." + userId + "." + cardId, 1);
    }

    static void cancel(long userId) {
        Context context = ApplicationLoader.applicationContext;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(alarm(userId));
        if (Build.VERSION.SDK_INT >= 23) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            for (StatusBarNotification notification : manager.getActiveNotifications()) {
                if (notification.getTag() != null && notification.getTag().startsWith("morok.memory." + userId + ".")) {
                    manager.cancel(notification.getTag(), notification.getId());
                }
            }
        }
    }

    /** Call after normal application/account initialization, and when notification permission changes. */
    public static void restoreAll() {
        if (Build.VERSION.SDK_INT < 23) return;
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (UserConfig.getInstance(i).isClientActivated()) MorokMemoryStore.forAccount(i).list(null);
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        ApplicationLoader.postInitApplication();
        PendingResult result = goAsync();
        long userId = intent.getLongExtra("morok_user_id", 0);
        if (ACTION.equals(intent.getAction())) {
            int account = MorokMemoryStore.resolveAccount(userId);
            if (account < 0) { result.finish(); return; }
            MorokMemoryStore.forAccount(account).deliverReminders(result::finish);
        } else {
            ArrayList<Integer> accounts = new ArrayList<>();
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) if (UserConfig.getInstance(i).isClientActivated()) accounts.add(i);
            AtomicInteger left = new AtomicInteger(accounts.size());
            if (accounts.isEmpty()) { result.finish(); return; }
            for (int account : accounts) MorokMemoryStore.forAccount(account).deliverReminders(() -> { if (left.decrementAndGet() == 0) result.finish(); });
        }
    }
}
