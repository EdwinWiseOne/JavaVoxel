package com.simreal.VoxEngine;

import com.simreal.VoxEngine.annotations.Stateless;
import javax.vecmath.Vector3d;


@Stateless
public class Lighting {

    static public long illuminate(long material, Vector3d normal, Vector3d light, Vector3d view) {
        int albedo = Material.albedo(material);
        int reflect = Material.reflectance(material);

        double kd = (double)albedo / 255.0;
        double ka = light.length() * 0.25;
        double ks = (double)reflect / 255.0;
        Vector3d H = new Vector3d(light);
        H.add(view);
        H.normalize();

        ks = ks*Math.pow(H.dot(normal), reflect);
        double illum = (ka + kd*normal.dot(light));
        if (illum < ka) illum = ka;

        int red = (int)(Material.red(material) * illum);
        int green = (int)(Material.green(material) * illum);
        int blue = (int)(Material.blue(material) * illum);
        int alpha = Material.alpha(material);

        // Add in specular from light
        red += 64 * ks;
        green += 192 * ks;
        blue += 192 * ks;
        if (ks > 0.0) alpha += 255.0 * ks;

        red = Math.max(0, Math.min(255, red));
        green = Math.max(0, Math.min(255, green));
        blue = Math.max(0, Math.min(255, blue));
        alpha = Math.min(255, alpha);

        return Material.setMaterial(red, green, blue, alpha, albedo, reflect);
    }


    static public double pulse() {
        double cycle = (double)System.currentTimeMillis() / 125.0;
        return 1.0 + Math.pow(Math.cos(cycle), 3.0);
    }
}
