package com.zeddigital.applicationnew;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

public class MyForegroundService extends Service {

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "ForegroundServiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Notification notification;
        notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        // Perform your long-running task or background operation here


        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        int iconColor = ContextCompat.getColor(this, R.color.custom_icon_color); // Use the color resource ID// Replace with your desired color resource ID
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Zig - Travel Places Safety")
                .setContentText("Welcome Board")
                .setSmallIcon(R.drawable.custom_icon);
        /*NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Zig -Travel Places Safety")
                .setContentText("Welcome Board")
            //    .setSmallIcon(R.drawable.notification);
                .setSmallIcon(R.drawable.custom_icon);

        return builder.build();
    }*/

        // Replace with your desired color

        // Create a new bitmap or drawable with the desired color
     //   Bitmap iconBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.custom_icon);
      //  iconBitmap = changeBitmapColor(iconBitmap, iconColor);

        // Set the modified bitmap or drawable as the small icon
     /*   NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Zig - Travel Places Safety")
                .setContentText("Welcome Board")
                .setSmallIcon(R.drawable.custom_icon);*/




        builder.setColorized(true); // Enable colorization of the small icon
        builder.setColor(iconColor); // Set the color of the small icon

        Notification notification = builder.build();
        startForeground(NOTIFICATION_ID, notification);


        return builder.build();
    }



    private Bitmap changeBitmapColor(Bitmap bitmap, int color) {
        if (bitmap == null) {
            return null;
        }

        Bitmap modifiedBitmap = bitmap.copy(bitmap.getConfig(), true);
        Paint paint = new Paint();
        ColorFilter filter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
        paint.setColorFilter(filter);
        Canvas canvas = new Canvas(modifiedBitmap);
        canvas.drawBitmap(modifiedBitmap, 0, 0, paint);
        return modifiedBitmap;
    }

}
