package com.jirawat2105.panotess;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.database.*;
import java.io.*;
import java.util.*;
public final class NativeSmoke extends Instrumentation {
    static String AUTH="com.jirawat2105.panotess.tests.documents";
    MainActivity activity;
    public void onCreate(Bundle args){super.onCreate(args);start();}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    Uri child(Uri tree,Uri parent,String name)throws Exception{Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(parent));try(Cursor c=getTargetContext().getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){while(c.moveToNext())if(c.getString(1).equals(name))return DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0));}throw new AssertionError("Missing "+name);}
    void waitExport()throws Exception{long until=System.currentTimeMillis()+15000;while(activity.busy&&System.currentTimeMillis()<until)Thread.sleep(25);waitForIdleSync();check(!activity.busy,"Export timed out");check(activity.status.getText().toString().startsWith("Export Finished"),activity.status.getText().toString());}
    public void onStart(){Bundle result=new Bundle();try{
        getUiAutomation().adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS");
        Uri tree=DocumentsContract.buildTreeDocumentUri(AUTH,"root");Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,"root");ContentResolver resolver=getTargetContext().getContentResolver();String stem="sample"+System.currentTimeMillis();
        Uri input=DocumentsContract.createDocument(resolver,root,"image/png",stem+".png");Bitmap source=Bitmap.createBitmap(101,37,Bitmap.Config.ARGB_8888);for(int y=0;y<37;y++)for(int x=0;x<101;x++)source.setPixel(x,y,Color.rgb(x*13%256,y*7%256,(x+y)%256));source.setHasAlpha(false);try(OutputStream out=resolver.openOutputStream(input)){source.compress(Bitmap.CompressFormat.PNG,100,out);}
        Intent launch=new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext(),"com.jirawat2105.panotess.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(MainActivity)startActivitySync(launch);
        runOnMainSync(()->{activity.images.add(input);activity.destination=tree;activity.startExport();});waitExport();Uri folder=child(tree,root,stem);
        int[][] boxes=Slices.boxes(101,37,3,0,0);for(int i=0;i<3;i++){Uri tileUri=child(tree,folder,stem+String.format(java.util.Locale.ROOT,"_%02d.png",i+1));try(InputStream in=resolver.openInputStream(tileUri)){Bitmap tile=BitmapFactory.decodeStream(in);check(tile.getWidth()==boxes[i][2]-boxes[i][0],"Wrong tile size");for(int y=0;y<37;y++)for(int x=0;x<tile.getWidth();x++)check(tile.getPixel(x,y)==source.getPixel(x+boxes[i][0],y),"PNG pixel mismatch");tile.recycle();}}
        child(tree,folder,"panotess.json");
        runOnMainSync(()->{activity.ratio=2;activity.startExport();});waitExport();Uri cropped=child(tree,root,stem+"_2");try(InputStream in=resolver.openInputStream(child(tree,cropped,stem+"_01.png"))){Bitmap tile=BitmapFactory.decodeStream(in);check(tile.getWidth()==28&&tile.getHeight()==35,"Wrong 4:5 crop");check(tile.getPixel(0,0)==source.getPixel(8,1),"Wrong centered crop");tile.recycle();}
        runOnMainSync(()->{activity.ratio=0;activity.format=1;activity.startExport();});waitExport();Uri jpgFolder=child(tree,root,stem+"_3");try(InputStream in=resolver.openInputStream(child(tree,jpgFolder,stem+"_01.jpg"))){Bitmap jpg=BitmapFactory.decodeStream(in);check(jpg!=null&&jpg.getWidth()==33&&jpg.getHeight()==37,"Invalid JPG");jpg.recycle();}
        // Exercise the actual preview loader too.
        runOnMainSync(()->activity.loadPreview());long until=System.currentTimeMillis()+10000;while(activity.preview.bitmap==null&&System.currentTimeMillis()<until)Thread.sleep(25);waitForIdleSync();check(activity.preview.bitmap!=null,"Preview failed");source.recycle();
        runOnMainSync(()->{activity.preview.bitmap.recycle();activity.preview.bitmap=null;android.view.View view=activity.getWindow().getDecorView();Bitmap screen=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(screen));try(OutputStream out=new FileOutputStream(new File(activity.getExternalFilesDir(null),"ui-test.png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception error){throw new RuntimeException(error);}finally{screen.recycle();}});
        result.putString("stream","PASS: Android 17 launch, PNG stitch pixel comparison, crop, no-overwrite folders, JPG, preview and manifests.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+error.toString()+"\n"+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
}
