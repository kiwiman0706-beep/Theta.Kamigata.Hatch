import jp.yaegaki.kururihair.CameraGeometry;

/** Known orientations and physical invariants, not a second copy of the production formula. */
public final class CameraGeometryTest {
    private static int checks=0;
    private static void near(double value,double expected,String why) {
        if(Math.abs(value-expected)>0.0001)throw new AssertionError(why+": "+value+" != "+expected);
        checks++;
    }
    private static void require(boolean condition,String why) {
        if(!condition)throw new AssertionError(why);checks++;
    }
    public static void main(String[] args) {
        // Regression for the reported portrait preview: already upright, so apply NO additional 90 degrees.
        CameraGeometry.Preview back=CameraGeometry.preview(90,0,1280,960,600,800);
        near(back.rotationDegrees,0,"portrait back camera must not double-rotate");
        near(back.scaleX,1,"portrait matching ratio x");near(back.scaleY,1,"portrait matching ratio y");
        CameraGeometry.Preview front=CameraGeometry.preview(270,0,1280,960,600,800);
        near(front.rotationDegrees,0,"portrait front camera must not double-rotate");
        near(front.scaleX,1,"portrait front x");near(front.scaleY,1,"portrait front y");
        // Screenshot shape is much taller than 4:3: preserve proportions, crop width, never rotate again.
        CameraGeometry.Preview tall=CameraGeometry.preview(90,0,1280,960,600,854);
        near(tall.rotationDegrees,0,"reported tall preview is still portrait");
        near(tall.scaleX,1.0675,"center crop tall preview in x");near(tall.scaleY,1,"center crop tall preview in y");
        CameraGeometry.Preview landscape=CameraGeometry.preview(90,90,1280,960,800,600);
        near(landscape.rotationDegrees,-90,"compensate display only");
        near(landscape.scaleX,.75,"undo pre-rotation x stretch");near(landscape.scaleY,4.0/3,"undo pre-rotation y stretch");
        CameraGeometry.Preview tablet=CameraGeometry.preview(0,90,1280,960,600,800);
        near(tablet.rotationDegrees,-90,"landscape-native tablet in portrait");
        near(tablet.scaleX,4.0/3,"landscape-native tablet x");near(tablet.scaleY,.75,"landscape-native tablet y");
        // All sensor orientations, display orientations and common view shapes.
        for(int sensor:new int[]{0,90,180,270})for(int display:new int[]{0,90,180,270})
            for(int[] size:new int[][]{{600,854},{320,500},{800,600},{1280,800},{800,1280}}){
                CameraGeometry.Preview p=CameraGeometry.preview(sensor,display,1280,960,size[0],size[1]);
                double naturalWidth=sensor%180==0?1280:960,naturalHeight=sensor%180==0?960:1280;
                // Physical pixel scales along the original texture axes must be equal: no stretching.
                near(size[0]*p.scaleX/naturalWidth,size[1]*p.scaleY/naturalHeight,"uniform pixel scale");
                double scaledW=size[0]*p.scaleX,scaledH=size[1]*p.scaleY;
                if(display%180!=0){double tmp=scaledW;scaledW=scaledH;scaledH=tmp;}
                require(scaledW>=size[0]-.001&&scaledH>=size[1]-.001,"center-cropped buffer covers the view");
                require(Math.abs(scaledW-size[0])<.001||Math.abs(scaledH-size[1])<.001,"one edge fits exactly");
                near(p.rotationDegrees,-display,"app rotation independent of sensor orientation");
            }
        // Preview correction must not remove the sensor rotation from captured JPEGs.
        int[] backExpected={90,0,270,180},frontExpected={270,0,90,180};
        for(int i=0;i<4;i++){
            near(CameraGeometry.jpegOrientation(90,i*90,false),backExpected[i],"back JPEG orientation");
            near(CameraGeometry.jpegOrientation(270,i*90,true),frontExpected[i],"front JPEG orientation");
        }
        boolean invalid=false;try{CameraGeometry.preview(90,0,0,960,600,800);}catch(IllegalArgumentException e){invalid=true;}
        require(invalid,"reject unknown buffer dimensions");
        System.out.println("Camera geometry checks passed: "+checks+". Physical camera rendering remains untested here.");
    }
}
