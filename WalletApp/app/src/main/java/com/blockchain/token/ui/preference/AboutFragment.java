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

import com.blockchain.token.error.ErrorListActivity;
import com.blockchain.token.error.Model.ErrorModel;
import com.matthewmitchell.peercoinj.core.VersionMessage;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;

import com.blockchain.token.Constants;
import com.blockchain.token.WalletApplication;
import com.blockchain.token.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Andreas Schildbach
 */
public final class AboutFragment extends PreferenceFragment{

	private WalletApplication application;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		application = (WalletApplication)context.getApplicationContext();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preference_about);

		findPreference("about_version").setSummary("v"+application.packageInfo().versionName);

	}

}

