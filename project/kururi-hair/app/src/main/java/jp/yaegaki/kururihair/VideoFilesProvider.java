package jp.yaegaki.kururihair;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.UUID;

/** A grant-only, narrowly scoped output URI for the system video camera. */
public final class VideoFilesProvider extends ContentProvider {
    public static File newVideo(Context context) throws IOException {
        File directory=new File(context.getCacheDir(),"videos");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("動画の保存先を用意できません。");
        File file=new File(directory,"video-"+UUID.randomUUID()+".mp4");
        if(!file.createNewFile())throw new IOException("動画の保存先を用意できません。");
        return file;
    }
    public static File checkedFile(Context context,String path) throws IOException {
        if(path==null)throw new IOException("動画が見つかりません。");
        File file=new File(path).getCanonicalFile();
        File directory=new File(context.getCacheDir(),"videos").getCanonicalFile();
        if(!directory.equals(file.getParentFile())||!file.getName().matches("video-[a-f0-9-]{36}\\.mp4"))throw new IOException("動画の保存先が正しくありません。");
        return file;
    }
    public static Uri uri(Context context,File file) throws IOException {
        File safe=checkedFile(context,file.getPath());
        return new Uri.Builder().scheme("content").authority(context.getPackageName()+".video").appendPath(safe.getName()).build();
    }
    private File file(Uri uri) throws FileNotFoundException {
        try{
            if(!"content".equals(uri.getScheme())||!(getContext().getPackageName()+".video").equals(uri.getAuthority())||uri.getPathSegments().size()!=1)throw new IOException();
            return checkedFile(getContext(),new File(new File(getContext().getCacheDir(),"videos"),uri.getLastPathSegment()).getPath());
        }catch(IOException e){throw new FileNotFoundException("動画の保存先が正しくありません。");}
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "video/mp4";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.parseMode(mode));
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        try{
            File file=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor cursor=new MatrixCursor(columns,1);Object[] row=new Object[columns.length];
            for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?file.getName():OpenableColumns.SIZE.equals(columns[i])?file.length():null;
            cursor.addRow(row);return cursor;
        }catch(IOException e){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){return 0;}
}
