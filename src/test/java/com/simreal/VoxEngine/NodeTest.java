package com.simreal.VoxEngine;

import org.testng.Assert;
import org.testng.annotations.*;

public class NodeTest {

    @DataProvider(name = "children")
    private Object[][] createChildren() {
        return new Object[][] {
                // Base node, child index
                {0, 0},
                {0, 1},
                {0, ~0},
                {~0, 0},
                {~0, 1},
                {~0, ~0}
        };
    }

    @DataProvider(name = "leaf")
    private Object[][] createLeaf() {
        return new Object[][] {
                // Base node, leaf, length (guard)
                {0, true, 0},
                {0, false, 0},
                {~0, true, 15},
                {~0, false, 15},
        };
    }

    @DataProvider(name = "length")
    private Object[][] createDepth() {
        return new Object[][] {
                // Base node, length
                {0, 0},
                {0, 1},
                {0, ~0},
                {~0, 0},
                {~0, 1},
                {~0, ~0},
        };
    }

    @Test(dataProvider = "children")
    public void nodeChildTest(int node, int child) {
        node = Node.setTile(node, child);
        Assert.assertEquals(Node.tile(node), child&0xFFFFFF);
    }

    @Test(dataProvider = "leaf")
    public void nodeLeafTest(int node, boolean leaf, int depth) {
        node = Node.setLeaf(node, leaf);
        Assert.assertEquals(Node.isLeaf(node), leaf);
        Assert.assertEquals(Node.depth(node), depth);
    }

    @Test(dataProvider =  "length")
    public void nodeDepthTest(int node, int depth) {
        node = Node.setDepth(node, (byte)depth);
        Assert.assertEquals(Node.depth(node), depth & 0x0F);
    }


    @Test
    public void nodeStringTest() {
        int node = Node.setTile(0, 7);
        node = Node.setLeaf(node, true);
        node = Node.setDepth(node, (byte)8);

        String str = Node.toString(node);
        Assert.assertEquals(str, "LEAF { Depth: 8 }");

        node = Node.setLeaf(node, false);
        str = Node.toString(node);
        Assert.assertEquals(str, "NODE { Depth: 8, STUB }");
    }
}
