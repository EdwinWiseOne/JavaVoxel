package com.simreal.VoxEngine;

import org.apache.hadoop.hbase.util.Bytes;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;

public class NodePool {
    private int numNodes;
    private int[] nodes;
    private long[] materials;
    private long[] paths;
    private int firstFreeNode;

    public static final int NO_FREE_NODE_INDEX = -1;

    public NodePool() {
        numNodes = 0;
    }

    public NodePool(int size) {
        init(size);
    }

    private void init(int size) {
        numNodes = size;

        nodes = new int[numNodes];
        materials = new long[numNodes];
        paths = new long[numNodes];

        // Chain together all of the free nodes
        for (int idx=0; idx<(numNodes-1); ++idx) {
            nodes[idx] = Node.setChild(0, idx+1);
            materials[idx] = 0L;
            paths[idx] = 0L;
        }
        nodes[numNodes-1] = Node.END_OF_FREE_NODES;

        firstFreeNode = 0;
    }

    public int size() {
        return numNodes;
    }

    public int[] nodes() {
        return nodes;
    }
    public long[] materials() {
        return materials;
    }
    public long[] paths() {
        return paths;
    }

    public int getFree() {
        int freeNodeIndex = firstFreeNode;
        firstFreeNode = Node.child(nodes[freeNodeIndex]);
        if (firstFreeNode == Node.END_OF_FREE_NODES) {
            firstFreeNode = NO_FREE_NODE_INDEX;
        }
        nodes[freeNodeIndex] = Node.setUsed(nodes[freeNodeIndex], true);
        return freeNodeIndex;
    }

    public void putFree(int nodeIndex) {
        int nextFree = firstFreeNode;
        if (nextFree == NO_FREE_NODE_INDEX) {
            nextFree = Node.END_OF_FREE_NODES;
        }

        nodes[nodeIndex] = Node.setUsed(Node.setChild(0, nextFree), false);
        firstFreeNode = nodeIndex;
    }

    // TODO: Move from RuntimeException to Exception.  Doing Runtime for now because I don't want to
    // update the entire call chain.
    public int node(int index)
        throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }
        return nodes[index];
    }
    public long material(int index)
            throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }
        return materials[index];
    }
    public long path(int index)
            throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }
        return paths[index];
    }

    public void set(int index, int node, long material, long path)
        throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }

        nodes[index] = node;
        materials[index] = material;
        paths[index] = path;
    }

    public void setNode(int index, int node)
            throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }

        nodes[index] = node;
    }

    public void setMaterial(int index, long material)
            throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }

        materials[index] = material;
    }

    public void setPath(int index, long path)
            throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }

        paths[index] = path;
    }

    // --------------------------------------
    // Analysis and debugging
    // --------------------------------------

    class Statistics {
        public int depth;
        public int numUsed;
        public int numNodes;
        public int numLeaves;

        Statistics() {
            numUsed = 0;
            numNodes = 0;
            numLeaves = 0;
        }
    }


    Statistics analyze() {
        Statistics stats = new Statistics();

        int node;
        for (int idx=0; idx<numNodes; ++idx){
            node = nodes[idx];
            if (Node.isUsed(node)) {
                ++stats.numUsed;
                if (Node.isLeaf(node))
                    ++stats.numLeaves;
                else
                    ++stats.numNodes;
            }
        }

        return stats;
    }

    public NodePool compress() {
        // Determine our size
        NodePool.Statistics stats = analyze();

        // Allocate a just-right pool
        NodePool newPool = new NodePool(stats.numUsed);

        // Always a root node
        int dstIndex = newPool.getFree();
        int rootNode = nodes[0];
        newPool.setNode(dstIndex, rootNode);
        newPool.setMaterial(dstIndex, materials[0]);
        newPool.setPath(dstIndex, paths[0]);

        if (!Node.isLeaf(rootNode)) {
            try {
                copyTileSubtree(Node.child(rootNode), newPool);
            } catch (Exception e) {
                System.out.println(e);
            }
        }

        return newPool;
    }

    private int copyTileSubtree(int srcTileIndex, NodePool dstPool) {
        int srcIndex = 0;
        int dstIndex = 0;
        int dstTileIndex = 0;

        // Copy this tile across
        for (int child=0; child<8; ++child) {
            srcIndex = srcTileIndex + child;
            dstIndex = dstPool.getFree();
            if (child == 0) dstTileIndex = dstIndex;

            dstPool.setNode(dstIndex, nodes[srcIndex]);
            dstPool.setMaterial(dstIndex, materials[srcIndex]);
            dstPool.setPath(dstIndex, paths[srcIndex]);
        }

        // Now descend through the tile
        for (int child=0; child<8; ++child) {
            srcIndex = srcTileIndex + child;

            if (Node.isNode(nodes[srcIndex])) {
                dstIndex = dstTileIndex + child;

                srcIndex = copyTileSubtree(Node.child(nodes[srcIndex]), dstPool);

                dstPool.setNode( dstIndex, Node.setChild( dstPool.node(dstIndex), srcIndex));
            }
        }

        return dstTileIndex;
    }


    public String serializeJSON() {
        JsonFactory jsonFactory = new JsonFactory();
        StringWriter output = new StringWriter();
        try {

            JsonGenerator gen = jsonFactory.createJsonGenerator(output);

            gen.writeStartObject();
            gen.writeNumberField("size", numNodes);
            gen.writeArrayFieldStart("nodes");
            // To make more clean, would need to store pool as ByteBuffer (and cast to LongBuffer, etc)
            for (int index=0; index<numNodes; ++index) {
                gen.writeNumber(nodes[index]);
            }
            gen.writeEndArray();

            gen.writeArrayFieldStart("materials");
            // To make more clean, would need to store pool as ByteBuffer (and cast to LongBuffer, etc)
            for (int index=0; index<numNodes; ++index) {
                gen.writeNumber(materials[index]);
            }
            gen.writeEndArray();

            gen.writeArrayFieldStart("paths");
            // To make more clean, would need to store pool as ByteBuffer (and cast to LongBuffer, etc)
            for (int index=0; index<numNodes; ++index) {
                gen.writeNumber(paths[index]);
            }
            gen.writeEndArray();

            gen.writeEndObject();
            gen.close();
        } catch (Exception e) {
            System.out.println(e);
        }
        return output.toString();
    }


    public byte[] serializeBytes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        output.write(Bytes.toBytes(numNodes), 0, Integer.SIZE/8);

        for (int val : nodes) {
            output.write(Bytes.toBytes(val), 0, Integer.SIZE/8);
        }

        for (long val : materials) {
            output.write(Bytes.toBytes(val), 0, Long.SIZE/8);
        }

        for (long val : paths) {
            output.write(Bytes.toBytes(val), 0, Long.SIZE/8);
        }

        return output.toByteArray();
    }

    public void deserializeJSON(String json) {
        StringReader input = new StringReader(json);
        int size = 0;

        JsonFactory jsonFactory = new JsonFactory();
        try {
            JsonParser parse = jsonFactory.createJsonParser(input);

            if (parse.nextToken() != JsonToken.START_OBJECT) {
                throw new Exception("Serialized data must begin with a START_OBJECT");
            }
            while (parse.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parse.getCurrentName();
                parse.nextToken(); // move to value, or START_OBJECT/START_ARRAY

                if ("size".equals(fieldName)) {
                    size = parse.getIntValue();
                    init(size);
                } else if (Arrays.asList("nodes", "materials", "paths").contains(fieldName)) {
                    if (size == 0) {
                        throw new Exception("Serialized data has a zero pool size.");
                    }

                    if (parse.getCurrentToken() != JsonToken.START_ARRAY) {
                        throw new Exception("Serialized data " + fieldName + " must begin with a START_ARRAY");
                    }
                    int index=0;

                    if ("nodes".equals(fieldName)) {
                        while (parse.nextToken() != JsonToken.END_ARRAY) {
                            nodes[index++] =parse.getIntValue();
                        }
                    } else if ("materials".equals(fieldName)) {
                        while (parse.nextToken() != JsonToken.END_ARRAY) {
                            materials[index++] = parse.getLongValue();
                        }
                    } else if ("paths".equals(fieldName)) {
                        while (parse.nextToken() != JsonToken.END_ARRAY) {
                            paths[index++] = parse.getLongValue();
                        }
                    }
                }
            }
            parse.close(); // ensure resources get clean

//            System.out.println(loadPool);
            return;

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void deserializeBytes(byte[] source) {
        ByteArrayInputStream input = new ByteArrayInputStream(source);
        int size = 0;

        byte[] buf = new byte[8];

        input.read(buf, 0, Integer.SIZE/8);
        size = Bytes.toInt(buf);

        init(size);

        for (int cnt=0; cnt<size; ++cnt) {
            input.read(buf, 0, Integer.SIZE/8);
            nodes[cnt]= Bytes.toInt(buf);
        }

        for (int cnt=0; cnt<size; ++cnt) {
            input.read(buf, 0, Long.SIZE/8);
            materials[cnt]= Bytes.toLong(buf);
        }

        for (int cnt=0; cnt<size; ++cnt) {
            input.read(buf, 0, Long.SIZE/8);
            paths[cnt]= Bytes.toLong(buf);
        }
    }


    public String toString(){
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        NodePool.Statistics stats = analyze();

        result.append(this.getClass()).append(" NodePool {").append(NEW_LINE);
        result.append("   Free Node: ").append(firstFreeNode).append(" of ").append(numNodes).append(NEW_LINE);
        result.append("   (").append(stats.numNodes).append(" nodes, ").append(stats.numLeaves).append(" leaves)").append(NEW_LINE);
        boolean elided = false;
        int num = Math.min(numNodes, 64);
        for (int idx=0; idx<num; ++idx){
            if (Node.isUsed(nodes[idx])) {
                result.append(idx);
                result.append(": ");
                result.append(Node.toString(nodes[idx]));
                result.append(" - ");
                result.append(Material.toString(materials[idx]));
                result.append(" - ");
                result.append(Path.toString(paths[idx]));
                result.append(NEW_LINE);
                elided = false;
            } else {
                if (!elided) {
                    result.append("...").append(NEW_LINE);
                    elided = true;
                }
            }
        }
        result.append("...").append(NEW_LINE);
        result.append("}");

        return result.toString();
    }

}
