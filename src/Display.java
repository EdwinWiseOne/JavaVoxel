import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;

import javax.swing.JFrame;

import javax.vecmath.Point3d;
import javax.vecmath.Point3i;
import javax.vecmath.Vector3d;

public class Display extends Canvas implements Runnable, KeyListener {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    public static final int WIDTH = 320;
    public static final int VIEW_WIDTH = WIDTH*3;
    public static final int HEIGHT = 240;
    public static final int VIEW_HEIGHT = HEIGHT*3;
    private static final double ASPECT = (double)HEIGHT/(double)WIDTH;

    private static final double H_FOV = Math.PI / 3.0;  // 60 degrees
    private static final double V_FOV = H_FOV * ASPECT;
    private static final double VIEW_DIST = WIDTH / Math.tan(H_FOV * 0.5);

    private static final int imageType = BufferedImage.TYPE_INT_RGB;

    public static final String TITLE = "Title";

    private boolean running = false;
    private BufferedImage img;
    private Random rand;

    private Point3d pov;
    private double heading;
    private double elevation;

    private Vector3d fwVec;
    private Point3d topLeft;
    private Vector3d ltVec;
    private Vector3d upVec;

    private VoxTree tree;
    private int activeNode;

    public Display(){
        rand = new Random();
        img = new BufferedImage(WIDTH, HEIGHT, imageType);

        // TODO: don't use 256, but query the voxtree for its dimension
        pov = new Point3d(rand.nextInt(256), rand.nextInt(256), 64);
        heading = Math.toRadians(rand.nextInt(360));
        elevation = 0.0;
        activeNode = 0;


pov.set(127, 127, -256);
heading = Math.toRadians(90);
elevation = Math.toRadians(0);

        calculateView();


        tree = new VoxTree();

        int cx = 16;
        for (int vx=56; vx<=184; vx+=32){
            int cy = 16;
            for (int vy=56; vy<=184; vy+=32){
                tree.setVoxel(new Point3i(vx, vy, 63), (int) VoxTree.setColor(cx, cy, 255, 255));
                cy += 32;
            }
            cx += 32;
        }
        System.out.println(tree);

        addKeyListener(this);
    }

    private void calculateView(){

        double cosElevation = Math.cos(elevation);
        fwVec = new Vector3d(Math.cos(heading)*cosElevation, Math.sin(elevation), Math.sin(heading)*cosElevation);
        //fwVec.normalize();

        ltVec = new Vector3d();
        ltVec.cross(fwVec, new Vector3d(0, 1, 0));

        upVec = new Vector3d();
        upVec.cross(ltVec, fwVec);

        Point3d center = new Point3d();
        center.scaleAdd(VIEW_DIST, fwVec, pov);

        topLeft = new Point3d();
        topLeft.scaleAdd(WIDTH >> 1, ltVec, center);
        topLeft.scaleAdd(HEIGHT >> 1, upVec, topLeft);

        Point3d topRight = new Point3d();
        topRight.scaleAdd(-WIDTH, ltVec, topLeft);

        Point3d btmLeft = new Point3d();
        btmLeft.scaleAdd(-HEIGHT, upVec, topLeft);

        Point3d btmRight = new Point3d();
        btmRight.scaleAdd(-WIDTH, ltVec, btmLeft);

        /*
        System.out.println("POV: " + pov);
        System.out.println("Forward: " + fwVec);
        System.out.println("View Dist: " + VIEW_DIST);
        System.out.println("Left:    " + ltVec);
        System.out.println("Up:      " + upVec);
        System.out.println("Center:      " + center);
        System.out.println("TopLeft:     " + topLeft);
        System.out.println("TopRight:    " + topRight);
        System.out.println("BottomRight: " + btmRight);
        System.out.println("BottomLeft:  " + btmLeft);
        */


    }
    public void keyPressed(KeyEvent e){
        //System.out.println("Pressed " + e.getKeyCode());
    }


    public void keyReleased(KeyEvent e){
        //System.out.println("Released " + e.getKeyCode());
    }

    public void keyTyped(KeyEvent e){
        char c = e.getKeyChar();
        switch (c){
            case 'a':
                pov.x -= 1;
                break;
            case 'A':
                pov.x -= 10;
                break;
            case 'd':
                pov.x += 1;
                break;
            case 'D':
                pov.x += 10;
                break;
            case 'w':
                pov.z += 1;
                break;
            case 'W':
                pov.z += 10;
                break;
            case 's':
                pov.z -= 1;
                break;
            case 'S':
                pov.z -= 10;
                break;
            case 'q':
                heading += Math.toRadians(1);
                break;
            case 'e':
                heading -= Math.toRadians(1);
                break;
            case 'z':
                elevation += Math.toRadians(1);
                break;
            case 'x':
                elevation -= Math.toRadians(1);
                break;
        }
        calculateView();
    }

    private void start() {
        Thread thread;

        if(running)
            return;
        running = true;
        thread = new Thread(this);
        thread.start();

    }

    @Override
    public void run() {
        int count = 0;
        long time = System.currentTimeMillis();
        while (running){
            render();
            ++count;
            long newTime = System.currentTimeMillis();
            if ((newTime - time) > 1000){
                time += 1000;
                System.out.println("Frames/Second: " + count);
                count = 0;
            }
        }
    }


    public void render(){
        BufferStrategy bs = this.getBufferStrategy();
        if(bs == null){
            createBufferStrategy(2);
            return;
        }

        Graphics g = bs.getDrawGraphics();

        //g.drawImage(img, 0, 0, null);
        //g.drawImage(createImg(WIDTH, HEIGHT), 0, 0, null);
        g.drawImage(createImg(WIDTH, HEIGHT), 0, 0, VIEW_WIDTH, VIEW_HEIGHT, Color.BLACK, null);

        g.dispose();
        bs.show();

    }



    public static void main(String[] args) {
        Display rc = new Display();
        JFrame frame = new JFrame();
        frame.add(rc);
        frame.pack();
        frame.setTitle(TITLE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(VIEW_WIDTH, VIEW_HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);

        rc.start();
    }

    public BufferedImage createImg(int width, int height){
        int[] pixels = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();

        int x;

        Point3d column0 = new Point3d(topLeft);
        Point3d at = new Point3d();
        Vector3d facing = new Vector3d();

        int newActiveNode = (int)tree.walkRay((int)(pov.x+0.5), (int)(pov.y+0.5), (int)(pov.z+0.5), fwVec.x, fwVec.y, fwVec.z, true);
        if (activeNode != newActiveNode){
            activeNode = newActiveNode;
            System.out.println("Pick node " + activeNode);
        }

        tree.setHotNode(activeNode);

        for(int i = 0; i < pixels.length;i++){
            x = i % WIDTH;
            if (x == 0){
                at.set(column0);
                column0.sub(upVec);
            }
            double epsilon = .5;
            if ( (Math.abs(at.x - 100) < epsilon) && (Math.abs(at.y - 50) < epsilon)){
                int fred = 1;
            }

            facing.sub(at, pov);
            facing.normalize();
            pixels[i] = (int)tree.walkRay((int)(pov.x+0.5), (int)(pov.y+0.5), (int)(pov.z+0.5), facing.x, facing.y, facing.z, false);

            at.sub(ltVec);
        }

        return img;
    }

}