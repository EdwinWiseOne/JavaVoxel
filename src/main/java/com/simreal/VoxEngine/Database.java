package com.simreal.VoxEngine;

import com.simreal.VoxEngine.brick.BrickFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;

// RUNNING HBASE:
// TODO: Manage local hbase via system calls?
//
// ./hbase-0.94.12/bin/start-hbase.sh
//
// ./hbase-0.94.12/bin/stop-hbase.sh
//
// ./hbase-0.94.12/bin/hbase shell
//      create 'world', 'n'
//

/**
 * Database wrapper, providing a path into and out of a persistent store for voxel tree objects,
 * such as Bricks and so forth.
 *
 * Currently attaches to a local HBase instance, but this will change.
 */
public class Database {

    // --------------------------------------
    // Singleton instance
    // --------------------------------------
    private static Database _databaseInstance = null;
    // --------------------------------------

    HTable worldTable = null;

    // table: 'world'
    // column family: 'n' (for node)
    // column 'b' for bricks, 'v' for voxels, 'c' for configuration parameters
    static private final String T_WORLD = "world";
    static private final byte[] CF_NODE_BYTES = Bytes.toBytes("n");
    static private final byte[] C_BRICK_BYTES = Bytes.toBytes("b");
    static private final byte[] C_VOXEL_BYTES = Bytes.toBytes("v");
    static private final byte[] C_CONFIG_BYTES = Bytes.toBytes("c");


    public static Database instance() {
        if (_databaseInstance == null) {
            _databaseInstance = new Database();
        }

        return _databaseInstance;
    }

    private Database() {
        // TODO: Run database connection in a background thread
        // TODO: Determine if database is alive before doing the heavyweight startup?
        // TODO: Early exit for dead database?


        Configuration conf = HBaseConfiguration.create();
        conf.set("hbase.zookeeper.quorum", "localhost");
        try {
            worldTable = new HTable(conf, T_WORLD);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void putBrick(String name, NodePool brick, BrickFactory factory) {


        // Construct a put object with the row key
        Put put = new Put(Bytes.toBytes(name))
                        .add(CF_NODE_BYTES, C_BRICK_BYTES, brick.serializeBytes())
                        .add(CF_NODE_BYTES, C_CONFIG_BYTES, factory.serializeBytes());

        try {

            worldTable.put(put);

            // optional
            worldTable.flushCommits();

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void getBrick(String name, NodePool brick, BrickFactory factory) {
        Get get = new Get(Bytes.toBytes(name))
                    .addColumn(CF_NODE_BYTES, C_BRICK_BYTES)
                    .addColumn(CF_NODE_BYTES, C_CONFIG_BYTES);

        try {
            Result result = worldTable.get(get);

            brick.deserializeBytes(result.getValue(CF_NODE_BYTES, C_BRICK_BYTES));
            factory.deserializeBytes(result.getValue(CF_NODE_BYTES, C_CONFIG_BYTES));

        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
