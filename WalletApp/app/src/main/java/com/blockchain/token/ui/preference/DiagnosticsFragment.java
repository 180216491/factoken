/*
 * Copyright 2014 the original author or authors.
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

package com.blockchain.token.ui.preference;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import com.blockchain.token.Constants;
import com.blockchain.token.WalletApplication;
import com.blockchain.token.ui.DialogBuilder;
import com.blockchain.token.R;

/**
 * @author Andreas Schildbach
 */
public final class DiagnosticsFragment extends PreferenceFragment
{
	private Activity activity;
	private WalletApplication application;

	private static final String PREFS_KEY_REPORT_ISSUE = "report_issue";
	private static final String PREFS_KEY_INITIATE_RESET = "initiate_reset";

	private static final Logger log = LoggerFactory.getLogger(DiagnosticsFragment.class);

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;
		this.application = (WalletApplication) activity.getApplication();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preference_diagnostics);
	}

	@Override
	public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen, final Preference preference)
	{
		final String key = preference.getKey();

		if (PREFS_KEY_REPORT_ISSUE.equals(key))
		{
			return true;
		}
		else if (PREFS_KEY_INITIATE_RESET.equals(key))
		{
			handleInitiateReset();
			return true;
		}

		return false;
	}

	public void handleInitiateReset() {
		final DialogBuilder dialog = new DialogBuilder(activity);
		dialog.setTitle(R.string.preferences_initiate_reset_title);
		dialog.setMessage(R.string.preferences_initiate_reset_dialog_message);
		dialog.setPositiveButton(R.string.preferences_initiate_reset_dialog_positive, new OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				log.info("manually initiated blockchain reset");

				new AsyncTask<Void, Void, Void>() {
					
					private ProgressDialog progressDialog = null;
					
					@Override
					protected void onPreExecute () {
						progressDialog = ProgressDialog.show(activity, getString(R.string.reset_blockchain_title), getString(R.string.reset_blockchain_message), true, false);
					}

					@Override
					protected Void doInBackground(Void... params) {
						application.resetBlockchain();
						return null;
					}
					
					@Override
					protected  void onPostExecute(Void result) {
						progressDialog.dismiss();
						activity.finish();
					}
					
				}.execute();
				
			}
		});
		dialog.setNegativeButton(R.string.button_dismiss, null);
		dialog.show();
	}
}
