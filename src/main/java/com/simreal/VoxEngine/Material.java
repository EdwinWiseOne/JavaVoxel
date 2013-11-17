package com.simreal.VoxEngine;


import com.simreal.VoxEngine.annotations.Stateless;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Formatter;

/**
 * A Material defines the visible properties of a node, including the RGB color,
 * alpha transparency, albedo (reflection of diffuse light), and reflectance (reflection
 * of specular light).
 *
 * Includes static functions for blending materials in different ways.
 *
 * This class defines all the relevant bit manipulations for the defining and
 * querying a material, as stored in a simple long primitive value.
 *
 * <pre>
 *  64              56              48              40              32
 *    +-------+-------+-------+-------+-------+-------+-------+-------+
 *    | Node Resp.    | (unused)      | reflectance   | albedo        |
 *    +-------+-------+-------+-------+-------+-------+-------+-------+
 *    | alpha         | red           | green         | blue          |
 *    +-------+-------+-------+-------+-------+-------+-------+-------+
 *  32              24              16               8               0
 *  </pre>
 */
@Stateless
public class Material {

    static final Logger LOG = LoggerFactory.getLogger(Material.class.getName());

    private static final long BLUE_MASK     = 0x00000000000000FFL;
    private static final long GREEN_MASK    = 0x000000000000FF00L;
    private static final long RED_MASK      = 0x0000000000FF0000L;
    private static final long ALPHA_MASK    = 0x00000000FF000000L;
    private static final long ALBEDO_MASK   = 0x000000FF00000000L;
    private static final long REFLECT_MASK  = 0x0000FF0000000000L;
    private static final long UNUSED_MASK   = 0x00FF000000000000L;
    private static final long NODE_MASK     = 0xFF00000000000000L;

    private static final byte BLUE_SHIFT    = 0;
    private static final byte GREEN_SHIFT   = 8;
    private static final byte RED_SHIFT     = 16;
    private static final byte ALPHA_SHIFT   = 24;
    private static final byte ALBEDO_SHIFT  = 32;
    private static final byte REFLECT_SHIFT = 40;
    private static final byte UNUSED_SHIFT  = 48;
    private static final byte NODE_SHIFT    = 56;

    private static final int BYTE_MASK      = 0xFF;

    /**
     * Gets the red color component of the material.
     *
     * @param material      Material long to get the red component from
     * @return              Red component value in [0..255]
     */
    public static int red(long material){
        return (int)((material & RED_MASK) >>> RED_SHIFT);
    }

    /**
     * Gets the green color component of the material.
     *
     * @param material      Material long to get the green component from
     * @return              Green component value in [0..255]
     */
    public static int green(long material){
        return (int)((material & GREEN_MASK) >>> GREEN_SHIFT);
    }

    /**
     * Gets the blue color component of the material.
     *
     * @param material      Material long to get the blue component from
     * @return              Blue component value in [0..255]
     */
    public static int blue(long material){
        return (int)((material & BLUE_MASK) >>> BLUE_SHIFT);
    }

    /**
     * Gets the transparency (alpha) component of the material.
     *
     * @param material      Material long to get the alpha from
     * @return              Alpha transparency value in [0..255]
     */
    public static int alpha(long material){
        return (int)((material & ALPHA_MASK) >>> ALPHA_SHIFT);
    }

    /**
     * Gets the full RGBA color specification of the material, in a format directly
     * compatible with Java BufferedImage Raster data
     *
     * @param material      Material long to get the RGBA color specification from
     * @return              BufferedImage compatible RGBA value
     */
    public static int RGBA(long material) {
        // Implicit truncation to RGBA part
        return (int)material;
    }

    /**
     * Gets the albedo (diffuse light reflection factor) component of the material
     *
     * @param material      Material long to get the albedo component from
     * @return              Albedo component (diffuse light reflection factor) in [0..255]
     */
    public static int albedo(long material){
        return (int)((material & ALBEDO_MASK) >>> ALBEDO_SHIFT);
    }

    /**
     * Gets the reflectance (specular light reflection factor) component of the material
     *
     * @param material      Material long to get the reflectance component from
     * @return              Reflectance component (specular light reflection factor) in [0..255]
     */
    public static int reflectance(long material){
        return (int)((material & REFLECT_MASK) >>> REFLECT_SHIFT);
    }

    public static int node(long material){
        return (int)((material & NODE_MASK) >>> NODE_SHIFT);
    }

    public static long setNode(long material, int node){
        return (material & ~NODE_MASK)
                | ((long)node << NODE_SHIFT);
    }

    public static long clearNode(long material) {
        return (material & ~NODE_MASK);
    }

    /**
     * Sets all of the components in a material long.  All values are in the range [0.255]
     *
     * @param red               Red color component
     * @param green             Green color component
     * @param blue              Blue color component
     * @param alpha             Alpha transparency component
     * @param albedo            Albedo diffuse reflection component
     * @param reflectance       Reflectance specular reflection component
     * @return                  Material long with all components set
     */
    public static long setMaterial(int red, int green, int blue, int alpha, int albedo, int reflectance){
        return ((long)(red & BYTE_MASK) << RED_SHIFT)
                | ((long)(green & BYTE_MASK) << GREEN_SHIFT)
                | ((long)(blue & BYTE_MASK) << BLUE_SHIFT)
                | ((long)(alpha & BYTE_MASK) << ALPHA_SHIFT)
                | ((long)(albedo & BYTE_MASK) << ALBEDO_SHIFT)
                | ((long)(reflectance & BYTE_MASK) << REFLECT_SHIFT);
    }

    public static long scaleAlpha(long material, double scale) {
        int alpha = (int)((material & ALPHA_MASK) >>> ALPHA_SHIFT);
        alpha = Math.max(0, Math.min(255, (int)(alpha*scale)));
        return ((material & ~ALPHA_MASK) | ((long)alpha << ALPHA_SHIFT));
    }

    /**
     * Sets all of the components in a material long, specifying the color components
     * via a AWT Color parameter.
     * 
     * @param color         AWT-compatible RGBA color value
     * @param albedo        Albedo diffuse reflection component in [0..255]
     * @param reflectance   Reflectance specular reflection component in [0..255]
     * @return              Material long with all the components set
     */
    public static long setMaterial(java.awt.Color color, int albedo, int reflectance) {
        if (null == color) {
            return 0L;
        }
        return setMaterial(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), albedo, reflectance);
    }

    /**
     * Blends two materials using their alpha parameter, such that material-1 is OVER material-2.
     * See: http://cairographics.org/operators/
     *
     * @param mat1      Material (long) in front in [0..255]
     * @param mat2      Material (long) behind in [0..255]
     * @return          Material long that is the alpha blended result of Material1 OVER Material2
     */
    public static long alphaBlend(long mat1, long mat2){
        // Scale the alpha bytes into normalized form
        double a1 = (double) Material.alpha(mat1) / 255.0;
        double a2 = (double) Material.alpha(mat2) / 255.0;
        // Alpha result, also used in blending
        double a3 = a1 + a2*(1.0-a1);

        int red = (int)(((double) Material.red(mat1)*a1 + (double) Material.red(mat2)*a2*(1.0-a1)) / a3);
        int green = (int)(((double) Material.green(mat1)*a1 + (double) Material.green(mat2)*a2*(1.0-a1)) / a3);
        int blue = (int)(((double) Material.blue(mat1)*a1 + (double) Material.blue(mat2)*a2*(1.0-a1)) / a3);
        int albedo = (int)(((double) Material.albedo(mat1)*a1 + (double) Material.albedo(mat2)*a2*(1.0-a1)) / a3);
        int reflect = (int)(((double) Material.reflectance(mat1)*a1 + (double) Material.reflectance(mat2)*a2*(1.0-a1)) / a3);
        int alpha = (int)(a3 * 255.0);

        return setMaterial(red, green, blue, alpha, albedo, reflect);
    }

    /**
     * Calculates a linear interpolation (gradient) between Material1 and Material2 using the
     * proportional control t, where t=0 returns Material1, t=1 returns Material2.
     *
     * @param mat1      Material (long) at t=0
     * @param mat2      Material (long) at t=1
     * @param t         Gradient control in [0..1]
     * @return          Material long that is the linear blend between Material1 and Material2, m1*(1-t) + m2*t
     */
    public static long gradient(long mat1, long mat2, double t)
    {
        double u = 1.0 - t;

        int red = (int)((double) Material.red(mat1)*u + (double) Material.red(mat2)*t);
        int green = (int)((double) Material.green(mat1)*u + (double) Material.green(mat2)*t);
        int blue = (int)((double) Material.blue(mat1)*u + (double) Material.blue(mat2)*t);
        int albedo = (int)((double) Material.albedo(mat1)*u + (double) Material.albedo(mat2)*t);
        int reflect = (int)((double) Material.reflectance(mat1)*u + (double) Material.reflectance(mat2)*t);
        int alpha = (int)((double) Material.alpha(mat1)*u + (double) Material.alpha(mat2)*t);

        return setMaterial(red, green, blue, alpha, albedo, reflect);
    }


    /**
     * Create a string representation of this material
     *
     * @param material  Material long to describe
     * @return          String description of the material
     */
    static public String toString(long material){
        StringBuilder result = new StringBuilder();
        Formatter fmt = new Formatter();

        if (Material.alpha(material) == 0) {
            return "(void)";
        }

        result.append("Material { ");
        fmt.format("R%02X, ", Material.red(material));
        fmt.format("G%02X, ", Material.green(material));
        fmt.format("B%02X, ", Material.blue(material));
        fmt.format("A%02X, ", Material.alpha(material));
        fmt.format("a%02X, ", Material.albedo(material));
        fmt.format("r%02X", Material.reflectance(material));
        result.append(fmt.toString());
        result.append(" }");
        return result.toString();
    }

}
