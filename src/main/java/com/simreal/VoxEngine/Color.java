package com.simreal.VoxEngine;


import com.simreal.VoxEngine.annotations.Stateless;

import java.util.Formatter;

@Stateless
public class Color {
    /**
     * Color bit manipulations.
     * Sized and organized to match colors in Node
     *
     *
     *  64            56            48            40            32
     *    +------+------+------+------+------+------+------+------+
     *    | (unused)                                              |
     *    +------+------+------+------+------+------+------+------+
     *    | alpha       | red         | green       | blue        |
     *    +------+------+------+------+------+------+------+------+
     *                24            16             8             0
     */
    private static final long COLOR_BLUE_MASK   = 0x00000000000000FFL;
    private static final long COLOR_GREEN_MASK  = 0x000000000000FF00L;
    private static final long COLOR_RED_MASK    = 0x0000000000FF0000L;
    private static final long COLOR_ALPHA_MASK  = 0x00000000FF000000L;

    private static final byte COLOR_BLUE_SHIFT  = 0;
    private static final byte COLOR_GREEN_SHIFT = 8;
    private static final byte COLOR_RED_SHIFT   = 16;
    private static final byte COLOR_ALPHA_SHIFT = 24;

    private static final int COLOR_BYTE_MASK    = 0xFF;

    public static int red(long color){
        return (int)((color & COLOR_RED_MASK) >>> COLOR_RED_SHIFT);
    }

    public static int green(long color){
        return (int)((color & COLOR_GREEN_MASK) >>> COLOR_GREEN_SHIFT);
    }

    public static int blue(long color){
        return (int)((color & COLOR_BLUE_MASK) >>> COLOR_BLUE_SHIFT);
    }

    public static int alpha(long color){
        return (int)((color & COLOR_ALPHA_MASK) >>> COLOR_ALPHA_SHIFT);
    }

    public static long setColor(int red, int green, int blue, int alpha){
        return ((long)(red & COLOR_BYTE_MASK) << COLOR_RED_SHIFT)
                | ((long)(green & COLOR_BYTE_MASK) << COLOR_GREEN_SHIFT)
                | ((long)(blue & COLOR_BYTE_MASK) << COLOR_BLUE_SHIFT)
                | ((long)(alpha & COLOR_BYTE_MASK) << COLOR_ALPHA_SHIFT);
    }

    public static long setColor(java.awt.Color color) {
        return setColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    public static long illuminate(long color, double illum){
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        int alpha = Color.alpha(color);

        red = Math.min(255, (int)(red * illum));
        green = Math.min(255, (int)(green * illum));
        blue = Math.min(255, (int)(blue * illum));

        return Color.setColor(red, green, blue, alpha);
    }

    public static long blend(long c1, long c2){
        double a1 = (double)Color.alpha(c1) / 255.0;
        double a2 = (double)Color.alpha(c2) / 255.0;
        double a3 = a1 + a2*(1.0-a1);

        long red = (long)(((double)Color.red(c1)*a1 + (double)Color.red(c2)*a2*(1.0-a1)) / a3);
        long green = (long)(((double)Color.green(c1)*a1 + (double)Color.green(c2)*a2*(1.0-a1)) / a3);
        long blue = (long)(((double)Color.blue(c1)*a1 + (double)Color.blue(c2)*a2*(1.0-a1)) / a3);
        long alpha = (long)(a3 * 255.0);

        return (red << COLOR_RED_SHIFT)
                | (green << COLOR_GREEN_SHIFT)
                | (blue << COLOR_BLUE_SHIFT)
                | (alpha << COLOR_ALPHA_SHIFT);
    }

    public static long gradient(long c1, long c2, double t)
    {
        double u = 1.0 - t;

        long red = (long)((double)Color.red(c1)*u + (double)Color.red(c2)*t);
        long green = (long)((double)Color.green(c1)*u + (double)Color.green(c2)*t);
        long blue = (long)((double)Color.blue(c1)*u + (double)Color.blue(c2)*t);
        long alpha = (long)((double)Color.alpha(c1)*u + (double)Color.alpha(c2)*t);

        return (red << COLOR_RED_SHIFT)
                | (green << COLOR_GREEN_SHIFT)
                | (blue << COLOR_BLUE_SHIFT)
                | (alpha << COLOR_ALPHA_SHIFT);
    }

    static public String toString(long color){
        StringBuilder result = new StringBuilder();
        Formatter fmt = new Formatter();

        result.append("RGBA { ");
        fmt.format("%02X, ", Color.red(color));
        fmt.format("%02X, ", Color.green(color));
        fmt.format("%02X, ", Color.blue(color));
        fmt.format("%02X", Color.alpha(color));
        result.append(fmt.toString());
        result.append(" }");
        return result.toString();
    }

}
