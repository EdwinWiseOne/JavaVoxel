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

// TODO: CRITICAL CHANGE -- all nodes in the pool MUST BE part of an 8-child tile... all usage must be by TILE.  Remove the root node.

/**
 * The NodePool is the backing store for the {@link VoxTree}, holding all of the {@link Node} representations,
 * {@link Material} choices, and {@Path} identifiers in use.
 *
 * The NodePool is a fixed size and handles the distribution of free nodes, as well as accepting nodes that are
 * no longer used back into the free node list.
 */
public class NodePool {
    /** Total number of nodes in the pool, used and unused */
    private int numNodes;
    /** Index of the first free (unused) node in the pool.  Free nodes link into a chain. */
    private int firstFreeNode;
    /** Nodes */
    private int[] nodes;
    /** Materials */
    private long[] materials;
    /** Path meta data */
    private long[] paths;
    /** Visibility usage timestamps */
    private int[] usage;
    /** The most recently used timestamp */
    private int now;
    /** LRU list */
    private int[] lru;
    /** The number of visible nodes in the LRU */
    private int mruNum;

    /** Marker for the end of the node chain */
    public static final int NO_FREE_NODE_INDEX = -1;

    /** Marker for the child of the last node in the free node chain */
     public static final int END_OF_FREE_NODES = 0;


     /**
     * Construct an empty, null node pool
     */
    public NodePool() {
        numNodes = 0;
    }

    /**
     * Constuct a pool of the given size.
     *
     * @param size      Size of the pool, fixed for the life of the pool
     */
    public NodePool(int size) {
        init(size);
    }

    /**
     * Do the heavy lifting required to initialize a new node pool.
     *
     * Used during construction, as well as deserialization.
     *
     * @param size      Size of the pool, fixed for the life of the pool
     */
    private void init(int size) {
        numNodes = size;

        // --------------------------------------
        // The backing arrays themselves
        // --------------------------------------
        nodes = new int[numNodes];
        materials = new long[numNodes];
        paths = new long[numNodes];
        usage = new int[numNodes];
        lru = new int[numNodes];

        // --------------------------------------
        // Chain together all of the free nodes
        // --------------------------------------
        firstFreeNode = 0;
        for (int idx=0; idx<(numNodes-1); ++idx) {
            nodes[idx] = Node.setChild(0, idx+1);
            materials[idx] = 0L;
            paths[idx] = 0L;
            usage[idx] = 0;
            lru[idx] = 0;
        }
        nodes[numNodes-1] = END_OF_FREE_NODES;
    }

    /**
     * Return the size of the pool
     *
     * @return      The number of nodes stored in the pool
     */
    public int size() {
        return numNodes;
    }

    /**
     * Return the nodes
     *
     * @return      The nodes array
     */
    public int[] nodes() {
        return nodes;
    }

    /**
     * Return the materials for the nodes
     *
     * @return      The materials array
     */
    public long[] materials() {
        return materials;
    }

    /**
     * Return the paths to the nodes
     *
     * @return      The paths array
     */
    public long[] paths() {
        return paths;
    }

    /**
     * Get the next free node, mark it as being in use, update the free node chain,
     * and return the index of the found node.
     *
     * @return      A free node from the pool to put into use
     */
    public int getFree() {
        int freeNodeIndex = firstFreeNode;
        firstFreeNode = Node.child(nodes[freeNodeIndex]);
        if (firstFreeNode == END_OF_FREE_NODES) {
            firstFreeNode = NO_FREE_NODE_INDEX;
        }
        nodes[freeNodeIndex] = Node.setUsed(nodes[freeNodeIndex], true);
        return freeNodeIndex;
    }

    /**
     * Put a node back into the free pool, mark it as being free, and update the free node chain,
     *
     * @param nodeIndex    The index of the node to return to the pool
     */
    public void putFree(int nodeIndex) {
        int nextFree = firstFreeNode;
        if (nextFree == NO_FREE_NODE_INDEX) {
            nextFree = END_OF_FREE_NODES;
        }

        nodes[nodeIndex] = Node.setUsed(Node.setChild(0, nextFree), false);
        firstFreeNode = nodeIndex;
    }

    /**
     * Returns the node at a given index in the pool
     *
     * @param index     Index of the node to retrieve
     * @return          The node integer
     * @throws RuntimeException
     */
    // TODO: Move from RuntimeException to Exception.  Doing Runtime for now because I don't want to update the entire call chain.
    public int node(int index)
        throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }
        return nodes[index];
    }

    /**
     * Returns the material for the node at a given index in the pool
     *
     * @param index     Index of the node to retrieve the material of
     * @return          The material of the node
     * @throws RuntimeException
     */
    public long material(int index)
            throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }
        return materials[index];
    }

    /**
     * Returns the path to the node at the given index in the pool
     *
     * @param index     Index of the node to retrieve the path of
     * @return          The path of the node
     * @throws RuntimeException
     */
    public long path(int index)
            throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }
        return paths[index];
    }

    /**
     * Sets the {@link Node}, {@link Material}, and {@link Path} data for the node at the given index in the pool
     *
     * @param index         Index of the node in the pool, as returned by {@link #getFree}
     * @param node          Node to set in the pool
     * @param material      Material to set in the pool
     * @param path          Path to set in the pool
     * @throws RuntimeException
     */
    public void set(int index, int node, long material, long path)
        throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }

        nodes[index] = node;
        materials[index] = material;
        paths[index] = path;
    }

    /**
     * Sets the {@link Node} data for the node at the given index in the pool
     *
     * @param index     Index of the node in the pool
     * @param node      Node data to set
     * @throws RuntimeException
     */
    public void setNode(int index, int node)
            throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }

        nodes[index] = node;
    }

    /**
     * Sets the {@link Material} data for the node at the given index in the pool
     *
     * @param index         Index of the node in the pool
     * @param material      Material data to set
     * @throws RuntimeException
     */
    public void setMaterial(int index, long material)
            throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }

        materials[index] = material;
    }

    /**
     * Sets the {@link Path} data for the node at the given index in the pool
     *
     * @param index     Index of the node in the pool
     * @param path      Path data to set
     * @throws RuntimeException
     */
    public void setPath(int index, long path)
            throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }

        paths[index] = path;
    }

    /**
     * Sets the usage timestamp for the node at the given index in the pool
     *
     * @param index         Index of the node in the pool
     * @param timestamp     Scan number or other timestamp to set
     * @throws RuntimeException
     */
    public void stamp(int index, int timestamp)
            throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }

        usage[index] = timestamp;
        now = timestamp;
    }

    /**
     * Compresses the node pool, creating a new pool with only the nodes in use.
     *
     * @return      New, compressed node pool
     */
    public NodePool compress() {
        // --------------------------------------
        // Determine the number of nodes in use
        // --------------------------------------
        NodePool.Statistics stats = analyze(now);

        // --------------------------------------
        // Allocate a just-right pool
        // --------------------------------------
        NodePool newPool = new NodePool(stats.numUsed);

        // --------------------------------------
        // There must be a root node; put it in the new pool
        // --------------------------------------
        int rootNode = nodes[0];
        int dstIndex = newPool.getFree();
        newPool.setNode(dstIndex, rootNode);
        newPool.setMaterial(dstIndex, materials[0]);
        newPool.setPath(dstIndex, paths[0]);
        newPool.stamp(dstIndex, usage[0]);

        // --------------------------------------
        // If there are more nodes than the root, recursively copy the children
        // tiles (of 8 sub-nodes) to the new pool
        // --------------------------------------
        if (!Node.isLeaf(rootNode)) {
            try {
                // --------------------------------------
                copyTileSubtree(Node.child(rootNode), newPool);
                // --------------------------------------
            } catch (Exception e) {
                System.out.println(e);
            }
        }

        return newPool;
    }

    /**
     * Copies a tile of 8 children to the given pool, and recursively copies their children
     * if they exist.
     *
     * @param srcTileIndex      Index of the first child in the node tile
     * @param dstPool           Pool to copy the tile (and all sub-tiles) into
     * @return                  The index of the tile as it lives in the destination pool
     */
    private int copyTileSubtree(int srcTileIndex, NodePool dstPool) {
        int srcIndex = 0;
        int dstIndex = 0;
        int dstTileIndex = 0;

        // --------------------------------------
        // Copy the current tile across
        // --------------------------------------
        for (int child=0; child<8; ++child) {
            srcIndex = srcTileIndex + child;
            dstIndex = dstPool.getFree();
            if (child == 0) dstTileIndex = dstIndex;

            dstPool.setNode(dstIndex, nodes[srcIndex]);
            dstPool.setMaterial(dstIndex, materials[srcIndex]);
            dstPool.setPath(dstIndex, paths[srcIndex]);
            dstPool.stamp(dstIndex, usage[srcIndex]);
        }

        // --------------------------------------
        // For each non-leaf child in this tile, copy its tile across
        // --------------------------------------
        for (int child=0; child<8; ++child) {
            srcIndex = srcTileIndex + child;

            if (Node.isParent(nodes[srcIndex])) {
                dstIndex = dstTileIndex + child;

                // --------------------------------------
                srcIndex = copyTileSubtree(Node.child(nodes[srcIndex]), dstPool);
                // --------------------------------------

                // Keep the nodes properly linked, using the destination indices
                dstPool.setNode( dstIndex, Node.setChild( dstPool.node(dstIndex), srcIndex));
            }
        }

        return dstTileIndex;
    }

    /*
    Stream Compaction
    http://www.cse.chalmers.se/~uffe/streamcompaction.pdf
    http://http.developer.nvidia.com/GPUGems2/gpugems2_chapter36.html
    http://www.seas.upenn.edu/~cis565/LECTURES/CUDA%20Tricks.pdf
     */
    public void processLRU() {
        // Count valid usages
        // Generate the LRU indirection table
    }



    /**
     * Convert the entire node pool into JSON format
     *
     * @return      Massive JSON string
     */
    public String serializeJSON() {
        JsonFactory jsonFactory = new JsonFactory();
        StringWriter output = new StringWriter();
        try {

            JsonGenerator gen = jsonFactory.createJsonGenerator(output);

            gen.writeStartObject();
            gen.writeStringField("version", "1.0.0");
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

            // Usage timestamps are NOT serialized

            gen.writeEndObject();
            gen.close();
        } catch (Exception e) {
            System.out.println(e);
        }
        return output.toString();
    }

    /**
     * Interpret a massive JSON string and fill this node pool with the data
     *
     * @param json      JSON representation of a node pool
     */
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

                if ("version".equals(fieldName)) {
                    if (!parse.getText().equals("1.0.0")) {
                        throw new Exception("We only know how to parse version 1.0.0 at this time: " + parse.getText() + " is not valid.");
                    }
                } else if ("size".equals(fieldName)) {
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
                    // Usage timestamps are NOT serialized
                }
            }
            parse.close(); // ensure resources get clean

            return;

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    /**
     * Convert the entire node pool into a byte array
     *
     * @return  Byte array representing the node pool
     */
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

        // Usage timestamps are NOT serialized

        return output.toByteArray();
    }


    /**
     * Interpret a byte array and fill this node pool with the data.  Any current existing data is lost.
     *
     * @param source    Byte array holding a serialized node pool
     */
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
        // Usage timestamps are NOT serialized
    }

    /**
     * Statistics holding class
     */
    class Statistics {
        /** Number of nodes in use */
        public int numUsed;
        /** Nodes in use that are not leaves */
        public int numNodes;
        /** Nodes in use that are leaves */
        public int numLeaves;
        /** Nodes that were used in the indicated timestamp */
        public int numVisible;

        Statistics() {
            numUsed = 0;
            numNodes = 0;
            numLeaves = 0;
            numVisible = 0;
        }
    }

    /**
     * Scan the node pool and log the count of the various types of nodes
     *
     * @param when      Timestamp to run the analysis against
     * @return          Instance of the Statistics object holding the counts
     */
    Statistics analyze(int when) {
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
                if (usage[idx] == when)
                    ++stats.numVisible;
            }
        }

        return stats;
    }


    /**
     * Generates a string representation of the NodePool, just a partial readout for debugging purposes.
     *
     * @return      String representation of the pool
     */
    public String toString(){
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        NodePool.Statistics stats = analyze(now);

        result.append(this.getClass()).append(" NodePool {").append(NEW_LINE);
        result.append("   Free Node: ")
                .append(firstFreeNode)
                .append(" of ")
                .append(numNodes)
                .append(NEW_LINE);
        result.append("   (")
                .append(stats.numNodes)
                .append(" nodes, ")
                .append(stats.numLeaves)
                .append(" leaves, ")
                .append(stats.numVisible)
                .append(" visible)")
                .append(NEW_LINE);
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
        if ((!elided) && (num < numNodes)) {
            result.append("...").append(NEW_LINE);
        }
        result.append("}");

        return result.toString();
    }

}
