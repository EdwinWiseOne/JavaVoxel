package com.simreal.VoxEngine;

public class NodePool {
    private static int numNodes;
    private static long[] pool;
    private static int firstFreeNode;

    public static final int NO_FREE_NODE_INDEX = -1;

    public NodePool(int size) {
        numNodes = size;

        pool = new long[numNodes];
        // Chain together all of the free nodes
        for (int idx=0; idx<(numNodes-1); ++idx) {
            pool[idx] = Node.setChild(0L, idx+1);
        }
        pool[numNodes-1] = Node.END_OF_FREE_NODES;

        firstFreeNode = 0;
    }

    public int size() {
        return numNodes;
    }

    public int getFree() {
        int freeNodeIndex = firstFreeNode;
        firstFreeNode = Node.child(pool[freeNodeIndex]);
        if (firstFreeNode == Node.END_OF_FREE_NODES) {
            firstFreeNode = NO_FREE_NODE_INDEX;
        }
        pool[freeNodeIndex] = Node.setUsed(pool[freeNodeIndex], true);
        return freeNodeIndex;
    }

    public void putFree(int nodeIndex) {
        int nextFree = firstFreeNode;
        if (nextFree == NO_FREE_NODE_INDEX) {
            nextFree = Node.END_OF_FREE_NODES;
        }

        pool[nodeIndex] = Node.setUsed(Node.setChild(0L, nextFree), false);
        firstFreeNode = nodeIndex;
    }

    // TODO: Move from RuntimeException to Exception.  Doing Runtime for now because I don't want to
    // update the entire call chain.
    public long node(int index)
        throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }
        return pool[index];
    }

    public void set(int index, long node)
        throws RuntimeException {

        if ((index < 0) || (index >= numNodes)) {
            throw new RuntimeException("NodePool index out of bounds");
        }

        pool[index] = node;
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

        long node;
        for (int idx=0; idx<numNodes; ++idx){
            node = pool[idx];
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
            if (Node.isUsed(pool[idx])) {
                result.append(idx);
                result.append(": ");
                result.append(Node.toString(pool[idx]));
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
