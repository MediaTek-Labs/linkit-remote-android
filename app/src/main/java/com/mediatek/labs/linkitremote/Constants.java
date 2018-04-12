package com.mediatek.labs.linkitremote;

import android.os.ParcelUuid;

import java.util.UUID;

/**
 * Constants for use in the Bluetooth Advertisements sample
 */
class Constants {
    static final int REQUEST_ENABLE_BT = 0xBEE0;
    static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 0xBEE1;

    static final int SCAN_PERIOD_MS = 10000;     // scan for 10 seconds
    static final int VIEW_PADDING = 8;

    static final int PROTOCOL_VERSION = 4;

    static final ParcelUuid rcService = ParcelUuid.fromString("3f60ab39-1710-4456-930c-7e9c9539917e");
    static final UUID rcControlCount = UUID.fromString("3f60ab39-1711-4456-930c-7e9c9539917e");
    static final UUID rcControlTypes = UUID.fromString("3f60ab39-1712-4456-930c-7e9c9539917e");
    static final UUID rcRow = UUID.fromString("3f60ab39-1713-4456-930c-7e9c9539917e");
    static final UUID rcCol = UUID.fromString("3f60ab39-1714-4456-930c-7e9c9539917e");
    static final UUID rcColors = UUID.fromString("3f60ab39-1715-4456-930c-7e9c9539917e"); // Array of UINT8, enum of
    static final UUID rcFrames = UUID.fromString("3f60ab39-1716-4456-930c-7e9c9539917e"); // Array of UINT8[4],  = UUID.fromString(x,
    static final UUID rcNames = UUID.fromString("3f60ab39-1717-4456-930c-7e9c9539917e"); // String of control names,
    static final UUID rcEventArray = UUID.fromString("b5d2ff7b-6eff-4fb5-9b72-6b9cff5181e7"); // Array of UINT8[4],
    static final UUID rcConfigDataArray = UUID.fromString("5d7a63ff-4155-4c7c-a348-1c0a323a6383");
    static final UUID rcOrientation = UUID.fromString("203fbbcd-9967-4eba-b0ff-0f72e5a634eb"); // 0: portrait, 1: landscape
    static final UUID rcProtocolVersion = UUID.fromString("ae73266e-65d4-4023-8868-88b070d5d576"); // protocol version, to ensure version match between arduino and mobile.
    static final UUID rcUIUpdate = UUID.fromString("e4b1ddfe-eb37-4c78-aba8-c5fa944775cb"); // variable-length structure for UI Label text update

}