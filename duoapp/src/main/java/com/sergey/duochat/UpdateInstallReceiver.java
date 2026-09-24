package com.sergey.duochat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

public class UpdateInstallReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_STATUS = "com.sergey.ourfamily.UPDATE_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirm);
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            SecureStore.prefs(context).edit().remove("stable_update_pending").apply();
            Toast.makeText(context, "Обновление установлено", Toast.LENGTH_SHORT).show();
            return;
        }

        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        SecureStore.prefs(context).edit().remove("stable_update_pending").apply();
        Toast.makeText(context,
                "Не удалось установить обновление" + (msg == null ? "" : ": " + msg),
                Toast.LENGTH_LONG).show();
    }
}
