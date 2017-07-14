package com.giovannibozzano.wakeblock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Slog;

public class WakeBlockService
{
	private static final WakeBlockService INSTANCE = new WakeBlockService();
	private static final String TAG = "WakeBlockService";
	private Messenger client = null;
	private Messenger server = null;
	private boolean serviceBound = false;
	private static volatile boolean acquire = true;
	private static final Object lock = new Object();
	private static boolean bindTime = false;
	private final Intent serviceIntent = new Intent("com.giovannibozzano.wakeblock.Service");
	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			WakeBlockService.this.server = new Messenger(service);
			WakeBlockService.this.serviceBound = true;
			try {
				Message message = Message.obtain(null, 3);
				Bundle bundle = new Bundle();
				bundle.putString("version", "1.0");
				message.setData(bundle);
				WakeBlockService.this.server.send(message);
			} catch (RemoteException exception) {
				WakeBlockService.this.server = null;
				WakeBlockService.this.serviceBound = false;
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName className)
		{
			WakeBlockService.this.server = null;
			WakeBlockService.this.serviceBound = false;
			synchronized (WakeBlockService.lock) {
				WakeBlockService.lock.notifyAll();
			}
		}
	};

	private WakeBlockService()
	{
		HandlerThread handlerThread = new HandlerThread("wakeblock_client");
		handlerThread.start();
		Handler handler = new Handler(handlerThread.getLooper())
		{
			@Override
			public void handleMessage(Message message)
			{
				switch (message.what) {
					case 0:
						synchronized (WakeBlockService.lock) {
							WakeBlockService.lock.notify();
						}
						break;
					case 1:
						synchronized (WakeBlockService.lock) {
							WakeBlockService.acquire = false;
							WakeBlockService.lock.notify();
						}
						break;
					default:
						super.handleMessage(message);
				}
			}
		};
		this.client = new Messenger(handler);
		this.serviceIntent.setPackage("com.giovannibozzano.wakeblock");
	}

	public static void injectWakeBlock()
	{
		WakeBlockService.bindTime = true;
	}

	public void bindService(final Context context)
	{
		if (!WakeBlockService.bindTime) {
			return;
		}
		WakeBlockService.bindTime = false;
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				context.bindService(WakeBlockService.this.serviceIntent, WakeBlockService.this.serviceConnection, Context.BIND_AUTO_CREATE);
			}
		}).start();
	}

	public void wakeLockUpdateProperties(IBinder lock, String mTag, String tag)
	{
		if (!this.serviceBound || mTag.equals(tag)) {
			return;
		}
		synchronized (WakeBlockService.lock) {
			try {
				Message message = Message.obtain(null, 2);
				Bundle bundle = new Bundle();
				bundle.putBinder("lock", lock);
				bundle.putString("old_tag", tag);
				bundle.putString("new_tag", tag);
				message.setData(bundle);
				message.replyTo = this.client;
				this.server.send(message);
			} catch (RemoteException exception) {
				this.server = null;
				this.serviceBound = false;
				return;
			}
			try {
				WakeBlockService.lock.wait();
			} catch (InterruptedException exception) {
				Slog.e(WakeBlockService.TAG, exception.getMessage());
			}
		}
	}

	public boolean wakeLockAcquireNew(IBinder lock, String tag, String packageName)
	{
		if (!this.serviceBound) {
			return true;
		}
		synchronized (WakeBlockService.lock) {
			WakeBlockService.acquire = true;
			try {
				Message message = Message.obtain(null, 0);
				Bundle bundle = new Bundle();
				bundle.putBinder("lock", lock);
				bundle.putString("tag", tag);
				bundle.putString("package_name", packageName);
				message.setData(bundle);
				message.replyTo = this.client;
				this.server.send(message);
			} catch (RemoteException exception) {
				this.server = null;
				this.serviceBound = false;
				WakeBlockService.acquire = true;
				return true;
			}
			try {
				WakeBlockService.lock.wait();
			} catch (InterruptedException exception) {
				Slog.e(WakeBlockService.TAG, exception.getMessage());
			}
			return WakeBlockService.acquire;
		}
	}

	public void wakeLockRelease(IBinder lock, String mTag)
	{
		if (!this.serviceBound) {
			return;
		}
		synchronized (WakeBlockService.lock) {
			try {
				Message message = Message.obtain(null, 1);
				Bundle bundle = new Bundle();
				bundle.putBinder("lock", lock);
				bundle.putString("tag", mTag);
				message.setData(bundle);
				message.replyTo = this.client;
				this.server.send(message);
			} catch (RemoteException exception) {
				this.server = null;
				this.serviceBound = false;
				return;
			}
			try {
				WakeBlockService.lock.wait();
			} catch (InterruptedException exception) {
				Slog.e(WakeBlockService.TAG, exception.getMessage());
			}
		}
	}

	public void wakeLockHandleDeath(IBinder mLock, String mTag)
	{
		if (!this.serviceBound) {
			return;
		}
		synchronized (WakeBlockService.lock) {
			try {
				Message message = Message.obtain(null, 1);
				Bundle bundle = new Bundle();
				bundle.putBinder("lock", mLock);
				bundle.putString("tag", mTag);
				message.setData(bundle);
				message.replyTo = this.client;
				this.server.send(message);
			} catch (RemoteException exception) {
				this.server = null;
				this.serviceBound = false;
				return;
			}
			try {
				WakeBlockService.lock.wait();
			} catch (InterruptedException exception) {
				Slog.e(WakeBlockService.TAG, exception.getMessage());
			}
		}
	}

	public static WakeBlockService getInstance()
	{
		return WakeBlockService.INSTANCE;
	}
}