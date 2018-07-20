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

import javax.annotation.CheckForNull;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.blockchain.token.error.ErrorListActivity;
import com.matthewmitchell.peercoinj.core.Coin;

import com.blockchain.token.WalletApplication;
import com.blockchain.token.service.BlockchainState;
import com.blockchain.token.service.BlockchainStateLoader;
import com.blockchain.token.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletBalanceFragment extends Fragment
{
	private int clickCnt = 1;
	private WalletApplication application;
	private AbstractWalletActivity activity;
	private LoaderManager loaderManager;

	private View total_layout;
	private CurrencyTextView viewBalanceTotal;
	private CurrencyTextView viewBalanceEnable;

	private TextView viewProgress;

	@CheckForNull
	private Coin totalBalance, enableBalance;

	@CheckForNull
	private BlockchainState blockchainState = null;

	private static final int ID_BALANCE_LOADER = 0;
	private static final int ID_BLOCKCHAIN_STATE_LOADER = 1;
	private static final long BLOCKCHAIN_UPTODATE_THRESHOLD_MS = DateUtils.HOUR_IN_MILLIS;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);
		this.activity = (AbstractWalletActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.loaderManager = getLoaderManager();
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		return inflater.inflate(R.layout.wallet_balance_fragment, container, false);
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		viewBalanceTotal = (CurrencyTextView) view.findViewById(R.id.wallet_balance_total);
		viewBalanceEnable = (CurrencyTextView) view.findViewById(R.id.wallet_balance_enable);
		viewProgress = (TextView) view.findViewById(R.id.wallet_progress);

		total_layout = view.findViewById(R.id.total_layout);
		total_layout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(clickCnt >= 5){
					startError();
				}else{
					clickCnt++;
				}
			}
		});
	}

	public void startError(){
		clickCnt = 1;
		Intent intent = new Intent(getActivity(), ErrorListActivity.class);
		startActivity(intent);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		activity.runAfterLoad(new Runnable() {
		    @Override
		    public void run() {
				loaderManager.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
				loaderManager.initLoader(ID_BLOCKCHAIN_STATE_LOADER, null, blockchainStateLoaderCallbacks);
			}
		});
		updateView();
	}

	@Override
	public void onPause() {
		loaderManager.destroyLoader(ID_BLOCKCHAIN_STATE_LOADER);
		loaderManager.destroyLoader(ID_BALANCE_LOADER);
		super.onPause();
	}

	private void updateView()
	{
		if (!isAdded())
			return;

		final boolean showProgress;

		if (blockchainState != null && blockchainState.loaded && blockchainState.bestChainDate != null)
		{
			final long blockchainLag = System.currentTimeMillis() - blockchainState.bestChainDate.getTime();
			final boolean blockchainUptodate = blockchainLag > BLOCKCHAIN_UPTODATE_THRESHOLD_MS;
			final boolean noImpediments = blockchainState.impediments.isEmpty();
			showProgress = blockchainUptodate;
			final String downloading = getString(noImpediments ? R.string.blockchain_state_progress_downloading
					: R.string.blockchain_state_progress_stalled);
			if (blockchainLag < 2 * DateUtils.DAY_IN_MILLIS)
			{
				final long hours = blockchainLag / DateUtils.HOUR_IN_MILLIS;
				viewProgress.setText(getString(R.string.blockchain_state_progress_hours, downloading, hours));
			}
			else if (blockchainLag < 2 * DateUtils.WEEK_IN_MILLIS)
			{
				final long days = blockchainLag / DateUtils.DAY_IN_MILLIS;
				viewProgress.setText(getString(R.string.blockchain_state_progress_days, downloading, days));
			}
			else if (blockchainLag < 90 * DateUtils.DAY_IN_MILLIS)
			{
				final long weeks = blockchainLag / DateUtils.WEEK_IN_MILLIS;
				viewProgress.setText(getString(R.string.blockchain_state_progress_weeks, downloading, weeks));
			}
			else
			{
				final long months = blockchainLag / (30 * DateUtils.DAY_IN_MILLIS);
				viewProgress.setText(getString(R.string.blockchain_state_progress_months, downloading, months));
			}
		}else
		{
			showProgress = false;
		}
		if (!showProgress){

			if(totalBalance != null){
				viewBalanceTotal.setVisibility(View.VISIBLE);
				viewBalanceTotal.setAmount(totalBalance);
			}

			if (enableBalance != null) {
				viewBalanceEnable.setVisibility(View.VISIBLE);
				// Configuration should be set now since the balance is loaded
				viewBalanceEnable.setAmount(enableBalance);
			}

			viewProgress.setVisibility(View.INVISIBLE);
		} else {
			viewProgress.setVisibility(View.VISIBLE);
		}
	}

	private final LoaderCallbacks<BlockchainState> blockchainStateLoaderCallbacks = new LoaderManager.LoaderCallbacks<BlockchainState>()
	{
		@Override
		public Loader<BlockchainState> onCreateLoader(final int id, final Bundle args)
		{
			return new BlockchainStateLoader(getActivity().getApplicationContext());
		}

		@Override
		public void onLoadFinished(final Loader<BlockchainState> loader, final BlockchainState blockchainState)
		{
			WalletBalanceFragment.this.blockchainState = blockchainState;
			updateView();
		}

		@Override
		public void onLoaderReset(final Loader<BlockchainState> loader)
		{

		}
	};

	private final LoaderCallbacks<Coin[]> balanceLoaderCallbacks = new LoaderManager.LoaderCallbacks<Coin[]>()
	{
		@Override
		public Loader<Coin[]> onCreateLoader(final int id, final Bundle args)
		{
			return new MyWalletBalanceLoader(activity, application.getWallet());
		}

		@Override
		public void onLoadFinished(final Loader<Coin[]> loader, final Coin[] balance)
		{
			WalletBalanceFragment.this.totalBalance = balance[0];
			WalletBalanceFragment.this.enableBalance = balance[1];
			updateView();
		}

		@Override
		public void onLoaderReset(final Loader<Coin[]> loader)
		{
		}
	};
}
