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

import javax.annotation.Nonnull;

import com.blockchain.token.ui.scan.ScanActivity;
import com.matthewmitchell.peercoinj.core.Transaction;
import com.matthewmitchell.peercoinj.core.VerificationException;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

import com.blockchain.token.WalletApplication;
import com.blockchain.token.data.PaymentIntent;
import com.blockchain.token.ui.InputParser.StringInputParser;
import com.blockchain.token.ui.send.SendCoinsActivity;

/**
 * @author zm
 */
public final class SendCoinsQrActivity extends Activity
{
	private static final int REQUEST_CODE_SCAN = 0;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		verifyPermissions(Manifest.permission.CAMERA, new String[]{Manifest.permission.CAMERA}, 1, new AbstractWalletActivity.PermissionCallBack() {
			@Override
			public void onGranted() {
				startActivityForResult(new Intent(SendCoinsQrActivity.this, ScanActivity.class), REQUEST_CODE_SCAN);
			}
			@Override
			public void onDenied() {

			}
		});
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
		if (requestCode == REQUEST_CODE_SCAN && resultCode == Activity.RESULT_OK)
		{
			final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

			new StringInputParser(input)
			{
				@Override
				protected void handlePaymentIntent(@Nonnull final PaymentIntent paymentIntent)
				{
					SendCoinsActivity.start(SendCoinsQrActivity.this, paymentIntent);

					SendCoinsQrActivity.this.finish();
				}

				@Override
				protected void handleDirectTransaction(final Transaction transaction) throws VerificationException
				{
					final WalletApplication application = (WalletApplication) getApplication();
					application.processDirectTransaction(transaction);

					SendCoinsQrActivity.this.finish();
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(SendCoinsQrActivity.this, dismissListener, 0, messageResId, messageArgs);
				}

				private final OnClickListener dismissListener = new OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int which)
					{
						SendCoinsQrActivity.this.finish();
					}
				};
			}.parse();
		}
		else
		{
			finish();
		}
	}

	private int requestCode;

	private AbstractBindServiceActivity.PermissionCallBack callBack;

	public void verifyPermissions(String permission, String[] reqestPermissions, int requestCode, AbstractBindServiceActivity.PermissionCallBack callBack) {
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
}
