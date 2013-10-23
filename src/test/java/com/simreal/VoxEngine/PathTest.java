package com.simreal.VoxEngine;

import org.testng.Assert;
import org.testng.annotations.*;

import javax.vecmath.Point3i;
import java.util.Random;

public class PathTest {

    @DataProvider(name = "addChild")
    private Object[][] addChildData() {
        return new Object[][][] {
                {
                        new Integer[] { 4, 2, 0, 7, 15}
                },
                {
                        new Integer[] { 3, 2, 0, 7, 15, 3, 2, 0, 7, 15, 3, 2, 0, 7, 5, 6, 0, 1, 9, 8, 12}
                },
                {
                        new Integer[] { 0, 0, 0, 0, 4}
                }
        };
    }
    @Test(dataProvider = "addChild")
    public void addChildTest(Integer[] children) {
        long path = 0L;
        int firstChild = 0;

        // Build path from children choices
        int depth = 0;
        for (int child : children) {
            if (path == 0L) {
                firstChild = child;
            }
            ++depth;

            // -------------------------------------------
            path = Path.addChild(path, child);
            // -------------------------------------------

            Assert.assertEquals(Path.child(path, 0), firstChild);
            if (depth < Path.PATH_MAX_LENGTH) {
                Assert.assertEquals(Path.length(path), depth);
                Assert.assertEquals(Path.child(path, depth-1), child & Path.PATH_CHILD_MASK);
            } else {
                Assert.assertEquals(Path.length(path), Path.PATH_MAX_LENGTH);
            }
        }

        // Verify all children
        depth = 0;
        for (int child : children) {
            Assert.assertEquals(Path.child(path, depth), child & Path.PATH_CHILD_MASK);
            ++depth;
            if (depth >= Path.PATH_MAX_LENGTH) {
                break;
            }
        }

    }

    @DataProvider(name = "fromPosition")
    public Object[][] fromPositionData() {
        return new Object[][]  {
                // Voxels are 2 wide; offset to center is therefore 1
                { 4, new Point3i(1, 1, 1),      0x0000000000000004L},
                { 4, new Point3i(3, 3, 3),      0x0070000000000004L},
                { 4, new Point3i(31, 31, 31),   0xFFF0000000000004L},
                { 4, new Point3i(17, 17, 1),    0xC000000000000004L},
                { 4, new Point3i(27, 7, 15),    0x95F0000000000004L}
        };
    }
    @Test(dataProvider = "fromPosition")
    public void fromPositionTest(int depth, Point3i position, long checkPath) {
        int edge = 2 << depth;
        long path = Path.fromPosition(position, edge, depth);
        Assert.assertEquals(path, checkPath);

    }

    @DataProvider(name = "toPosition")
    public Object[][] toPositionData() {
        return new Object[][] {
                { 4, 32, 0x0000000000000004L, new Point3i(1, 1, 1)},
                { 4, 32, 0x0070000000000004L, new Point3i(3, 3, 3)},
                { 4, 32, 0xFFF0000000000004L, new Point3i(31,31,31)},
                { 4, 32, 0xC000000000000004L, new Point3i(17, 17, 1)},
                { 4, 64, 0xC000000000000004L, new Point3i(34, 34, 2)},
                { 4, 32, 0x95F0000000000004L, new Point3i(27, 7, 15)}
        };
    }
    @Test(dataProvider = "toPosition")
    public void toPositionTest(int depth, int edge, long path, Point3i checkPosition) {
        Point3i position = Path.toPosition(path, edge);
        Assert.assertEquals(position, checkPosition);

    }

    @Test
    public void positionTest() {
        int depth = 8;
        int edge = 2 << depth;
        int num = 1000;

        Random rand = new Random();

        // Random test position/path round trips
        for (int cnt=0; cnt<num; ++cnt) {
            int x = (rand.nextInt(edge) & ~1) + 1;
            int y = (rand.nextInt(edge) & ~1) + 1;
            int z = (rand.nextInt(edge) & ~1) + 1;
            Point3i position = new Point3i(x,y,z);

            long path = Path.fromPosition(position, edge, depth);
            Point3i position2 = Path.toPosition(path, edge);
            long path2 = Path.fromPosition(position2, edge, depth);

            Assert.assertEquals(path, path2);
            Assert.assertEquals(position, position2);

        }
    }
}
