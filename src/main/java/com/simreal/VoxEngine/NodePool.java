package com.simreal.VoxEngine;

public class NodePool {
    private int numNodes;
    private int[] nodes;
    private long[] materials;
    private long[] paths;
    private int firstFreeNode;

    public static final int NO_FREE_NODE_INDEX = -1;

    public NodePool(int size) {
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

    public String toString(){
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        NodePool.Statistics stats = analyze();

        result.append(this.getClass()).append(" NodePool {").append(NEW_LINE);
        result.append("   Free Node: ").append(firstFreeNode).append(" of ").append(numNodes).append(NEW_LINE);
        result.append("   (").append(stats.numNodes).append(" nodes, ").append(stats.numLeaves).append(" leaves)").append(NEW_LINE);
        boolean elided = false;
        for (int idx=0; idx<64; ++idx){
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
