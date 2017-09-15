package com.mediatek.labs.linkitremote;

/*
 * Created by MediaTek Labs on 2017/9/13.
 */

import android.graphics.Rect;
import android.view.View;

class ControlInfo {
    enum ControlType {
        label,
        pushButton,
        circleButton,
        switchButton,
        slider;

        static public ControlType getEnum(byte b) {
            switch(b) {
                case 1:
                    return label;
                case 2:
                    return pushButton;
                case 3:
                    return circleButton;
                case 4:
                    return switchButton;
                case 5:
                    return slider;
                default:
                    return pushButton;
            }
        }
    }

    enum ColorType {
        gold,
        yellow,
        blue,
        green,
        pink,
        grey;

        static public ColorType getEnum(byte b) {
            switch(b) {
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
                default:
                    return grey;
            }
        }
    }

    ControlType type;    // control type, such as button or label
    int color;     // control color set
    Rect cell;           // coordinate in Remote Grid, not actual screen space.
    String text;         // control label text
    ControlConfig config;
    View view;
    int index;   // index in the control / event array
}

