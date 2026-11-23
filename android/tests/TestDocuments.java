package com.jirawat2105.panotess;
import android.provider.*;
import android.database.*;
import android.os.*;
import java.io.*;
/** Test-only document provider. Never included in the application APK. */
public final class TestDocuments extends DocumentsProvider {
    File root;
    public boolean onCreate(){root=new File(getContext().getFilesDir(),"documents");root.mkdirs();return true;}
    File file(String id)throws FileNotFoundException {try{File f=id.equals("root")?root:new File(root,id);if(!f.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator)&&!f.equals(root))throw new FileNotFoundException();return f;}catch(IOException e){throw new FileNotFoundException();}}
    String id(File f){return f.equals(root)?"root":f.getAbsolutePath().substring(root.getAbsolutePath().length()+1);}
    static String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS};
    void row(MatrixCursor c,File f){MatrixCursor.RowBuilder r=c.newRow();for(String col:c.getColumnNames()){if(col.equals(columns[0]))r.add(id(f));else if(col.equals(columns[1]))r.add(f.getName());else if(col.equals(columns[2]))r.add(f.isDirectory()?DocumentsContract.Document.MIME_TYPE_DIR:"application/octet-stream");else if(col.equals(columns[3]))r.add(DocumentsContract.Document.FLAG_SUPPORTS_DELETE|DocumentsContract.Document.FLAG_SUPPORTS_WRITE|(f.isDirectory()?DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE:0));else r.add(null);}}
    public Cursor queryRoots(String[] projection){return new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE});}
    public Cursor queryDocument(String documentId,String[] projection)throws FileNotFoundException{MatrixCursor c=new MatrixCursor(projection==null?columns:projection);row(c,file(documentId));return c;}
    public Cursor queryChildDocuments(String parent,String[] projection,String sort)throws FileNotFoundException{MatrixCursor c=new MatrixCursor(projection==null?columns:projection);File[] children=file(parent).listFiles();if(children!=null)for(File f:children)row(c,f);return c;}
    public ParcelFileDescriptor openDocument(String documentId,String mode,CancellationSignal signal)throws FileNotFoundException{return ParcelFileDescriptor.open(file(documentId),ParcelFileDescriptor.parseMode(mode));}
    public String createDocument(String parent,String mime,String name)throws FileNotFoundException{File f=new File(file(parent),name);try{boolean created=DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)?f.mkdir():f.createNewFile();if(!created)throw new IOException("Exists");return id(f);}catch(IOException error){throw new FileNotFoundException(error.toString());}}
    public void deleteDocument(String documentId)throws FileNotFoundException{erase(file(documentId));}
    void erase(File f)throws FileNotFoundException{File[] children=f.listFiles();if(children!=null)for(File child:children)erase(child);if(!f.delete())throw new FileNotFoundException("Test cleanup failed");}
    public boolean isChildDocument(String parent,String documentId){try{return file(documentId).getAbsolutePath().startsWith(file(parent).getAbsolutePath()+File.separator);}catch(FileNotFoundException error){return false;}}
}
