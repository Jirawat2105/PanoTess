import com.jirawat2105.panotess.Slices;
public class GeometryTest {
    public static void main(String[] args) {
        for(int n=2;n<=4;n++) {
            int[][] boxes=Slices.boxes(101,37,n,0,0);
            int end=0;
            for(int[] b:boxes){if(b[0]!=end||b[1]!=0||b[3]!=37)throw new AssertionError("Lost pixels");end=b[2];}
            if(end!=101)throw new AssertionError("Lost last column");
            int[][] ratios={{1,1},{4,5},{3,4},{2,3},{9,16},{16,9}};
            for(int[] ratio:ratios)for(int[] b:Slices.boxes(2401,1001,n,ratio[0],ratio[1])) {
                if((b[2]-b[0])*ratio[1]!=(b[3]-b[1])*ratio[0])throw new AssertionError("Wrong ratio");
                if(b[0]<0||b[1]<0||b[2]>2401||b[3]>1001)throw new AssertionError("Outside source");
            }
        }
        System.out.println("Android slice geometry tests passed.");
    }
}
