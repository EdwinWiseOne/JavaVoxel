package com.simreal.VoxEngine;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TilePoolTest {

    private TilePool _pool;
    final private int _tiles = 4;
    final private int _nodes = _tiles*8;

    @BeforeMethod
    private void initTree() {
        _pool = new TilePool(_tiles);
    }
    
    @Test
    public void tileTestPushPop1() {
        Assert.assertEquals(_pool.numTiles(), _tiles);
        Assert.assertEquals(_pool.numNodes(), _nodes);

        TilePool.Statistics stats = _pool.analyze(0);
        Assert.assertEquals(stats.numTiles, 0);
        Assert.assertEquals(stats.numVisible, 0);
        Assert.assertEquals(stats.numParents, 0);
        Assert.assertEquals(stats.numLeaves, 0);

        int tile1 = _pool.getFreeTile();
        int tile2 = _pool.getFreeTile();
        int tile3 = _pool.getFreeTile();
        int tile4 = _pool.getFreeTile();
        int tile5 = _pool.getFreeTile();

        stats = _pool.analyze(0);
        Assert.assertEquals(tile5, -1);
        Assert.assertEquals(stats.numTiles, _tiles);
        Assert.assertEquals(stats.numVisible, _tiles);
        Assert.assertEquals(stats.numParents, 0);
        Assert.assertEquals(stats.numLeaves, 0);

        _pool.putTileFree(tile1);
        _pool.putTileFree(tile2);
        _pool.putTileFree(tile3);
        _pool.putTileFree(tile4);

        boolean excepted = false;
        try {
            _pool.putTileFree(tile4);
        } catch (Exception e) {

            excepted = true;
        }
        Assert.assertTrue(excepted);

        stats = _pool.analyze(0);
        Assert.assertEquals(stats.numTiles, 0);
        Assert.assertEquals(stats.numParents, 0);
        Assert.assertEquals(stats.numLeaves, 0);
        Assert.assertEquals(stats.numVisible, 0);
    }

    @Test
    public void tileTestPushPop2() {
        int tile1 = _pool.getFreeTile();
        int tile2 = _pool.getFreeTile();
        int tile3 = _pool.getFreeTile();
        int tile4 = _pool.getFreeTile();

        _pool.putTileFree(tile4);
        _pool.putTileFree(tile3);
        _pool.putTileFree(tile2);
        _pool.putTileFree(tile1);

        TilePool.Statistics stats = _pool.analyze(0);
        Assert.assertEquals(stats.numTiles, 0);
        Assert.assertEquals(stats.numParents, 0);
        Assert.assertEquals(stats.numLeaves, 0);
        Assert.assertEquals(stats.numVisible, 0);
    }

    @Test
    public void tileTestMath() {
        Assert.assertEquals(_pool.getNodeInTileIdx(0, 0), 1);
        Assert.assertEquals(_pool.getNodeInTileIdx(0, 1), 2);
        Assert.assertEquals(_pool.getNodeInTileIdx(1, 0), 9);
        Assert.assertEquals(_pool.getNodeInTileIdx(1, 1), 10);

        Assert.assertEquals(_pool.getTileForNodeIdx(1), 0);
        Assert.assertEquals(_pool.getTileForNodeIdx(2), 0);
        Assert.assertEquals(_pool.getTileForNodeIdx(8), 0);
        Assert.assertEquals(_pool.getTileForNodeIdx(9), 1);
        Assert.assertEquals(_pool.getTileForNodeIdx(10), 1);

        Assert.assertEquals(_pool.getChildForNodeIdx(1), 0);
        Assert.assertEquals(_pool.getChildForNodeIdx(2), 1);
        Assert.assertEquals(_pool.getChildForNodeIdx(9), 0);
        Assert.assertEquals(_pool.getChildForNodeIdx(10), 1);
    }
}
