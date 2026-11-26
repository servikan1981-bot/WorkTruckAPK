package com.sergey.duochat;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;

import java.util.Collections;

public final class TelecomCallManager {
    public static final String EXTRA_CALL_ID = "family_call_id";
    public static final String EXTRA_CALLER_ROLE = "family_caller_role";
    public static final String EXTRA_KIND = "family_call_kind";
    private static final String ACCOUNT_ID = "ourfamily_voip_v1";

    private TelecomCallManager() {}

    public static PhoneAccountHandle handle(Context context) {
        ComponentName cn = new ComponentName(context, FamilyConnectionService.class);
        return new PhoneAccountHandle(cn, ACCOUNT_ID);
    }

    public static void register(Context context) {
        try {
            TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (tm == null) return;
            PhoneAccountHandle h = handle(context);
            int caps = PhoneAccount.CAPABILITY_CALL_PROVIDER;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                caps |= PhoneAccount.CAPABILITY_VIDEO_CALLING;
            }
            PhoneAccount account = new PhoneAccount.Builder(h, "Наша семья")
                    .setCapabilities(caps)
                    .setSupportedUriSchemes(Collections.singletonList("ourfamily"))
                    .build();
            tm.registerPhoneAccount(account);
        } catch (Exception ignored) {}
    }

    public static boolean isEnabled(Context context) {
        try {
            TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (tm == null) return false;
            PhoneAccount a = tm.getPhoneAccount(handle(context));
            return a != null && a.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public static void openSettings(Activity activity) {
        register(activity);
        try {
            Intent i = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
            activity.startActivity(i);
        } catch (Exception ignored) {}
    }

    public static boolean reportIncomingCall(
            Context context, String callerRole, String callId, String kind) {
        try {
            register(context);
            TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (tm == null || !isEnabled(context)) return false;

            boolean video = kind != null && kind.contains("video");
            Bundle extras = new Bundle();
            extras.putString(EXTRA_CALL_ID, callId);
            extras.putString(EXTRA_CALLER_ROLE, callerRole);
            extras.putString(EXTRA_KIND, kind == null ? "video" : kind);
            extras.putParcelable(
                    TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    Uri.parse("ourfamily:" + Uri.encode(callerRole)));
            extras.putInt(
                    TelecomManager.EXTRA_INCOMING_VIDEO_STATE,
                    video ? VideoProfile.STATE_BIDIRECTIONAL : VideoProfile.STATE_AUDIO_ONLY);

            tm.addNewIncomingCall(handle(context), extras);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
