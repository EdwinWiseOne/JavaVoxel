package com.simreal.VoxEngine;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Random;

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

    @DataProvider(name = "response")
    private Object[][] createResponse() {
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
        Assert.assertEquals(Node.tile(node), node&0xFFFFFF);
    }

    @Test(dataProvider =  "length")
    public void nodeDepthTest(int node, int depth) {
        node = Node.setDepth(node, (byte)depth);
        Assert.assertEquals(Node.depth(node), depth & 0x0F);
        Assert.assertEquals(Node.tile(node), node&0xFFFFFF);
    }

    @Test(dataProvider = "response")
    public void nodeResponseTest(int node, int response) {
        node = Node.setReponse(node, response);
        Assert.assertEquals(Node.response(node), response&0xFF);
        Assert.assertEquals(Node.tile(node), node&0xFFFFFF);
    }

    @Test
    public void nodeFlagsTest() {
        Random rn = new Random();

        for (int num=0; num<100; ++num) {
            int node = rn.nextInt() & Node.TILE_MASK;

            if (num == 0) {
                node = 0;
            } else if (num == 1) {
                node = Node.TILE_MASK;
            }
            int base = node;

            Assert.assertTrue(Node.isLeaf(node));
            Assert.assertFalse(Node.isParent(node));
            Assert.assertFalse(Node.isLoaded(node));
            Assert.assertTrue(Node.isStub(node));
            Assert.assertFalse(Node.isUsed(node));

            node = Node.setLeaf(node, !true);
            node = Node.setLoaded(node, true);
            node = Node.setUsed(node, true);

            Assert.assertFalse(Node.isLeaf(node));
            Assert.assertTrue(Node.isParent(node));
            Assert.assertTrue(Node.isLoaded(node));
            Assert.assertFalse(Node.isStub(node));
            Assert.assertTrue(Node.isUsed(node));

            node = Node.setLeaf(node, !false);
            node = Node.setLoaded(node, false);
            node = Node.setUsed(node, false);

            Assert.assertEquals(base, node);
        }
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
