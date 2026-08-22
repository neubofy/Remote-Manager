package com.neubofy.remotemanager.BroadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.neubofy.remotemanager.Services.TriggerService;

public class BootReciever extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            new TriggerService(context).queueTrigger();
        }
    }
}
