package com.jirawat2105.panotess;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class MainActivity extends Activity {
    static final int BG=0xff101115, CARD=0xff191b21, INK=0xffebebf1, MUTED=0xff979daf, ACCENT=0xffc0afff;
    final ArrayList<Uri> images=new ArrayList<>();
    final ArrayList<View> controls=new ArrayList<>();
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    volatile boolean busy=false, cancelled=false, dead=false;
    int current=0, count=3, ratio=0, format=0, generation=0;
    final int[][] ratios={{0,0},{1,1},{4,5},{3,4},{2,3},{9,16},{16,9}};
    Uri destination;
    Preview preview;
    TextView filename, dimensions, status, output;
    Button previous,next,export,cancel;
    ProgressBar progress;
    void ui(Runnable action){runOnUiThread(()->{if(!dead)action.run();});}
    int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    GradientDrawable background(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(7),0,dp(7));return t;}
    Button button(String title,LinearLayout parent,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(INK);b.setBackground(background(0xff2b2d38));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.setMargins(0,dp(5),0,dp(5));parent.addView(b,p);b.setOnClickListener(v->action.run());controls.add(b);return b;}
    LinearLayout card(LinearLayout root){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(16),dp(12),dp(16),dp(12));box.setBackground(background(CARD));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(12),0,0);root.addView(box,p);return box;}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{log("E_CRASH",error);android.os.Process.killProcess(android.os.Process.myPid());});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(12),dp(20),dp(24));scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        root.addView(text("PanoTess",30,INK));root.addView(text("One panorama. Perfectly connected.",13,MUTED));
        LinearLayout imageCard=card(root);button("+  Select images",imageCard,()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png"});i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,10);});
        preview=new Preview();imageCard.addView(preview,new LinearLayout.LayoutParams(-1,dp(245)));
        filename=text("Select JPG or PNG images",15,INK);dimensions=text("Preview is scaled. Export is never resized.",12,MUTED);imageCard.addView(filename);imageCard.addView(dimensions);
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(0);imageCard.addView(nav);
        previous=button("‹ Previous",nav,()->{if(current>0){current--;loadPreview();}});next=button("Next ›",nav,()->{if(current+1<images.size()){current++;loadPreview();}});
        for(int n=0;n<nav.getChildCount();n++)nav.getChildAt(n).setLayoutParams(new LinearLayout.LayoutParams(0,dp(46),1));
        button("Clear images",imageCard,()->{images.clear();current=0;generation++;if(preview.bitmap!=null){preview.bitmap.recycle();preview.bitmap=null;}filename.setText("Select JPG or PNG images");dimensions.setText("");status.setText("");preview.invalidate();});
        LinearLayout settings=card(root);settings.addView(text("Slice settings",19,INK));
        settings.addView(text("Number of slices",12,MUTED));spinner(settings,new String[]{"2 images","3 images","4 images"},1,value->{count=value+2;preview.invalidate();});
        settings.addView(text("Aspect ratio · per slice",12,MUTED));spinner(settings,new String[]{"Original · keep full image","1:1","4:5","3:4","2:3","9:16","16:9"},0,value->{ratio=value;preview.invalidate();});
        settings.addView(text("Fixed ratios use a centered crop. Shaded areas are excluded.",12,MUTED));
        settings.addView(text("Output format",12,MUTED));spinner(settings,new String[]{"PNG · lossless","JPG · quality 100 (re-encoded)"},0,value->format=value);
        settings.addView(text("Android export uses decoded 8-bit pixels. JPEG tables and desktop TIFF export are not supported. Transparent images require PNG.",12,MUTED));
        button("Choose output folder",settings,()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,11);});
        output=text("Each image gets its own subfolder in the selected folder.",12,MUTED);settings.addView(output);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);settings.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        status=text("Ready",14,MUTED);settings.addView(status);export=button("Export slices",settings,this::startExport);export.setBackground(background(ACCENT));export.setTextColor(BG);
        cancel=button("Cancel export",settings,()->{cancelled=true;status.setText("Cancelling after current slice…");});cancel.setVisibility(View.GONE);
        button("View logs",root,()->{String value="No errors recorded.";try{byte[] data=java.nio.file.Files.readAllBytes(new File(getFilesDir(),"panotess.log").toPath());value=new String(data,java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ignored){}new AlertDialog.Builder(this).setTitle("PanoTess logs").setMessage(value).setPositiveButton("Close",null).show();});
        root.addView(text("PanoTess Android 1.0 · Local processing",11,MUTED));
        String saved=getPreferences(0).getString("output",null);if(saved!=null){destination=Uri.parse(saved);output.setText("Output folder selected");}
    }
    interface Choice{void select(int value);}
    void spinner(LinearLayout parent,String[] items,int selected,Choice choice){Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items);s.setAdapter(a);s.setSelection(selected);parent.addView(s,new LinearLayout.LayoutParams(-1,dp(50)));controls.add(s);s.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?>p){}public void onItemSelected(android.widget.AdapterView<?>p,View v,int position,long id){choice.select(position);}});}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        try{if(request==10){if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)add(data.getData());loadPreview();}
        if(request==11){destination=data.getData();getContentResolver().takePersistableUriPermission(destination,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));getPreferences(0).edit().putString("output",destination.toString()).apply();output.setText("Output folder selected");}}catch(Exception error){fail(error);}}
    void add(Uri uri){if(!images.contains(uri))images.add(uri);}
    String name(Uri uri){try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}return "image";}
    Bitmap decode(Uri uri,boolean thumbnail)throws Exception{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
        if(!"image/jpeg".equals(bounds.outMimeType)&&!"image/png".equals(bounds.outMimeType))throw new IOException("E_FORMAT: Only JPG and PNG still images are supported.");
        if("image/png".equals(bounds.outMimeType))try(DataInputStream in=new DataInputStream(getContentResolver().openInputStream(uri))){in.readLong();for(int chunks=0;chunks<10000;chunks++){int length=in.readInt();int type=in.readInt();if(length<0||length>33554432)throw new IOException("E_IMAGE_READ: Invalid PNG metadata.");if(type==0x6163544c)throw new IOException("E_FORMAT: Animated PNG is not supported.");if(type==0x49484452){if(length!=13)throw new IOException("E_IMAGE_READ: Invalid PNG header.");in.readInt();in.readInt();if(in.readUnsignedByte()>8)throw new IOException("E_COLOR_DEPTH: PNG above 8-bit is not supported.");in.skipBytes(4);}else if(type==0x49444154)break;else{int remaining=length;while(remaining>0){int skipped=in.skipBytes(remaining);if(skipped<=0)throw new EOFException();remaining-=skipped;}}in.readInt();}}
        if(bounds.outWidth<1||bounds.outHeight<1)throw new IOException("E_IMAGE_READ: Invalid image.");
        long pixels=(long)bounds.outWidth*bounds.outHeight;
        if(!thumbnail && pixels*12>Runtime.getRuntime().maxMemory()*0.65)throw new IOException("E_MEMORY: Image exceeds the safe memory budget of this phone.");
        Bitmap result=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);if(thumbnail){int w=info.getSize().getWidth(),h=info.getSize().getHeight();double scale=Math.min(1,1400.0/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)));}});
        if(!thumbnail&&result.getConfig()!=Bitmap.Config.ARGB_8888){result.recycle();throw new IOException("E_COLOR_DEPTH: This image cannot be exported as 8-bit without conversion.");}return result;
    }
    void loadPreview(){if(images.isEmpty())return;final Uri uri=images.get(current);final int ticket=++generation;filename.setText((current+1)+" / "+images.size()+" · "+name(uri));dimensions.setText("Loading preview…");worker.execute(()->{try{Bitmap bitmap=decode(uri,true);ui(()->{if(ticket!=generation){bitmap.recycle();return;}if(preview.bitmap!=null)preview.bitmap.recycle();preview.bitmap=bitmap;preview.invalidate();dimensions.setText("Preview only is scaled · Export uses full resolution");});}catch(Throwable error){ui(()->{if(ticket==generation){if(preview.bitmap!=null){preview.bitmap.recycle();preview.bitmap=null;}preview.invalidate();dimensions.setText("Preview unavailable");fail(error);}});}});}
    void startExport(){if(busy)return;if(images.isEmpty()||destination==null){status.setText("Select images and an output folder first.");return;}busy=true;cancelled=false;for(View v:controls)v.setEnabled(false);cancel.setEnabled(true);cancel.setVisibility(View.VISIBLE);status.setTextColor(MUTED);status.setText("Exporting…");progress.setProgress(0);
        final ArrayList<Uri> batch=new ArrayList<>(images);final Uri tree=destination;final int slices=count,a=ratios[ratio][0],b=ratios[ratio][1],jpg=format;worker.execute(()->{int saved=0,errors=0,done=0;for(Uri uri:batch){if(cancelled||dead)break;Uri folder=null;Bitmap source=null;try{source=decode(uri,false);
            if(jpg==1&&source.hasAlpha()){int[] row=new int[source.getWidth()];for(int y=0;y<source.getHeight();y++){source.getPixels(row,0,row.length,0,y,row.length,1);for(int pixel:row)if((pixel>>>24)!=255)throw new IOException("E_JPEG_ALPHA: Transparency requires PNG.");}}
            String stem=name(uri).replaceAll("\\.[^.]+$","").replaceAll("[\\\\/:*?\"<>|]","_");if(stem.trim().isEmpty())stem="image";
            Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));String unique=stem;int suffix=2;while(exists(tree,parent,unique))unique=stem+"_"+(suffix++);
            folder=DocumentsContract.createDocument(getContentResolver(),parent,DocumentsContract.Document.MIME_TYPE_DIR,unique);if(folder==null)throw new IOException("E_FOLDER: Cannot create output folder.");
            int[][] boxes=Slices.boxes(source.getWidth(),source.getHeight(),slices,a,b);JSONArray manifestSlices=new JSONArray();
            for(int i=0;i<slices;i++){if(cancelled||dead)throw new IOException("E_CANCELLED");int[] box=boxes[i];Bitmap tile=Bitmap.createBitmap(source,box[0],box[1],box[2]-box[0],box[3]-box[1]);String file=stem+String.format(java.util.Locale.ROOT,"_%02d.",i+1)+(jpg==1?"jpg":"png");try{Uri document=DocumentsContract.createDocument(getContentResolver(),folder,jpg==1?"image/jpeg":"image/png",file);if(document==null)throw new IOException("E_WRITE: Cannot create slice.");try(OutputStream out=getContentResolver().openOutputStream(document,"w")){if(out==null||!tile.compress(jpg==1?Bitmap.CompressFormat.JPEG:Bitmap.CompressFormat.PNG,100,out))throw new IOException("E_WRITE: Cannot encode slice.");}}finally{if(tile!=source)tile.recycle();}manifestSlices.put(new JSONObject().put("file",file).put("box",new JSONArray(box)));done++;final int value=done*100/(batch.size()*slices);ui(()->progress.setProgress(value));}
            JSONObject manifest=new JSONObject().put("app","PanoTess").put("resampled",false).put("slices",manifestSlices);Uri document=DocumentsContract.createDocument(getContentResolver(),folder,"application/json","panotess.json");if(document==null)throw new IOException("E_WRITE: Cannot save manifest.");try(OutputStream out=getContentResolver().openOutputStream(document,"w")){if(out==null)throw new IOException("E_WRITE: Cannot save manifest.");out.write(manifest.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}saved++;
        }catch(Throwable error){if(folder!=null)try{DocumentsContract.deleteDocument(getContentResolver(),folder);}catch(Exception cleanup){log("E_CLEANUP",cleanup);}if(!cancelled&&!dead){errors++;log("E_EXPORT",error);final String message=error.toString();ui(()->status.setText(message));}done=(saved+errors)*slices;}finally{if(source!=null)source.recycle();}}
        final int ok=saved,failed=errors;ui(()->{busy=false;for(View v:controls)v.setEnabled(true);cancel.setVisibility(View.GONE);status.setTextColor(!cancelled&&failed==0?0xff83dfad:0xffffb4a2);status.setText(cancelled?"Export cancelled · "+ok+" images saved":failed>0?ok+" saved · "+failed+" failed. See logs.":"Export Finished · "+ok+" images");if(!cancelled)progress.setProgress(100);});});
    }
    boolean exists(Uri tree,Uri folder,String name)throws Exception{Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(folder));try(Cursor c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(c==null)throw new IOException("E_FOLDER: Cannot list folder.");while(c.moveToNext())if(name.equalsIgnoreCase(c.getString(0)))return true;}return false;}
    void log(String code,Throwable error){try{File file=new File(getFilesDir(),"panotess.log");if(file.length()>1000000){File previous=new File(getFilesDir(),"previous.log");java.nio.file.Files.move(file.toPath(),previous.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}try(PrintWriter out=new PrintWriter(new FileOutputStream(file,true))){out.println(new Date()+" "+code);error.printStackTrace(out);}}catch(Exception ignored){}}
    void fail(Throwable error){log("E_APP",error);status.setTextColor(0xffffb4a2);status.setText(error.toString());}
    @Override protected void onDestroy(){dead=true;cancelled=true;worker.shutdown();if(preview!=null&&preview.bitmap!=null)preview.bitmap.recycle();super.onDestroy();}
    final class Preview extends View{Bitmap bitmap;Paint paint=new Paint(3);Preview(){super(MainActivity.this);}protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(BG);if(bitmap==null){paint.setColor(MUTED);paint.setTextSize(dp(14));Paint.FontMetrics metrics=paint.getFontMetrics();String message="Your panorama preview";c.drawText(message,(getWidth()-paint.measureText(message))/2f,(getHeight()-metrics.ascent-metrics.descent)/2f,paint);return;}float scale=Math.min((getWidth()-dp(16f))/(float)bitmap.getWidth(),(getHeight()-dp(16f))/(float)bitmap.getHeight());float x=(getWidth()-bitmap.getWidth()*scale)/2,y=(getHeight()-bitmap.getHeight()*scale)/2;RectF dst=new RectF(x,y,x+bitmap.getWidth()*scale,y+bitmap.getHeight()*scale);c.drawBitmap(bitmap,null,dst,paint);try{int[][] boxes=Slices.boxes(bitmap.getWidth(),bitmap.getHeight(),count,ratios[ratio][0],ratios[ratio][1]);RectF crop=new RectF(x+boxes[0][0]*scale,y+boxes[0][1]*scale,x+boxes[count-1][2]*scale,y+boxes[0][3]*scale);paint.setColor(0xaa101115);c.drawRect(dst.left,dst.top,dst.right,crop.top,paint);c.drawRect(dst.left,crop.bottom,dst.right,dst.bottom,paint);c.drawRect(dst.left,crop.top,crop.left,crop.bottom,paint);c.drawRect(crop.right,crop.top,dst.right,crop.bottom,paint);paint.setColor(ACCENT);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));for(int[] box:boxes)c.drawRect(x+box[0]*scale,y+box[1]*scale,x+box[2]*scale,y+box[3]*scale,paint);paint.setStyle(Paint.Style.FILL);}catch(Exception ignored){paint.setStyle(Paint.Style.FILL);}}}
}
