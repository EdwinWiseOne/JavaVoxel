
import javax.swing.*;
import javax.vecmath.Matrix3d;
import javax.vecmath.Point3d;
import javax.vecmath.Point3i;
import javax.vecmath.Vector3d;

import java.awt.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;

import com.simreal.VoxEngine.VoxTree;
import com.simreal.VoxEngine.Color;

public class Display extends Canvas implements Runnable {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public static final int WIDTH = 320;
    public static final int VIEW_WIDTH = WIDTH*3;
    public static final int HEIGHT = 240;
    public static final int VIEW_HEIGHT = HEIGHT*3;

    public static final int TREE_DEPTH = 4;

    private static final double H_FOV = Math.PI / 3.0;  // 60 degrees
    private static final int DEPTH = (int)(WIDTH / Math.tan(H_FOV * 0.5));
    private static final double spread = Math.sin(H_FOV / 2.0);

    private static final int imageType = BufferedImage.TYPE_INT_RGB;

    public static final String TITLE = "Title";

    private boolean running = false;
    private BufferedImage img;
    UserInput ui;

    private VoxTree tree;
    private int activeNode;

    public Display(){
        Random rand = new Random();
        img = new BufferedImage(WIDTH, HEIGHT, imageType);

        activeNode = 0;
        tree = new VoxTree(TREE_DEPTH);

        int stride = tree.stride();
        int offset = stride >> 1;

        // Floor of black
        for (int x=0; x<tree.edgeLength(); ++x){
            for (int y=0; y<tree.edgeLength(); ++y){
                tree.setVoxel(new Point3i((x*stride)+offset, 0, (y*stride)+offset), (int)Color.setColor(30,30,30,255));
            }
        }
        //Corner blue voxel
        tree.setVoxel(new Point3i(0, (1*stride)+offset, 0), (int) Color.setColor(0, 0, 192, 255));

        System.out.println("Spread: " + spread);
        System.out.println(tree);

    }

    private void start() {
        Thread thread;

        // TODO: Shut down thread in an orderly manner, running=false somewhere
        if(running)
            return;
        running = true;
        thread = new Thread(this);
        thread.start();

        ui = UserInput.getUI(this, tree);
        (new Thread(ui)).start();
    }


    @Override
    public void run() {
        int count = 0;
        long time = System.currentTimeMillis();

        this.requestFocus();

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

        g.drawImage(createImg(), 0, 0, VIEW_WIDTH, VIEW_HEIGHT, java.awt.Color.BLACK, null);

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

    public BufferedImage createImg(){
        int[] pixels = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();

        int x;

        Point3d viewPoint = new Point3d();
        Vector3d ltVec = new Vector3d();
        Vector3d upVec = new Vector3d();
        Vector3d fwVec = new Vector3d();
        Point3d topLeft = new Point3d();

        if (ui != null) ui.getView(WIDTH, HEIGHT, DEPTH, viewPoint, ltVec, upVec, fwVec, topLeft);

        Point3d column0 = new Point3d(topLeft);
        Point3d at = new Point3d();
        Vector3d facing = new Vector3d();

        tree.castRay(viewPoint, fwVec, true);

        for(int i = 0; i < pixels.length;i++){
            x = i % WIDTH;
            if (x == 0){
                at.set(column0);
                column0.sub(upVec);
            }

            facing.sub(at, viewPoint);
            facing.normalize();
            pixels[i] = (int)tree.castRay(viewPoint, facing, false);

            at.sub(ltVec);
        }

        return img;
    }

}