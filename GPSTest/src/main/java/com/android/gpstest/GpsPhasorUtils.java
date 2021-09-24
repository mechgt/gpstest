package com.android.gpstest;

import java.util.HashMap;

public class GpsPhasorUtils {
    private static HashMap<Line, Float> offset;
    private static float freq = 60f;
    private static double w;
    private static double time_base = 1e9;  // Nano seconds in 1 second
    public enum Line {
        A, B, C, N1, N2;
   }

    GpsPhasorUtils() {
        offset.put(Line.A, 0f);
        offset.put(Line.B, 120f);
        offset.put(Line.C, 240f);

        w = freq * 2 * Math.PI;
    }

    public static double getAngle(double x, long soc, long fos, Line line) {
        // x = Xm cos(wt + phi)
        // f = 60 cycles/sec
        // w = 60*2pi rad./sec
        //double t = soc +
        double Xm = x / Math.cos(w * soc);

        return Xm;
    }

    public static String getPhasorText(double value, double ang) {
        return getPhasorText(value, ang, "%.2f∠%.1f°");
    }

    public static String getPhasorText(double value, double ang, String fmt) {
        return String.format(fmt, value);
    }

    public static int fnomToHz(int fnom) {
        if (fnom == 0)
            return 60;
        else if (fnom == 1)
            return 50;
        else if (fnom == 2)
            return 1;
        else
            return 60;
    }
}
