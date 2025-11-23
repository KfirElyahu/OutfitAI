package com.kfir.outfitai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class HelperUserDB extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "OutfitAIDB.db";
    private static final int DATABASE_VERSION = 4;

    public static final String TABLE_USERS = "users";
    public static final String COLUMN_USER_EMAIL = "email";
    public static final String COLUMN_USER_NAME = "username";
    public static final String COLUMN_USER_PASSWORD = "password";
    public static final String COLUMN_USER_PROFILE_PIC = "profile_pic";

    public static final String TABLE_HISTORY = "history";
    public static final String COLUMN_HISTORY_ID = "id";
    public static final String COLUMN_HISTORY_USER_EMAIL = "user_email";
    public static final String COLUMN_HISTORY_PERSON_URI = "person_uri";
    public static final String COLUMN_HISTORY_CLOTH_URI = "cloth_uri";
    public static final String COLUMN_HISTORY_RESULT_URIS = "result_uris";
    public static final String COLUMN_HISTORY_TIMESTAMP = "timestamp";

    public HelperUserDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + "("
                + COLUMN_USER_EMAIL + " TEXT PRIMARY KEY,"
                + COLUMN_USER_NAME + " TEXT,"
                + COLUMN_USER_PASSWORD + " TEXT,"
                + COLUMN_USER_PROFILE_PIC + " TEXT" + ")";
        db.execSQL(CREATE_USERS_TABLE);

        String CREATE_HISTORY_TABLE = "CREATE TABLE " + TABLE_HISTORY + "("
                + COLUMN_HISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_HISTORY_USER_EMAIL + " TEXT,"
                + COLUMN_HISTORY_PERSON_URI + " TEXT,"
                + COLUMN_HISTORY_CLOTH_URI + " TEXT,"
                + COLUMN_HISTORY_RESULT_URIS + " TEXT,"
                + COLUMN_HISTORY_TIMESTAMP + " INTEGER" + ")";
        db.execSQL(CREATE_HISTORY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
            String CREATE_HISTORY_TABLE = "CREATE TABLE " + TABLE_HISTORY + "("
                    + COLUMN_HISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_HISTORY_USER_EMAIL + " TEXT,"
                    + COLUMN_HISTORY_PERSON_URI + " TEXT,"
                    + COLUMN_HISTORY_CLOTH_URI + " TEXT,"
                    + COLUMN_HISTORY_RESULT_URIS + " TEXT,"
                    + COLUMN_HISTORY_TIMESTAMP + " INTEGER" + ")";
            db.execSQL(CREATE_HISTORY_TABLE);
        }

        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + COLUMN_USER_PROFILE_PIC + " TEXT");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void addUser(User user) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_EMAIL, user.getEmail());
        values.put(COLUMN_USER_NAME, user.getUsername());
        values.put(COLUMN_USER_PASSWORD, user.getPassword());
        values.put(COLUMN_USER_PROFILE_PIC, user.getProfilePicUri());
        db.insert(TABLE_USERS, null, values);
        db.close();
    }

    public User getUserDetails(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, COLUMN_USER_EMAIL + " = ?", new String[]{email}, null, null, null);
        User user = null;
        if (cursor.moveToFirst()) {
            user = new User();
            user.setEmail(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_EMAIL)));
            user.setUsername(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_NAME)));
            user.setPassword(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_PASSWORD)));

            int picIndex = cursor.getColumnIndex(COLUMN_USER_PROFILE_PIC);
            if (picIndex != -1) {
                user.setProfilePicUri(cursor.getString(picIndex));
            }
        }
        cursor.close();
        db.close();
        return user;
    }

    public boolean updateUserProfile(String currentEmail, User updatedUser) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_USER_NAME, updatedUser.getUsername());
            values.put(COLUMN_USER_PASSWORD, updatedUser.getPassword());
            values.put(COLUMN_USER_PROFILE_PIC, updatedUser.getProfilePicUri());

            if (!currentEmail.equals(updatedUser.getEmail())) {
                Cursor cursor = db.query(TABLE_USERS, new String[]{COLUMN_USER_EMAIL},
                        COLUMN_USER_EMAIL + " = ?", new String[]{updatedUser.getEmail()}, null, null, null);
                boolean exists = cursor.getCount() > 0;
                cursor.close();

                if (exists) {
                    return false;
                }

                values.put(COLUMN_USER_EMAIL, updatedUser.getEmail());

                db.update(TABLE_USERS, values, COLUMN_USER_EMAIL + " = ?", new String[]{currentEmail});

                ContentValues historyValues = new ContentValues();
                historyValues.put(COLUMN_HISTORY_USER_EMAIL, updatedUser.getEmail());
                db.update(TABLE_HISTORY, historyValues, COLUMN_HISTORY_USER_EMAIL + " = ?", new String[]{currentEmail});
            } else {
                db.update(TABLE_USERS, values, COLUMN_USER_EMAIL + " = ?", new String[]{currentEmail});
            }

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public boolean checkUserExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, new String[]{COLUMN_USER_EMAIL}, COLUMN_USER_EMAIL + " = ?", new String[]{email}, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        db.close();
        return count > 0;
    }

    public boolean checkUserCredentials(String emailOrUsername, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = "(" + COLUMN_USER_EMAIL + " = ? OR " + COLUMN_USER_NAME + " = ?) AND " + COLUMN_USER_PASSWORD + " = ?";
        Cursor cursor = db.query(TABLE_USERS, new String[]{COLUMN_USER_EMAIL}, selection, new String[]{emailOrUsername, emailOrUsername, password}, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        db.close();
        return count > 0;
    }

    public void addHistoryItem(HistoryItem item, String userEmail) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_HISTORY_USER_EMAIL, userEmail);
        values.put(COLUMN_HISTORY_PERSON_URI, item.getPersonUri());
        values.put(COLUMN_HISTORY_CLOTH_URI, item.getClothUri());
        values.put(COLUMN_HISTORY_RESULT_URIS, item.getResultUris());
        values.put(COLUMN_HISTORY_TIMESTAMP, System.currentTimeMillis());
        db.insert(TABLE_HISTORY, null, values);
        db.close();
    }

    public List<HistoryItem> getUserHistory(String userEmail) {
        List<HistoryItem> historyList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String selection = COLUMN_HISTORY_USER_EMAIL + " = ?";
        String[] selectionArgs = { userEmail };

        Cursor cursor = db.query(TABLE_HISTORY, null, selection, selectionArgs, null, null,
                COLUMN_HISTORY_TIMESTAMP + " DESC");

        if (cursor.moveToFirst()) {
            do {
                HistoryItem item = new HistoryItem();
                item.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_ID)));
                item.setPersonUri(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_PERSON_URI)));
                item.setClothUri(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_CLOTH_URI)));
                item.setResultUris(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_RESULT_URIS)));
                item.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_TIMESTAMP)));
                historyList.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return historyList;
    }
}