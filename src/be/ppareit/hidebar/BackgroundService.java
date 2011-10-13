/*******************************************************************************
 * Copyright (c) 2011 Pieter Pareit.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Pieter Pareit - initial API and implementation
 ******************************************************************************/
package be.ppareit.hidebar;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;
import be.ppareit.android.GlobalTouchListener;

public class BackgroundService extends Service {

    static final String TAG = BackgroundService.class.getSimpleName();
    static final String HIDE_ACTION = "be.ppareit.hidebar.HIDE_ACTION";
    static private Intent intent = null;

    private GlobalTouchListener swipeUpTouchListener = null;

    /**
     * Only instantiate class using this method!
     */
    static public void start(Context context) {
        intent = new Intent(context, BackgroundService.class);
        context.startService(intent);
    }

    static public void stop(Context context) {
        context.stopService(intent);
        intent = null;
    }

    class HideReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "HideReceiver.onReceive");

            // we received the intent to hide the statusbar
            showBar(false);

            switch (HideBarPreferences.methodToShowBar(context)) {
            case BOTTOM_TOUCH:
                // make a touch listener, on correct touch we show the statusbar and stop
                swipeUpTouchListener = new GlobalTouchListener(BackgroundService.this) {
                    @Override
                    public void onTouchEvent(MotionEvent event) {
                        if (event.getAction() != MotionEvent.ACTION_DOWN) return;
                        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                        Display display = wm.getDefaultDisplay();
                        if (event.getY() > display.getHeight() - 20) {
                            Log.v(TAG, "Swipe Up detected");
                            showBar(true);
                            stopListening();
                            swipeUpTouchListener = null;
                        }
                    }
                };
                swipeUpTouchListener.startListening();
                break;
            case NONE:
                break;
            }
        }
    }

    @Override
    public void onCreate() {
        Log.v(TAG, "onCreate");

        // TODO: call can be removed, but this is helpfull during development
        showBar(true);

        // create the intent that can hide the statusbar
        registerReceiver(new HideReceiver(), new IntentFilter(HIDE_ACTION));
        Intent hideBarIntent = new Intent(HIDE_ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, hideBarIntent, 0);

        // create the notification used to start the intent to hide the statusbar
        NotificationManager nm =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = new Notification.Builder(this)
        .setContentTitle(getResources().getText(R.string.hidebar_notification))
        .setSmallIcon(R.drawable.ic_icon_hidebar)
        .setOngoing(true)
        .setContentIntent(pi)
        .getNotification();
        nm.notify(TAG, 0, notification);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");

        // remove the notification
        NotificationManager nm =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancelAll();

        // we where asked to stop running, so make sure the user gets back his status bar
        showBar(true);

        // also stop listening to the swipe up events
        if (swipeUpTouchListener != null) {
            swipeUpTouchListener.stopListening();
            swipeUpTouchListener = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");

        // return sticky, so android will keep our service running
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind");
        return null;
    }

    protected void showBar(boolean makeVisible) {
        try {
            if (makeVisible) {
                Process proc;
                proc = Runtime.getRuntime().exec(new String[]{
                        "am","startservice","-n","com.android.systemui/.SystemUIService"});
                proc.waitFor();
            } else {
                Process proc = Runtime.getRuntime().exec(new String[]{
                        "su","-c","service call activity 79 s16 com.android.systemui"});
                proc.waitFor();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}