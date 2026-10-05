package com.sergey.duochat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;

import org.json.JSONObject;

public class FamilyConnectionService extends ConnectionService {

    @Override
    public Connection onCreateIncomingConnection(
            android.telecom.PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Bundle extras = request == null ? null : request.getExtras();
        String callId = extras == null ? "" : extras.getString(TelecomCallManager.EXTRA_CALL_ID, "");
        String callerRole = extras == null ? "" : extras.getString(TelecomCallManager.EXTRA_CALLER_ROLE, "");
        String kind = extras == null ? "video" : extras.getString(TelecomCallManager.EXTRA_KIND, "video");

        FamilyConnection c = new FamilyConnection(this, callId, callerRole, kind);
        Uri address = request == null ? null : request.getAddress();
        if (address == null) address = Uri.parse("ourfamily:" + Uri.encode(callerRole));
        c.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        c.setCallerDisplayName(FamilyDirectory.name(callerRole), TelecomManager.PRESENTATION_ALLOWED);
        c.setAudioModeIsVoip(true);
        c.setVideoState(kind != null && kind.contains("video")
                ? VideoProfile.STATE_BIDIRECTIONAL
                : VideoProfile.STATE_AUDIO_ONLY);
        c.setRinging();
        return c;
    }

    @Override
    public void onCreateIncomingConnectionFailed(
            android.telecom.PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request);
    }

    private static final class FamilyConnection extends Connection {
        private final Context context;
        private final String callId;
        private final String callerRole;
        private final String kind;
        private boolean accepted = false;
        private boolean finished = false;

        FamilyConnection(Context context, String callId, String callerRole, String kind) {
            this.context = context.getApplicationContext();
            this.callId = callId == null ? "" : callId;
            this.callerRole = callerRole == null ? "" : callerRole;
            this.kind = kind == null ? "video" : kind;
        }

        @Override
        public void onAnswer() {
            answerAndHandOff();
        }

        @Override
        public void onAnswer(int videoState) {
            answerAndHandOff();
        }

        private void answerAndHandOff() {
            if (finished) return;
            accepted = true;
            try {
                JSONObject o = new JSONObject();
                o.put("action", "accept");
                o.put("callId", callId);
                o.put("callerRole", callerRole);
                o.put("kind", kind);
                SecureStore.prefs(context).edit()
                        .putString("pending_call_action", o.toString())
                        .apply();
            } catch (Exception ignored) {}

            setActive();

            Intent open = new Intent(context, MainActivity.class);
            open.putExtra("call_action", "accept");
            open.putExtra("call_id", callId);
            open.putExtra("caller_role", callerRole);
            open.putExtra("call_kind", kind);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try { context.startActivity(open); } catch (Exception ignored) {}

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!finished) {
                    finished = true;
                    setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                    destroy();
                }
            }, 1400L);
        }

        @Override
        public void onReject() {
            rejectAndFinish();
        }

        @Override
        public void onDisconnect() {
            if (!accepted) {
                CallControl.sendDecline(context, callerRole, callId);
            }
            finishConnection(accepted ? DisconnectCause.LOCAL : DisconnectCause.REJECTED);
        }

        @Override
        public void onAbort() {
            if (!accepted) CallControl.sendDecline(context, callerRole, callId);
            finishConnection(DisconnectCause.CANCELED);
        }

        private void rejectAndFinish() {
            if (finished) return;
            CallControl.sendDecline(context, callerRole, callId);
            finishConnection(DisconnectCause.REJECTED);
        }

        private void finishConnection(int cause) {
            if (finished) return;
            finished = true;
            setDisconnected(new DisconnectCause(cause));
            destroy();
        }
    }
}
