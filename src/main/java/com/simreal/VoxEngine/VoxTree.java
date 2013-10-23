package com.simreal.VoxEngine;

import com.simreal.VoxEngine.brick.BrickFactory;

import javax.vecmath.Point3d;
import javax.vecmath.Point3i;
import javax.vecmath.Vector3d;
import java.util.Random;

/**
 * VoxTree (Voxel Octal Tree) manages the efficient traversal of the voxel tree structure, interpreting
 * the tree implied by the node data in the NodePool.
 *
 * The NodePool associated with the tree holds the actual data that makes up the tree,
 * in a flat array form.
 */
public class VoxTree {
    /**
     * Raycasting State used to traverse the voxel oct tree
     */
    class State {
        /** Near crossing, distance along the ray */
        public Point3d t0;
        /** Far crossing, distance along the ray */
        public Point3d t1;
        /** Midpoint of t0 and t1 */
        public Point3d tM;
        /** Path so far, to this state */
        public long nodePath;
        /** {@link NodePool} index for the node at this state/path */
        public int nodeIndex;
        /** Child octant this state represents (path choice) */
        public int octant;

        /**
         * Construct a blank state object
         */
        State(){
            t0 = new Point3d();
            t1 = new Point3d();
            tM = new Point3d();
            nodePath = 0L;
            nodeIndex = 0;
            octant = 0;
        }

        /**
         * Clone state settings
         *
         * @param state     State to clone
         */
        public void set(State state){
            this.t0.set(state.t0);
            this.t1.set(state.t1);
            this.tM.set(state.tM);
            this.nodePath = state.nodePath;
            this.nodeIndex = state.nodeIndex;
            this.octant = state.octant;
        }
    }

    // --------------------------------------
    // How far distant along the t ray is a voxel considered within
    // picking distance.  Multiple of brick sizes.
    // --------------------------------------
    private static final int PICK_DEPTH = 256 * 3;

    // --------------------------------------
    // Tree dimensions
    // --------------------------------------
    static final int BRICK_EDGE = 16;
    int depth;
    int breadth;
    int edgeLength;
    private Point3d nearTopLeft;
    private Point3d farBottomRight;

    // --------------------------------------
    // Holds the actual data
    // --------------------------------------
    NodePool nodePool;
    BrickFactory factory;
    Lighting lighting;

    // --------------------------------------
    // Raycasting
    // --------------------------------------
    // Ray details
    private volatile int mirror;
    private Point3d t0;
    private Point3d t1;
    private Point3d origin;
    private Vector3d ray;
    private int facet;          // Visible face (facet) of the voxel the ray intersects
    // State details
    private State[] stateStack;
    private State state;
    private State newState;

    // --------------------------------------
    // Pick data is public for easy access.  It isn't particularly critical, and is read-only outside of VoxTree
    // --------------------------------------
    public long pickNodePath;
    public int pickNodeIndex;
    public int pickFacet;
    public Vector3d pickRay;

    // --------------------------------------
    // The axes of the voxels
    // --------------------------------------
    public static final int XY_PLANE = 1;
    public static final int XZ_PLANE = 2;
    public static final int YZ_PLANE = 4;

    // --------------------------------------
    // Misc
    // --------------------------------------
    private Random rand;
    static final long startTime = System.currentTimeMillis();

    /**
     * Construct a tree of the given length.
     *
     * @param depth Number of levels in the tree
     */
    public VoxTree(int depth){

        // --------------------------------------
        // Calculate the vital dimensions
        // --------------------------------------
        this.depth = depth;
        this.breadth = 1 << depth;
        this.edgeLength = breadth * BRICK_EDGE;
        // TODO: Relevant constants in a configuration file / object
        int nodePoolSize = 1024 * 1024;

        // --------------------------------------
        // Initialize the node pool
        // --------------------------------------
        nodePool = new NodePool(nodePoolSize);
        // Root node
        int nodeIndex = nodePool.getFree();
        nodePool.set(nodeIndex, Node.setLeaf(nodePool.node(nodeIndex), true), 0L, 0L);

        // --------------------------------------
        // Helper objects
        // --------------------------------------
        factory = BrickFactory.instance();
        lighting = Lighting.instance();

        // --------------------------------------
        // Define the world cube
        // --------------------------------------
        nearTopLeft = new Point3d(0, 0, 0);
        farBottomRight = new Point3d(edgeLength, edgeLength, edgeLength);

        // --------------------------------------
        // Raycast traversal State and data
        // --------------------------------------
        stateStack = new State[depth+1];
        for (int idx=0; idx<= depth; ++idx) stateStack[idx] = new State();

        state = new State();
        newState = new State();

        t0 = new Point3d();
        t1 = new Point3d();
        origin = new Point3d();
        ray = new Vector3d();

        facet = 0;

        // --------------------------------------
        // Picking details
        // --------------------------------------
        pickNodePath = 0;
        pickNodeIndex = 0;
        pickFacet = 0;
        pickRay = new Vector3d();

        // --------------------------------------
        // Misc
        // --------------------------------------
        rand = new Random();

    }

    /**
     * @return Depth of the tree, how many layers deep it goes
     */
    public int depth() {
        return depth;
    }

    /**
     * @return Breadth of the tree, how many leaf nodes wide it is at the bottom level
     */
    public int breadth() {
        return breadth;
    }

    /**
     * @return Number of pixels in the smallest voxel
     */
    public int stride(){
        return BRICK_EDGE;
    }

    /**
     * @return Length of the cube in terms of pixels: Breadth * Stride
     */
    public int edgeLength(){
        return edgeLength;
    }

    /**
     * @return Access to the nodePool object that backs this tree
     */
    public NodePool nodePool() {
        return nodePool;
    }

    /**
     * Change the backing data of the tree with a shiny new NodePool
     *
     * @param pool  New backing data, from a load or BrickFactory process
     */
    public void setPool(NodePool pool) {
        nodePool = pool;
    }

    /**
     * Set the contents of a leaf voxel given a coordinate within the tree's extent
     *
     * @param voxelPos      Coordinate within the tree's extents
     * @param material      Material to set the voxel to (0L for empty)
     */
    public void setVoxelPoint(Point3i voxelPos, long material){

        // --------------------------------------
        // If our point isn't inside our tree, we have an easy exit
        // --------------------------------------
        if ( (voxelPos.x < nearTopLeft.x)
            || (voxelPos.y < nearTopLeft.y)
            || (voxelPos.z < nearTopLeft.z)
            || (voxelPos.x > farBottomRight.x)
            || (voxelPos.y > farBottomRight.y)
            || (voxelPos.z > farBottomRight.z) ){
            return;
        }
        // --------------------------------------
        // Convert a position to a tree path, which is the canonical
        // way we define a place in the tree; then set it
        // --------------------------------------
        long path = Path.fromPosition(voxelPos, edgeLength, depth);

        setVoxelPath(path, material);
    }

    /**
     * Given a path through the tree, set the indicated voxel
     * to the given material.  Path SHOULD have the same length
     * as the tree length, but if it doesn't we end up setting
     * a non-leaf voxel (covering a number of the lowest level voxels)
     *
     * @param path          Path to a voxel
     * @param material      Material to set it to
     */
    public void setVoxelPath(long path, long material) {

        // --------------------------------------
        // Figure out the precise voxel in the NodePool and set it
        // (and the path metadata describing it)
        // --------------------------------------
        int nodeIndex = getIndexForPath(path, true);
        nodePool.setMaterial(nodeIndex, material);
        nodePool.setPath(nodeIndex, path);

        // TODO: If we are overwriting a node with a material, release any children voxels in the subtree

        // --------------------------------------
        // Consolidate the parent voxel if this setting makes it homogeneous
        // --------------------------------------
        refineVoxelPath(path);
    }

    /**
     * Return the material specified at the given position
     *
     * @param voxelPos  Position within the voxel tree to test
     * @return          The material that given voxel is composed of
     */
    public long testVoxelPoint(Point3i voxelPos) {
        // --------------------------------------
        // If our point isn't inside our tree, we have an easy exit
        // --------------------------------------
        if ( (voxelPos.x < nearTopLeft.x)
                || (voxelPos.y < nearTopLeft.y)
                || (voxelPos.z < nearTopLeft.z)
                || (voxelPos.x > farBottomRight.x)
                || (voxelPos.y > farBottomRight.y)
                || (voxelPos.z > farBottomRight.z) ){
            return 0L;
        }
        // --------------------------------------
        // Convert a position to a tree path
        // --------------------------------------
        long path = Path.fromPosition(voxelPos, this.edgeLength, depth);
        if (path == 0L) return 0L;

        // --------------------------------------
        return testVoxelPath(path);
    }

    /**
     * Return the material specified by the given path
     *
     * @param path      Path through the tree to a voxel
     * @return          The material that given voxel is composed of
     */
    public long testVoxelPath(long path) {

        // --------------------------------------
        // Traverse the path, do NOT split to reach the end
        // --------------------------------------
        int nodeIndex = getIndexForPath(path, false);
        if (nodeIndex == 0) return 0L;

        // --------------------------------------
        return nodePool.material(nodeIndex);
    }

    /**
     * Take the given voxel (by index; find the voxel via Path operations) and
     * divide it into eight sub-voxels.
     *
     * @param nodeIndex     Index of voxel in NodePool to split
     * @return              The new contents of the node
     */
    private int splitVoxel(int nodeIndex) {
        // --------------------------------------
        // Clone the parent node for the children, updating the length
        // --------------------------------------
        int node = nodePool.node(nodeIndex);
        int childNode = Node.setDepth(node, (byte)(Node.depth(node)+1));

        // --------------------------------------
        // Grab nodes from the pool and set them as a new tile
        // --------------------------------------
        for (int idx=0; idx<8; ++idx) {
            // TODO: Verify we got a node from the pool, and deal with errors
            int childIndex = nodePool.getFree();

            // --------------------------------------
            // First child in the tile gets its offset stored in the parent node
            // --------------------------------------
            if (idx == 0) {
                node = Node.setChild(Node.setLeaf(node, false), childIndex);
                nodePool.setNode(nodeIndex, node);
            }

            // --------------------------------------
            // Set the child's context
            // --------------------------------------
            nodePool.setNode(childIndex, childNode);
            nodePool.setMaterial(childIndex, nodePool.material(nodeIndex));
            nodePool.setPath(childIndex, Path.addChild(nodePool.path(nodeIndex), idx));
        }
        return node;
    }

    /**
     * Test the contents of the nodes along the path and if the children
     * are all the same content, collapse the children into the parent.
     * Do this from the bottom to the top, refining the full path.
     *
     * @param path  Path of the child voxel that triggered the refinement
     */
    public void refineVoxelPath(long path) {
        boolean merge = true;
        int depth = Path.length(path);

        // --------------------------------------
        // Traverse the path from bottom to top, merging until we
        // don't have a merge
        // --------------------------------------
        for (int level=depth-1; level >= 0; --level) {
            path = Path.setLength(path, level);
            merge &= refineVoxel(path, merge);
        }
    }

    /**
     * For a given voxel, as defined by the path, make its material a
     * sum of the children materials (colors).  If we are allowed to merge and all
     * of the children have the same material, then remove the children and make
     * this voxel a leaf.
     *
     * @param path          Path to voxel whose children we are summing / merging
     * @param allowMerge    True if we may delete the children and make this voxel a leaf
     * @return              True if we performed a merge
     */
    private boolean refineVoxel(long path, boolean allowMerge) {
        // --------------------------------------
        // Identify the parent node and the children tile
        // --------------------------------------
        int nodeIndex = getIndexForPath(path, true);
        int parentNode = nodePool.node(nodeIndex);
        int childIndex = Node.child(parentNode);

        // --------------------------------------
        // Test the children to determine merge
        // --------------------------------------
        long color = nodePool.material(childIndex);
        boolean merge = allowMerge;
        for (int idx=1; merge && (idx<8); ++idx){
            if (color != nodePool.material(childIndex+idx)) {
                merge = false;
            }
        }

        // --------------------------------------
        // If all children are the same color, coalesce into this parent (if we may)
        // --------------------------------------
        if (merge && allowMerge) {
            nodePool.setNode(nodeIndex, Node.setLeaf(parentNode, true));
            nodePool.setMaterial(nodeIndex, color);

            // Free the children
            for (int idx=7; idx>=0; --idx) {
                nodePool.putFree(childIndex + idx);
            }
            // Merged!
            return true;
        }

        // --------------------------------------
        // Didn't merge, so accumulate child materials
        // --------------------------------------
        long red = 0;
        long green = 0;
        long blue = 0;
        long alpha = 0;
        long albedo = 0;
        long reflectance = 0;
        for (int idx=0; idx<8; ++idx){
            long material = nodePool.material(childIndex + idx);
            red += Material.red(material);
            green += Material.green(material);
            blue += Material.blue(material);
            alpha += Material.alpha(material);
            albedo += Material.albedo(material);
            reflectance += Material.reflectance(material);
        }
        // New material in the parent node
        nodePool.setMaterial(nodeIndex,
                Material.setMaterial((int) (red >>> 3), (int) (green >>> 3), (int) (blue >>> 3),
                        (int) (alpha >>> 3), (int) (albedo >>> 3), (int) (reflectance >>> 3)));

        // Didn't merge!
        return false;
    }

    /**
     * Given a path, find the specific node in the NodePool that it represents,
     * by traversing the node tree using the choices in the path.
     *
     * For pick rays, we want to split the nodes all the way to the tree root.  For
     * other purposes, we don't need to split but are just looking for the material at
     * the position indicated, even if it's inside a larger-than-leaf node.
     *
     * @param path      Path to traverse to the node
     * @param split     True if we split merged nodes to get at their (virtual) children
     * @return          Index of the node (or its non-split predecessor, which has the same material).
     */
    public int getIndexForPath(long path, boolean split) {
        int node;
        int nodeIndex = 0;
        int depth = Path.length(path);

        // --------------------------------------
        // Walk down the path...
        // --------------------------------------
        for (int cnt=0; cnt<depth; ++cnt) {
            node = nodePool.node(nodeIndex);

            // --------------------------------------
            // Subdivide if we hit a leaf before the bottom?
            // --------------------------------------
            if (Node.isLeaf(node)){
                if (split) {
                    node = splitVoxel(nodeIndex);
                } else {
                    break;
                }
            }

            // --------------------------------------
            // ... and choose the child at this step of the path
            // --------------------------------------
            nodeIndex = Node.child(node) + Path.child(path, cnt);
        }

        return nodeIndex;
    }


    /**
     * Given a viewpoint origin and a ray direction, determine the voxels in
     * the ray path, accumulating material attributes until it is determined that
     * the color along the path is fully defined.
     *
     * Picking returns color, but also sets the internal details of the picked voxel
     * for access later.  See {@link #pickNodePath}, {@link #pickNodeIndex}, {@link #pickFacet}, and {@link #pickRay}
     *
     * @param inOrigin  Viewpoint origin in the world
     * @param inRay     Normalized ray to traverse
     * @param pick      True if we are picking and not rendering
     * @return          Color of voxels along the ray (or picked voxel)
     */
    public int castRay(Point3d inOrigin, Vector3d inRay, boolean pick){
        // Record a pick detail
        if (pick) {
            pickRay.set(inRay);
        }

        // --------------------------------------
        // Mirror the ray into quadrant 1, all positive ray elements,
        // operating on a COPY of the ray (and origin)
        // --------------------------------------
        ray.set(inRay);
        origin.set(inOrigin);
        mirror = 0;
        if (ray.x < 0){
            ray.x = -ray.x;
            origin.x = edgeLength - origin.x;
            mirror |= 4;
        }
        if (ray.y < 0){
            ray.y = -ray.y;
            origin.y = edgeLength - origin.y;
            mirror |= 2;
        }
        if (ray.z < 0){
            ray.z = -ray.z;
            origin.z = edgeLength - origin.z;
            mirror |= 1;
        }

        // --------------------------------------
        // Find the T values at all six edge planes
        // --------------------------------------
        final double verySmallValue = 0.000000001;
        ray.x = Math.max(verySmallValue, ray.x);
        ray.y = Math.max(verySmallValue, ray.y);
        ray.z = Math.max(verySmallValue, ray.z);

        t0.sub(nearTopLeft, origin);
        t0.x /= ray.x;
        t0.y /= ray.y;
        t0.z /= ray.z;

        t1.sub(farBottomRight, origin);
        t1.x /= ray.x;
        t1.y /= ray.y;
        t1.z /= ray.z;

        double tmin = Math.max(t0.x, Math.max(t0.y, t0.z));
        double tmax = Math.min(t1.x, Math.min(t1.y, t1.z));

        // --------------------------------------
        // Do the actual raycasting work, recursively on the t-values, and return the
        // pick or accumulated color
        // --------------------------------------
        long material = 0L;
        if ( (tmin < tmax) && (tmax > 0.0d)){

            // Don't cast if the the entire vox tree is behind the view
            if ((t1.x >= 0.0) && (t1.y >= 0.0) && (t1.z >= 0.0)) {

                // --------------------------------------
                material = castSubtree(t0, t1, inRay, pick);
                // --------------------------------------

                if (pick || (Material.alpha(material) >= 250))
                    return Material.RGBA(material);
            }
        }

        // --------------------------------------
        // We didn't find enough material (or any) so add in a noisy background
        // --------------------------------------

        // Conversion factor so noise is consistent between BrickFactory and screen uses
        double screenFactor = 128.0;
        // Animation for noise texture, axis in noise space
        double tick = (double)(System.currentTimeMillis()-startTime) / 1000.0;

        // Blend material from casting, with black with alpha from textured noise; black texture overlay
        material = Material.alphaBlend(
                material,
                Material.setMaterial(0, 0, 0,
                        Texture.toByte(factory.texture().value(inRay.x * screenFactor, inRay.y * screenFactor, tick)),
                        32, 32));

        // Additional blending of pure noise driving the red channel; red static behind the texture
        return Material.RGBA(
                Material.alphaBlend(material,
                        Material.setMaterial(rand.nextInt(256), 0, 0, 255, 255, 32)));
    }

    /**
     * Do the heavy lifting on the ray casting. Instead of recursion down the voxel oct tree, keeps the
     * state on an explicit stack and iterates down the tree.
     *
     * Based tightly on the work of GigaVoxels:  http://maverick.inria.fr/Publications/2009/CNLE09/
     *
     * @param t0      Near crossing of the ray as it enters the tree, distance along ray
     * @param t1      Far crossing of the ray as it exits the tree, distance along ray
     * @param view    View vector, used for lighting the voxels caught in the ray
     * @param pick    True if picking instead of rendering
     * @return        Accumulated material along the ray
     */
    private long castSubtree(Point3d t0, Point3d t1, Vector3d view, boolean pick){

        // Working variables
        Point3d tM1 = new Point3d();    // Midpoint of t0 and t1
        Point3i tOct = new Point3i();   // Holds the index of the NEXT octant after this one
        long material = 0L;             // Material accumulator

        // --------------------------------------
        // Setup the initial state
        // --------------------------------------
        tM1.add(t0, t1);
        tM1.scale(0.5);
        int octant = findOctant(t0, tM1);   // Which sub-node is the entry point into this node
        state.octant = octant;
        state.t0 = t0;
        state.t1 = t1;
        state.tM = tM1;
        state.nodePath = 0L;
        state.nodeIndex = 0;

        int stateStackTop = 0;
        stateStack[stateStackTop++].set(state);

        // --------------------------------------
        // Process the state stack until it is empty
        // --------------------------------------
        while (stateStackTop > 0){

            // --------------------------------------
            // Get the top state from the stack and its node
            // --------------------------------------
            state.set(stateStack[--stateStackTop]);
            int node = nodePool.node(state.nodeIndex);

            // --------------------------------------
            // If picking, we must traverse to the very bottom of the tree
            // so split a leaf node that isn't at the bottom
            // --------------------------------------
            if ( Node.isLeaf(node)
                    && pick
                    && (Node.depth(node) < depth)) {

                double tmin = Math.max(state.t0.x, Math.max(state.t0.y, state.t0.z));

                if (tmin <= PICK_DEPTH) {
                    // Close enough to pick, so do the split
                    node = splitVoxel(state.nodeIndex);
                    nodePool.setNode(state.nodeIndex, node);
                }
            }

            if (Node.isLeaf(node)) {
                // --------------------------------------
                // Hit a leaf so check its material
                // --------------------------------------
                long newMaterial = nodePool.material(state.nodeIndex);
                if (newMaterial > 0) {
                    // Material isn't a void...

                    if (pick) {
                        // --------------------------------------
                        // If we are picking, try to pick it...
                        // --------------------------------------
                        double tmin = Math.max(state.t0.x, Math.max(state.t0.y, state.t0.z));

                        int prevPickNodeIndex = pickNodeIndex;

                        if (tmin > PICK_DEPTH) {
                            // Too far away, fail the pick
                            pickNodeIndex = 0;
                        }
                        else{
                            // Close enough, record the pick
                            pickNodePath = state.nodePath;
                            pickNodeIndex = state.nodeIndex;
                            pickFacet = facet;
                        }

                        // Picking tends to split voxels, so refine them away
                        refineVoxelPath(state.nodePath);

                        return 0;
                    } else {
                        // --------------------------------------
                        // Not picking, so process the material we found
                        // --------------------------------------

                        // Lighting model... Fake it for now, no lights defined yet
                        double elevation = Math.toRadians(System.currentTimeMillis() / 25);
                        double heading = Math.toRadians(System.currentTimeMillis() / 73);
                        double cosElevation = Math.cos(elevation);
                        Vector3d light = new Vector3d(Math.cos(heading)*cosElevation, Math.sin(elevation), Math.sin(heading)*cosElevation);

                        // Surface normal of the voxel based on the face the ray intersected
                        Vector3d normal = new Vector3d();
                        switch (facet) {
                            case YZ_PLANE:
                                normal.set(1.0, 0.0, 0.0);
                                break;
                            case XZ_PLANE:
                                normal.set(0.0, 1.0, 0.0);
                                break;
                            case XY_PLANE:
                                normal.set(0.0, 0.0, 1.0);
                                break;
                        }
                        // Undo mirroring for the surface normal
                        if ((facet & mirror) > 0)
                            normal.scale(-1);

                        // Indicator on any picked voxel facet
                        if ((pickNodeIndex > 0) && (pickNodeIndex == state.nodeIndex) && (pickFacet == facet) ){
                            light.scale(lighting.pulse());
                        }

                        // Light the new material and alphaBlend it with the accumulated material
                        material = Material.alphaBlend(material, lighting.BlinnPhongFixedLight(newMaterial, normal, light, view));

                        // If we have a decent opacity, exit
                        if (Material.alpha(material) >= 250)
                            return material;
                    }
                }

            } else {
                // --------------------------------------
                // Hit a node, so descend to the appropriate child
                // --------------------------------------
                // Remember where we started as we travel farther
                int thisOctant = state.octant;
                // Subdivide to a new state for the child at the start of the ray
                // and log the options for which child is next
                switch (state.octant){
                    case 0:
                        newState.t0.set(state.t0);
                        newState.t1.set(state.tM);
                        tOct.set(4, 2, 1);
                        break;
                    case 1:
                        newState.t0.set(state.t0.x, state.t0.y, state.tM.z);
                        newState.t1.set(state.tM.x, state.tM.y, state.t1.z);
                        tOct.set(5, 3, 8);
                        break;
                    case 2:
                        newState.t0.set(state.t0.x, state.tM.y, state.t0.z);
                        newState.t1.set(state.tM.x, state.t1.y, state.tM.z);
                        tOct.set(6, 8, 3);
                        break;
                    case 3:
                        newState.t0.set(state.t0.x, state.tM.y, state.tM.z);
                        newState.t1.set(state.tM.x, state.t1.y, state.t1.z);
                        tOct.set(7, 9, 10);
                        break;
                    case 4:
                        newState.t0.set(state.tM.x, state.t0.y, state.t0.z);
                        newState.t1.set(state.t1.x, state.tM.y, state.tM.z);
                        tOct.set(8, 6, 5);
                        break;
                    case 5:
                        newState.t0.set(state.tM.x, state.t0.y, state.tM.z);
                        newState.t1.set(state.t1.x, state.tM.y, state.t1.z);
                        tOct.set(9, 7, 12);
                        break;
                    case 6:
                        newState.t0.set(state.tM.x, state.tM.y, state.t0.z);
                        newState.t1.set(state.t1.x, state.t1.y, state.tM.z);
                        tOct.set(10, 12, 7);
                        break;
                    case 7:
                        newState.t0.set(state.tM);
                        newState.t1.set(state.t1);
                        tOct.set(11, 13, 14);
                        break;
                }

                // Traverse... determine the sibling node based on the exit path of the ray
                // and push it to the state stack... used when we pop back up the tree
                state.octant = nextOctant(newState.t1, tOct);
                if (state.octant < 8) {
                    stateStack[stateStackTop++].set(state);
                }

                // Descend... find the child node at the start of the ray and
                // set up a new state to capture it
                newState.tM.add(newState.t0, newState.t1);
                newState.tM.scale(0.5);
                octant = findOctant(newState.t0, newState.tM);
                newState.octant = octant;
                int octantUnMirrored = thisOctant ^ mirror;
                newState.nodeIndex = Node.child(node) + octantUnMirrored;
                newState.nodePath = Path.addChild(state.nodePath, octantUnMirrored);
                if (stateStackTop > depth) {
                    System.out.println("STATE STACK OVERFLOW");
                    return material;
                }

                // ... push the descent state, which will be popped at the top
                // of the loop
                stateStack[stateStackTop++].set(newState);
            }
        }

        // Done travelling the tree, return what we have regardless
        return material;
    }

    /**
     * Determine the entry voxel from the entry plane (from t0)
     * and the placement of the midpoint (tM)
     *
     * @param t0    Near distance along ray intersecting the cube
     * @param tM    Midpoint of t0 (near) and t1 (far)
     * @return      The child number of the entry voxel
     */
    private int findOctant(Point3d t0, Point3d tM){
        int octant = 0;

        if (t0.x > t0.y){
            if (t0.x > t0.z){ // enter YZ Plane
                if (t0.x > tM.y) octant |= 2;
                if (t0.x > tM.z) octant |= 1;
                facet = YZ_PLANE;

                return octant;
            }
        }
        else{
            if (t0.y > t0.z){ // enter XZ Plane
                if (t0.y > tM.x) octant |= 4;
                if (t0.y > tM.z) octant |= 1;
                facet = XZ_PLANE;

                return octant;
            }
        }
        // enter XY Plane
        if (t0.z > tM.x) octant |= 4;
        if (t0.z > tM.y) octant |= 2;
        facet = XY_PLANE;

        return octant;
    }

    /**
     * Determine the next voxel in sequence given the exit plane (t1)
     * and the list of possible exit voxels
     *
     * @param t1        Near distance along ray intersecting the cube
     * @param octant    List of exit voxels by exit plane
     * @return          The child number of the exit voxel
     */
    private int nextOctant(Point3d t1, Point3i octant){
        if (t1.x < t1.y){
            if (t1.x < t1.z){
                facet = YZ_PLANE;
                return octant.x;    // exit YZ Plane
            }
        }
        else{
            if (t1.y < t1.z){
                facet = XZ_PLANE;
                return octant.y;     // exit XZ Plane
            }
        }
        facet = XY_PLANE;
        return octant.z;  // exit XY Plane
    }

    /**
     * String interpretation of the tree, for debugging purposes
     *
     * @return  String representation of the key tree features
     */
    public String toString() {
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        result.append(this.getClass()).append(" NodePool {").append(NEW_LINE);
        result.append("   Tree Depth: ").append(this.depth).append(NEW_LINE);
        result.append("   Edge Length: ").append(edgeLength).append(NEW_LINE);
        result.append("   Corner 0: ").append(nearTopLeft).append(NEW_LINE);
        result.append("   Corner 1: ").append(farBottomRight).append(NEW_LINE);
        result.append(nodePool.toString());
        result.append("}");

        return result.toString();
    }

}
