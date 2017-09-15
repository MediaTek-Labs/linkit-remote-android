package com.mediatek.labs.linkitremote;

/**
 * Created by MediaTek Labs on 2017/9/12.
 */

class Brandcolor {
    public static final ColorSet gold = new ColorSet( 0xFFF39A1E, 0xFFDEC9A5);
    public static final ColorSet yellow = new ColorSet( 0xFFFED100, 0xFFE1D4A0);
    public static final ColorSet blue = new ColorSet( 0xFF00A1DE, 0xFFABCBDD);
    public static final ColorSet green = new ColorSet( 0xFF69BE28, 0xFFB6CEA9);
    public static final ColorSet pink = new ColorSet( 0xFFD71F85, 0xFFDCAEC9);
    public static final ColorSet grey = new ColorSet( 0xFF353630, 0xFF353630);
    public static final ColorSet font = new ColorSet( 0xFFFFFFFF, 0xFFFDFDFD);

    static public ColorSet fromBLE(byte colorType) {
        switch(colorType) {
            case 1:
                return gold;
            case 2:
                return yellow;
            case 3:
                return blue;
            case 4:
                return green;
            case 5:
                return pink;
            case 6:
                return grey;
        }

        return grey;
    }
}
