package com.simreal.VoxEngine;

import org.testng.Assert;
import org.testng.annotations.*;

public class ColorTest {

    @DataProvider(name = "colors")
    private Object[][] createColors() {
        return new Object[][] {
                // Red, Green, Blue, Alpha
                // Basic field testing
                { 0x00, 0x00, 0x00, 0x00 },
                { 0x10, 0x20, 0x40, 0x0C },
                { 0xFF, 0xFF, 0xFF, 0xFF },
                { 0x08, 0xFF, 0xFF, 0xFF },
                { 0xFF, 0x08, 0xFF, 0xFF },
                { 0xFF, 0xFF, 0x08, 0xFF },
                { 0xFF, 0xFF, 0xFF, 0x08 },
                // Oversized value testing
                { 0xFFF, 0x00, 0x00, 0x00 },
                { 0x00, 0xFFF, 0x00, 0x00 },
                { 0x00, 0x00, 0xFFF, 0x00 },
                { 0x00, 0x00, 0x00, 0xFFF },
        };
    }

    @Test(dataProvider = "colors")
    public void colorTest(int red, int green, int blue, int alpha) {
        long color = Color.setColor(red, green, blue, alpha);
        Assert.assertEquals(Color.red(color), (red & 0xFF));
        Assert.assertEquals(Color.green(color), (green & 0xFF));
        Assert.assertEquals(Color.blue(color), (blue & 0xFF));
        Assert.assertEquals(Color.alpha(color), (alpha & 0xFF));
    }

    @Test
    public void colorStringTest() {
        long color = Color.setColor(1, 2, 3, 4);
        String str = Color.toString(color);
        Assert.assertEquals(str, "RGBA { 01, 02, 03, 04 }");
    }
}
