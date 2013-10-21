import com.simreal.VoxEngine.Database;
import com.simreal.VoxEngine.Material;
import com.simreal.VoxEngine.VoxTree;

import javax.swing.JFrame;
import javax.vecmath.Point3d;
import javax.vecmath.Point3i;
import javax.vecmath.Vector3d;
import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class BrickEditor extends Canvas implements Runnable {

    // TODO: Relevant constants in a configuration file / object
    // --------------------------------------
    // View definition, controlling the pixels that get calculated (versus rendered)
    // --------------------------------------
    /** Rendering width for pixels being rendered */
    public static final int WIDTH = 320;
    /** Rendered height for pixels being rendered */
    public static final int HEIGHT = 240;
    /** Field of View, 60 degrees across the horizontal rendering */
    private static final double H_FOV = Math.PI / 3.0;
    /** Distance the viewing plane needs to be from the viewer to achieve the field of view */
    private static final int DEPTH = (int)(WIDTH / Math.tan(H_FOV * 0.5));
    /** Backing store for the pixels */
    private static final int imageType = BufferedImage.TYPE_INT_RGB;
    /** Image that manages the backing store */
    private BufferedImage img;

    // --------------------------------------
    // Canvas definition, the pixels that get rendered
    // --------------------------------------
    /** Display canvas width */
    public static final int CANVAS_WIDTH = WIDTH*3;
    /** Display canvas height */
    public static final int CANVAS_HEIGHT = HEIGHT*3;

    // --------------------------------------
    // Viewpoint  TODO: better names
    // --------------------------------------
    private static Point3d viewPoint;
    private static Vector3d ltVec;
    private static Vector3d upVec;
    private static Vector3d fwVec;
    private static Point3d topLeft;

    // --------------------------------------
    // Raycasting  TODO: better names
    // --------------------------------------
    private static Point3d column0;
    private static Point3d at;
    private static Vector3d facing;

    // --------------------------------------
    // Tree definition.  4 levels gives 16 voxels on an edge, for the standard brick
    // --------------------------------------
    /** Tree length, which determines the maximum number of nodes in the tree and the size cube the tree can represent */
    public static final int TREE_DEPTH = 4;

    // --------------------------------------
    // Window and Interface definition
    // --------------------------------------
    /** Window title */
    public static final String TITLE = "Title";
    private UserInput uiListeners;

    // --------------------------------------
    // Brick data -- backing store and current brick tree
    // --------------------------------------
    private Database database;
    private VoxTree tree;

    // --------------------------------------
    // Misc
    // --------------------------------------
    private boolean running = false;

    /**
     * Constructor, set the tree up.
     */
    public BrickEditor() {

        // --------------------------------------
        // Viewpoint backing data
        // --------------------------------------
        viewPoint = new Point3d();
        ltVec = new Vector3d();
        upVec = new Vector3d();
        fwVec = new Vector3d();
        topLeft = new Point3d();

        // --------------------------------------
        // Raycasting backing data
        // --------------------------------------
        column0 = new Point3d(topLeft);
        at = new Point3d();
        facing = new Vector3d();

        // --------------------------------------
        // Image (pixel) buffer we render into
        // --------------------------------------
        img = new BufferedImage(WIDTH, HEIGHT, imageType);

        // --------------------------------------
        // Database where the voxel data lives
        // --------------------------------------
        database = new Database();

        // --------------------------------------
        // Voxel tree that holds the current subset of voxel data
        // --------------------------------------
        tree = new VoxTree(TREE_DEPTH);
        int stride = tree.stride();
        int offset = stride >> 1;

        // --------------------------------------
        // Black floor of the brick, a non-useful default model placeholder
        // --------------------------------------
        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                tree.setVoxelPoint(
                        new Point3i((x*stride)+offset,
                                0,
                                (y*stride)+offset),
                                Material.setMaterial(0, 0, 0, 255, 128, 64));
            }
        }
    }

    /**
     * UI Thread Start
     */
    private void startUI() {
        if(running)
            return;
        running = true;

        Thread thread = new Thread(this, "UI");
        thread.start();

        uiListeners = UserInput.getUI(this, tree, database);

        (new Thread(uiListeners)).start();
    }


    /**
     * Implements Runnable (for the rendering thread)
     */
    @Override
    public void run() {
        final int TEN_SECONDS = 10000;

        int frameCount = 0;  // Performance counter
        long baseTime = System.currentTimeMillis();

        // --------------------------------------
        // Grab the focus on startup so we don't have to click in the window
        // --------------------------------------
        this.requestFocus();

        // --------------------------------------
        // Run forever...
        // --------------------------------------
        while (running){
            render();

            // --------------------------------------
            // Performance statistics, logged every 10 seconds
            // --------------------------------------
            ++frameCount;
            long currentTime = System.currentTimeMillis();
            if ((currentTime - baseTime) > TEN_SECONDS){
                baseTime += TEN_SECONDS;
                System.out.println("Frames/Second: " + frameCount/10);
                frameCount = 0;
            }
        }
    }


    /**
     * Create and render one image frame.
     */
    public void render(){

        BufferStrategy bs = this.getBufferStrategy();
        if(bs == null){
            createBufferStrategy(2);
            // Try again next pass
            return;
        }

        Graphics g = bs.getDrawGraphics();

        g.drawImage(createImg(), 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT, java.awt.Color.BLACK, null);

        g.dispose();

        bs.show();
    }

    /**
     * Entry point for the brick editor.
     * Does very little except create the BrickEditor instance and fire up the
     * window we are rendering into.
     *
     * @param args
     */
    public static void main(String[] args) {

        BrickEditor editor = new BrickEditor();

        JFrame frame = new JFrame();

        // Add the editor as the sole component of the Frame
        frame.add(editor);

        // Various attributes
        frame.setTitle(TITLE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);

        // Manage the layout
        frame.pack();

        // Fire it up!
        editor.startUI();
    }

    /**
     * Raycast into the VoxTree to generate a pixel image that represents what
     * we see from this specific viewpoint.
     *
     * TODO: Lighting model, etc
     *
     * @return  BufferedImage object suitable for framing.  Or rendering to the canvas.
     */
    public BufferedImage createImg(){

        // --------------------------------------
        // Get the backing pixels
        // --------------------------------------
        int[] pixels = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();

        // --------------------------------------
        // Only get the viewpoint from the UI manager if it has already been allocated
        // (race condition)
        // --------------------------------------
        if (uiListeners != null) uiListeners.getView(WIDTH, HEIGHT, DEPTH, viewPoint, ltVec, upVec, fwVec, topLeft);

        // --------------------------------------
        // Picking ray in the center of the canvas
        // --------------------------------------
        tree.castRay(viewPoint, fwVec, true);

        // --------------------------------------
        // Scan the entire view canvas and cast a ray from the viewpoint through each canvas point
        // to determine that pixel's color
        // --------------------------------------
        int x;
        column0.set(topLeft);
        for(int i = 0; i < pixels.length;i++){
            x = i % WIDTH;  // Wrap X ordinate; just used as a counter
            if (x == 0){
                at.set(column0);
                column0.sub(upVec); // Advance Y position
            }

            // Ray from viewPoint to pixel position
            facing.sub(at, viewPoint);
            facing.normalize();

            pixels[i] = tree.castRay(viewPoint, facing, false);

            at.sub(ltVec);  // Advance X position
        }

        return img;
    }

}