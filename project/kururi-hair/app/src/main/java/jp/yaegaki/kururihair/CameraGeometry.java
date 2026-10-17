package jp.yaegaki.kururihair;

/** Geometry only: testable on a JVM without Android camera hardware. */
public final class CameraGeometry {
    private CameraGeometry() {}

    public static final class Preview {
        public final float scaleX,scaleY;
        public final int rotationDegrees;
        private Preview(float scaleX,float scaleY,int rotationDegrees) {
            this.scaleX=scaleX;this.scaleY=scaleY;this.rotationDegrees=rotationDegrees;
        }
    }

    /**
     * TextureView already rotates the buffer for SENSOR_ORIENTATION (and handles
     * the front-camera producer transform). Its default scale stretches that
     * upright buffer to the entire view. Undo only the stretch, center-crop at
     * one uniform pixel scale, and compensate only the DISPLAY rotation.
     * Do not apply SENSOR_ORIENTATION as another view rotation.
     *
     * Android behavior: https://developer.android.com/media/camera/camera2/camera-preview#textureview
     */
    public static Preview preview(int sensorDegrees,int displayDegrees,
            int bufferWidth,int bufferHeight,int viewWidth,int viewHeight) {
        if(bufferWidth<=0||bufferHeight<=0||viewWidth<=0||viewHeight<=0)
            throw new IllegalArgumentException("Camera and view sizes must be positive");
        int sensor=quadrant(sensorDegrees),display=quadrant(displayDegrees);
        boolean sensorSwapped=sensor%180!=0,displaySwapped=display%180!=0;
        float uprightWidth=sensorSwapped?bufferHeight:bufferWidth;
        float uprightHeight=sensorSwapped?bufferWidth:bufferHeight;
        float shownWidth=displaySwapped?uprightHeight:uprightWidth;
        float shownHeight=displaySwapped?uprightWidth:uprightHeight;
        float uniformScale=Math.max(viewWidth/shownWidth,viewHeight/shownHeight);
        return new Preview(uniformScale*uprightWidth/viewWidth,
                uniformScale*uprightHeight/viewHeight,-display);
    }

    /** JPEGs are raw sensor output; unlike TextureView they still need sensor rotation. */
    public static int jpegOrientation(int sensorDegrees,int displayDegrees,boolean front) {
        int sensor=quadrant(sensorDegrees),display=quadrant(displayDegrees);
        // Display rotations are opposite to OrientationEventListener angles.
        return Math.floorMod(sensor+(front?display:-display),360);
    }

    private static int quadrant(int degrees) {
        if(degrees%90!=0)throw new IllegalArgumentException("Orientation must be a multiple of 90");
        return Math.floorMod(degrees,360);
    }
}
