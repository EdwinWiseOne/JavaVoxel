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

// TODO: Clone BrickEditor, add Load and Save, and make it into a Brick Editor
// TODO: Load bricks from a brick file, and render them at the end of the path (separate pathing for bricks)
public class BrickEditor extends Canvas implements Runnable {
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

    public BrickEditor(){
        img = new BufferedImage(WIDTH, HEIGHT, imageType);

        activeNode = 0;
        tree = new VoxTree(TREE_DEPTH);

        int stride = tree.stride();
        int offset = stride >> 1;

        // Floor of black
        int value;
        for (int x=0; x<tree.breadth(); ++x){
            for (int y=0; y<tree.breadth(); ++y){
                value = (y * tree.breadth()) + x;
                tree.setVoxelPoint(new Point3i((x*stride)+offset, 0, (y*stride)+offset), (long) Material.setMaterial(255, 255, 255, 255-value, value, value));
            }
        }

        System.out.println("Spread: " + spread);
//        System.out.println(tree);

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
            if ((newTime - time) > 10000){
                time += 10000;
                System.out.println("Frames/Second: " + count/10);
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
        BrickEditor rc = new BrickEditor();
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