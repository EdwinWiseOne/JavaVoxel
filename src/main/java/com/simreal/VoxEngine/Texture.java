package com.simreal.VoxEngine;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Texture generation, based on Perlin noise, founded on SimplexNoise as implemented by
 * http://webstaff.itn.liu.se/~stegu/simplexnoise/SimplexNoise.java.
 *
 * This code was placed in the public domain by its original author,
 * Stefan Gustavson. You may use it as you see fit, but
 * attribution is appreciated.
 *
 * Based on example code by Stefan Gustavson (stegu@itn.liu.se).
 * Optimisations by Peter Eastman (peastman@drizzle.stanford.edu).
 * Better rank ordering method by Stefan Gustavson in 2012.
 *
 * The raw noise is in the range of (-1..+1); raw noise is only internal to the Texture process.
 * Value transformations convert the raw noise to a value from (0..1), and this can be converted
 * to [0..255] via the toByte function.
 */
public class Texture {

    static final Logger LOG = LoggerFactory.getLogger(Texture.class.getName());

    // --------------------------------------
    // Texture transform control flags
    // --------------------------------------
    /** Reflect the raw noise value via Math.fabs  */
    public static final int REFLECT     = 0x01;
    /** Raise the raw noise value to the power of 2 */
    public static final int SQUARE      = 0X02;
    /** Curl the raw noise by passing it through Math.cos. Uses {@link @curlScale} to inputScale the curl. */
    public static final int CURL        = 0x04;
    /** Clamp the output to be near the indicated {@link #band} value */
    public static final int BANDCLAMP   = 0x08;
    /** Invert the output, shifting it from (0..1) to (1..0) */
    public static final int INVERT      = 0x20;
    /** Quantize the output, forcing it into the number of values specified by {@link @quantNum} */
    public static final int QUANT       = 0x80;

    // --------------------------------------
    // Texture control parameters (defaulted)
    // --------------------------------------
    /** Transformation control bitflag */
    public int transform = 0x00;
    /** Input scaling factor, scales the resulting noise */
    public double inputScale = 1.0;
    /** Curl scaling factor, scaling the input to cosine.  Curl processes the Y or Z ordinate. */
    public double curlScale = 1.0;
    /** Value band to constrain the output to, in the range (0..1) */
    public double band = 0.0;
    /** Number of output values to quantize to */
    public int quantNum = 0;

    /**
     * Scales the texture input w from (0..1) to [0..255]
     *
     * @param w     Input noise value in (0..1)
     * @return      Byte value in [0..255]
     */
    public static int toByte(double w) {
        return (int)(w * 256);
    }

    /**
     * Calculate the transformed texture value for the given position in X,Y space
     *
     * @param x     X ordinate in texture space (scaled by {@link #inputScale})
     * @param y     Y ordinate in texture space (scaled by {@link #inputScale})
     * @return      Texture value in (0..1)
     */
    public double value(double x, double y) {
        return xform(y, noise(x * inputScale, y * inputScale));
    }

    /**
     * Calculate the transformed texture value for the given position in X,Y,Z space
     *
     * @param x    X ordinate in texture space (scaled by {@link #inputScale})
     * @param y    Y ordinate in texture space (scaled by {@link #inputScale})
     * @param z    Z ordinate in texture space (scaled by {@link #inputScale})
     * @return      Texture value in (0..1)
     */
    public double value(double x, double y, double z) {
        return xform(z, noise(x * inputScale, y * inputScale, z * inputScale));
    }

    /**
     * Apply the transforms flagged in {@link @transform} to the raw noise value in (-1..+1),
     * returning the transformed texture value in (0..1).  Certain transformations (e.g. {@link #CURL}
     * use a key ordinate to control the transform.
     *
     * @param key    Key value for certain transforms
     * @param w      Raw noise value in (-1 .. +1)
     * @return       Transformed noise value in (0..1)
     */
    public double xform(double key, double w) {
        // --------------------------------------
        // w is (-1 .. +1)
        // --------------------------------------
        if ((transform & CURL) != 0) {
            double twist = curlScale * 45.0;
            w = Math.cos(Math.toRadians(key/twist + w*twist));
        }

        if ((transform & SQUARE) != 0) {
            w *= w;
        }

        if ((transform & REFLECT) != 0) {
            w = fastfabs(w);
        }

        // --------------------------------------
        // w to the (0..1) range now
        // --------------------------------------
        if ((transform & (REFLECT | SQUARE)) == 0) {
            w = (w + 1.0) * 0.5;
        }

        if ((transform & BANDCLAMP) != 0) {
            double dw = fastfabs(w - band);
            double t = Math.cos(dw * Math.PI);
            w = Math.pow(t, 10);
       }

        if ((transform & QUANT) != 0) {
            w = (double)((int)(w * quantNum)) / (double) quantNum;
        }

        if ((transform & INVERT) != 0) {
            w = 1.0 - w;
        }

        return w;
    }

    // --------------------------------------
    // Inner class to speed up gradient computations
    // (array access is apparently a lot slower than member access)
    // --------------------------------------
    private static class Gradient
    {
        double x, y, z, w;

        Gradient(double x, double y, double z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Gradient(double x, double y, double z, double w)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }
    }

    private static Gradient _gradient3[] = {new Gradient(1,1,0),new Gradient(-1,1,0),new Gradient(1,-1,0),new Gradient(-1,-1,0),
            new Gradient(1,0,1),new Gradient(-1,0,1),new Gradient(1,0,-1),new Gradient(-1,0,-1),
            new Gradient(0,1,1),new Gradient(0,-1,1),new Gradient(0,1,-1),new Gradient(0,-1,-1)};

    private static Gradient _gradient4[]= {new Gradient(0,1,1,1),new Gradient(0,1,1,-1),new Gradient(0,1,-1,1),new Gradient(0,1,-1,-1),
            new Gradient(0,-1,1,1),new Gradient(0,-1,1,-1),new Gradient(0,-1,-1,1),new Gradient(0,-1,-1,-1),
            new Gradient(1,0,1,1),new Gradient(1,0,1,-1),new Gradient(1,0,-1,1),new Gradient(1,0,-1,-1),
            new Gradient(-1,0,1,1),new Gradient(-1,0,1,-1),new Gradient(-1,0,-1,1),new Gradient(-1,0,-1,-1),
            new Gradient(1,1,0,1),new Gradient(1,1,0,-1),new Gradient(1,-1,0,1),new Gradient(1,-1,0,-1),
            new Gradient(-1,1,0,1),new Gradient(-1,1,0,-1),new Gradient(-1,-1,0,1),new Gradient(-1,-1,0,-1),
            new Gradient(1,1,1,0),new Gradient(1,1,-1,0),new Gradient(1,-1,1,0),new Gradient(1,-1,-1,0),
            new Gradient(-1,1,1,0),new Gradient(-1,1,-1,0),new Gradient(-1,-1,1,0),new Gradient(-1,-1,-1,0)};

    // --------------------------------------
    // Permutation arrays
    // --------------------------------------
    private static short p[] = {151,160,137,91,90,15,
            131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,8,99,37,240,21,10,23,
            190, 6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,57,177,33,
            88,237,149,56,87,174,20,125,136,171,168, 68,175,74,165,71,134,139,48,27,166,
            77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,55,46,245,40,244,
            102,143,54, 65,25,63,161, 1,216,80,73,209,76,132,187,208, 89,18,169,200,196,
            135,130,116,188,159,86,164,100,109,198,173,186, 3,64,52,217,226,250,124,123,
            5,202,38,147,118,126,255,82,85,212,207,206,59,227,47,16,58,17,182,189,28,42,
            223,183,170,213,119,248,152, 2,44,154,163, 70,221,153,101,155,167, 43,172,9,
            129,22,39,253, 19,98,108,110,79,113,224,232,178,185, 112,104,218,246,97,228,
            251,34,242,193,238,210,144,12,191,179,162,241, 81,51,145,235,249,14,239,107,
            49,192,214, 31,181,199,106,157,184, 84,204,176,115,121,50,45,127, 4,150,254,
            138,236,205,93,222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180};
    // To remove the need for index wrapping, double the permutation table length to 1024
    static final int PERM_TABLE_LENGTH = 512;
    private static short perm[] = new short[PERM_TABLE_LENGTH];
    private static short permMod12[] = new short[PERM_TABLE_LENGTH];
    static {
        for(int i=0; i<PERM_TABLE_LENGTH; i++)
        {
            perm[i]=p[i & 0xFF];
            permMod12[i] = (short)(perm[i] % 12);
        }
    }

    // --------------------------------------
    // Skewing and unskewing factors for 2, 3, and 4 dimensions
    // --------------------------------------
    private static final double F2 = 0.5*(Math.sqrt(3.0)-1.0);
    private static final double G2 = (3.0-Math.sqrt(3.0))/6.0;
    private static final double F3 = 1.0/3.0;
    private static final double G3 = 1.0/6.0;

    // --------------------------------------
    // High-speed math functions
    // --------------------------------------

    /**
     * Finds the gives the largest integer that is less than or equal to the argument.
     *
     * @param x     Double value
     * @return      Integer that is less than x
     */
    private static int fastfloor(double x) {
        int xi = (int)x;
        return x<xi ? xi-1 : xi;
    }

    /**
     * Calculates 2 dimensional dot product of a gradient by x,y returning the cosine
     * of the angle between the vectors times the length of the vectors.
     *
     * @param g     Gradient, with x,y significant
     * @param x     X ordinate of second vector
     * @param y     Y ordinate of the second vector
     * @return      Result of g dot (x,y)
     */
    private static double dot(Gradient g, double x, double y) {
        return g.x*x + g.y*y;
    }

    /**
     * Calculates 3 dimensional dot product of a gradient by x,y,z returning the cosine
     * of the angle between the vectors times the length of the vectors.
     *
     * @param g     Gradient, with x,y,z significant
     * @param x     X ordinate of second vector
     * @param y     Y ordinate of the second vector
     * @param z     Z ordinate of the second vector
     * @return      Result of g dot (x,y,z)
     */
    private static double dot(Gradient g, double x, double y, double z) {
        return g.x*x + g.y*y + g.z*z;
    }

    /**
     * Calculate the absolute value of the argument.
     *
     * @param x     Signed double value
     * @return      Unsigned double result
     */
    private static double fastfabs(double x) {
        return (x < 0) ? -x : x;
    }

    /**
     * 2D simplex noise
     *
     * @param xin   X ordinate in noise space
     * @param yin   Y ordinate in noise space
     * @return      Noise value in the range (-1, 1)
     */
    public static double noise(double xin, double yin) {
        double n0, n1, n2; // Noise contributions from the three corners
        // Skew the input space to determine which simplex cell we're in
        double s = (xin+yin)*F2; // Hairy factor for 2D
        int i = fastfloor(xin+s);
        int j = fastfloor(yin+s);
        double t = (i+j)*G2;
        double X0 = i-t; // Unskew the cell origin back to (x,y) space
        double Y0 = j-t;
        double x0 = xin-X0; // The x,y distances from the cell origin
        double y0 = yin-Y0;
        // For the 2D case, the simplex shape is an equilateral triangle.
        // Determine which simplex we are in.
        int i1, j1; // Offsets for second (middle) corner of simplex in (i,j) coords
        if(x0>y0) {i1=1; j1=0;} // lower triangle, XY order: (0,0)->(1,0)->(1,1)
        else {i1=0; j1=1;}      // upper triangle, YX order: (0,0)->(0,1)->(1,1)
        // A step of (1,0) in (i,j) means a step of (1-c,-c) in (x,y), and
        // a step of (0,1) in (i,j) means a step of (-c,1-c) in (x,y), where
        // c = (3-sqrt(3))/6
        double x1 = x0 - i1 + G2; // Offsets for middle corner in (x,y) unskewed coords
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2; // Offsets for last corner in (x,y) unskewed coords
        double y2 = y0 - 1.0 + 2.0 * G2;
        // Work out the hashed gradient indices of the three simplex corners
        int ii = i & 255;
        int jj = j & 255;
        int gi0 = permMod12[ii+perm[jj]];
        int gi1 = permMod12[ii+i1+perm[jj+j1]];
        int gi2 = permMod12[ii+1+perm[jj+1]];
        // Calculate the contribution from the three corners
        double t0 = 0.5 - x0*x0-y0*y0;
        if(t0<0) n0 = 0.0;
        else {
            t0 *= t0;
            n0 = t0 * t0 * dot(_gradient3[gi0], x0, y0);  // (x,y) of _gradient3 used for 2D gradient
        }
        double t1 = 0.5 - x1*x1-y1*y1;
        if(t1<0) n1 = 0.0;
        else {
            t1 *= t1;
            n1 = t1 * t1 * dot(_gradient3[gi1], x1, y1);
        }
        double t2 = 0.5 - x2*x2-y2*y2;
        if(t2<0) n2 = 0.0;
        else {
            t2 *= t2;
            n2 = t2 * t2 * dot(_gradient3[gi2], x2, y2);
        }
        // Add contributions from each corner to get the final noise value.
        // The result is scaled to return values in the interval [-1,1].
        return 70.0 * (n0 + n1 + n2);
    }


    /**
     * 3D simplex noise
     *
     * @param xin   X ordinate in noise space
     * @param yin   Y ordinate in noise space
     * @param zin   Z ordinate in noise space
     * @return      Noise value in the range (-1, 1)
     */
    private static double noise(double xin, double yin, double zin) {
        double n0, n1, n2, n3; // Noise contributions from the four corners
        // Skew the input space to determine which simplex cell we're in
        double s = (xin+yin+zin)*F3; // Very nice and simple skew factor for 3D
        int i = fastfloor(xin+s);
        int j = fastfloor(yin+s);
        int k = fastfloor(zin+s);
        double t = (i+j+k)*G3;
        double X0 = i-t; // Unskew the cell origin back to (x,y,z) space
        double Y0 = j-t;
        double Z0 = k-t;
        double x0 = xin-X0; // The x,y,z distances from the cell origin
        double y0 = yin-Y0;
        double z0 = zin-Z0;
        // For the 3D case, the simplex shape is a slightly irregular tetrahedron.
        // Determine which simplex we are in.
        int i1, j1, k1; // Offsets for second corner of simplex in (i,j,k) coords
        int i2, j2, k2; // Offsets for third corner of simplex in (i,j,k) coords
        if(x0>=y0) {
            if(y0>=z0)
            { i1=1; j1=0; k1=0; i2=1; j2=1; k2=0; } // X Y Z order
            else if(x0>=z0) { i1=1; j1=0; k1=0; i2=1; j2=0; k2=1; } // X Z Y order
            else { i1=0; j1=0; k1=1; i2=1; j2=0; k2=1; } // Z X Y order
        }
        else { // x0<y0
            if(y0<z0) { i1=0; j1=0; k1=1; i2=0; j2=1; k2=1; } // Z Y X order
            else if(x0<z0) { i1=0; j1=1; k1=0; i2=0; j2=1; k2=1; } // Y Z X order
            else { i1=0; j1=1; k1=0; i2=1; j2=1; k2=0; } // Y X Z order
        }
        // A step of (1,0,0) in (i,j,k) means a step of (1-c,-c,-c) in (x,y,z),
        // a step of (0,1,0) in (i,j,k) means a step of (-c,1-c,-c) in (x,y,z), and
        // a step of (0,0,1) in (i,j,k) means a step of (-c,-c,1-c) in (x,y,z), where
        // c = 1/6.
        double x1 = x0 - i1 + G3; // Offsets for second corner in (x,y,z) coords
        double y1 = y0 - j1 + G3;
        double z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0*G3; // Offsets for third corner in (x,y,z) coords
        double y2 = y0 - j2 + 2.0*G3;
        double z2 = z0 - k2 + 2.0*G3;
        double x3 = x0 - 1.0 + 3.0*G3; // Offsets for last corner in (x,y,z) coords
        double y3 = y0 - 1.0 + 3.0*G3;
        double z3 = z0 - 1.0 + 3.0*G3;
        // Work out the hashed gradient indices of the four simplex corners
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        int gi0 = permMod12[ii+perm[jj+perm[kk]]];
        int gi1 = permMod12[ii+i1+perm[jj+j1+perm[kk+k1]]];
        int gi2 = permMod12[ii+i2+perm[jj+j2+perm[kk+k2]]];
        int gi3 = permMod12[ii+1+perm[jj+1+perm[kk+1]]];
        // Calculate the contribution from the four corners
        double t0 = 0.6 - x0*x0 - y0*y0 - z0*z0;
        if(t0<0) n0 = 0.0;
        else {
            t0 *= t0;
            n0 = t0 * t0 * dot(_gradient3[gi0], x0, y0, z0);
        }
        double t1 = 0.6 - x1*x1 - y1*y1 - z1*z1;
        if(t1<0) n1 = 0.0;
        else {
            t1 *= t1;
            n1 = t1 * t1 * dot(_gradient3[gi1], x1, y1, z1);
        }
        double t2 = 0.6 - x2*x2 - y2*y2 - z2*z2;
        if(t2<0) n2 = 0.0;
        else {
            t2 *= t2;
            n2 = t2 * t2 * dot(_gradient3[gi2], x2, y2, z2);
        }
        double t3 = 0.6 - x3*x3 - y3*y3 - z3*z3;
        if(t3<0) n3 = 0.0;
        else {
            t3 *= t3;
            n3 = t3 * t3 * dot(_gradient3[gi3], x3, y3, z3);
        }
        // Add contributions from each corner to get the final noise value.
        // The result is scaled to stay just inside [-1,1]
        return 32.0*(n0 + n1 + n2 + n3);
    }
}
