package com.android.gpstest;

import java.util.HashMap;

public class GpsPhasorUtils {
    private static HashMap<Phase, Float> offset;
    private static float freq = 60f;
    private static double w;
    private static double time_base = 1e9;  // Nano seconds in 1 second
    public enum Phase {
        A, B, C;
   }

    GpsPhasorUtils() {
        offset.put(Phase.A, 0f);
        offset.put(Phase.B, 120f);
        offset.put(Phase.C, 240f);

        w = freq * 2 * Math.PI;
    }

    public static double getAngle(double x, long soc, long fos, Phase phase) {
        // x = Xm cos(wt + phi)
        // f = 60 cycles/sec
        // w = 60*2pi rad./sec
        //double t = soc +
        double Xm = x / Math.cos(w * soc);

        return Xm;
    }

    public static String getPhasorText(float value) {
        String phasor_fmt = "%.2f∠%.2f";

        return String.format(phasor_fmt, value);
    }
}
