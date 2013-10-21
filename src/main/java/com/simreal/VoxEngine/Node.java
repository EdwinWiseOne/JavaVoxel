package com.simreal.VoxEngine;

import com.simreal.VoxEngine.annotations.Stateless;

@Stateless
class Node {
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
     *   |depth  | flags | child                                         |
     *   +-------+-------+-------+-------+-------+-------+-------+-------+
     * 32              24              16               8               0
     * </pre>
     */
    private static final int CHILD_MASK         = 0x00FFFFFF;
    private static final int FLAG_LEAF_MASK     = 0x01000000;
    private static final int FLAG_USED_MASK     = 0x02000000;
    private static final int DEPTH_MASK         = 0xF0000000;

    private static final byte CHILD_SHIFT   = 0;
    private static final byte DEPTH_SHIFT   = 28;

    /**
     * Sets the index of the tile that holds this node's children
     *
     * @param node      Node long to set the child tile into
     * @param child     Index of the first child in the tile
     * @return          Node as modified by the new tile index
     */
    static int setChild(int node, int child){
        return (node & ~CHILD_MASK)
                | (child << CHILD_SHIFT);
    }

    /**
     * Gets the index of the child tile for a node
     *
     * @param node      Node long to extract the child tile index from
     * @return          Child tile index (first of 8 nodes that subdivide this node)
     */
    static int child(int node){
        return ((node & CHILD_MASK) >>> CHILD_SHIFT);
    }

    /**
     * Sets/clears the boolean flag that indicates a node is a leaf (with no children)
     *
     * @param node      Node long to define the leaf flag in
     * @param leaf      True if the node is a leaf, False if it has children
     * @return          Node as modified by the setLeaf operation
     */
    static int setLeaf(int node, boolean leaf){
        if (leaf){
            return (node | FLAG_LEAF_MASK);
        }
        return (node & ~FLAG_LEAF_MASK);
    }

    /**
     * Determines if the given node is a leaf node (with no children)
     *
     * @param node      Node long to test the leaf status of
     * @return          True if the node is a leaf with no children
     */
    static boolean isLeaf(int node){
        return (node & FLAG_LEAF_MASK) == FLAG_LEAF_MASK;
    }

    /**
     * Determines if the given node is a parent node (with 8 children)
     *
     * @param node      Node long to test the parent status of
     * @return          True if the node is a parent with 8 children
     */
    static boolean isParent(int node){
        return (node & FLAG_LEAF_MASK) == 0;
    }

    /**
     * Sets/clears the boolean flag that indicates a node is in use (or free)
     *
     * @param node      Node long to define the used status of
     * @param used      True if the node is in use (not part of the free pool)
     * @return          Node as modified by the setUsed operation
     */
    static int setUsed(int node, boolean used){
        if (used){
            return (node | FLAG_USED_MASK);
        }
        return (node & ~FLAG_USED_MASK);
    }

    /**
     * Determines if the given node is in use (or free)
     *
     * @param node      Node long to test the in-use status of
     * @return          True if the node is in use
     */
    static boolean isUsed(int node){
        return (node & FLAG_USED_MASK) == FLAG_USED_MASK;
    }

    /**
     * Sets the depth parameter in the node, saying where in the tree
     * the node resides.  Note that this is redundant with the path length
     * information associated with this node.
     *
     * TODO: Eliminate node depth as a value and use the path instead
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

    /**
     * Create a string representation of this node
     *
     * @param node      Node long to describe
     * @return          String description of the node
     */
    static String toString(int node){
        StringBuilder result = new StringBuilder();

        if (Node.isLeaf(node)) {
            result.append("LEAF {");
        } else {
            result.append("NODE {");
        }
        result.append(" Depth: ").append(Node.depth(node));
        result.append(", Child: ").append(Node.child(node));
        result.append(" }");
        return result.toString();
    }

}
