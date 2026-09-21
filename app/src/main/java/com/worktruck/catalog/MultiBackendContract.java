package com.worktruck.catalog;

/**
 * Contract for the future server-side integration with the company's licensed Scania Multi installation.
 * Do not ship Scania's proprietary Multi database inside the APK.
 */
public final class MultiBackendContract {
    private MultiBackendContract(){}

    public static final String LOOKUP_BY_CHASSIS="/api/mobile/multi/chassis/{chassis}";
    public static final String SEARCH_PARTS="/api/mobile/multi/chassis/{chassis}/parts";
    public static final String PART_DETAILS="/api/mobile/multi/parts/{partNumber}";
    public static final String WORKTRUCK_STOCK="/api/mobile/products/by-oem/{partNumber}";

    // Expected response chain:
    // chassis -> vehicle metadata -> applicable component groups -> OEM part numbers
    // -> Work Truck equivalents, warehouses, stock, price, request/order action.
}
