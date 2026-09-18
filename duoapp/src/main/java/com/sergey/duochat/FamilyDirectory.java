package com.sergey.duochat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FamilyDirectory {
    public static final LinkedHashMap<String,String> MEMBERS = new LinkedHashMap<>();
    static {
        MEMBERS.put("sergey", "Сергей");
        MEMBERS.put("sveta", "Света");
        MEMBERS.put("natasha", "Наташа");
        MEMBERS.put("vova", "Вова");
        MEMBERS.put("zhenya", "Женя");
        MEMBERS.put("yana", "Яна");
        MEMBERS.put("vadim", "Вадим");
        MEMBERS.put("danil", "Данил");
    }

    private FamilyDirectory() {}

    public static boolean validRole(String role) {
        return MEMBERS.containsKey(role);
    }

    public static String name(String role) {
        String n = MEMBERS.get(role);
        return n == null ? role : n;
    }

    public static String sha256(String s) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] b = d.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte x : b) out.append(String.format("%02x", x & 0xff));
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String tag(String code, String role) {
        return sha256("OurFamily-v5-tag|" + code + "|" + role).substring(0, 16);
    }

    public static String inboxTopic(String code, String role) {
        return "of5-" + sha256("OurFamily-v5-inbox|" + code + "|" + role).substring(0, 48);
    }

    public static String roleFromTag(String code, String tag) {
        for (Map.Entry<String,String> e : MEMBERS.entrySet()) {
            if (tag(code, e.getKey()).equals(tag)) return e.getKey();
        }
        return "";
    }

    public static String controlToken(String code, String callId, String action, String fromRole, String toRole) {
        return sha256("OurFamily-v5-control|" + code + "|" + callId + "|" + action + "|" + fromRole + "|" + toRole).substring(0, 32);
    }
}
