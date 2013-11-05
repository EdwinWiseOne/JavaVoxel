package com.simreal.VoxEngine;

import org.testng.annotations.Test;

public class TilePoolTest {

    @Test
    public void tileTest1() {
        TilePool pool = new TilePool(4);

        int tile1 = pool.getFreeTile();
        int tile2 = pool.getFreeTile();
        int tile3 = pool.getFreeTile();
        int tile4 = pool.getFreeTile();
        int tile5 = pool.getFreeTile();

        pool.putTileFree(tile1);
        pool.putTileFree(tile2);
        pool.putTileFree(tile3);
        pool.putTileFree(tile4);
    }

    @Test
    public void tileTest2() {
        TilePool pool = new TilePool(4);

        int tile1 = pool.getFreeTile();
        int tile2 = pool.getFreeTile();
        int tile3 = pool.getFreeTile();
        int tile4 = pool.getFreeTile();

        pool.putTileFree(tile4);
        pool.putTileFree(tile3);
        pool.putTileFree(tile2);
        pool.putTileFree(tile1);
    }
}
