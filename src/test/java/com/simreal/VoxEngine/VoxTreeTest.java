package com.simreal.VoxEngine;


import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.vecmath.Point3i;

public class VoxTreeTest {

    private static final int DEPTH = 4;

        private VoxTree _tree;

    @BeforeMethod
    private void initTree() {
        _tree = new VoxTree(DEPTH, 1,    1, 1);
    }

    @Test
    public void testConstruction() {
        Assert.assertEquals(_tree.depth, DEPTH);
        Assert.assertEquals(_tree.edgeLength, (1<<DEPTH) * VoxTree.BRICK_EDGE);
        Assert.assertEquals(_tree.stride(), VoxTree.BRICK_EDGE);

        TilePool.Statistics stats = _tree.tilePool.analyze(0);
        Assert.assertEquals(stats.numTiles, 0);
        Assert.assertEquals(stats.numParents, 0);
        Assert.assertEquals(stats.numLeaves, 0);
        Assert.assertEquals(stats.numVisible, 0);

        _tree.setVoxelPoint(new Point3i(0, 0, 0), Material.setMaterial(0, 0, 255, 255, 128, 128));

        stats = _tree.tilePool.analyze(0);
        Assert.assertEquals(stats.numTiles, 4);
        Assert.assertEquals(stats.numParents, 3);
        Assert.assertEquals(stats.numLeaves, 29);
        Assert.assertEquals(stats.numVisible, 4);

        // Note that root is node 0, but not actually indexed in the nodes[] array, but all our node indices account for this offset
        // so nodes[index-1]
        Assert.assertEquals(_tree.getNodeIndexForPath(0, false), 0);    // Root
        Assert.assertEquals(_tree.getNodeIndexForPath(1, false), 1);
        Assert.assertEquals(_tree.getNodeIndexForPath(2, false), 9);
        Assert.assertEquals(_tree.getNodeIndexForPath(3, false), 17);
        Assert.assertEquals(_tree.getNodeIndexForPath(4, false), 25);

        _tree.setVoxelPath(4, 0);
        stats = _tree.tilePool.analyze(0);
        Assert.assertEquals(stats.numTiles, 0);
        Assert.assertEquals(stats.numParents, 0);
        Assert.assertEquals(stats.numLeaves, 0);
        Assert.assertEquals(stats.numVisible, 0);
    }

    @DataProvider(name = "insertion")
    private Object[][][] insertionData() {
        return new Object[][][][] {
                // Test 8 children at 0,0,0 corner
                {
                        {
                                // X, Y, Z, Leaves, Nodes
                                { 0x00 + 8, 0x00 + 8, 0x00 + 8, 29, 4, 3 },
                                { 0x00 + 8, 0x00 + 8, 0x10 + 8, 29, 4, 3 },
                                { 0x00 + 8, 0x10 + 8, 0x00 + 8, 29, 4, 3 },
                                { 0x00 + 8, 0x10 + 8, 0x10 + 8, 29, 4, 3 },
                                { 0x10 + 8, 0x00 + 8, 0x00 + 8, 29, 4, 3 },
                                { 0x10 + 8, 0x00 + 8, 0x10 + 8, 29, 4, 3 },
                                { 0x10 + 8, 0x10 + 8, 0x00 + 8, 29, 4, 3 },
                                { 0x10 + 8, 0x10 + 8, 0x10 + 8, 22, 3, 2 },
                        },
                },
                // Test 8 children deeper in
                {
                        {
                                // X, Y, Z, Leaves, Nodes
                                { 0xC0 + 8, 0xC0 + 8, 0xC0 + 8, 29, 4, 3 },
                                { 0xC0 + 8, 0xC0 + 8, 0xD0 + 8, 29, 4, 3 },
                                { 0xC0 + 8, 0xD0 + 8, 0xC0 + 8, 29, 4, 3 },
                                { 0xC0 + 8, 0xD0 + 8, 0xD0 + 8, 29, 4, 3 },
                                { 0xD0 + 8, 0xC0 + 8, 0xC0 + 8, 29, 4, 3 },
                                { 0xD0 + 8, 0xC0 + 8, 0xD0 + 8, 29, 4, 3 },
                                { 0xD0 + 8, 0xD0 + 8, 0xC0 + 8, 29, 4, 3 },
                                { 0xD0 + 8, 0xD0 + 8, 0xD0 + 8, 22, 3, 2 },
                        },
                },
                // Split open two cubes
                {
                        {
                                // X, Y, Z, Leaves, Nodes
                                { 0x00 + 8, 0x00 + 8, 0x00 + 8, 29, 4, 3 },
                                { 0x20 + 8, 0x00 + 8, 0x10 + 8, 36, 5, 4 },
                        }
                },
        };
    }
    @Test(dataProvider = "insertion")
    public void testInsertion(Object[][] details) {
        int test = details.length;
        long color = (Long)(Material.setMaterial(255, 0, 0, 255, 128, 32));

        for (int idx=0; idx<details.length; ++idx) {
            Object[] row = details[idx];
            _tree.setVoxelPoint(new Point3i((Integer) row[0], (Integer) row[1], (Integer) row[2]), (int) color);

            int leaves = (Integer)row[3];
            int tiles = (Integer)row[4];
            int parents = (Integer)row[5];
            TilePool.Statistics stats = _tree.tilePool.analyze(0);
            Assert.assertEquals(stats.numLeaves, leaves);
            Assert.assertEquals(stats.numTiles, tiles);
            Assert.assertEquals(stats.numParents, parents);
        }
    }
}
