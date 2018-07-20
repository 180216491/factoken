/*
 * Copyright 2013-2014 the original author or authors.
 *
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
 */

package com.blockchain.token.ui;

import javax.annotation.CheckForNull;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

import com.blockchain.token.WalletApplication;
import com.blockchain.token.service.BlockchainService;
import com.blockchain.token.service.BlockchainServiceImpl;

import static junit.framework.Assert.assertTrue;

/**
 * @author Andreas Schildbach
 */
public abstract class AbstractBindServiceActivity extends AbstractWalletActivity
{
	@CheckForNull
	private BlockchainService blockchainService;

	private final ServiceConnection serviceConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(final ComponentName name, final IBinder binder)
		{
			blockchainService = ((BlockchainServiceImpl.LocalBinder) binder).getService();
		}

		@Override
		public void onServiceDisconnected(final ComponentName name)
		{
			blockchainService = null;
		}
	};

	@Override
	protected void onResume()
	{
		super.onResume();
		
		final Activity a = this;
		
		runAfterLoad(new Runnable() {

			@Override
			public void run() {
				bindService(new Intent(a, BlockchainServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE);
			}
			
		});
		
	}

	@Override
	protected void onPause()
	{
		unbindService(serviceConnection);

		super.onPause();
	}

	protected BlockchainService getBlockchainService()
	{
		return blockchainService;
	}

}
