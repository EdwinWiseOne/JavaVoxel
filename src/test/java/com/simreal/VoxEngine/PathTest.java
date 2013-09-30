package com.simreal.VoxEngine;

import org.testng.Assert;
import org.testng.annotations.*;

public class PathTest {

    @DataProvider(name = "paths")
    private Object[][][] createPath1() {
        return new Object[][][][] {
                {
                        {
                                // choice, depth
                                { 4, 1 },
                                { 2, 2 },
                                { 0, 3 },
                                { 7, 4 },
                                { 15, 5 },
                        }
                },
                {
                        {
                                // choice, depth
                                {  3, 1 },
                                {  2, 2 },
                                {  0, 3 },
                                {  7, 4 },
                                { 15, 5 },
                                {  3, 6 },
                                {  2, 7 },
                                {  0, 8 },
                                {  7, 9 },
                                { 15, 10 },
                                {  3, 11 },
                                {  2, 12 },
                                {  0, 13 },
                                {  7, 14 },
                                { 15, 15 },
                                {  3, 15 },
                                {  2, 15 },
                        },
                },
                {
                        {
                                // choice, depth, first choice (guard)
                                { 0, 1 },
                                { 0, 2 },
                                { 0, 3 },
                                { 4, 4 },
                        }
                }
        };
    }

    @Test(dataProvider = "paths")
    public void pathTest(Object[][] choices) {
        long path = 0L;

        Object[] choice;
        int first = 0;
        for (int idx=0; idx<choices.length; ++idx) {
            choice = choices[idx];
            int child = (Integer)choice[0];
            int depth = (Integer)choice[1];
            if (idx == 0) {
                first = child;

            }
            // -------------------------------------------
            path = Path.addChild(path, child);
            Assert.assertEquals(Path.depth(path), depth);
            // -------------------------------------------

            depth = Path.depth(path);
            if (depth < Path.PATH_MAX_DEPTH) {
                Assert.assertEquals(Path.child(path, depth-1), child & 0x07);
            }
            Assert.assertEquals(Path.child(path, 0), first);
        }
    }
}
