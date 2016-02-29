package com.example.android.sunshine.app;

import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by abhinavtyagi on 28/02/16.
 */
public class WearSyncService extends WearableListenerService {

    private static final String TAG = WearSyncService.class.getSimpleName();

    private static final String WEATHER_PATH = "/weather";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                String path = dataEvent.getDataItem().getUri().getPath();
                Log.d(TAG, path);
                if (path.equals(WEATHER_PATH)) {
                    Log.d(TAG, "Sync Immediately");
                    SunshineSyncAdapter.syncImmediately(this);
                }
            }
        }
    }
}