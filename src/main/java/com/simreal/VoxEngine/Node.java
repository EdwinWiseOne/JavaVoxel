package com.simreal.VoxEngine;

import com.simreal.VoxEngine.annotations.Stateless;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

@Stateless
public class Node {

    static final Logger LOG = LoggerFactory.getLogger(Node.class.getName());

    /**
     * A Node is an entry in a Sparse Voxel Octree (SVO), which is aware of its
     * depth in the tree, has a pointer to its children tile (which holds all eight
     * children), and has a few boolean flags defining its form.
     *
     * This class defines all the relevant bit manipulations for the defining and
     * querying a node, as stored in a simple int primitive value.
     *
     * <pre>
     *   +-------+-------+-------+-------+-------+-------+-------+-------+
     *   |depth  | flags | tile                                          |
     *   +-------+-------+-------+-------+-------+-------+-------+-------+
     * 32              24              16               8               0
     * </pre>
     *
     */

    static final int TILE_MASK                  = 0x00FFFFFF;
    private static final int RESPONSE_MASK      = 0xFF000000;
    private static final int DEPTH_MASK         = 0xF0000000;
    private static final int FLAGS_MASK         = 0x0F000000;

    private static final int FLAG_PARENT_MASK   = 0x01000000;
    private static final int FLAG_USED_MASK     = 0x02000000;
    private static final int FLAG_LOADED_MASK   = 0x04000000;

    public static final int EMPTY_USED_NODE     = 0x02000000;
    public static final int EMPTY_UNUSED_NODE   = 0x00000000;

    private static final byte TILE_SHIFT        = 0;
    private static final byte RESPONSE_SHIFT    = 24;
    private static final byte DEPTH_SHIFT       = 28;

    private static HashSet<Integer> _histo = new HashSet<Integer>();
    // or HashMap to COUNT hits

    /**
     * Private constructor locks down the utility class
     */
    private Node() {
    }

    /**
     * Sets the index of the tile that holds this node's children
     *
     *
     * @param node      Node long to set the child tile into
     * @param tile      Index of the tile
     * @return          Node as modified by the new tile index
     */
    static int setTile(int node, int tile){
        return (node & ~TILE_MASK)
                | (tile << TILE_SHIFT);
    }

    /**
     * Gets the index of the child tile for a node
     *
     * @param node      Node long to extract the tile index from
     * @return          Child tile index (first of 8 nodes that subdivide this node)
     */
    static int tile(int node){
        return ((node & TILE_MASK) >>> TILE_SHIFT);
    }

    /**
     * Sets/clears the boolean flag that indicates a node is a leaf (with no children)
     *
     * @param node      Node long to define the leaf flag in
     * @param leaf      True if the node is a leaf, False if it has children
     * @return          Node as modified by the setLeaf operation
     */
    static int setLeaf(int node, boolean leaf){
        int val;
        if (!leaf){
            val = (node | FLAG_PARENT_MASK);
        } else {
            val = (node & ~FLAG_PARENT_MASK);
        }
        _histo.add(val & FLAGS_MASK);
        return val;
    }

    /**
     * Determines if the given node is a leaf node (with no children)
     *
     * @param node      Node long to test the leaf status of
     * @return          True if the node is a leaf with no children
     */
    static boolean isLeaf(int node){
        return (node & FLAG_PARENT_MASK) == 0;
    }

    /**
     * Determines if the given node is a parent node (with 8 children)
     *
     * @param node      Node long to test the parent status of
     * @return          True if the node is a parent with 8 children
     */
    static boolean isParent(int node){
        return (node & FLAG_PARENT_MASK) != 0;
    }

    /**
     * Sets/clears the boolean flag that indicates a node is in use (or free)
     *
     * @param node      Node long to define the used status of
     * @param used      True if the node is in use (not part of the free pool)
     * @return          Node as modified by the setUsed operation
     */
    static int setUsed(int node, boolean used){
        int val;
        if (used){
            val = (node | FLAG_USED_MASK);
        } else {
            val = (node & ~FLAG_USED_MASK);
        }
        _histo.add(val & FLAGS_MASK);
        return val;
    }

    /**
     * Determines if the given node is in use (or free)
     *
     * @param node      Node long to test the in-use status of
     * @return          True if the node is in use
     */
    static boolean isUsed(int node){
        return (node & FLAG_USED_MASK) != 0;
    }

    /**
     * Sets/clears the boolean flag that indicates a node's child tile is loaded (or is a stub).
     * A stub is a node (isParent True) where the children tile is not in the node pool.
     *
     * Loaded/Stub state is only relevant for non-leaf (parent) nodes.
     *
     * @param node    Node long to define the stub status of
     * @param loaded  True if the node is loaded (is not a stub)
     * @return
     */
    static int setLoaded(int node, boolean loaded) {
        int val;
        if (loaded) {
            val = (node | FLAG_LOADED_MASK);
        } else {
            val = (node & ~FLAG_LOADED_MASK);
        }
        _histo.add(val & FLAGS_MASK);
        return val;
    }

    /**
     * Determines if the given node has its children loaded (or not)
     *
     * @param node      Node long to test the loaded status of
     * @return          True if the node's children are loaded
     */
    static public boolean isLoaded(int node) {
        return (node & FLAG_LOADED_MASK) != 0;
    }

    /**
     * Determins if the given node is a stub (or is loaded).
     * @param node      Node long to test the stub status of
     * @return          True if the node's children are not loaded
     */
    static boolean isStub(int node) {
        return (node & FLAG_LOADED_MASK) == 0;
    }

    /**
     * Sets the depth parameter in the node, saying where in the tree
     * the node resides.  Note that this is redundant with the path length
     * information associated with this node.
     *
     * @param node
     * @param depth
     * @return
     */
    static int setDepth(int node, byte depth){
        return (node & ~DEPTH_MASK)
                    | ((int)depth << DEPTH_SHIFT);
    }

    /**
     * Return the depth parameter from this node.
     *
     * @param node      Node long to extract the depth from
     * @return          Depth of the node in the tree
     */
    static byte depth(int node){
        return (byte)((node & DEPTH_MASK) >>> DEPTH_SHIFT);
    }

    static int setReponse(int node, int response){
        int val = (node & ~RESPONSE_MASK)
                    | (response << RESPONSE_SHIFT);
        _histo.add(val & FLAGS_MASK);
        return val;
    }

    static int response(int node){
        return (int)((node & RESPONSE_MASK) >>> RESPONSE_SHIFT);
    }

    /**
     * Create a string representation of this node
     *
     * @param node      Node long to describe
     * @return          String description of the node
     */
    static public String toString(int node){
        StringBuilder result = new StringBuilder();

        result.append( Node.isLeaf(node) ? "LEAF {" : "NODE {")
                .append(" Depth: ")
                    .append(Node.depth(node))
                .append( Node.isStub(node)
                    ? Node.isParent(node) ? ", STUB" : ""
                    : ", Tile: " + Node.tile(node) )
                .append(" }");
        return result.toString();
    }

    static public String getHistogram() {
        StringBuilder result = new StringBuilder();

        result.append("Node Flags:\n");
        for (int flag : _histo) {
            result.append(String.format("%08X : %s\n", flag, toString(flag)));
        }
        return result.toString();
    }
}
