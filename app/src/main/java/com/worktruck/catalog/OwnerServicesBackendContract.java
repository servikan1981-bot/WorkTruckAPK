package com.worktruck.catalog;

public final class OwnerServicesBackendContract {
    private OwnerServicesBackendContract(){}
    public static final String SERVICE_BOOK="/api/mobile/service/book";
    public static final String SERVICE_ORDERS="/api/mobile/service/orders";
    public static final String SERVICE_STATUS="/api/mobile/service/orders/{id}";
    public static final String VEHICLE_JOURNAL="/api/mobile/vehicles/{vehicleId}/journal";
    public static final String VEHICLE_REMINDERS="/api/mobile/vehicles/{vehicleId}/reminders";
    public static final String SOS="/api/mobile/sos";
    public static final String ERROR_LOOKUP="/api/mobile/diagnostics/codes/{code}";
}
