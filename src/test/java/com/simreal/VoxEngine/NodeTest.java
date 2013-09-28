package com.simreal.VoxEngine;

import org.testng.Assert;
import org.testng.annotations.*;

public class NodeTest {

    @DataProvider(name = "colors")
    public Object[][] createColors() {
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

    @DataProvider(name = "children")
    public Object[][] createChildren() {
        return new Object[][] {
                // Base node, child index, alpha (guard)
                {0L, 0, 0},
                {0L, 1, 0},
                {0L, ~0, 0},
                {~0L, 0, 255},
                {~0L, 1, 255},
                {~0L, ~0, 255}
        };
    }

    @DataProvider(name = "leaf")
    public Object[][] createLeaf() {
        return new Object[][] {
                // Base node, leaf, depth (guard)
                {0L, true, 0},
                {0L, false, 0},
                {~0L, true, 15},
                {~0L, false, 15},
        };
    }

    @DataProvider(name = "depth")
    public Object[][] createDepth() {
        return new Object[][] {
                // Base node, depth, blue (guard)
                {0L, 0, 0},
                {0L, 1, 0},
                {0L, ~0, 0},
                {~0L, 0, 255},
                {~0L, 1, 255},
                {~0L, ~0, 255},
        };
    }

    @Test(dataProvider = "colors")
    public void nodeColorTest(int red, int green, int blue, int alpha) {
        long node = 0;
        node = Node.setColor(node, red, green, blue, alpha);
        Assert.assertEquals(Node.red(node), (red & 0xFF));
        Assert.assertEquals(Node.green(node), (green & 0xFF));
        Assert.assertEquals(Node.blue(node), (blue & 0xFF));
        Assert.assertEquals(Node.alpha(node), (alpha & 0xFF));
        Assert.assertEquals(Node.child(node), 0);

        long color = Color.setColor(red, green, blue, alpha);
        node = Node.setColor(node, color);
        Assert.assertEquals(Node.red(node), (red & 0xFF));
        Assert.assertEquals(Node.green(node), (green & 0xFF));
        Assert.assertEquals(Node.blue(node), (blue & 0xFF));
        Assert.assertEquals(Node.alpha(node), (alpha & 0xFF));
        Assert.assertEquals(Node.child(node), 0);
    }

    @Test(dataProvider = "children")
    public void nodeChildTest(long node, int child, int alpha) {
        node = Node.setChild(node, child);
        Assert.assertEquals(Node.child(node), child&0xFFFFFF);
        Assert.assertEquals(Node.alpha(node), alpha);
    }

    @Test(dataProvider = "leaf")
    public void nodeLeafTest(long node, boolean leaf, int depth) {
        node = Node.setLeaf(node, leaf);
        Assert.assertEquals(Node.isLeaf(node), leaf);
        Assert.assertEquals(Node.depth(node), depth);
    }

    @Test(dataProvider =  "depth")
    public void nodeDepthTest(long node, int depth, int blue) {
        node = Node.setDepth(node, (byte)depth);
        Assert.assertEquals(Node.depth(node), depth & 0x0F);
        Assert.assertEquals(Node.blue(node), blue);
    }

    @Test
    public void nodeStringTest() {
        long node = Node.setColor(0L, 1, 2, 3, 4);
        node = Node.setChild(node, 5);
        node = Node.setLeaf(node, true);
        node = Node.setDepth(node, (byte)6);

        String str = Node.toString(node);
        Assert.assertEquals(str, "LEAF {Color: RGBA { 01, 02, 03, 04 }, Depth: 6, Child: 5 }");

        node = Node.setLeaf(node, false);
        str = Node.toString(node);
        Assert.assertEquals(str, "NODE {Color: RGBA { 01, 02, 03, 04 }, Depth: 6, Child: 5 }");
    }
}
