package com.simreal.VoxEngine;

import org.testng.Assert;
import org.testng.annotations.*;

public class PathTest {

    @DataProvider(name = "path1")
    public Object[][] createPath1() {
        return new Object[][] {
                // choice, depth, first choice (guard)
                { 4, 1, 4 },
                { 2, 2, 4 },
                { 0, 3, 4 },
                { 7, 4, 4 },
                { 15, 5, 4 },
        };
    }

    @DataProvider(name = "path2")
    public Object[][] createPath2() {
        return new Object[][] {
                // choice, depth, first choice (guard)
                { 3, 1, 3 },
                { 2, 2, 3 },
                { 0, 3, 3 },
                { 7, 4, 3 },
                { 15, 5, 3 },
                { 3, 6, 3 },
                { 2, 7, 3 },
                { 0, 8, 3 },
                { 7, 9, 3 },
                { 15, 10, 3 },
                { 3, 11, 3 },
                { 2, 12, 3 },
                { 0, 13, 3 },
                { 7, 14, 3 },
                { 15, 15, 3 },
                { 3, 15, 3 },
                { 2, 15, 3 },
        };
    }

    @DataProvider(name = "path3")
    public Object[][] createPath3() {
        return new Object[][] {
                // choice, depth, first choice (guard)
                { 0, 1, 0 },
                { 0, 2, 0 },
                { 0, 3, 0 },
                { 4, 4, 0 },
        };
    }

    private long doPathTest(long path, int child, int depth, int first) {
        path = Path.addChild(path, child);

        Assert.assertEquals(Path.depth(path), depth);

        depth = Path.depth(path);
        if (depth < Path.PATH_MAX_DEPTH) {
            Assert.assertEquals(Path.child(path, depth-1), child & 0x07);
        }
        Assert.assertEquals(Path.child(path, 0), first);

        return path;
    }

    static long path1 = 0L;
    @Test(dataProvider = "path1")
    public void pathTest1(int child, int depth, int first) {
        path1 = doPathTest(path1, child, depth, first);
    }

    static long path2 = 0L;
    @Test(dataProvider = "path2")
    public void pathTest2(int child, int depth, int first) {
        path2 = doPathTest(path2, child, depth, first);
    }

    static long path3 = 0L;
    @Test(dataProvider = "path3")
    public void pathTest3(int child, int depth, int first) {
        path3 = doPathTest(path3, child, depth, first);
    }

}
