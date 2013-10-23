package com.simreal.VoxEngine;

import com.simreal.VoxEngine.annotations.Stateless;
import javax.vecmath.Vector3d;


/**
 * Lighting provides calculation methods that apply a lighting model to a {@link Material}, as
 * well as other lighting manipulations.
 *
 */
@Stateless
public class Lighting {

    private static Lighting _lightingInstance = null;

    /**
     * Private constructor to enforce Factory access to the Lighting coordinator
     */
    private Lighting() {
    }

    static public Lighting instance() {
        if (_lightingInstance == null) {
            _lightingInstance = new Lighting();
        }

        return _lightingInstance;
    }

    /**
     * Illuminate a pixel of material with the Blinn-Phong lighting model, using a single light
     * with a predetermined color (white with regards to ambient and diffuse lighting and the
     * color 40,C0,C0 for specular highlights.
     *
     * kd (diffuse) = albedo, ka (ambient) = 1/4 diffuse, ks (specular) = reflectance
     * Blinn power factor = reflectance
     *
     * @param material    Material long to illuminate
     * @param normal      Normal vector off the material
     * @param light       Lighting vector
     * @param view        Viewing vector
     * @return            Material that has been lit; the RGBA are modified, and albedo, reflectance are copies of the input.
     */
    public long BlinnPhongFixedLight(long material, Vector3d normal, Vector3d light, Vector3d view) {

        // --------------------------------------
        // Calculate the various lighting coefficients
        // --------------------------------------
        int albedo = Material.albedo(material);
        int reflect = Material.reflectance(material);
        double kd = (double)albedo / 255.0;
        double ka = light.length() * 0.25;
        double ks = (double)reflect / 255.0;

        // --------------------------------------
        // Blinn vector is midway between the light and view vector
        // --------------------------------------
        Vector3d H = new Vector3d(light);
        H.add(view);
        H.normalize();

        // --------------------------------------
        // Ambient + diffuse illumination, clamped at the lower end by ka
        // --------------------------------------
        double illum = (ka + kd*normal.dot(light));
        if (illum < ka) illum = ka;

        int red = (int)(Material.red(material) * illum);
        int green = (int)(Material.green(material) * illum);
        int blue = (int)(Material.blue(material) * illum);
        int alpha = Material.alpha(material);

        // --------------------------------------
        // Specular highlights are colored by the light
        // Hard-coded to 40,C0,C0
        // --------------------------------------
        ks = ks*Math.pow(H.dot(normal), reflect);
        red += 0x40 * ks;
        green += 0xC0 * ks;
        blue += 0xC0 * ks;
        if (ks > 0.0) alpha += 255.0 * ks;

        // --------------------------------------
        // Clamp colors and alpha
        // --------------------------------------
        red = Math.max(0, Math.min(255, red));
        green = Math.max(0, Math.min(255, green));
        blue = Math.max(0, Math.min(255, blue));
        alpha = Math.min(255, alpha);

        return Material.setMaterial(red, green, blue, alpha, albedo, reflect);
    }

    /**
     * Pulsing light function, returns a lighting multiplier as a factor of time.
     *
     * @return      Lighting multiplier
     */
    public double pulse() {
        double cycle = (double)System.currentTimeMillis() / 125.0;
        return 1.0 + Math.pow(Math.cos(cycle), 3.0);
    }
}
