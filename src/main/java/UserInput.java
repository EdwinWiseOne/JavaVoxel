
import com.simreal.VoxEngine.Color;
import com.simreal.VoxEngine.Path;
import com.simreal.VoxEngine.VoxTree;

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


public class UserInput implements Runnable, KeyListener, MouseListener, MouseMotionListener {

    private static UserInput userInput;

    private Canvas canvas;
    private VoxTree tree;
    private Robot robot;

    private boolean running;
    private long time;

    private volatile double heading;
    private volatile double elevation;
    private Point3d viewPoint;
    private Vector3d fwVec;
    private Vector3d ltVec;
    private Vector3d upVec;

    private int movement;


    private java.awt.Color selectedColor = java.awt.Color.CYAN;

    static final int MOVE_FORWARDS      = 0x0001;
    static final int MOVE_BACKWARDS     = 0x0002;
    static final int MOVE_LEFT          = 0x0004;
    static final int MOVE_RIGHT         = 0x0008;
    static final int MOVE_UP            = 0x0010;
    static final int MOVE_DOWN          = 0x0020;

    static final int MOVE_FAST          = 0x1000;
    static final int MOVE_SLOW          = 0x2000;

    // Singleton, factory
    public static UserInput getUI(Canvas canvas, VoxTree tree){
        if (userInput == null) userInput = new UserInput(canvas, tree);

        return userInput;
    }

    private UserInput(Canvas canvas, VoxTree tree){
        try {
            robot = new Robot();
        } catch (AWTException e1) {
            e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        this.canvas = canvas;
        this.tree = tree;
        canvas.addKeyListener(this);
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);

        heading = Math.toRadians(90.0);
        elevation = 0.0;

        viewPoint = new Point3d(0.0, 2.0, 0.0);
        fwVec = new Vector3d();
        ltVec = new Vector3d();
        upVec = new Vector3d();

heading = 1.5306;
elevation = -0.7138;
viewPoint.set(30, 210, -206);

        time = System.currentTimeMillis();

        running = false;
    }

    @Override
    public void run() {
        // TODO: Shut down thread in an orderly manner, running=false somewhere
        running = true;

        while (running){
            try {
                Thread.sleep(16);

                double cosElevation = Math.cos(elevation);
                fwVec.set(Math.cos(heading)*cosElevation, Math.sin(elevation), Math.sin(heading)*cosElevation);
                ltVec.cross(fwVec, new Vector3d(0, 1, 0));
                upVec.cross(ltVec, fwVec);

                double speed = 1.0;
                if ((movement & MOVE_FAST) != 0)        speed *= 10.0;
                if ((movement & MOVE_SLOW) != 0)        speed *= 0.25;

                Point3d prevPoint = new Point3d(viewPoint);

                if ((movement & MOVE_FORWARDS) != 0)    viewPoint.scaleAdd(speed, fwVec, viewPoint);
                if ((movement & MOVE_BACKWARDS) != 0)   viewPoint.scaleAdd(-speed, fwVec, viewPoint);
                if ((movement & MOVE_LEFT) != 0)        viewPoint.scaleAdd(speed, ltVec, viewPoint);
                if ((movement & MOVE_RIGHT) != 0)       viewPoint.scaleAdd(-speed, ltVec, viewPoint);
                if ((movement & MOVE_UP) != 0)          viewPoint.scaleAdd(speed, upVec, viewPoint);
                if ((movement & MOVE_DOWN) != 0)        viewPoint.scaleAdd(-speed, upVec, viewPoint);

                Point3i voxPoint = new Point3i((int)viewPoint.x, (int)viewPoint.y, (int)viewPoint.z);
                if (tree.testVoxelPoint(voxPoint) != 0L) {
                    viewPoint.set(prevPoint);
                    // TODO: Refine the voxPoint that was undoubtedly split
                }

            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }

    public synchronized void getView(int width, int height, int depth, Point3d viewPoint, Vector3d ltVec, Vector3d upVec, Vector3d fwVec, Point3d topLeft){

        viewPoint.set(this.viewPoint);
        fwVec.set(this.fwVec);
        ltVec.set(this.ltVec);
        upVec.set(this.upVec);

        Point3d center = new Point3d();
        center.scaleAdd(depth, fwVec, viewPoint);

        topLeft.scaleAdd(width >> 1, ltVec, center);
        topLeft.scaleAdd(height >> 1, upVec, topLeft);

        /*
        System.out.println("=============================");
        System.out.println("viewPoint " + viewPoint);
        System.out.println("heading " + ((int)Math.toDegrees(heading)));
        System.out.println("elevation " + ((int)Math.toDegrees(elevation)));

        System.out.println("fwVec " + fwVec);
        System.out.println("ltVec " + ltVec);
        System.out.println("upVec " + upVec);

        System.out.println("center " + center);
        System.out.println("topLeft " + topLeft);
        */
    }



    public void keyPressed(KeyEvent e){
        //System.out.println("Pressed " + e.getKeyCode() + ", " + e.getKeyChar());
        switch (e.getKeyCode()) {
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
            case KeyEvent.VK_CONTROL:
                movement |= MOVE_SLOW;
                break;
        }
    }


    public void keyReleased(KeyEvent e){
        //System.out.println("Released " + e.getKeyCode());
        switch (e.getKeyCode()) {
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
            case KeyEvent.VK_CONTROL:
                movement &= ~MOVE_SLOW;
                break;
        }
    }

    public void keyTyped(KeyEvent e){
        switch (e.getKeyChar()) {
            case 'i':
                JFrame guiFrame = new JFrame();
                selectedColor = JColorChooser.showDialog(guiFrame, "Pick a Color", selectedColor);
                long color = Color.setColor(selectedColor);
                System.out.println(Color.toString(color) +
                        " @ 1%: " + Color.toString(Color.illuminate(color, 0.01)) +
                        " 50% : " + Color.toString(Color.illuminate(color, 0.5)) +
                        " 100% : " + Color.toString(Color.illuminate(color, 1.0))
                );
                break;
            case 's':
                tree.save("Test");
                break;
            case '?':
                int test = 1;
                break;
        }
    }

    public void mouseEntered(MouseEvent e){

    }

    public void mouseExited(MouseEvent e){

    }

    public void mousePressed(MouseEvent e){

    }

    public void mouseReleased(MouseEvent e){

    }

    public void mouseClicked(MouseEvent e){
        long path = tree.pickNodePath;
        if (e.getButton() == MouseEvent.BUTTON3){
            tree.setVoxelPath(path, 0);
        } else if (e.getButton() == MouseEvent.BUTTON1){
            Point3i center = Path.toPosition(tree.pickNodePath, tree.edgeLength());
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
            tree.setVoxelPoint(center, (int)Color.setColor(selectedColor));
        }
    }

    public void mouseDragged(MouseEvent e){

    }

    public void mouseMoved(MouseEvent e){
        // Canvas width/height may be different than the calculateView width/height (bitmap scaling)
        int cx = canvas.getWidth() / 2;
        int cy = canvas.getHeight() / 2;

        double dx, dy;
        dx = cx - e.getX();
        dy = e.getY() - cy;

        int mouseClip = 30;
        double mouseScale = 0.05;

        if (Math.abs(dx) > mouseClip) dx = (mouseClip * Math.signum(dx));
        if (Math.abs(dy) > mouseClip) dy = (mouseClip * Math.signum(dy));

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

        Point topLeft = canvas.getLocationOnScreen();
        robot.mouseMove(cx + topLeft.x, cy + topLeft.y);
    }

}