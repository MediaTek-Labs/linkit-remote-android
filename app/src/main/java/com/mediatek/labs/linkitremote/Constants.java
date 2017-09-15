package com.mediatek.labs.linkitremote;

import android.os.ParcelUuid;

import java.util.UUID;

/**
 * Constants for use in the Bluetooth Advertisements sample
 */
class Constants {
    public static final int REQUEST_ENABLE_BT = 0xBEE0;
    public static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 0xBEE1;

    public static final int SCAN_PERIOD_MS = 5000;
    public static final int VIEW_PADDING = 8;

    public static final ParcelUuid rcService = ParcelUuid.fromString("3f60ab39-1710-4456-930c-7e9c9539917e");
    public static final UUID rcControlCount = UUID.fromString("3f60ab39-1711-4456-930c-7e9c9539917e");
    public static final UUID rcControlTypes = UUID.fromString("3f60ab39-1712-4456-930c-7e9c9539917e");
    public static final UUID rcRow = UUID.fromString("3f60ab39-1713-4456-930c-7e9c9539917e");
    public static final UUID rcCol = UUID.fromString("3f60ab39-1714-4456-930c-7e9c9539917e");
    public static final UUID rcColors = UUID.fromString("3f60ab39-1715-4456-930c-7e9c9539917e"); // Array of UINT8, enum of
    public static final UUID rcFrames = UUID.fromString("3f60ab39-1716-4456-930c-7e9c9539917e"); // Array of UINT8[4],  = UUID.fromString(x,
    public static final UUID rcNames = UUID.fromString("3f60ab39-1717-4456-930c-7e9c9539917e"); // String of control names,
    public static final UUID rcEventArray = UUID.fromString("b5d2ff7b-6eff-4fb5-9b72-6b9cff5181e7"); // Array of UINT8[4],
    public static final UUID rcConfigDataArray = UUID.fromString("5d7a63ff-4155-4c7c-a348-1c0a323a6383");
    public static final UUID rcOrientation = UUID.fromString("203fbbcd-9967-4eba-b0ff-0f72e5a634eb"); // 0: portrait, 1: landscape
}