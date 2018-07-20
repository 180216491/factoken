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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.matthewmitchell.peercoinj.core.Address;
import com.matthewmitchell.peercoinj.core.Transaction;
import com.matthewmitchell.peercoinj.core.Wallet;
import com.matthewmitchell.peercoinj.uri.PeercoinURI;

import com.blockchain.token.AddressBookProvider;
import com.blockchain.token.Constants;
import com.blockchain.token.WalletApplication;
import com.blockchain.token.util.BitmapFragment;
import com.blockchain.token.util.Qr;
import com.blockchain.token.util.WalletUtils;
import com.blockchain.token.util.WholeStringBuilder;
import com.blockchain.token.R;

/**
 * @author  
 */
public final class MineAddressesFragment extends FancyListFragment
{
	private Wallet wallet;
	private AddressBookActivity activity;
	private WalletApplication application;
	private ClipboardManager clipboardManager;
	private MineAddressesAdapter adapter;
	private LoaderManager loaderManager;
	LocalBroadcastManager broadcastManager;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);
		this.activity = (AddressBookActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.wallet = application.getWallet();
		this.clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
		this.loaderManager = getLoaderManager();
		
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		this.broadcastManager = LocalBroadcastManager.getInstance(getActivity().getApplicationContext());
		broadcastManager.registerReceiver(walletChangeReceiver, new IntentFilter(WalletApplication.ACTION_WALLET_CHANGED));
		adapter = new MineAddressesAdapter(activity);
		setListAdapter(adapter);
		activity.runAfterLoad(new Runnable() {
			@Override
			public void run() {

				loaderManager.initLoader(1, null, callbacks);
			}
		});
	}

	private final BroadcastReceiver walletChangeReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			loaderManager.restartLoader(1, null, callbacks);
		}
	};

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(loaderManager != null){
			loaderManager.destroyLoader(1);
		}
		if(broadcastManager != null){
			broadcastManager.unregisterReceiver(walletChangeReceiver);
		}
	}

	LoaderManager.LoaderCallbacks<List<Address>> callbacks = new LoaderManager.LoaderCallbacks<List<Address>>() {
		@Override
		public Loader<List<Address>> onCreateLoader(int i, Bundle bundle) {
			return new AddressLoader(getActivity(), wallet);
		}

		@Override
		public void onLoadFinished(Loader<List<Address>> loader, List<Address> addresses) {
			adapter.replace(addresses);
		}

		@Override
		public void onLoaderReset(Loader<List<Address>> loader) {

		}
	};

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		setEmptyText(WholeStringBuilder.bold(getString(R.string.address_book_empty_text)));
	}

	@Override
	public void onResume()
	{
		super.onResume();
		updateView();
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater)
	{
		inflater.inflate(R.menu.wallet_addresses_fragment_options, menu);

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		activity.startActionMode(new ActionMode.Callback()
		{
			@Override
			public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
			{
				final MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.wallet_addresses_context, menu);
				return true;
			}

			@Override
			public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
			{
				final String label = AddressBookProvider.resolveLabel(activity, adapter.getItem(position).toString());
				mode.setTitle(label != null ? label : WalletUtils.formatHash(adapter.getItem(position).toString(), Constants.ADDRESS_FORMAT_GROUP_SIZE, 0));
				return true;
			}

			@Override
			public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
			{
				switch (item.getItemId())
				{
					case R.id.wallet_addresses_context_edit:
						handleEdit(getAddress(position));

						mode.finish();
						return true;

					case R.id.wallet_addresses_context_show_qr:
						handleShowQr(getAddress(position));

						mode.finish();
						return true;

					case R.id.wallet_addresses_context_copy_to_clipboard:
						handleCopyToClipboard(getAddress(position));

						mode.finish();
						return true;

					case R.id.wallet_addresses_context_browse:
//						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.EXPLORE_BASE_URL + "address/"
//								+ getAddress(position).toString())));
//						mode.finish();
						String url = Constants.EXPLORE_BASE_URL + "address/" + getAddress(position).toString();
						Intent intent = new Intent(getActivity(), WebViewActivity.class);
						intent.putExtra("url", url);
						startActivity(intent);
						mode.finish();
						return true;
				}

				return false;
			}

			@Override
			public void onDestroyActionMode(ActionMode actionMode) {

			}

			private Address getAddress(final int position)
			{
				return adapter.getItem(position);
			}

			private void handleEdit(@Nonnull final Address address)
			{
				EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
			}

			private void handleShowQr(@Nonnull final Address address)
			{
				final String uri = PeercoinURI.convertToPeercoinURI(address, null, null, null);
				final int size = getResources().getDimensionPixelSize(R.dimen.bitmap_dialog_qr_size);
				BitmapFragment.show(getFragmentManager(), Qr.bitmap(uri, size));
			}

			private void handleCopyToClipboard(@Nonnull final Address address)
			{
				clipboardManager.setPrimaryClip(ClipData.newPlainText("clccoin address", address.toString()));
				activity.toast(R.string.wallet_address_fragment_clipboard_msg);
			}

		});
	}

	private void updateView()
	{
		ListAdapter adapter = getListAdapter();
		if (adapter != null)
			((BaseAdapter) adapter).notifyDataSetChanged();
	}

	public static class AddressLoader extends AsyncTaskLoader<List<Address>>
	{

		private final Wallet wallet;

		public AddressLoader(final Context context, @Nonnull final Wallet wallet)
		{
			super(context);
			Log.i(" ", "------AddressLoader");
			this.wallet = wallet;
		}

		@Override
		protected void onStartLoading() {
			super.onStartLoading();
			forceLoad();
		}

		@Override
		public List<Address> loadInBackground()
		{
			Address address = wallet.currentReceiveAddress();
//			final Set<Transaction> transactions = wallet.getTransactions(true);
			List<Address> addrList = new ArrayList<>();
			addrList.add(address);
//			for (final Transaction tx : transactions)
//			{
//				final boolean sent = tx.getValue(wallet).signum() < 0;
//				final boolean isInternal = tx.getPurpose() == Transaction.Purpose.KEY_ROTATION;
//				if (!sent && !isInternal){
//					Address item = WalletUtils.getWalletAddressOfReceived(tx, wallet);
//					if(item != null){
//						addrList.add(item);
//					}
//				}
//			}
			return addrList;
		}
	}
}
