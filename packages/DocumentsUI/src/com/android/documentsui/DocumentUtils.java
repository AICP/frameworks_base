package com.android.documentsui;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import com.android.documentsui.model.DocumentInfo;

import java.io.File;

/**
 * Created by bmc on 7/8/14.
 */
public class DocumentUtils {

    private final static String TAG = DocumentUtils.class.getSimpleName();

    public boolean isLocalDocument(String url) {
        return (url != null && !url.startsWith("http://") && !url.startsWith("http://"));
    }

    public static String getContentFromUri(Uri uri) {
        String[] split = uri.toString().split("/");
        return split[2];
    }

    // Override of RootInfo.isExternalStorage accepting a URI
    public static boolean isExternalStorageDocument(Uri uri) {
        return getContentFromUri(uri).equals("com.android.externalstorage.documents");
    }

    // Override of RootInfo.isDownloads accepting a URI
    public boolean isDownloadsDocument(Uri uri) {
        return getContentFromUri(uri).equals("com.android.providers.downloads.documents");
    }

    public boolean isMediaDocument(Uri uri) {
        return getContentFromUri(uri).equals("com.android.providers.media.documents");
    }

    public static String getPath(final Uri uri) {
        final String docId = DocumentsContract.getDocumentId(uri);
        final String[] split = docId.split(":");
        final String type = split[0];
        Log.d(TAG, "docId: " + docId + " split: " + split + " type: " + type);

        if (type.equalsIgnoreCase("primary")) {
            return Environment.getExternalStorageDirectory() + "/" + split[1];
        }
        return null;
    }

    public static String getExtension(String uri) {
        Log.d(TAG, "uri is " + uri);
        if (uri == null) {
            return null;
        }

        String extension = uri.substring((uri.lastIndexOf(".")+1), uri.length());
        return (extension != null) ? extension : "";
    }

    public String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = { column };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    public static void installApplication(Context context, DocumentInfo doc) {
        Uri uri = doc.derivedUri;
        String mimeType = doc.mimeType;
        String path = getPath(uri);
        File apk = new File(path);
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setDataAndType(Uri.fromFile(apk), doc.mimeType);
        context.startActivity(intent);
    }
}
