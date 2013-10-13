package com.simreal.VoxEngine;


import org.testng.Assert;
import org.testng.annotations.*;
import javax.vecmath.Point3i;

public class VoxTreeTest {

    private static final int DEPTH = 4;

    private VoxTree tree;

    @BeforeMethod
    private void initTree() {
        tree = new VoxTree(DEPTH);
    }

    @Test
    public void testConstruction() {
        Assert.assertEquals(tree.depth, DEPTH);
        Assert.assertEquals(tree.edgeLength, (1<<DEPTH) * VoxTree.BRICK_EDGE);
        Assert.assertEquals(tree.stride(), VoxTree.BRICK_EDGE);

        NodePool.Statistics stats = tree.nodePool.analyze();
        Assert.assertEquals(stats.numLeaves, 1);
        Assert.assertEquals(stats.numNodes, 0);
    }

    @DataProvider(name = "insertion")
    private Object[][][] insertionData() {
        return new Object[][][][] {
                // Test 8 children at 0,0,0 corner
                {
                        {
                                // X, Y, Z, Leaves, Nodes
                                { 0x00 + 8, 0x00 + 8, 0x00 + 8, 29, 4 },
                                { 0x00 + 8, 0x00 + 8, 0x10 + 8, 29, 4 },
                                { 0x00 + 8, 0x10 + 8, 0x00 + 8, 29, 4 },
                                { 0x00 + 8, 0x10 + 8, 0x10 + 8, 29, 4 },
                                { 0x10 + 8, 0x00 + 8, 0x00 + 8, 29, 4 },
                                { 0x10 + 8, 0x00 + 8, 0x10 + 8, 29, 4 },
                                { 0x10 + 8, 0x10 + 8, 0x00 + 8, 29, 4 },
                                { 0x10 + 8, 0x10 + 8, 0x10 + 8, 22, 3 },
                        },
                },
                // Test 8 children deeper in
                {
                        {
                                // X, Y, Z, Leaves, Nodes
                                { 0xC0 + 8, 0xC0 + 8, 0xC0 + 8, 29, 4 },
                                { 0xC0 + 8, 0xC0 + 8, 0xD0 + 8, 29, 4 },
                                { 0xC0 + 8, 0xD0 + 8, 0xC0 + 8, 29, 4 },
                                { 0xC0 + 8, 0xD0 + 8, 0xD0 + 8, 29, 4 },
                                { 0xD0 + 8, 0xC0 + 8, 0xC0 + 8, 29, 4 },
                                { 0xD0 + 8, 0xC0 + 8, 0xD0 + 8, 29, 4 },
                                { 0xD0 + 8, 0xD0 + 8, 0xC0 + 8, 29, 4 },
                                { 0xD0 + 8, 0xD0 + 8, 0xD0 + 8, 22, 3 },
                        },
                },
                // Split open two cubes
                {
                        {
                                // X, Y, Z, Leaves, Nodes
                                { 0x00 + 8, 0x00 + 8, 0x00 + 8, 29, 4 },
                                { 0x20 + 8, 0x00 + 8, 0x10 + 8, 36, 5 },
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
            tree.setVoxelPoint(new Point3i((Integer)row[0], (Integer)row[1], (Integer)row[2]), (int)color);
System.out.println(tree);

            int leaves = (Integer)row[3];
            int nodes = (Integer)row[4];
            NodePool.Statistics stats = tree.nodePool.analyze();
            Assert.assertEquals(stats.numLeaves, leaves);
            Assert.assertEquals(stats.numNodes, nodes);
        }
    }
}
