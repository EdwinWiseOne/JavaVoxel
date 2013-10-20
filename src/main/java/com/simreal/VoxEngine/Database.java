package com.simreal.VoxEngine;

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
public class Database {

    HTable worldTable = null;

    public Database() {
        // TODO: Run database connection in a background thread
        // TODO: Determine if database is alive before doing the heavyweight startup?
        // TODO: Early exit for dead database?


        Configuration conf = HBaseConfiguration.create();
        conf.set("hbase.zookeeper.quorum", "localhost");
        try {
            worldTable = new HTable(conf, "world");
            // table: 'world'
            // column family: 'n' (for node)
            // column 'b' for bricks, 'v' for voxels
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void putBrick(String name, NodePool brick) {
        // Construct a put object with the row key
        byte[] brickBytes = brick.serializeBytes();
        Put put = new Put(Bytes.toBytes(name))
                        .add(Bytes.toBytes("n"), Bytes.toBytes("b"), brickBytes);

        try {

            worldTable.put(put);

            // optional
            worldTable.flushCommits();

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public NodePool getBrick(String name) {
        NodePool brickPool = new NodePool();

        Get get = new Get(Bytes.toBytes(name))
                    .addColumn(Bytes.toBytes("n"), Bytes.toBytes("b"));

        try {
            Result result = worldTable.get(get);

            brickPool.deserializeBytes(result.value());

        } catch (Exception e) {
            System.out.println(e);
        }

        return brickPool;
    }
}
