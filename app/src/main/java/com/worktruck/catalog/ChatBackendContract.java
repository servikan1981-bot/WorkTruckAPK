package com.worktruck.catalog;

/**
 * Server contract for the future public Scania owners chat.
 * The mobile UI works locally now; when Work Truck CRM/API is connected,
 * implement these endpoints behind the same screens.
 */
public final class ChatBackendContract {
    private ChatBackendContract(){}

    public static final String API_PREFIX="/api/mobile/chat";
    public static final String AUTH="/api/mobile/auth";
    public static final String ROOMS=API_PREFIX+"/rooms";
    public static final String MESSAGES=API_PREFIX+"/rooms/{roomId}/messages";
    public static final String SEND=API_PREFIX+"/rooms/{roomId}/messages";
    public static final String UPLOAD=API_PREFIX+"/uploads";
    public static final String REPORT=API_PREFIX+"/messages/{messageId}/report";
    public static final String BLOCK=API_PREFIX+"/users/{userId}/block";
    public static final String WS="/ws/mobile/chat";

    // Expected CRM identity: clientId, displayName, company, city,
    // verifiedClient, managerId and optional saved Scania vehicles.
}
