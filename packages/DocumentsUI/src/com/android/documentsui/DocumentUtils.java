package com.android.documentsui;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import com.android.documentsui.model.DocumentInfo;

import java.io.*;
import java.util.*;

/**
 * Created by bmc on 7/8/14.
 */
public class DocumentUtils {

    private final static String TAG = DocumentUtils.class.getSimpleName();


    public static String getContentFromUri(Uri uri) {
        String[] split = uri.toString().split("/");
        return split[2];
    }

    public boolean isLocalDocument(String url) {
        return (url != null && !url.startsWith("http://") && !url.startsWith("http://"));
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

    public static String getPath(final String label, Uri uri) {
        final String docId = DocumentsContract.getDocumentId(uri);
        final String[] split = docId.split(":");
        final String type = split[0];

        if (isPrimaryStorage(uri)) {
            return Environment.getExternalStorageDirectory() + "/" + split[1];
        }

        if (!isPrimaryStorage(uri) && type != null) {
            return "/storage/" + label + "/" + split[1];
        }
        return null;
    }

    public static boolean isPrimaryStorage(Uri uri) {
        final String docId = DocumentsContract.getDocumentId(uri);
        final String[] split = docId.split(":");
        return split[0].equals("primary");
    }

    public static boolean isAtRootOfVolume(String directory) {
        if (directory == null) {
            return false;
        }
        // generate list of labels of the mounted volumes
        List<DocumentUtils.StorageInfo> volumes = DocumentUtils.StorageInfo.getMountedVolumes();
        for (DocumentUtils.StorageInfo volume : volumes) {
            String label = volume.path.substring((volume.path.lastIndexOf("/")+1), volume.path.length());
            Log.d(TAG, "directory=" + directory + ", label=" + label);
            if (directory.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    public static String getExtension(String uri) {
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

    public static void installApplication(Context context, String storageLabel, DocumentInfo doc) {
        Uri uri = doc.derivedUri;
        String path = getPath(storageLabel, uri);
        File apk = new File(path);
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setDataAndType(Uri.fromFile(apk), doc.mimeType);
        context.startActivity(intent);
    }

    public static class StorageInfo {

        public final String path;
        public final boolean readonly;
        public final boolean removable;
        public final int number;

        StorageInfo(String path, boolean readonly, boolean removable, int number) {
            this.path = path;
            this.readonly = readonly;
            this.removable = removable;
            this.number = number;
        }

        public static List<StorageInfo> getMountedVolumes() {
            List<StorageInfo> list = new ArrayList<StorageInfo>();
            String primary = Environment.getExternalStorageDirectory().getPath();
            boolean isPrimaryRemovable = Environment.isExternalStorageRemovable();
            String primaryState = Environment.getExternalStorageState();
            boolean isPrimaryAvailable = primaryState.equals(Environment.MEDIA_MOUNTED)
                    || primaryState.equals(Environment.MEDIA_MOUNTED_READ_ONLY);
            boolean isPrimaryReadOnly = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY);

            HashSet<String> paths = new HashSet<String>();
            int num_removable = 1;

            if (isPrimaryAvailable) {
                paths.add(primary);
                list.add(0, new StorageInfo(
                        primary, isPrimaryReadOnly, isPrimaryRemovable, isPrimaryRemovable ? num_removable++ : -1));
            }

            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new FileReader("/proc/mounts"));
                String mount;
                while ((mount = bufferedReader.readLine()) != null) {
                    if (mount.contains("vfat") || mount.contains("/mnt")) {
                        StringTokenizer tokens = new StringTokenizer(mount, " ");
                        String unused = tokens.nextToken();
                        String mountPoint = tokens.nextToken();
                        if (paths.contains(mountPoint)) {
                            continue;
                        }
                        unused = tokens.nextToken();
                        List<String> flags = Arrays.asList(tokens.nextToken().split(","));
                        boolean readOnly = flags.contains("ro");

                        if (mount.contains("/dev/block/vold")) {
                            if (!mount.contains("/mnt/secure")
                                    && !mount.contains("/mnt/asec")
                                    && !mount.contains("/mnt/obb")
                                    && !mount.contains("/dev/mapper")
                                    && !mount.contains("tmpfs")) {
                                paths.add(mountPoint);
                                list.add(new StorageInfo(mountPoint, readOnly, true, num_removable++));
                            }
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException io) {
                io.printStackTrace();
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException ignored) {}
                }
            }
            return list;
        }

    }
}
