package com.simreal.VoxEngine;

import org.apache.hadoop.hbase.util.Bytes;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;

/**
 * The TilePool is the backing store for the {@link VoxTree}, holding all of the {@link Node} representations,
 * {@link Material} choices, and {@Path} identifiers in use.
 *
 * The TilePool is a fixed size and handles the distribution of free nodes, as well as accepting nodes that are
 * no longer used back into the free node list.
 */
public class TilePool {

    static final Logger LOG = LoggerFactory.getLogger(TilePool.class.getName());

    // --------------------------------------
    // By Tile ... one tile is eight nodes
    // --------------------------------------

    /** Total number of tiles in the pool, used and unused.  One tile is 8 nodes. */
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
    /** Paths that we are requesting, from generateRequest */
    private long[] requestPaths;
    /** Indices from generateRequest */
    private int[] tileRequestIndices;

    /** The most recently used timestamp */
    private int now;
    /** The number of visible nodes in the LRU */
    private int mruNum;

    // --------------------------------------
    // By Node ... dependent data structures that match tile layout
    // --------------------------------------

    /** Root Node held specially */
    private int rootNode;
    private long rootNodeMaterial;

    /** Nodes: eight per tile */
    private int[] nodes;
    /** Materials */
    private long[] nodeMaterials;
    /** Request List, which holds the scan # at the index of the node whose child we want to load */
    private int[] nodeRequests;
    /** Number of requests at the head of the nodeRequests, after compaction */
    private int requestNum;

    // --------------------------------------
    // Free-Tile Link Markers
    // --------------------------------------
    /** Marker for the end of the node chain */
    public static final int NO_FREE_TILE_INDEX = -1;
    /** Marker for the child of the last node in the free node chain */
     public static final int END_OF_FREE_TILES = -1;
    /** Marker for tile usage, for tiles not actually in use */
    public static final int UNUSED_TILE = -1;
    /** How many scans must pass before we free an unseen tile */
    public static final int STALE_TILE_AGE = 3;

    /** Root Node */
    static final int ROOT_NODE_INDEX = -1;

    /** Tile Size because I hate magic numbers */
    public static final int TILE_SIZE = 8;
    public static final int TILE_SHIFT = 3;

     /**
     * Construct an empty, null tile pool
     */
/*
    public TilePool() {
        numTiles = 0;
    }
*/

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

        nodes = new int[numNodes];
        nodeRequests = new int[numNodes];
        nodeMaterials = new long[numNodes];

        tilePaths = new long[numTiles];
        tileUsage = new int[numTiles];
        tileLRU = new int[numTiles];
        tileRequestIndices = new int[numTiles];

        // --------------------------------------
        // Chain together all of the free tiles
        // --------------------------------------
        firstFreeTileIdx = NO_FREE_TILE_INDEX;
        int nodeIdx;
        for (int tileIdx=(numTiles-1); tileIdx>=0; --tileIdx) {
            nodeIdx = tileIdx << TILE_SHIFT;
            // Fake up the node so we can do a Put which chains it in
            nodes[nodeIdx] = Node.EMPTY_USED_NODE;
            putTileFree(tileIdx);
        }

        // --------------------------------------
        // Init node-grained data
        // --------------------------------------
        for (nodeIdx=0; nodeIdx < numNodes; ++nodeIdx) {
            nodeMaterials[nodeIdx] = 0L;
            nodeRequests[nodeIdx] = ~0;
        }

        // --------------------------------------
        // Init tile-grained data
        // --------------------------------------
        for (int tileIdx=0; tileIdx<numTiles; ++tileIdx) {
            // Paths begin empty
            tilePaths[tileIdx] = ~0L;
            // Usage is marked as freed
            tileUsage[tileIdx] = UNUSED_TILE;
            // LRU points to the tileUsage area.  Init in order.
            tileLRU[tileIdx] = tileIdx ;
            // Zero request indexes
            tileRequestIndices[tileIdx] = 0;
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
     * Return the size of the tile pool in nodes
     *
     * @return      The number of nodes stored in the pool
     */
    public int numNodes() {
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
    public long[] nodeMaterials() {
        return nodeMaterials;
    }

    /**
     * Return the paths to the nodes
     *
     * @return      The paths array
     */
/*
    public long[] tilePaths() {
        return tilePaths;
    }
*/

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
     * different nodes would resolve to the same tile.
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
        firstFreeTileIdx = nodes[freeNodeIndex];
        if (firstFreeTileIdx == END_OF_FREE_TILES) {
            firstFreeTileIdx = NO_FREE_TILE_INDEX;
        }

        // --------------------------------------
        // Clear the nodes in the tile and set them as in use
        // --------------------------------------
        for (int child=0; child<TILE_SIZE; ++child) {
            nodes[freeNodeIndex] = Node.EMPTY_USED_NODE;
            ++freeNodeIndex;
        }
//        audit(-1);
        tileUsage[freeTileIndex] = 0;   // Remove unused marker, but make it old
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
        // --------------------------------------
        // Mark the tile as being unused
        // --------------------------------------
        if (tileUsage[tileIdx] == UNUSED_TILE) {
            throw new RuntimeException("PUTTING BACK already freed tile " + tileIdx);
        }
        tileUsage[tileIdx] = UNUSED_TILE;

        // --------------------------------------
        // Put back any children of this tile
        // --------------------------------------
        int nodeIdx = tileIdx << TILE_SHIFT;
        int node;
        audit(0);
        for (int child=0; child<TILE_SIZE; ++child) {
            node = nodes[nodeIdx + child];
            if (Node.isParent(node)
                    && Node.isLoaded(node)) {
                LOG.info("Free sub-tile {}", Node.tile(node));
                putTileFree(Node.tile(node));
            }
            nodes[nodeIdx+child] = Node.EMPTY_UNUSED_NODE;
        }
        nodes[nodeIdx] = nextFreeTileIdx;

        // --------------------------------------
        firstFreeTileIdx = tileIdx;

        // --------------------------------------
        // Mark the parent of this tile as unloaded
        // --------------------------------------
        long path = tilePaths[tileIdx];
        nodeIdx = getNodeIndexForPath(path) - 1;
        // TODO: Audit and possibly remove all the mixed-mode node index usages
        if (nodeIdx >= 0) {
            nodes[nodeIdx] = Node.setLoaded(nodes[nodeIdx], false);
        } else {
            rootNode = Node.setLoaded(rootNode, false);
        }

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
        return nodes[(tileIdx << TILE_SHIFT) + child];
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
        return nodes[nodeIdx];
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
     * Returns the path to the tile at the given index in the pool
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
     * Returns the path to the node at the given index in the pool
     *
     * @param nodeIndex   Index of the node to retrieve the path of
     * @return          The path of the node
     * @throws RuntimeException
     */
    public long nodePath(int nodeIndex)
            throws RuntimeException {
        if ((nodeIndex < 0) || (nodeIndex >= numNodes)) {
            throw new RuntimeException("nodePath: TilePool index " + nodeIndex + " out of bounds");
        }

        int tileIdx = getTileForNodeIdx(nodeIndex);
        long path = 0L;
        if (tileIdx >= 0) {
            path = Path.addChild(tilePaths[tileIdx], getChildForNodeIdx(nodeIndex));
        }

        return path;
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
            nodes[nodeIdx] = node;
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
            nodes[nodeIdx] = node;
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

    public long material(int nodeIdx)
        throws RuntimeException {

        if ((nodeIdx < 0) || (nodeIdx >= numNodes)) {
            throw new RuntimeException("TilePool index out of bounds");
        }

        --nodeIdx;
        if (nodeIdx == ROOT_NODE_INDEX) {
            return rootNodeMaterial;
        } else {
            return nodeMaterials[nodeIdx];
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

        if ((tileIdx < -1) || (tileIdx >= numTiles)) {
            throw new RuntimeException("TilePool index " + tileIdx + " out of bounds");
        }

        if (tileIdx == ROOT_NODE_INDEX) {
            return;
        }

        tileUsage[tileIdx] = timestamp;
        now = timestamp;
    }

    /**
     * Mark in the pool that we need to load the children of a given node.
     * This is tricky, since we want to request based on a path, so we mark the timestamp
     * into the parent of the child we want to load and use the parent's path to find
     * the child.  The parent is loaded, the child is not.
     *
     * @param nodeIdx
     * @param timestamp
     * @throws RuntimeException
     */
    public void request(int nodeIdx, int timestamp)
        throws RuntimeException {
        if ((nodeIdx < 0) || (nodeIdx >= numNodes)) {
            throw new RuntimeException("TilePool node " + nodeIdx + " out of bounds");
        }

        --nodeIdx;
        if (nodeIdx == ROOT_NODE_INDEX) {
            throw new RuntimeException("Root node can not be requested");
        }
        nodeRequests[nodeIdx] = timestamp;
    }

    /**
     * Given a path, find the specific node in the NodePool that it represents,
     * by traversing the node tree using the choices in the path.
     *
     * Technically this is a VoxTree function (and VoxTree has a variation of it)
     * but for freeing tiles, we need to make the parents as unloaded, hence
     * we need to interpret the path.
     *
     * @param path      Path to traverse to the node
     * @return          Index of the leaf node we edned on
     */
    public int getNodeIndexForPath(long path) {
        // Cnt and depth of zero is root node
        int node;
        int nodeIndex = 0;
        int depth = Path.length(path);

        // --------------------------------------
        // Walk down the path...
        // --------------------------------------
        for (int cnt=0; cnt<depth; ++cnt) {
            node = node(nodeIndex);

            // --------------------------------------
            // Exit if we hit a leaf before the bottom
            // --------------------------------------
            if (Node.isLeaf(node)){
                break;
            }

            // --------------------------------------
            // ... and choose the child at this step of the path
            // --------------------------------------
            int tile = Node.tile(node);
            int child = Path.child(path, cnt);
            nodeIndex = getNodeInTileIdx(tile, child);
        }

        return nodeIndex;
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
        // Determine the number of nodes in use
        // --------------------------------------
        TilePool.Statistics stats = analyze(now);

        // --------------------------------------
        // Allocate a just-right pool
        // --------------------------------------
        TilePool newPool = new TilePool(stats.numTiles);

        newPool.rootNode = rootNode;
        newPool.rootNodeMaterial = rootNodeMaterial;

        // --------------------------------------
        // Recursively copy the children tiles (of 8 sub-nodes)
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

            dstPool.setNode(dstNodeIndex, nodes[srcNodeIndex]);
            dstPool.setMaterial(dstNodeIndex, nodeMaterials[srcNodeIndex]);
        }
        dstPool.setPath(dstTileIndex, tilePaths[srcTileIndex]);
        dstPool.stamp(dstTileIndex, tileUsage[srcTileIndex]);

        // --------------------------------------
        // For each non-leaf child in this tile, copy its tile across
        // --------------------------------------
        for (int child=0; child<8; ++child) {
            srcNodeIndex = (srcTileIndex<<TILE_SHIFT) + child;

            if (Node.isParent(nodes[srcNodeIndex])) {
                dstNodeIndex = (dstTileIndex<<TILE_SHIFT) + child;

                // --------------------------------------
                // Keep the nodes properly linked, using the destination indices
                dstPool.setNode( dstNodeIndex,
                        Node.setTile( dstPool.node(dstNodeIndex),
                                copyTileSubtree(Node.tile(nodes[srcNodeIndex]), dstPool)
                        )
                );
                // --------------------------------------

            }
        }

        return dstTileIndex;
    }

    /**
     * Free any tiles that have not been viewed in the last scan, to make room for newly requested tiles.
     *
     * @param scan      Scan number
     */
    public void freeUnseen(int scan) {
        /*
            Tile Usage has scan numbers.
            LRU has indexes into the tile layout (e.g. tileUsage, other tile ordered things)
            Scan the LRU indirecting to tile usage and move all recent to the front, keeping order of all
            not recent after that.
            Unused tiles are last?
            Free the oldest (X%) of the unseen tiles?
         */
        // --------------------------------------
        // First Pass: Count Seen, Unseen, Unused
        // --------------------------------------
        int seen = 0;
        int unseen = 0;
        int unused = 0;
        int tileIdx;
        for (tileIdx=0; tileIdx<numTiles; ++tileIdx) {
            if (tileUsage[tileIdx] == scan) {
                ++seen;
            } else if (tileUsage[tileIdx] == UNUSED_TILE) {
                ++unused;
            } else {
                ++unseen;
            }
        }

        // --------------------------------------
        // Second Pass: Sort Seen to the front
        // --------------------------------------
        unused = seen + unseen;
        unseen = seen;
        seen = 0;

        int[] newLRU = new int[numTiles];
        for (int srcIdx=0; srcIdx<numTiles; ++srcIdx) {
            tileIdx = tileLRU[srcIdx];
            if (tileUsage[tileIdx] == scan) {
                newLRU[seen] = tileIdx;
                ++seen;
            } else if (tileUsage[tileIdx] == UNUSED_TILE) {
                newLRU[unused] = tileIdx;
                ++unused;
            } else {
                newLRU[unseen] = tileIdx;
                ++unseen;
            }
        }
        tileLRU = newLRU;

        // --------------------------------------
        // Third Pass: Make sure we have free tiles on hand
        // Free anything not seen in 3 scans
        // --------------------------------------
        scan -= STALE_TILE_AGE;
        for (int srcIdx=seen; srcIdx<unseen; ++srcIdx) {
            tileIdx = tileLRU[srcIdx];
            if ( (tileUsage[tileIdx] > 0)
                && (tileUsage[tileIdx] < scan)
                && (tileIdx > 0)) {

                LOG.info("Free Tile {}", tileIdx);
                // TODO: SET PARENT OF THIS TILE TO STUB
                putTileFree(tileIdx);
                debugPrint("Freeing tile " + tileIdx);
            }
        }
    }


    public long[] generateRequests(int scan) {
        requestPaths = null;

        // --------------------------------------
        // First pass, count the number of requests
        // --------------------------------------
        int requested = 0;
        int nodeIdx;
        for (nodeIdx=0; nodeIdx<numNodes; ++nodeIdx) {
            if (nodeRequests[nodeIdx] == scan) {
                ++requested;
            }
        }

        // --------------------------------------
        // Second pass, create a list of requested tile indices
        // TODO: Use fixed list; merge two loops
        // --------------------------------------
        if (requested > 0) {
            requestPaths = new long[requested];

            int dstIdx = 0;
            for (nodeIdx=0; (nodeIdx<numNodes) && (dstIdx < requested); ++nodeIdx) {
                if (nodeRequests[nodeIdx] == scan) {
                    // Offset by 1 to skip root, when using API
                    int tileIdx = getTileForNodeIdx(nodeIdx+1);

                    long path = tilePaths[tileIdx];

                    requestPaths[dstIdx] = Path.addChild(path, getChildForNodeIdx(nodeIdx+1));
                    tileRequestIndices[dstIdx] = nodeIdx;
                    ++dstIdx;
                }
            }
        }
        return requestPaths;
    }

    public void provideResponse(long[] response) {
        int newTileIdx;
        int newNodeIdx;
        int nodeIdx;
        int node;
        long material;
        for (int responseIdx=0; responseIdx<response.length; responseIdx+=TILE_SIZE) {
            newTileIdx = getFreeTile();
            // TODO: Cope with getFreeTile failure

            nodeIdx = tileRequestIndices[responseIdx>>TILE_SHIFT];
            nodes[nodeIdx] = Node.setTile(Node.setLoaded(nodes[nodeIdx], true), newTileIdx);
            tilePaths[newTileIdx] = requestPaths[responseIdx>>TILE_SHIFT];

            for (int child=0; child<TILE_SIZE; ++child) {
                material = response[responseIdx+child];
                node = Node.setLoaded(Node.setNodeReponse(0, Material.node(material)), false);
                newNodeIdx = getNodeInTileIdx(newTileIdx, child) - 1;

                nodes[newNodeIdx] = node;
                nodeMaterials[newNodeIdx] = Material.clearNode(material);
            }
        }
        debugPrint("Loaded Response (" + response.length + " nodes)");
    }

    /*
    Stream Compaction
    http://www.cse.chalmers.se/~uffe/streamcompaction.pdf
    http://http.developer.nvidia.com/GPUGems2/gpugems2_chapter36.html
    http://www.seas.upenn.edu/~cis565/LECTURES/CUDA%20Tricks.pdf
     */



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

            gen.writeArrayFieldStart("nodes");
            for (int index=0; index<numNodes; ++index) {
                gen.writeNumber(nodes[index]);
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
                } else if (Arrays.asList("nodes", "materials", "paths").contains(fieldName)) {
                    if (numTiles == 0) {
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

        for (int val : nodes) {
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
            nodes[cnt]= Bytes.toInt(buf);
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
     * Scan the tile pool and log the count of the various types of nodes
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
            node = nodes[nodeIdx];
            if (Node.isUsed(node)) {
                ++stats.numTiles;

                // --------------------------------------
                // Used, is the tile visible?
                // --------------------------------------
                if (tileUsage[tileIdx] == when)
                    ++stats.numVisible;
            }
            for (int child=0; child<TILE_SIZE; ++child) {
                node = nodes[nodeIdx + child];

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

            if (Node.isUsed(nodes[nodeIdx])) {
                result.append(nodeIdx);
                result.append(": ");
                result.append(Path.toString(tilePaths[tileIdx]));
                result.append("{ ");
                result.append(NEW_LINE);

                for (int child=0; child<TILE_SIZE; ++child) {
                    result.append("   ");
                    result.append(child);
                    result.append(": ");
                    result.append(Node.toString(nodes[nodeIdx + child]));
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

    /**
     * Tree interpretation for debugging
     *
     */
    public void debugPrint(String message) {
        LOG.info("===== {} ====================================================", message);
        debugWalkNode(0, "root", 0);
    }

    private void debugWalkNode(int depth, String id, int nodeIndex) {

        return;

/*

        String prefix = String.format("%1$" + (3*depth + 1) + "s", " ");

        int node = node(nodeIndex);
        long material = material(nodeIndex);

        LOG.info("{}{}({}):{} - {}", new Object[]{prefix, id, nodeIndex, Node.toString(node), Material.toString(material)});
        if (Node.isParent(node)) {
            if (Node.isStub(node)) {
                debugWalkStub(depth+1);
            } else {
                int tileIndex = Node.tile(node);
                LOG.info("{}TILE {} - {}", new Object[]{prefix, tileIndex, Path.toString(tilePaths[tileIndex])});
                for (int child=0; child<TILE_SIZE; ++child) {
                    nodeIndex = getNodeInTileIdx(tileIndex, child);
                    debugWalkNode(depth+1, String.format("%d.%d", tileIndex, child), nodeIndex);
                }
            }
        }
*/
    }

    private void debugWalkStub(int depth) {
        String prefix = String.format("%1$" + (3*depth + 1) + "s", " ");

        LOG.info("{} * STUB", prefix);
    }

}
