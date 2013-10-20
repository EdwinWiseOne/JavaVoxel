package com.simreal.VoxEngine;


import com.simreal.VoxEngine.annotations.Stateless;

import java.util.Formatter;

@Stateless
public class Material {
    /**
     * Material bit manipulations.
     *
     *  64              56              48              40              32
     *    +-------+-------+-------+-------+-------+-------+-------+-------+
     *    | (unused)                      | reflectance   | albedo        |
     *    +-------+-------+-------+-------+-------+-------+-------+-------+
     *    | alpha         | red           | green         | blue          |
     *    +-------+-------+-------+-------+-------+-------+-------+-------+
     *                  24              16               8               0
     */
    private static final long MATERIAL_BLUE_MASK       = 0x00000000000000FFL;
    private static final long MATERIAL_GREEN_MASK      = 0x000000000000FF00L;
    private static final long MATERIAL_RED_MASK        = 0x0000000000FF0000L;
    private static final long MATERIAL_ALPHA_MASK      = 0x00000000FF000000L;
    private static final long MATERIAL_ALBEDO_MASK     = 0x000000FF00000000L;
    private static final long MATERIAL_REFLECT_MASK    = 0x0000FF0000000000L;
    private static final long MATERIAL_UNUSED_MASK     = 0xFFFF000000000000L;

    private static final byte MATERIAL_BLUE_SHIFT      = 0;
    private static final byte MATERIAL_GREEN_SHIFT     = 8;
    private static final byte MATERIAL_RED_SHIFT       = 16;
    private static final byte MATERIAL_ALPHA_SHIFT     = 24;
    private static final byte MATERIAL_ALBEDO_SHIFT    = 32;
    private static final byte MATERIAL_REFLECT_SHIFT   = 40;
    private static final byte MATERIAL_UNUSED_SHIFT    = 48;

    private static final int MATERIAL_BYTE_MASK    = 0xFF;

    public static int red(long material){
        return (int)((material & MATERIAL_RED_MASK) >>> MATERIAL_RED_SHIFT);
    }

    public static int green(long material){
        return (int)((material & MATERIAL_GREEN_MASK) >>> MATERIAL_GREEN_SHIFT);
    }

    public static int blue(long material){
        return (int)((material & MATERIAL_BLUE_MASK) >>> MATERIAL_BLUE_SHIFT);
    }

    public static int alpha(long material){
        return (int)((material & MATERIAL_ALPHA_MASK) >>> MATERIAL_ALPHA_SHIFT);
    }

    public static int RGBA(long material) {
        // Implicit truncation to RGBA part
        return (int)material;
    }

    public static int albedo(long material){
        return (int)((material & MATERIAL_ALBEDO_MASK) >>> MATERIAL_ALBEDO_SHIFT);
    }

    public static int reflectance(long material){
        return (int)((material & MATERIAL_REFLECT_MASK) >>> MATERIAL_REFLECT_SHIFT);
    }

    public static long setMaterial(int red, int green, int blue, int alpha, int albedo, int reflectance){
        return ((long)(red & MATERIAL_BYTE_MASK) << MATERIAL_RED_SHIFT)
                | ((long)(green & MATERIAL_BYTE_MASK) << MATERIAL_GREEN_SHIFT)
                | ((long)(blue & MATERIAL_BYTE_MASK) << MATERIAL_BLUE_SHIFT)
                | ((long)(alpha & MATERIAL_BYTE_MASK) << MATERIAL_ALPHA_SHIFT)
                | ((long)(albedo & MATERIAL_BYTE_MASK) << MATERIAL_ALBEDO_SHIFT)
                | ((long)(reflectance & MATERIAL_BYTE_MASK) << MATERIAL_REFLECT_SHIFT);
    }

    public static long setMaterial(java.awt.Color color, int albedo, int reflectance) {
        if (null == color) {
            return 0L;
        }
        return setMaterial(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), albedo, reflectance);
    }

    // White ambient illuminations
//    public static long illuminate(long material, double illum){
//        int red = Material.red(material);
//        int green = Material.green(material);
//        int blue = Material.blue(material);
//        int alpha = Material.alpha(material);
//
//        red = Math.min(255, (int)(red * illum));
//        green = Math.min(255, (int)(green * illum));
//        blue = Math.min(255, (int)(blue * illum));
//
//        return Material.setMaterial(red, green, blue, alpha);
//    }

    public static long blend(long c1, long c2){
        double a1 = (double) Material.alpha(c1) / 255.0;
        double a2 = (double) Material.alpha(c2) / 255.0;
        double a3 = a1 + a2*(1.0-a1);

        int red = (int)(((double) Material.red(c1)*a1 + (double) Material.red(c2)*a2*(1.0-a1)) / a3);
        int green = (int)(((double) Material.green(c1)*a1 + (double) Material.green(c2)*a2*(1.0-a1)) / a3);
        int blue = (int)(((double) Material.blue(c1)*a1 + (double) Material.blue(c2)*a2*(1.0-a1)) / a3);
        int albedo = (int)(((double) Material.albedo(c1)*a1 + (double) Material.albedo(c2)*a2*(1.0-a1)) / a3);
        int reflect = (int)(((double) Material.reflectance(c1)*a1 + (double) Material.reflectance(c2)*a2*(1.0-a1)) / a3);
        int alpha = (int)(a3 * 255.0);

        return setMaterial(red, green, blue, alpha, albedo, reflect);
    }

    public static long gradient(long c1, long c2, double t)
    {
        double u = 1.0 - t;

        int red = (int)((double) Material.red(c1)*u + (double) Material.red(c2)*t);
        int green = (int)((double) Material.green(c1)*u + (double) Material.green(c2)*t);
        int blue = (int)((double) Material.blue(c1)*u + (double) Material.blue(c2)*t);
        int albedo = (int)((double) Material.albedo(c1)*u + (double) Material.albedo(c2)*t);
        int reflect = (int)((double) Material.reflectance(c1)*u + (double) Material.reflectance(c2)*t);
        int alpha = (int)((double) Material.alpha(c1)*u + (double) Material.alpha(c2)*t);

        return setMaterial(red, green, blue, alpha, albedo, reflect);
    }


    static public String toString(long material){
        StringBuilder result = new StringBuilder();
        Formatter fmt = new Formatter();

        result.append("RGBAar { ");
        fmt.format("%02X, ", Material.red(material));
        fmt.format("%02X, ", Material.green(material));
        fmt.format("%02X, ", Material.blue(material));
        fmt.format("%02X, ", Material.alpha(material));
        fmt.format("%02X, ", Material.albedo(material));
        fmt.format("%02X", Material.reflectance(material));
        result.append(fmt.toString());
        result.append(" }");
        return result.toString();
    }

}
