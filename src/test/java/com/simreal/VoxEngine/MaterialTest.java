package com.simreal.VoxEngine;

import org.testng.Assert;
import org.testng.annotations.*;

public class MaterialTest {

    @DataProvider(name = "colors")
    private Object[][] createColors() {
        return new Object[][] {
                // Red, Green, Blue, Alpha, Albedo, Reflectance
                // Basic field testing
                { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
                { 0x10, 0x20, 0x40, 0x0C, 0x2c, 0x81 },
                { 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF },
                { 0x08, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF },
                { 0xFF, 0x08, 0xFF, 0xFF, 0xFF, 0xFF },
                { 0xFF, 0xFF, 0x08, 0xFF, 0xFF, 0xFF },
                { 0xFF, 0xFF, 0xFF, 0x08, 0xFF, 0xFF },
                { 0xFF, 0xFF, 0xFF, 0x08, 0x08, 0xFF },
                { 0xFF, 0xFF, 0xFF, 0x08, 0xFF, 0x08 },
                // Oversized value testing
                { 0xFFF, 0x00, 0x00, 0x00, 0x00, 0x00 },
                { 0x00, 0xFFF, 0x00, 0x00, 0x00, 0x00 },
                { 0x00, 0x00, 0xFFF, 0x00, 0x00, 0x00 },
                { 0x00, 0x00, 0x00, 0xFFF, 0x00, 0x00 },
                { 0x00, 0x00, 0x00, 0x00, 0xFFF, 0x00 },
                { 0x00, 0x00, 0x00, 0x00, 0x00, 0xFFF },
                // Specific testing
                { 30, 30, 30, 255, 128, 32 },
        };
    }

    @Test(dataProvider = "colors")
    public void colorTest(int red, int green, int blue, int alpha, int albedo, int reflectance) {

        long color = Material.setMaterial(red, green, blue, alpha, albedo, reflectance);
        Assert.assertEquals(Material.red(color), (red & 0xFF));
        Assert.assertEquals(Material.green(color), (green & 0xFF));
        Assert.assertEquals(Material.blue(color), (blue & 0xFF));
        Assert.assertEquals(Material.alpha(color), (alpha & 0xFF));
        Assert.assertEquals(Material.albedo(color), (albedo & 0xFF));
        Assert.assertEquals(Material.reflectance(color), (reflectance & 0xFF));
    }

    @Test
    public void colorStringTest() {
        long color = Material.setMaterial(10, 20, 30, 40, 50, 60);
        String str = Material.toString(color);
        Assert.assertEquals(str, "Material { R0A, G14, B1E, A28, a32, r3C }");
    }
}
