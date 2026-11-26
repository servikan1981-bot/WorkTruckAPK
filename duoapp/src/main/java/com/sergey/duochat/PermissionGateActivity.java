package com.sergey.duochat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

/**
 * Small launcher-only permission guide for incoming calls.
 *
 * It does not change the app's chat/call logic. Its only job is to make sure
 * Android is allowed to show the existing incoming-call UI on the lock screen.
 * Settings controlled by the user/OEM are opened directly for the user.
 */
public class PermissionGateActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 3101;
    private static final String GUIDE_SEEN = "call_settings_guide_v6031_seen";

    private boolean dialogShowing = false;
    private boolean waitingForSettings = false;
    private boolean continuing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureCallChannel();
        getWindow().getDecorView().postDelayed(this::checkNextStep, 180L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForSettings) {
            waitingForSettings = false;
            getWindow().getDecorView().postDelayed(this::checkNextStep, 350L);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            getWindow().getDecorView().postDelayed(this::checkNextStep, 200L);
        }
    }

    private void checkNextStep() {
        if (continuing || isFinishing() || dialogShowing) return;
        ensureCallChannel();

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            showNotificationPermissionPrompt();
            return;
        }

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && !nm.areNotificationsEnabled()) {
            showAppNotificationsPrompt();
            return;
        }

        if (!callChannelReady()) {
            showCallChannelPrompt(true);
            return;
        }

        if (Build.VERSION.SDK_INT >= 34 && nm != null && !nm.canUseFullScreenIntent()) {
            showFullScreenPrompt();
            return;
        }

        // Some manufacturers expose extra per-channel switches (pop-up/banner,
        // lock-screen visibility, sound) that Android does not let apps inspect.
        // Show this guide once after the update even when the standard checks pass.
        if (!SecureStore.prefs(this).getBoolean(GUIDE_SEEN, false)) {
            showCallChannelPrompt(false);
            return;
        }

        continueToApp();
    }

    private void showNotificationPermissionPrompt() {
        showDialog(
                "Разрешите уведомления",
                "Без разрешения на уведомления Android не сможет показать входящий звонок «Наша семья». Нажмите «Разрешить» и подтвердите системный запрос.",
                "Разрешить",
                () -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS));
    }

    private void showAppNotificationsPrompt() {
        showDialog(
                "Включите уведомления «Наша семья»",
                "Уведомления приложения сейчас выключены. Откройте настройки и включите «Разрешить уведомления», чтобы звонки и сообщения приходили сразу.",
                "Открыть настройки",
                this::openAppNotificationSettings);
    }

    private void showCallChannelPrompt(boolean required) {
        String text = required
                ? "Канал «Семейные звонки» выключен или переведён в тихий режим. Откройте его настройки и включите все доступные пункты: уведомления, всплывающий показ, экран блокировки, звук и вибрацию."
                : "Проверьте один раз канал «Семейные звонки». На некоторых телефонах Android отдельно управляет всплывающим показом и экраном блокировки. Включите все доступные пункты: уведомления, всплывающий показ, экран блокировки, звук и вибрацию.";
        showDialog(
                "Семейные звонки",
                text,
                "Открыть «Семейные звонки»",
                () -> {
                    SecureStore.prefs(this).edit().putBoolean(GUIDE_SEEN, true).apply();
                    openCallChannelSettings();
                });
    }

    private void showFullScreenPrompt() {
        showDialog(
                "Разрешите звонки на заблокированном экране",
                "Чтобы при заблокированном телефоне сразу появлялись зелёная кнопка «Принять» и красная «Отклонить», разрешите приложению полноэкранные входящие звонки.",
                "Разрешить звонки",
                this::openFullScreenSettings);
    }

    private void showDialog(String title, String message, String positiveText, Runnable positiveAction) {
        if (dialogShowing || continuing || isFinishing()) return;
        dialogShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveText, (d, w) -> {
                    dialogShowing = false;
                    positiveAction.run();
                })
                .setNegativeButton("Позже", (d, w) -> {
                    dialogShowing = false;
                    continueToApp();
                })
                .create();
        dialog.setOnCancelListener(d -> {
            dialogShowing = false;
            continueToApp();
        });
        dialog.show();
    }

    private void ensureCallChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(MessagingService.CH_CALLS) != null) return;

        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        NotificationChannel calls = new NotificationChannel(
                MessagingService.CH_CALLS,
                "Семейные звонки",
                NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription("Входящие аудио- и видеозвонки «Наша семья»");
        calls.enableVibration(true);
        calls.enableLights(true);
        calls.setLightColor(Color.GREEN);
        calls.setSound(ringtone, attrs);
        calls.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(calls);
    }

    private boolean callChannelReady() {
        if (Build.VERSION.SDK_INT < 26) return true;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return false;
        NotificationChannel channel = nm.getNotificationChannel(MessagingService.CH_CALLS);
        return channel != null && channel.getImportance() >= NotificationManager.IMPORTANCE_HIGH;
    }

    private void openAppNotificationSettings() {
        waitingForSettings = true;
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openCallChannelSettings() {
        waitingForSettings = true;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                i.putExtra(Settings.EXTRA_CHANNEL_ID, MessagingService.CH_CALLS);
                startActivity(i);
            } else {
                openAppNotificationSettings();
            }
        } catch (Exception e) {
            openAppNotificationSettings();
        }
    }

    private void openFullScreenSettings() {
        waitingForSettings = true;
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                openAppNotificationSettings();
            }
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        waitingForSettings = true;
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) {
            continueToApp();
        }
    }

    private void continueToApp() {
        if (continuing) return;
        continuing = true;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
