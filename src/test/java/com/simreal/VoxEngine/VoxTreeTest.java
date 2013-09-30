package com.simreal.VoxEngine;


import org.testng.Assert;
import org.testng.annotations.*;
import javax.vecmath.Point3i;

public class VoxTreeTest {

    private static final int DEPTH = 4;

    private VoxTree tree;

    @DataProvider(name = "insertion")
    private Object[][][] insertionData() {
        return new Object[][][][] {
                {
                        {
                                // X, Y, Z, Leaves, Nodes
                                { 0x00 + 8, 0x00 + 8, 0x00 + 8, 29, 4 },
                                { 0x10 + 8, 0x00 + 8, 0x00 + 8, 29, 4 },
                                { 0x10 + 8, 0x00 + 8, 0x10 + 8, 29, 4 },
                                { 0x00 + 8, 0x00 + 8, 0x10 + 8, 29, 4 },
                        },
                },
        };
    }

    @BeforeTest
    private void initTree() {
        tree = new VoxTree(DEPTH);
    }

    @Test
    public void testConstruction() {
        Assert.assertEquals(tree.depth, DEPTH);
        Assert.assertEquals(tree.edgeLength, (1<<DEPTH) * VoxTree.BRICK_EDGE);
        Assert.assertEquals(tree.stride(), VoxTree.BRICK_EDGE);
        Assert.assertEquals(tree.firstFreeNode, 1);

        VoxTree.VoxTreeStatistics stats = tree.analyze();
        Assert.assertEquals(stats.numLeaves, 1);
        Assert.assertEquals(stats.numNodes, 0);
    }

    @Test(dataProvider = "insertion")
    public void testInsertion(Object[][] details) {
        int test = details.length;
        long color = (Long)(Color.setColor(255, 0, 0, 255));

        for (int idx=0; idx<details.length; ++idx) {
            Object[] row = details[idx];
            tree.setVoxelPoint(new Point3i((Integer)row[0], (Integer)row[1], (Integer)row[2]), (int)color);

            int leaves = (Integer)row[3];
            int nodes = (Integer)row[4];
            System.out.println(tree);
            VoxTree.VoxTreeStatistics stats = tree.analyze();
            Assert.assertEquals(stats.numLeaves, leaves);
            Assert.assertEquals(stats.numNodes, nodes);
        }
    }
}