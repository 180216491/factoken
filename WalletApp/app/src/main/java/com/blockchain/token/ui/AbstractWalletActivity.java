/*
 * Copyright 2011-2014 the original author or authors.
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

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.blockchain.token.WalletApplication;
import com.blockchain.token.R;
import com.blockchain.token.ui.RestoreWalletTask.CloseAction;

import java.io.File;
import java.io.InputStream;

/**
 * @author Andreas Schildbach
 */
public abstract class AbstractWalletActivity extends LoaderActivity
{
	protected static final int DIALOG_RESTORE_WALLET = 0;
	private WalletApplication application;
	
	protected RestoreWalletTask restoreTask = null;
    public TransactionsListAdapter txListAdapter = null;

    public void setTxListAdapter(TransactionsListAdapter txListAdapter){
		this.txListAdapter = txListAdapter;
	}

	protected static final Logger log = LoggerFactory.getLogger(AbstractWalletActivity.class);

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		application = (WalletApplication) getApplication();

		super.onCreate(savedInstanceState);
	}

	protected WalletApplication getWalletApplication()
	{
		return application;
	}

	public final void toast(@Nonnull final String text, final Object... formatArgs)
	{
		toast(text, 0, Toast.LENGTH_SHORT, formatArgs);
	}

	public final void longToast(@Nonnull final String text, final Object... formatArgs)
	{
		toast(text, 0, Toast.LENGTH_LONG, formatArgs);
	}

	public final void toast(@Nonnull final String text, final int imageResId, final int duration, final Object... formatArgs)
	{
		final View view = getLayoutInflater().inflate(R.layout.transient_notification, null);
		TextView tv = (TextView) view.findViewById(R.id.transient_notification_text);
		tv.setText(String.format(text, formatArgs));
		tv.setCompoundDrawablesWithIntrinsicBounds(imageResId, 0, 0, 0);

		final Toast toast = new Toast(this);
		toast.setView(view);
		toast.setDuration(duration);
		toast.show();
	}

	public final void toast(final int textResId, final Object... formatArgs)
	{
		toast(textResId, 0, Toast.LENGTH_SHORT, formatArgs);
	}

	public final void longToast(final int textResId, final Object... formatArgs)
	{
		toast(textResId, 0, Toast.LENGTH_LONG, formatArgs);
	}

	public final void toast(final int textResId, final int imageResId, final int duration, final Object... formatArgs)
	{
		final View view = getLayoutInflater().inflate(R.layout.transient_notification, null);
		TextView tv = (TextView) view.findViewById(R.id.transient_notification_text);
		tv.setText(getString(textResId, formatArgs));
		tv.setCompoundDrawablesWithIntrinsicBounds(imageResId, 0, 0, 0);

		final Toast toast = new Toast(this);
		toast.setView(view);
		toast.setDuration(duration);
		toast.show();
	}
	
	protected void restoreWalletFromEncrypted(@Nonnull final InputStream cipher, @Nonnull final String password, final CloseAction closeAction) {
		restoreTask = new RestoreWalletTask();
		restoreTask.restoreWalletFromEncrypted(cipher, password, this, closeAction);
	}
	
	protected void restoreWalletFromEncrypted(@Nonnull final File file, @Nonnull final String password, final CloseAction closeAction) {
		restoreTask = new RestoreWalletTask();
		restoreTask.restoreWalletFromEncrypted(file, password, this, closeAction);
	}

	protected void restoreWalletFromProtobuf(@Nonnull final File file, final CloseAction closeAction) {
		restoreTask = new RestoreWalletTask();
		restoreTask.restoreWalletFromProtobuf(file, this, closeAction);
	}

	protected void restorePrivateKeysFromBase58(@Nonnull final File file, final CloseAction closeAction) {
		restoreTask = new RestoreWalletTask();
		restoreTask.restorePrivateKeysFromBase58(file, this, closeAction);
	}
	
	@Override
	protected void onStop() {
		if (restoreTask != null) { 
			restoreTask.cancel(false);
			restoreTask = null;
		}
		super.onStop();
	}

	// TODO: 2017/9/1   update
	private int requestCode;
	private PermissionCallBack callBack;

	public void verifyPermissions(String permission, String[] reqestPermissions, int requestCode, PermissionCallBack callBack) {
		this.requestCode = requestCode;
		this.callBack = callBack;
		int checkPermission = ActivityCompat.checkSelfPermission(this, permission);
		if (checkPermission != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(
					this,
					reqestPermissions,
					requestCode
			);
		}else {
			callBack.onGranted();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if(this.requestCode == requestCode){
			if (grantResults != null && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				// Permission Granted
				callBack.onGranted();
			} else {
				// Permission Denied
				callBack.onDenied();
			}
		}
	}

	public interface PermissionCallBack {

		void onGranted();

		void onDenied();
	}
}
