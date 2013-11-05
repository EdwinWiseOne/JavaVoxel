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

// TODO: Store the root node in the TilePool outside of the tiles.  Normalize exceptional behavior around the root node.

/**
 * The TilePool is the backing store for the {@link VoxTree}, holding all of the {@link Node} representations,
 * {@link Material} choices, and {@Path} identifiers in use.
 *
 * The TilePool is a fixed size and handles the distribution of free tileNodes, as well as accepting tileNodes that are
 * no longer used back into the free node list.
 */
public class TilePool {

    // --------------------------------------
    // By Tile ... one tile is eight tileNodes
    // --------------------------------------

    /** Total number of tiles in the pool, used and unused.  One tile is 8 tileNodes. */
    private int numTiles;
    /** Total number of nodes in the pool, 8 times numTiles, a convenience variable */
    private int numNodes;
    /** Index of the first free (unused) tile in the pool.  Free tiles link into a chain. */
    private int firstFreeTileIdx;

    /** Path meta data */
    private long[] tilePaths;
    /** Visibility usage timestamps */
    private int[] tileUsage;
    /** LRU list */
    private int[] tileLRU;

    /** The most recently used timestamp */
    private int now;
    /** The number of visible tileNodes in the LRU */
    private int mruNum;

    // --------------------------------------
    // By Node ... dependent data structures that match tile layout
    // --------------------------------------

    /** Root Node held specially */
    private int rootNode;
    private long rootNodeMaterial;

    /** Nodes: eight per tile */
    private int[] tileNodes;
    /** Materials */
    private long[] nodeMaterials;

    // --------------------------------------
    // Free-Tile Link Markers
    // --------------------------------------

    /** Marker for the end of the node chain */
    public static final int NO_FREE_TILE_INDEX = -1;

    /** Marker for the child of the last node in the free node chain */
     public static final int END_OF_FREE_TILES = -1;

    /** Root Node */
    static final int ROOT_NODE_INDEX = -1;

    /** Tile Size because I hate magic numbers */
    public static final int TILE_SIZE = 8;
    public static final int TILE_SHIFT = 3;

     /**
     * Construct an empty, null tile pool
     */
    public TilePool() {
        numTiles = 0;
    }

    /**
     * Constuct a pool of the given size.
     *
     * @param size      Size of the tile pool in tiles, fixed for the life of the pool
     */
    public TilePool(int size) {
        init(size);
    }

    /**
     * Do the heavy lifting required to initialize a new tile pool.
     *
     * Used during construction, as well as deserialization.
     *
     * @param size      Size of the tile pool, fixed for the life of the pool
     */
    private void init(int size) {
        System.out.println("init: Tile pool size " + size);
        free_count = 0;

        numTiles = size;
        numNodes = size << TILE_SHIFT;

        // --------------------------------------
        // The backing arrays themselves
        // --------------------------------------
        rootNode = Node.EMPTY_USED_NODE;
        rootNodeMaterial = 0L;

        tileNodes = new int[numNodes];
        nodeMaterials = new long[numNodes];

        tilePaths = new long[numTiles];
        tileUsage = new int[numTiles];
        tileLRU = new int[numTiles];

        // --------------------------------------
        // Chain together all of the free tiles
        // --------------------------------------
        firstFreeTileIdx = NO_FREE_TILE_INDEX;
        int nodeIdx;
        for (int tileIdx=(numTiles-1); tileIdx>=0; --tileIdx) {
            nodeIdx = tileIdx << TILE_SHIFT;
            // Fake up the node so we can do a Put which chains it in
            tileNodes[nodeIdx] = Node.EMPTY_USED_NODE;
            putTileFree(tileIdx);
        }

        // --------------------------------------
        // Init node-grained data
        // --------------------------------------
        for (nodeIdx=0; nodeIdx < numNodes; ++nodeIdx) {
            nodeMaterials[nodeIdx] = 0L;
        }

        // --------------------------------------
        // Init tile-grained data
        // --------------------------------------
        for (int tileIdx=0; tileIdx<numTiles; ++tileIdx) {
            tilePaths[tileIdx] = 0L;
            tileUsage[tileIdx] = 0;
            tileLRU[tileIdx] = 0;
        }
    }

    /**
     * Return the size of the tile pool in tiles
     *
     * @return      The number of tiles stored in the pool
     */
    public int numTiles() {
        return numTiles;
    }

    /**
     * Return the size of the tile pool in tileNodes
     *
     * @return      The number of tileNodes stored in the pool
     */
    public int numNodes() {
        return numNodes;
    }

    /**
     * Return the tileNodes
     *
     * @return      The tileNodes array
     */
    public int[] nodes() {
        return tileNodes;
    }

    /**
     * Return the materials for the tileNodes
     *
     * @return      The materials array
     */
    public long[] nodeMaterials() {
        return nodeMaterials;
    }

    /**
     * Return the paths to the tileNodes
     *
     * @return      The paths array
     */
    public long[] tilePaths() {
        return tilePaths;
    }

    /**
     * Get the node index inside a tile given the tile index and the child within
     * the tile.  Child 0 gives the tile's base index.
     *
     * @param tileIdx     Tile index
     * @param childNum    Child in the tile
     * @return            Index of the node within the tile.
     */
    public int getNodeInTileIdx(int tileIdx, int childNum) {
        return (tileIdx << TILE_SHIFT) + childNum + 1;
    }

    /**
     * Get the tile index for the tile that the indicate node resides within. Eight
     * different tileNodes would resolve to the same tile.
     *
     * @param nodeIdx   Node index
     * @return          Index to the tile the node is a part of
     */
    public int getTileForNodeIdx(int nodeIdx) {
        return (nodeIdx - 1) >> TILE_SHIFT;
    }

    /**
     * Get the child index for the tile that the indicate node resides within.
     *
     * @param nodeIdx   Node index
     * @return          Child index into the tile the node is a part of
     */
    public int getChildForNodeIdx(int nodeIdx) {
        return ((nodeIdx-1) & (TILE_SIZE-1));
    }


    /**
     * Get the next free tile, mark it as being in use, update the free tile chain,
     * and return the index of the found tile.  Node index would be 8 times this tile index.
     *
     * @return      The tile index of a free tile from the pool to put into use
     */
    public int getFreeTile() {
        if (firstFreeTileIdx == END_OF_FREE_TILES) {
            return NO_FREE_TILE_INDEX;
        }

        // --------------------------------------
        // Capture the head of the tile chain
        // --------------------------------------
        int freeTileIndex = firstFreeTileIdx;
        int freeNodeIndex = freeTileIndex << TILE_SHIFT;

        // --------------------------------------
        // Walk to the next free tile in the chain
        // --------------------------------------
        firstFreeTileIdx = tileNodes[freeNodeIndex];
        if (firstFreeTileIdx == END_OF_FREE_TILES) {
            firstFreeTileIdx = NO_FREE_TILE_INDEX;
        }

        // --------------------------------------
        // Clear the tileNodes in the tile and set them as in use
        // --------------------------------------
        for (int child=0; child<TILE_SIZE; ++child) {
            tileNodes[freeNodeIndex] = Node.EMPTY_USED_NODE;
            ++freeNodeIndex;
        }
//        audit(-1);
        return freeTileIndex;
    }

    /**
     * Put a tile back into the free pool, mark it as being free, and update the free tile chain,
     *
     * @param tileIdx       The index of the tile (not the node index) to return to the pool
     */
    public void putTileFree(int tileIdx) {
        // --------------------------------------
        // Push the tile to the head of the free tile chain
        // --------------------------------------
        int nextFreeTileIdx = firstFreeTileIdx;
        if (nextFreeTileIdx == NO_FREE_TILE_INDEX) {
            nextFreeTileIdx = END_OF_FREE_TILES;
        }
        int nodeIdx = tileIdx << TILE_SHIFT;

        // --------------------------------------
        // Mark the tile as being unused
        // --------------------------------------
        if (!Node.isUsed(tileNodes[nodeIdx])) {
            throw new RuntimeException("PUTTING BACK already freed tile " + tileIdx);
        }

        // TODO: If children are nodes, recursively free their child tiles too
        int node;
        audit(0);
        for (int child=0; child<TILE_SIZE; ++child) {
            node = tileNodes[nodeIdx + child];
            if (Node.isParent(node)) {
                putTileFree(Node.tile(node));
            }
            tileNodes[nodeIdx+child] = Node.EMPTY_UNUSED_NODE;
        }
        tileNodes[nodeIdx] = nextFreeTileIdx;

        // --------------------------------------
        firstFreeTileIdx = tileIdx;
        audit(1);
    }

/*
    */
/**
     * Returns the node data of a given tile at a particular child in the tile.
     *
     * @param tileIdx       Tile index
     * @param child         Child number
     * @return              Node data
     *//*

    public int tileNode(int tileIdx, int child) {
        if ((tileIdx < 0) || (tileIdx >= numTiles)) {
            throw new RuntimeException("TilePool tile index is out of bounds");
        }
        return tileNodes[(tileIdx << TILE_SHIFT) + child];
    }
*/
    /**
     * Returns the node data at a given index in the pool
     *
     * @param nodeIdx   Index of the node to retrieve
     * @return          The node data
     * @throws RuntimeException
     */
    public int node(int nodeIdx)
        throws RuntimeException {
        if ((nodeIdx < 0) || (nodeIdx >= numNodes)) {
            throw new RuntimeException("node: TilePool index " + nodeIdx + " out of bounds");
        }

        --nodeIdx;
        if (nodeIdx == ROOT_NODE_INDEX) {
            return rootNode;
        }
        return tileNodes[nodeIdx];
    }

    /**
     * Returns the material for the node at a given index in the pool
     *
     * @param nodeIdx   Index of the node to retrieve the material of
     * @return          The material of the node
     * @throws RuntimeException
     */
    public long nodeMaterial(int nodeIdx)
            throws RuntimeException {

        if ((nodeIdx < 0) || (nodeIdx >= numNodes)) {
            throw new RuntimeException("nodeMaterial: TilePool index " + nodeIdx + " out of bounds");
        }

        --nodeIdx;
        if (nodeIdx == ROOT_NODE_INDEX) {
            return rootNodeMaterial;
        }
        return nodeMaterials[nodeIdx];
    }

    /**
     * Returns the path to the node at the given index in the pool
     *
     * @param tileIdx   Index of the tile to retrieve the path of
     * @return          The path of the node
     * @throws RuntimeException
     */
    public long tilePath(int tileIdx)
            throws RuntimeException {
        if ((tileIdx < 0) || (tileIdx >= numTiles)) {
            throw new RuntimeException("tilePath: TilePool index " + tileIdx + " out of bounds");
        }
        return tilePaths[tileIdx];
    }

    /**
     * Sets the {@link Node} and {@link Material} data for the node at the given index in the pool
     *
     * @param nodeIdx       Index of the node in the pool
     * @param node          Node to set in the pool
     * @param material      Material to set in the pool
     * @throws RuntimeException
     */
    public void setNode(int nodeIdx, int node, long material)
        throws RuntimeException {

        if ((nodeIdx < 0) || (nodeIdx >= numNodes)) {
            throw new RuntimeException("TilePool index out of bounds");
        }

        if (!Node.isUsed(node)) {
            throw new RuntimeException("TilePool setNode given invalid node");
        }

        --nodeIdx;
        if (nodeIdx == ROOT_NODE_INDEX) {
            rootNode = node;
            rootNodeMaterial = material;
        } else {
            tileNodes[nodeIdx] = node;
            nodeMaterials[nodeIdx] = material;
        }
    }

    /**
     * Sets the {@link Node} data for the node at the given index in the pool
     *
     * @param nodeIdx   Index of the node in the pool
     * @param node      Node data to set
     * @throws RuntimeException
     */
    public void setNode(int nodeIdx, int node)
            throws RuntimeException {

        if ((nodeIdx < 0) || (nodeIdx >= numNodes)) {
            throw new RuntimeException("TilePool index out of bounds");
        }
        if (!Node.isUsed(node)) {
            throw new RuntimeException("TilePool setNode invalid node");
        }

        --nodeIdx;
        if (nodeIdx == ROOT_NODE_INDEX) {
            rootNode = node;
        } else {
            tileNodes[nodeIdx] = node;
        }
    }

    /**
     * Sets the {@link Material} data for the node at the given index in the pool
     *
     * @param nodeIdx       Index of the node in the pool
     * @param material      Material data to set
     * @throws RuntimeException
     */
    public void setMaterial(int nodeIdx, long material)
            throws RuntimeException {

        if ((nodeIdx < 0) || (nodeIdx >= numNodes)) {
            throw new RuntimeException("TilePool index out of bounds");
        }

        --nodeIdx;
        if (nodeIdx == ROOT_NODE_INDEX) {
            rootNodeMaterial = material;
        } else {
            nodeMaterials[nodeIdx] = material;
        }
    }

    /**
     * Sets the {@link Path} data for the node at the given index in the pool
     *
     * @param tileIdx   Index of the tile in the pool
     * @param path      Path o this tile
     * @throws RuntimeException
     */
    public void setPath(int tileIdx, long path)
            throws RuntimeException {

        if ((tileIdx < 0) || (tileIdx >= numTiles)) {
            throw new RuntimeException("TilePool index out of bounds");
        }

        tilePaths[tileIdx] = path;
    }

    /**
     * Sets the usage timestamp for the tile at the given index in the pool
     *
     * @param tileIdx       Index of the node in the pool
     * @param timestamp     Scan number or other timestamp to set
     * @throws RuntimeException
     */
    public void stamp(int tileIdx, int timestamp)
            throws RuntimeException {

        if ((tileIdx < 0) || (tileIdx >= numTiles)) {
            throw new RuntimeException("TilePool index " + tileIdx + " out of bounds");
        }

        tileUsage[tileIdx] = timestamp;
        now = timestamp;
    }

    /**
     * Compresses the tile pool, creating a new pool with only the tiles in use.
     *
     * LRU and usage data are not set
     *
     * @return      New, compressed tile pool.
     */
    public TilePool compress() {
        // --------------------------------------
        // Determine the number of tileNodes in use
        // --------------------------------------
        TilePool.Statistics stats = analyze(now);

        // --------------------------------------
        // Allocate a just-right pool
        // --------------------------------------
        TilePool newPool = new TilePool(stats.numTiles);

        newPool.rootNode = rootNode;
        newPool.rootNodeMaterial = rootNodeMaterial;

        // --------------------------------------
        // Recursively copy the children tiles (of 8 sub-tileNodes)
        // to the new pool.
        // --------------------------------------
        if (Node.isParent(rootNode)) {
            try {
                copyTileSubtree(Node.tile(rootNode), newPool);
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
     * @param srcTileIndex      Index of the tile to copy.
     * @param dstPool           Pool to copy the tile (and all sub-tiles) into
     * @return                  The index of the tile as it lives in the destination pool
     */
    private int copyTileSubtree(int srcTileIndex, TilePool dstPool) {
        int srcNodeIndex = 0;
        int dstTileIndex = 0;
        int dstNodeIndex = 0;

        // --------------------------------------
        // Copy the current tile across
        // --------------------------------------
        dstTileIndex = dstPool.getFreeTile();
        for (int child=0; child<TILE_SIZE; ++child) {
            srcNodeIndex = (srcTileIndex<<TILE_SHIFT) + child;
            dstNodeIndex = (dstTileIndex<<TILE_SHIFT) + child;

            dstPool.setNode(dstNodeIndex, tileNodes[srcNodeIndex]);
            dstPool.setMaterial(dstNodeIndex, nodeMaterials[srcNodeIndex]);
        }
        dstPool.setPath(dstTileIndex, tilePaths[srcTileIndex]);
        dstPool.stamp(dstTileIndex, tileUsage[srcTileIndex]);

        // --------------------------------------
        // For each non-leaf child in this tile, copy its tile across
        // --------------------------------------
        for (int child=0; child<8; ++child) {
            srcNodeIndex = (srcTileIndex<<TILE_SHIFT) + child;

            if (Node.isParent(tileNodes[srcNodeIndex])) {
                dstNodeIndex = (dstTileIndex<<TILE_SHIFT) + child;

                // --------------------------------------
                // Keep the tileNodes properly linked, using the destination indices
                dstPool.setNode( dstNodeIndex,
                        Node.setTile( dstPool.node(dstNodeIndex),
                                copyTileSubtree(Node.tile(tileNodes[srcNodeIndex]), dstPool)
                        )
                );
                // --------------------------------------

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
     * Convert the entire tile pool into JSON format
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
            gen.writeNumberField("size", numTiles);
            gen.writeNumberField("rootNode", rootNode);
            gen.writeNumberField("rootNodeMaterial", rootNodeMaterial);

            gen.writeArrayFieldStart("tileNodes");
            for (int index=0; index<numNodes; ++index) {
                gen.writeNumber(tileNodes[index]);
            }
            gen.writeEndArray();

            gen.writeArrayFieldStart("materials");
            for (int index=0; index<numNodes; ++index) {
                gen.writeNumber(nodeMaterials[index]);
            }
            gen.writeEndArray();

            gen.writeArrayFieldStart("paths");
            for (int index=0; index<numTiles; ++index) {
                gen.writeNumber(tilePaths[index]);
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
     * Interpret a massive JSON string and fill this tile pool with the data
     *
     * @param json      JSON representation of a tile pool
     */
    public void deserializeJSON(String json) {
        StringReader input = new StringReader(json);
        int numTiles = 0;

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
                    numTiles = parse.getIntValue();
                    init(numTiles);
                } else if ("rootNode".equals(fieldName)) {
                    rootNode = parse.getIntValue();
                } else if ("rootNodeMaterial".equals(fieldName)) {
                    rootNodeMaterial = parse.getLongValue();
                } else if (Arrays.asList("tileNodes", "materials", "paths").contains(fieldName)) {
                    if (numTiles == 0) {
                        throw new Exception("Serialized data has a zero pool size.");
                    }

                    if (parse.getCurrentToken() != JsonToken.START_ARRAY) {
                        throw new Exception("Serialized data " + fieldName + " must begin with a START_ARRAY");
                    }
                    int index=0;

                    if ("tileNodes".equals(fieldName)) {
                        while (parse.nextToken() != JsonToken.END_ARRAY) {
                            tileNodes[index++] =parse.getIntValue();
                        }
                    } else if ("materials".equals(fieldName)) {
                        while (parse.nextToken() != JsonToken.END_ARRAY) {
                            nodeMaterials[index++] = parse.getLongValue();
                        }
                    } else if ("paths".equals(fieldName)) {
                        while (parse.nextToken() != JsonToken.END_ARRAY) {
                            tilePaths[index++] = parse.getLongValue();
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
     * Convert the entire tile pool into a byte array
     *
     * @return  Byte array representing the tile pool
     */
    public byte[] serializeBytes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        output.write(Bytes.toBytes(numTiles), 0, Integer.SIZE/8);
        output.write(Bytes.toBytes(rootNode), 0, Integer.SIZE/8);
        output.write(Bytes.toBytes(rootNodeMaterial), 0, Long.SIZE/8);

        for (int val : tileNodes) {
            output.write(Bytes.toBytes(val), 0, Integer.SIZE/8);
        }

        for (long val : nodeMaterials) {
            output.write(Bytes.toBytes(val), 0, Long.SIZE/8);
        }

        for (long val : tilePaths) {
            output.write(Bytes.toBytes(val), 0, Long.SIZE/8);
        }

        // Usage timestamps are NOT serialized

        return output.toByteArray();
    }


    /**
     * Interpret a byte array and fill this tile pool with the data.  Any current existing data is lost.
     *
     * @param source    Byte array holding a serialized tile pool
     */
    public void deserializeBytes(byte[] source) {
        ByteArrayInputStream input = new ByteArrayInputStream(source);

        byte[] buf = new byte[8];

        input.read(buf, 0, Integer.SIZE/8);
        int sizeTiles = Bytes.toInt(buf);
        int sizeNodes = sizeTiles << TILE_SHIFT;

        init(sizeTiles);

        input.read(buf, 0, Integer.SIZE/8);
        rootNode = Bytes.toInt(buf);

        input.read(buf, 0, Long.SIZE/8);
        rootNodeMaterial = Bytes.toLong(buf);

        for (int cnt=0; cnt<sizeNodes; ++cnt) {
            input.read(buf, 0, Integer.SIZE/8);
            tileNodes[cnt]= Bytes.toInt(buf);
        }

        for (int cnt=0; cnt<sizeNodes; ++cnt) {
            input.read(buf, 0, Long.SIZE/8);
            nodeMaterials[cnt]= Bytes.toLong(buf);
        }

        for (int cnt=0; cnt<sizeTiles; ++cnt) {
            input.read(buf, 0, Long.SIZE/8);
            tilePaths[cnt]= Bytes.toLong(buf);
        }
        // Usage timestamps are NOT serialized
    }

    /**
     * Statistics holding class
     */
    class Statistics {
        /** Number of tiles in use */
        public int numTiles;
        /** Nodes in use that are not leaves */
        public int numParents;
        /** Nodes in use that are leaves */
        public int numLeaves;
        /** Nodes that were used in the indicated timestamp */
        public int numVisible;

        Statistics() {
            numTiles = 0;
            numParents = 0;
            numLeaves = 0;
            numVisible = 0;
        }
    }

    /**
     * Scan the tile pool and log the count of the various types of tileNodes
     *
     * @param when      Timestamp to run the analysis against
     * @return          Instance of the Statistics object holding the counts
     */
    Statistics analyze(int when) {
        Statistics stats = new Statistics();

        int nodeIdx;
        int node;
        for (int tileIdx=0; tileIdx<numTiles; ++tileIdx){
            nodeIdx = tileIdx << TILE_SHIFT;

            // --------------------------------------
            // Tile used?
            // --------------------------------------
            node = tileNodes[nodeIdx];
            if (Node.isUsed(node)) {
                ++stats.numTiles;

                // --------------------------------------
                // Used, is the tile visible?
                // --------------------------------------
                if (tileUsage[tileIdx] == when)
                    ++stats.numVisible;
            }
            for (int child=0; child<TILE_SIZE; ++child) {
                node = tileNodes[nodeIdx + child];

                // --------------------------------------
                // Tile children leaves or parents?
                // --------------------------------------
                if (Node.isLeaf(node))
                    ++stats.numLeaves;
                else
                    ++stats.numParents;
            }
        }

        return stats;
    }

    static int free_count = 0;
    boolean audit(int delta) {

/*
        free_count += delta;
        if (free_count < 0) {
            return false;
        }

        int tile_idx;
        int node_idx;
        int node;

        int count_free;
        if (firstFreeTileIdx == END_OF_FREE_TILES) {
            count_free = 0;
        } else {
            count_free = 1;
            tile_idx = firstFreeTileIdx;
            node_idx = getNodeInTileIdx(tile_idx, 0);
            while ((node = node(node_idx)) != END_OF_FREE_TILES) {
                ++ count_free;

                tile_idx = node;
                node_idx = getNodeInTileIdx(tile_idx, 0);
                if (node_idx > numNodes) {
                    return false;
                }
                if (count_free > numNodes) {
                    return false;
                }
            }
        }

        if (count_free != free_count) {
            return false;
        }
*/

        return true;
    }

    /**
     * Generates a string representation of the TilePool, just a partial readout for debugging purposes.
     *
     * @return      String representation of the pool
     */
    public String toString(){
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        TilePool.Statistics stats = analyze(now);

        result.append(this.getClass()).append(" TilePool {").append(NEW_LINE);
        result.append("   Free Tile: ")
                .append(firstFreeTileIdx)
                .append(" of ")
                .append(numTiles)
                .append(NEW_LINE);
        result.append("   (")
                .append(stats.numTiles)
                .append(" tiles with  ")
                .append(stats.numVisible)
                .append(" visible and ")
                .append(stats.numLeaves)
                .append(" leaves, ")
                .append(stats.numParents)
                .append(" parents)")
                .append(NEW_LINE);

        result.append("root : ");
        result.append(Node.toString(rootNode));
        result.append(" - ");
        result.append(Material.toString(rootNodeMaterial));
        result.append(NEW_LINE);

        boolean elided = false;
        int num = Math.min(numTiles, 8);
        for (int tileIdx=0; tileIdx<num; ++tileIdx){
            int nodeIdx = tileIdx << TILE_SHIFT;

            if (Node.isUsed(tileNodes[nodeIdx])) {
                result.append(nodeIdx);
                result.append(": ");
                result.append(Path.toString(tilePaths[tileIdx]));
                result.append("{ ");
                result.append(NEW_LINE);

                for (int child=0; child<TILE_SIZE; ++child) {
                    result.append("   ");
                    result.append(child);
                    result.append(": ");
                    result.append(Node.toString(tileNodes[nodeIdx + child]));
                    result.append(" - ");
                    result.append(Material.toString(nodeMaterials[nodeIdx + child]));
                    result.append(NEW_LINE);
                }
                elided = false;
            } else {
                if (!elided) {
                    result.append("...").append(NEW_LINE);
                    elided = true;
                }
            }
        }
        if ((!elided) && (num < numTiles)) {
            result.append("...").append(NEW_LINE);
        }
        result.append("}");

        return result.toString();
    }

}
