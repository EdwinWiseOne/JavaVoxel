package com.simreal.VoxEngine;

import com.simreal.VoxEngine.brick.BrickFactory;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

import javax.vecmath.Point3d;
import javax.vecmath.Point3i;
import javax.vecmath.Vector3d;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.jar.Attributes;

// QSC powered speakers

public class VoxTree {
    class State {
        public Point3d t0;
        public Point3d t1;
        public Point3d tM;
        public long nodePath;
        public int nodeIndex;
        public int octant;

        State(){
            t0 = new Point3d();
            t1 = new Point3d();
            tM = new Point3d();
            nodePath = 0L;
            nodeIndex = 0;
            octant = 0;
        }

        // TODO: Need to track depth in tree of ray, and path of parents to picked node to recombine the tree on set
        /*public void set(Point3d t0, Point3d t1, Point3d tM, int nodeIndex, int octant) {
            this.t0.set(t0);
            this.t1.set(t1);
            this.tM.set(tM);
            this.nodeIndex = nodeIndex;
            this.octant = octant;
        }*/

        public void set(State state){
            this.t0.set(state.t0);
            this.t1.set(state.t1);
            this.tM.set(state.tM);
            this.nodePath = state.nodePath;
            this.nodeIndex = state.nodeIndex;
            this.octant = state.octant;
        }
    }

    private static final int PICK_DEPTH = 256 * 1;

    int depth;
    int breadth;
    int edgeLength;
    int nodePoolSize;

    // TODO: Encapsulate node pool to help enforce memory management
    NodePool nodePool;

    private Point3d nearTopLeft;
    private Point3d farBottomRight;
    private volatile int mirror;

    private Point3d t0;
    private Point3d t1;
    private Point3d origin;
    private Vector3d ray;

    private State[] stateStack;

    private State state;
    private State newState;

    private Random rand;

    public long pickNodePath;
    public int pickNodeIndex;
    public int pickFacet;
    public Vector3d pickRay;


    private int facet;
    private Vector3d facing;

    public static final int XY_PLANE = 1;
    public static final int XZ_PLANE = 2;
    public static final int YZ_PLANE = 4;

    static final long startTime = System.currentTimeMillis();

    static final int BRICK_EDGE = 16;
    /**
     *
     */
    public VoxTree(int depth){
        this.depth = depth;
        this.breadth = 1 << depth;
        this.edgeLength = breadth * BRICK_EDGE;
        this.nodePoolSize = 1024 * 1024;

        // --------------------------------------
        // Initialize the node pool
        // --------------------------------------
        nodePool = new NodePool(nodePoolSize);
        // Root node
        int nodeIndex = nodePool.getFree();
        nodePool.set(nodeIndex, Node.setLeaf(nodePool.node(nodeIndex), true), 0L, 0L);

        // --------------------------------------
        // Define the world cube
        // --------------------------------------
        // TODO: use the world cube dimensions explicitly in Path
        nearTopLeft = new Point3d(0, 0, 0);
        farBottomRight = new Point3d(edgeLength, edgeLength, edgeLength);

        // --------------------------------------
        // Raycast traversal data
        // --------------------------------------
        stateStack = new State[depth+1];
        for (int idx=0; idx<= depth; ++idx) stateStack[idx] = new State();

        state = new State();
        newState = new State();

        t0 = new Point3d();
        t1 = new Point3d();
        origin = new Point3d();
        ray = new Vector3d();

        facing = new Vector3d(0.0, 0.0, 0.0);
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

    public int depth() {
        return depth;
    }

    public int breadth() {
        return breadth;
    }

    public int edgeLength(){
        return edgeLength;
    }

    public int stride(){
        return BRICK_EDGE;
    }

    public NodePool nodePool() {
        return nodePool;
    }

    public void setPool(NodePool pool) {
        nodePool = pool;
    }

    public void setVoxelPoint(Point3i voxel, long material){
        if ( (voxel.x < nearTopLeft.x)
            || (voxel.y < nearTopLeft.y)
            || (voxel.z < nearTopLeft.z)
            || (voxel.x > farBottomRight.x)
            || (voxel.y > farBottomRight.y)
            || (voxel.z > farBottomRight.z) ){
            return;
        }
        long path = Path.fromPosition(voxel, this.edgeLength, depth);
        setVoxelPath(path, material);
    }


    public void setVoxelPath(long path, long material) {

        int nodeIndex = getIndexForPath(path, true);
        nodePool.setMaterial(nodeIndex, material);
        nodePool.setPath(nodeIndex, path);

        refineVoxelPath(path);
    }

    public long testVoxelPoint(Point3i pos) {
        long path = Path.fromPosition(pos, this.edgeLength, depth);
        if (path == 0L) return 0L;

        return testVoxelPath(path);
    }

    public long testVoxelPath(long path) {
        int nodeIndex = getIndexForPath(path, false);
        if (nodeIndex == 0) return 0L;

        // refineVoxelPath(path);
        return nodePool.material(nodeIndex);
    }

    private int splitVoxel(int nodeIndex) {
        int node = nodePool.node(nodeIndex);
        int childNode = Node.setDepth(node, (byte)(Node.depth(node)+1));

//        System.out.println("Split: populating " + nodeIndex);

        for (int idx=0; idx<8; ++idx) {
            int childIndex = nodePool.getFree();
            if (idx == 0) {
                node = Node.setChild(Node.setLeaf(node, false), childIndex);
                nodePool.setNode(nodeIndex, node);
            }
            nodePool.setNode(childIndex, childNode);
            nodePool.setMaterial(childIndex, nodePool.material(nodeIndex));
            nodePool.setPath(childIndex, Path.addChild(nodePool.path(nodeIndex), idx));
        }
        return node;
    }

    public void refineVoxelPath(long path) {
        boolean merge = true;
        int depth = Path.depth(path);

        for (int level=depth-1; level >= 0; --level) {
            path = Path.setDepth(path, level);
            merge &= refineVoxel(path, merge);
        }
    }

    private boolean refineVoxel(long path, boolean allowMerge) {
        int nodeIndex = getIndexForPath(path, true);

        int parentNode = nodePool.node(nodeIndex);
        int childIndex = Node.child(parentNode);

        // If all children are the same color, coalesce into this parent
        long color = nodePool.material(childIndex);
        boolean merge = true;
        for (int idx=1; idx<8; ++idx){
            if (color != nodePool.material(childIndex+idx)) {
                merge = false;
                break;
            }
        }

        if (merge && allowMerge) {
//            System.out.println("Refine: trimming " + nodeIndex);

            nodePool.setNode(nodeIndex, Node.setLeaf(parentNode, true));
            nodePool.setMaterial(nodeIndex, color);
            for (int idx=7; idx>=0; --idx) {
                nodePool.putFree(childIndex + idx);
            }
            return true;
        }

        // Accumulate child colors
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
        nodePool.setMaterial(nodeIndex,
                Material.setMaterial((int) (red >>> 3), (int) (green >>> 3), (int) (blue >>> 3),
                        (int) (alpha >>> 3), (int) (albedo >>> 3), (int) (reflectance >>> 3)));
        return false;
    }

    public int getIndexForPath(long path, boolean split) {
        int depth = Path.depth(path);
        int nodeIndex = 0;
        int node;
//        int childNode;
        for (int cnt=0; cnt<depth; ++cnt) {
            node = nodePool.node(nodeIndex);

            // Subdivide if we hit a leaf before the bottom
            if (Node.isLeaf(node)){
                if (split) {
                    node = splitVoxel(nodeIndex);
                } else {
                    break;
                }
            }
            nodeIndex = Node.child(node) + Path.child(path, cnt);
        }
        return nodeIndex;
    }


    /**
     *
     */
    public long castRay(Point3d inOrigin, Vector3d inRay, boolean pick){
        // Mirror the ray into quadrant 1
        if (pick) {
            pickRay.set(inRay);
        }
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

        // Find our T values at all six edge planes
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

        long material = 0L;
        if ( (tmin < tmax) && (tmax > 0.0d)){
            material = castSubtree(t0, t1, inRay, pick);
            if (pick || (Material.alpha(material) >= 250)) return material;
        }

        double screenFactor = 128.0;
        double tick = (double)(System.currentTimeMillis()-startTime) / 1000.0;
        material = Material.blend(material, Material.setMaterial(0, 0, 0,
                Texture.toByte(BrickFactory.texture().value(inRay.x * screenFactor, inRay.y * screenFactor, tick)),
                32, 32));
        return Material.blend(material, Material.setMaterial(rand.nextInt(256), 0, 0, 255, 255, 32));
    }

    /**
     *
     */
    private long castSubtree(Point3d t0, Point3d t1, Vector3d view, boolean pick){

        // Error condition early exit
        if ((t1.x < 0.0) || (t1.y < 0.0) || (t1.z < 0.0)) {
            return 0L;
        }

        int stateStackTop = 0;

        Point3d tM1 = new Point3d();
        Point3i tOct = new Point3i();
        long material = 0L;

        tM1.add(t0, t1);
        tM1.scale(0.5);
        int octant = findOctant(t0, tM1);
        state.t0 = t0;
        state.t1 = t1;
        state.tM = tM1;
        state.nodePath = 0L;
        state.nodeIndex = 0;
        state.octant = octant;
        stateStack[stateStackTop++].set(state);

        while (stateStackTop > 0){
            // Get the top state from the stack
            state.set(stateStack[--stateStackTop]);

            // Child...
            int node = nodePool.node(state.nodeIndex);
            if ( Node.isLeaf(node)
                    && pick
                    && (Node.depth(node) < depth)) {

                double tmin = Math.max(state.t0.x, Math.max(state.t0.y, state.t0.z));

                if (tmin <= PICK_DEPTH) {
                    // If picking, we must traverse to the very bottom...
                    node = splitVoxel(state.nodeIndex);
                    nodePool.setNode(state.nodeIndex, node);
                }
            }

            if (Node.isLeaf(node)) {
                long newMaterial = nodePool.material(state.nodeIndex);
                // ... value
                if (newMaterial > 0) {
                    if (pick) {
                        double tmin = Math.max(state.t0.x, Math.max(state.t0.y, state.t0.z));

                        int prevPickNodeIndex = pickNodeIndex;

                        if (tmin > PICK_DEPTH) {
                            pickNodeIndex = 0;
                        }
                        else{
                            if ( (state.nodeIndex != pickNodeIndex) || (facet != pickFacet) ) {
                                pickNodePath = state.nodePath;
                                pickNodeIndex = state.nodeIndex;
                                pickFacet = facet;

//                                int index = getIndexForPath(pickNodePath, true);
                                //System.out.println("Picked " + pickNodeIndex + " -> " + Path.toString(pickNodePath) + " -> " + index);
                            }
                        }
                        if ((prevPickNodeIndex != 0) && (pickNodeIndex != prevPickNodeIndex)) {
                            refineVoxelPath(pickNodePath);
                        }

                        // TODO: Refine the parent path of the previously picked node, to possibly reverse
                        // any splitting we did to pick...

                        return 0;
                    }

                    // Lighting model!
                    // Fake it for now, no lights yet
                    double elevation = Math.toRadians(System.currentTimeMillis() / 25);
                    double heading = Math.toRadians(System.currentTimeMillis() / 73);
                    double cosElevation = Math.cos(elevation);
                    Vector3d light = new Vector3d(Math.cos(heading)*cosElevation, Math.sin(elevation), Math.sin(heading)*cosElevation);

                    Vector3d normal = new Vector3d(facing);
                    if ((facet & mirror) > 0)
                        normal.scale(-1);

                    if ((pickNodeIndex > 0) && (pickNodeIndex == state.nodeIndex) && (pickFacet == facet) ){
                        light.scale(Lighting.pulse());
                    }

                    material = Material.blend(material, Lighting.illuminate(newMaterial, normal, light, view));
//                    material = Material.blend(material, newMaterial);
                    if (Material.alpha(material) >= 250) return material;
                }

            } else {
                int thisOctant = state.octant;
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

                // Traverse
                state.octant = nextOctant(newState.t1, tOct);
                if (state.octant < 8) {
                    stateStack[stateStackTop++].set(state);
                }

                // ... descend
                newState.tM.add(newState.t0, newState.t1);
                newState.tM.scale(0.5);
                octant = findOctant(newState.t0, newState.tM);
                newState.octant = octant;
                int octantMirror = thisOctant ^ mirror;
                newState.nodeIndex = Node.child(node) + octantMirror;
                newState.nodePath = Path.addChild(state.nodePath, octantMirror);
                if (stateStackTop > depth) {
                    System.out.println("STATE STACK OVERFLOW");
                    return material;
                }
                stateStack[stateStackTop++].set(newState);
            }
        }
        return material;
    }

    /**
     *
     *
     */
    private int findOctant(Point3d t0, Point3d tM){
        int octant = 0;

        if (t0.x > t0.y){
            if (t0.x > t0.z){ // enter YZ Plane
                if (t0.x > tM.y) octant |= 2;
                if (t0.x > tM.z) octant |= 1;
                facing.set(1.0, 0.0, 0.0);
                facet = YZ_PLANE;

                return octant;
            }
        }
        else{
            if (t0.y > t0.z){ // enter XZ Plane
                if (t0.y > tM.x) octant |= 4;
                if (t0.y > tM.z) octant |= 1;
                facing.set(0.0, 1.0, 0.0);
                facet = XZ_PLANE;

                return octant;
            }
        }
        // enter XY Plane
        if (t0.z > tM.x) octant |= 4;
        if (t0.z > tM.y) octant |= 2;
        facing.set(0.0, 0.0, 1.0);
        facet = XY_PLANE;

        return octant;
    }

    /**
     *
     */
    private int nextOctant(Point3d t1, Point3i octant){
        if (t1.x < t1.y){
            if (t1.x < t1.z){
                facing.set(1.0, 0.0, 0.0);
                facet = YZ_PLANE;
                return octant.x;    // exit YZ Plane
            }
        }
        else{
            if (t1.y < t1.z){
                facing.set(0.0, 1.0, 0.0);
                facet = XZ_PLANE;
                return octant.y;     // exit XZ Plane
            }
        }
        facing.set(0.0, 0.0, 1.0);
        facet = XY_PLANE;
        return octant.z;  // exit XY Plane
    }

    // --------------------------------------
    // Save, Load, and related utilities
    // --------------------------------------

    public void save(String name, Attributes tags) {
        NodePool savePool = compressTree();

//        System.out.println(nodePool);
//        System.out.println(savePool);

        // TODO: Shift over to database storage

        // TODO: materials and paths need saving (and loading)

        JsonFactory jsonFactory = new JsonFactory();
        try {
            FileOutputStream  output = new FileOutputStream("bricks" + File.separator + name + ".node");

            JsonGenerator gen = jsonFactory.createJsonGenerator(output, JsonEncoding.UTF8); // or Stream, Reader

            gen.writeStartObject();
            gen.writeNumberField("size", savePool.size());
            for (Object key : tags.keySet()) {
                String keyStr = key.toString();
                gen.writeStringField(keyStr, tags.getValue(keyStr));
                System.out.println(keyStr + " = " + tags.getValue(keyStr));
            }
            gen.writeArrayFieldStart("nodes");
            // To make more clean, would need to store pool as ByteBuffer (and cast to LongBuffer, etc)
            for (int index=0; index<savePool.size(); ++index) {
                gen.writeNumber(savePool.node(index));
            }
            gen.writeEndArray();

            gen.writeArrayFieldStart("materials");
            // To make more clean, would need to store pool as ByteBuffer (and cast to LongBuffer, etc)
            for (int index=0; index<savePool.size(); ++index) {
                gen.writeNumber(savePool.material(index));
            }
            gen.writeEndArray();

            gen.writeArrayFieldStart("paths");
            // To make more clean, would need to store pool as ByteBuffer (and cast to LongBuffer, etc)
            for (int index=0; index<savePool.size(); ++index) {
                gen.writeNumber(savePool.path(index));
            }
            gen.writeEndArray();

            gen.writeEndObject();
            gen.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }


    public NodePool load(String name, Attributes tags) {
        NodePool loadPool = null;
        int size = 0;

        // TODO: Shift over to database storage

        JsonFactory jsonFactory = new JsonFactory();
        try {
            FileInputStream input = new FileInputStream("bricks" + File.separator + name + ".node");

            JsonParser parse = jsonFactory.createJsonParser(input);

            if (parse.nextToken() != JsonToken.START_OBJECT) {
                throw new Exception("File '" + name + "' must begin with a START_OBJECT");
            }
            while (parse.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parse.getCurrentName();
                parse.nextToken(); // move to value, or START_OBJECT/START_ARRAY

                if ("size".equals(fieldName)) {
                    size = parse.getIntValue();
                } else if (Arrays.asList("nodes", "materials", "paths").contains(fieldName)) {
                    if (size == 0) {
                        throw new Exception("File '" + name + "' has a zero pool size.");
                    }
                    if (null == loadPool) {
                        loadPool = new NodePool(size);
                    }
                    if (parse.getCurrentToken() != JsonToken.START_ARRAY) {
                        throw new Exception("File '" + name + "' " + fieldName + " must begin with a START_ARRAY");
                    }
                    int index=0;

                    if ("nodes".equals(fieldName)) {
                        while (parse.nextToken() != JsonToken.END_ARRAY) {
                            loadPool.setNode(index++, parse.getIntValue());
                        }
                    } else if ("materials".equals(fieldName)) {
                        while (parse.nextToken() != JsonToken.END_ARRAY) {
                            loadPool.setMaterial(index++, parse.getLongValue());
                        }
                    } else if ("paths".equals(fieldName)) {
                        while (parse.nextToken() != JsonToken.END_ARRAY) {
                            loadPool.setPath(index++, parse.getLongValue());
                        }
                    }
                } else {
                    tags.put(new Attributes.Name(fieldName), parse.toString());
                }
            }
            parse.close(); // ensure resources get clean

            System.out.println(loadPool);
            return loadPool;

        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }



    private int copyTileSubtree(NodePool srcPool, int srcTileIndex, NodePool dstPool) {
        int srcIndex = 0;
        int dstIndex = 0;
        int dstTileIndex = 0;

        // Copy this tile across
        for (int child=0; child<8; ++child) {
            srcIndex = srcTileIndex + child;
            dstIndex = dstPool.getFree();
            if (child == 0) dstTileIndex = dstIndex;

            dstPool.setNode(dstIndex, srcPool.node(srcIndex));
            dstPool.setMaterial(dstIndex, srcPool.material(srcIndex));
            dstPool.setPath(dstIndex, srcPool.path(srcIndex));
        }

        // Now descend through the tile
        for (int child=0; child<8; ++child) {
            srcIndex = srcTileIndex + child;

            if (Node.isNode(srcPool.node(srcIndex))) {
                dstIndex = dstTileIndex + child;

                srcIndex = copyTileSubtree(srcPool, Node.child(srcPool.node(srcIndex)), dstPool);

                dstPool.setNode( dstIndex, Node.setChild( dstPool.node(dstIndex), srcIndex));
            }
        }

        return dstTileIndex;
    }

    private NodePool compressTree() {
        // Determine our size
        NodePool.Statistics stats = nodePool.analyze();

        // Allocate a just-right pool
        NodePool newPool = new NodePool(stats.numUsed);

        // Always a root node
        int dstIndex = newPool.getFree();
        int rootNode = nodePool.node(0);
        newPool.setNode(dstIndex, rootNode);
        newPool.setMaterial(dstIndex, nodePool.material(0));
        newPool.setPath(dstIndex, nodePool.path(0));

        if (!Node.isLeaf(rootNode)) {
            try {
                copyTileSubtree(nodePool, Node.child(rootNode), newPool);
            } catch (Exception e) {
                System.out.println(e);
            }
        }

        return newPool;
    }

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
