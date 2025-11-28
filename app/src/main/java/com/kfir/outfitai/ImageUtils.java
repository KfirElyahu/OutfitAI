package com.kfir.outfitai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtils {

    private static final int MAX_DIMENSION = 256;
    private static final int COMPRESSION_QUALITY = 70;

    public static String processAndSaveImage(Context context, Uri sourceUri, String currentUserEmail) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
        Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
        if (inputStream != null) inputStream.close();

        if (originalBitmap == null) return null;

        originalBitmap = rotateImageIfRequired(context, originalBitmap, sourceUri);

        Bitmap resizedBitmap = scaleBitmap(originalBitmap, MAX_DIMENSION);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream);
        byte[] imageBytes = outputStream.toByteArray();

        saveToInternalStorage(context, imageBytes, currentUserEmail);

        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    public static void saveBase64ToLocal(Context context, String base64String, String currentUserEmail) throws IOException {
        byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
        saveToInternalStorage(context, decodedBytes, currentUserEmail);
    }

    private static void saveToInternalStorage(Context context, byte[] bytes, String email) throws IOException {
        File profileDir = new File(context.getFilesDir(), "profile_images");
        if (!profileDir.exists()) profileDir.mkdirs();

        File destFile = new File(profileDir, "profile_" + email + ".jpg");
        FileOutputStream fos = new FileOutputStream(destFile);
        fos.write(bytes);
        fos.close();
    }

    public static File getLocalProfileFile(Context context, String email) {
        File profileDir = new File(context.getFilesDir(), "profile_images");
        return new File(profileDir, "profile_" + email + ".jpg");
    }

    private static Bitmap scaleBitmap(Bitmap bitmap, int maxDimension) {
        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int newWidth, newHeight;

        if (originalWidth > originalHeight) {
            newWidth = maxDimension;
            newHeight = (int) ((float) maxDimension / originalWidth * originalHeight);
        } else {
            newHeight = maxDimension;
            newWidth = (int) ((float) maxDimension / originalHeight * originalWidth);
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    private static Bitmap rotateImageIfRequired(Context context, Bitmap img, Uri selectedImage) throws IOException {
        InputStream input = context.getContentResolver().openInputStream(selectedImage);
        ExifInterface ei;
        if (android.os.Build.VERSION.SDK_INT > 23) {
            ei = new ExifInterface(input);
        } else {
            return img;
        }

        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        if (input != null) input.close();

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: return rotate(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180: return rotate(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270: return rotate(img, 270);
            default: return img;
        }
    }

    private static Bitmap rotate(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
    }
}