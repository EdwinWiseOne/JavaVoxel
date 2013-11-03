import com.simreal.VoxEngine.Database;
import com.simreal.VoxEngine.Material;
import com.simreal.VoxEngine.Path;
import com.simreal.VoxEngine.VoxTree;
import com.simreal.VoxEngine.brick.BrickFactory;

import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.vecmath.Point3d;
import javax.vecmath.Point3i;
import javax.vecmath.Vector3d;
import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * Processes user input via keyboard and mouse.
 *
 * USER INPUT SCHEMA
 *
 * KEYBOARD
 *
 * Ctrl-*   BrickFactory commands
 *
 * Alt-I    Color menu
 * Alt-S    Save current brick
 * Alt-L    Load into current brick
 *
 * Shift    Fast movement
 *
 * A        Move Left
 * C        Move Down
 * D        Move Right
 * E        Move Up
 * S        Move Backwards
 * W        Move Forwards
 *
 * MOUSE
 *
 * Click Left   Set Voxel to Color
 * Click Right  Erase Voxel
 * Mouse Move   Change Heading
 */
public class UserInput implements Runnable, KeyListener, MouseListener, MouseMotionListener {

    // --------------------------------------
    // Singleton instance
    // --------------------------------------
    private static UserInput _userInputInstance = null;
    // --------------------------------------

    // Core properties
    private Canvas canvas;
    private VoxTree tree;
    private Database storage;
    private BrickFactory factory;

    // Interface Robot
    private Robot robot;

    // Execution support
    private long time;
    private boolean running;

    // Viewpoint details
    private volatile double heading;
    private volatile double elevation;
    private Point3d viewPoint;
    private Vector3d fwVec;
    private Vector3d ltVec;
    private Vector3d upVec;

    // Material (inventory) state
    private java.awt.Color selectedColor = java.awt.Color.BLACK;
    private long selectedMaterial = 0L;

    // Movement state and flags
    private int movement;
    static final int MOVE_FORWARDS      = 0x0001;
    static final int MOVE_BACKWARDS     = 0x0002;
    static final int MOVE_LEFT          = 0x0004;
    static final int MOVE_RIGHT         = 0x0008;
    static final int MOVE_UP            = 0x0010;
    static final int MOVE_DOWN          = 0x0020;

    static final int MOVE_FAST          = 0x1000;

    /**
     * Gets the UI instance.
     * Singleton Factory
     *
     * @param canvas    Canvas we are drawing upon
     * @param tree      VoxTree with rendering data
     * @return          The one instance of UserInput (constructed during the first call)
     */
    public static UserInput instance(Canvas canvas, VoxTree tree, Database storage, BrickFactory factory){
        if (_userInputInstance == null) _userInputInstance = new UserInput(canvas, tree, storage, factory);

        return _userInputInstance;
    }

    /**
     * Private constructor, used by the getUI Factory.
     *
     * @param canvas    Canvas we are drawing upon
     * @param tree      VoxTree with rendering data
     * @param storage   Database backing store for load and save
     */
    private UserInput(Canvas canvas, VoxTree tree, Database storage, BrickFactory factory){

        // --------------------------------------
        // Current and long term backing data
        // --------------------------------------
        this.tree = tree;
        this.storage = storage;
        this.factory = factory;

        // --------------------------------------
        // Robot is used to capture the mouse cursor into the middle of the canvas.
        // --------------------------------------
        try {
            robot = new Robot();
        } catch (AWTException e1) {
            e1.printStackTrace();
        }

        // --------------------------------------
        // Canvas and UI listeners
        // --------------------------------------
        this.canvas = canvas;
        canvas.addKeyListener(this);
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);

        // --------------------------------------
        // Put our viewpoint at an auspicious position for viewing the brick.
        // --------------------------------------
        heading = Math.toRadians(90.0);
        elevation = Math.toRadians(-20.0);
        int edgeLength = tree.edgeLength();
        viewPoint = new Point3d(edgeLength / 2.0, edgeLength * 1.5, -edgeLength * 3.0);

        fwVec = new Vector3d();
        ltVec = new Vector3d();
        upVec = new Vector3d();

        // --------------------------------------
        // Heartbeat
        // --------------------------------------
        time = System.currentTimeMillis();

        // --------------------------------------
        // Not running... yet
        // --------------------------------------
        running = false;
    }

    /**
     * Implementing Runnable (for the UI thread)
     *
     * Control viewpoint movement on the heartbeat.
     */
    @Override
    public void run() {

        // TODO: Shut down thread in an orderly manner, running=false somewhere. Capture shutdown signal, etc?
        running = true;

        while (running){
            try {
                // --------------------------------------
                // ~60 updates a second
                // --------------------------------------
                Thread.sleep(16);

                // --------------------------------------
                // Update view curlScale
                // --------------------------------------
                double cosElevation = Math.cos(elevation);
                fwVec.set(Math.cos(heading)*cosElevation, Math.sin(elevation), Math.sin(heading)*cosElevation);
                ltVec.cross(fwVec, new Vector3d(0, 1, 0));
                upVec.cross(ltVec, fwVec);

                // --------------------------------------
                // Update position movement
                // --------------------------------------
                double speed = 1.0;
                if ((movement & MOVE_FAST) != 0)        speed *= 10.0;

                Point3d prevPoint = new Point3d(viewPoint);
                if ((movement & MOVE_FORWARDS) != 0)    viewPoint.scaleAdd(speed, fwVec, viewPoint);
                if ((movement & MOVE_BACKWARDS) != 0)   viewPoint.scaleAdd(-speed, fwVec, viewPoint);
                if ((movement & MOVE_LEFT) != 0)        viewPoint.scaleAdd(speed, ltVec, viewPoint);
                if ((movement & MOVE_RIGHT) != 0)       viewPoint.scaleAdd(-speed, ltVec, viewPoint);
                if ((movement & MOVE_UP) != 0)          viewPoint.scaleAdd(speed, upVec, viewPoint);
                if ((movement & MOVE_DOWN) != 0)        viewPoint.scaleAdd(-speed, upVec, viewPoint);

                // --------------------------------------
                // Collision test: If the viewpoint goes into a voxel, bounce it back to where it was
                // --------------------------------------
                // TODO: Incorporate movement momentum, so the bounce actually bounces
                Point3i voxPoint = new Point3i((int)viewPoint.x, (int)viewPoint.y, (int)viewPoint.z);
                if (tree.testVoxelPoint(voxPoint) != 0L) {
                    viewPoint.set(prevPoint);
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Copies the various viewing parameters into the provided buffers.  Synchronized so it is thread-safe.
     *
     * @param width     Canvas width in pixels
     * @param height    Canvas height in pixels
     * @param depth     Viewpoint Depth to get FOV across the width, calculated as width / tan(FOV / 2)
     * @param viewPoint Position in space of the viewpoint.
     * @param ltVec     Unit vector pointing left from the viewpoint
     * @param upVec     Unit vector pointing up from the viewpoint
     * @param fwVec     Unit vector pointing up forwards (eyeline) from the viewpoint
     * @param topLeft   Position of top-left corner of the canvas in world coordinates
     */
    public synchronized void getView(
            int width,
            int height,
            int depth,
            Point3d viewPoint,
            Vector3d ltVec,
            Vector3d upVec,
            Vector3d fwVec,
            Point3d topLeft){

        viewPoint.set(this.viewPoint);
        fwVec.set(this.fwVec);
        ltVec.set(this.ltVec);
        upVec.set(this.upVec);

        Point3d center = new Point3d();
        center.scaleAdd(depth, fwVec, viewPoint);

        topLeft.scaleAdd(width >> 1, ltVec, center);
        topLeft.scaleAdd(height >> 1, upVec, topLeft);
    }


    /**
     *  Processes the key-down event.  Held keys are used for movement, and key down is also
     *  used to trigger keys (rather than the somewhat iffier to use keyTyped event)
     *
     * The result of this method is to change the state of the system in some way.
     *
     * @param key   Key event to process
     */
    public void keyPressed(KeyEvent key){
        if (key.isControlDown()) {

            // --------------------------------------
            // Control key events go to the brick factory
            // --------------------------------------
            switch (key.getKeyCode()) {
                default:
                    factory.keyPressed(key, tree);
                    break;
            }
        } else if (key.isAltDown()) {
            // --------------------------------------
            // Alt keys (triggering commands)
            // --------------------------------------
            switch (key.getKeyCode()) {
                // Inventory - just the material color at this point, with preset albedo and reflectance
                case KeyEvent.VK_I:
                    JFrame guiFrame = new JFrame();
                    selectedColor = JColorChooser.showDialog(guiFrame, "Pick a Material", selectedColor);
                    selectedMaterial = Material.setMaterial(selectedColor, 128, 32);
                    break;

                // Save the current brick
                case KeyEvent.VK_S:
                    storage.putBrick(factory.name(), tree.tilePool().compress(), factory);
                    break;

                // Load into the current brick
                case KeyEvent.VK_L:
                    storage.getBrick(factory.name(), tree.tilePool(), factory);
                    break;
            }

        } else {
            // --------------------------------------
            // Movement Keys (held)
            // --------------------------------------
            switch (key.getKeyCode()) {
                case KeyEvent.VK_W:
                    movement |= MOVE_FORWARDS;
                    movement &= ~MOVE_BACKWARDS;
                    break;
                case KeyEvent.VK_S:
                    movement |= MOVE_BACKWARDS;
                    movement &= ~MOVE_FORWARDS;
                    break;
                case KeyEvent.VK_A:
                    movement |= MOVE_LEFT;
                    movement &= ~MOVE_RIGHT;
                    break;
                case KeyEvent.VK_D:
                    movement |= MOVE_RIGHT;
                    movement &= ~MOVE_LEFT;
                    break;
                case KeyEvent.VK_E:
                    movement |= MOVE_UP;
                    movement &= ~MOVE_DOWN;
                    break;
                case KeyEvent.VK_C:
                    movement |= MOVE_DOWN;
                    movement &= ~MOVE_UP;
                    break;
                case KeyEvent.VK_SHIFT:
                    movement |= MOVE_FAST;
                    break;
            }
        }
    }

    /**
     * Process the key-up event, used to disable any held action flags
     *
     * @param key
     */
    public void keyReleased(KeyEvent key){
        if (key.isControlDown()) {
            // --------------------------------------
            // No held keys to release for Control keys
            // --------------------------------------

        } else if (key.isAltDown()) {
            // --------------------------------------
            // No held keys to release for Alt keys
            // --------------------------------------

        } else {

            // --------------------------------------
            // Release movement keys
            // --------------------------------------
            switch (key.getKeyCode()) {
                case KeyEvent.VK_W:
                    movement &= ~MOVE_FORWARDS;
                    break;
                case KeyEvent.VK_S:
                    movement &= ~MOVE_BACKWARDS;
                    break;
                case KeyEvent.VK_A:
                    movement &= ~MOVE_LEFT;
                    break;
                case KeyEvent.VK_D:
                    movement &= ~MOVE_RIGHT;
                    break;
                case KeyEvent.VK_E:
                    movement &= ~MOVE_UP;
                    break;
                case KeyEvent.VK_C:
                    movement &= ~MOVE_DOWN;
                    break;
                case KeyEvent.VK_SHIFT:
                    movement &= ~MOVE_FAST;
                    break;
            }
        }
    }

    public void keyTyped(KeyEvent e){
    }

    public void mouseEntered(MouseEvent e){

    }

    public void mouseExited(MouseEvent e){

    }

    public void mousePressed(MouseEvent e){

    }

    public void mouseReleased(MouseEvent e){

    }

    /**
     * Process a mouse click (any of the various buttons), acting upon the VoxTree model element in view.
     *
     * @param mouse     Mouse click event to process
     */
    public void mouseClicked(MouseEvent mouse){

        // --------------------------------------
        // Get the path to the current highlighted node; if zero, nothing is in range so exit
        // The Path holds the information about not only this node, but all parents to it, as
        // well as encoding the position of the voxel in world space.
        // --------------------------------------
        long path = tree.pickNodePath;
        if (path == 0L) return;

        if (mouse.getButton() == MouseEvent.BUTTON1){
            // --------------------------------------
            // Left Button is for creating...
            // --------------------------------------

            // Get the world position that corresponds to this path...
            Point3i center = Path.toPosition(tree.pickNodePath, tree.edgeLength());
            // ... and offset from the node in the direction of the selected facet of that node
            switch (tree.pickFacet) {
                case VoxTree.XY_PLANE:
                    center.add(new Point3i(0, 0, -(int)Math.copySign(tree.stride(), tree.pickRay.z)));
                    break;
                case VoxTree.YZ_PLANE:
                    center.add(new Point3i(-(int)Math.copySign(tree.stride(), tree.pickRay.x), 0, 0));
                    break;
                case VoxTree.XZ_PLANE:
                    center.add(new Point3i(0, -(int)Math.copySign(tree.stride(), tree.pickRay.y), 0));
                    break;
            }

            // Now create a new leaf voxel in the empty space adjacent to that selected voxel facet
            tree.setVoxelPoint(center, selectedMaterial);

        } else if (mouse.getButton() == MouseEvent.BUTTON3){
            // --------------------------------------
            // Right button is destructing...
            // --------------------------------------

            // Set the selected voxel to zero, which eliminates it (empty space)
            tree.setVoxelPath(path, 0);
        }

    }

    public void mouseDragged(MouseEvent e){

    }

    /**
     * Process a mouse motion, which changes the view curlScale
     *
     * @param mouse     Mouse motion event to process
     */
    public void mouseMoved(MouseEvent mouse){

        // --------------------------------------
        // Mouse events are relative to the canvas size.  Note that the canvas width and height may be
        // different from the view width/height to allow for bit scaling, so we may calculate fewer pixels
        // than we are displaying.
        // --------------------------------------
        int cx = canvas.getWidth() >> 1;
        int cy = canvas.getHeight() >> 1;

        // --------------------------------------
        // Mouse motion from canvas center
        // --------------------------------------
        double dx, dy;
        dx = cx - mouse.getX();
        dy = mouse.getY() - cy;

        // --------------------------------------
        // Constrain and inputScale the motion
        // --------------------------------------
        int mouseClip = 30;
        double mouseScale = 0.05;

        if (Math.abs(dx) > mouseClip) dx = (mouseClip * Math.signum(dx));
        if (Math.abs(dy) > mouseClip) dy = (mouseClip * Math.signum(dy));

        // --------------------------------------
        // Update the heading and elevation
        // --------------------------------------
        if ( (dx != 0) || (dy != 0) ){
            double twopi = Math.PI * 2.0;

            if (dx != 0){
                heading += Math.toRadians(dx * mouseScale);
                while (heading < 0) heading += twopi;
                while (heading > twopi) heading -= twopi;
            }

            if (dy != 0){
                elevation += Math.toRadians(dy * mouseScale);
                elevation = Math.max(Math.toRadians(-80), Math.min(Math.toRadians(80), elevation));
            }
        }

        // --------------------------------------
        // Capture and lock the mouse to the center of the canvas
        // --------------------------------------
        Point topLeft = canvas.getLocationOnScreen();
        robot.mouseMove(cx + topLeft.x, cy + topLeft.y);
    }
}