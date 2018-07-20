/*
 * Copyright 2012-2014 the original author or authors.
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

import javax.annotation.Nonnull;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.matthewmitchell.peercoinj.core.Address;

import com.blockchain.token.AddressBookProvider;
import com.blockchain.token.Constants;
import com.blockchain.token.util.WalletUtils;
import com.blockchain.token.R;

/**
 * @author  
 */
public class MineAddressesAdapter extends BaseAdapter
{
	private final Context context;
	private final int colorInsignificant;
	private final LayoutInflater inflater;

	private final List<Address> list = new ArrayList<>();

	public MineAddressesAdapter(final Context context)
	{
		final Resources res = context.getResources();

		this.context = context;
		colorInsignificant = res.getColor(R.color.fg_insignificant);
		inflater = LayoutInflater.from(context);
	}

	public void add(@Nonnull  Address address)
	{
		this.list.add(address);
		notifyDataSetChanged();
	}

	public void addAll(@Nonnull List<Address> list)
	{
		this.list.addAll(list);
		notifyDataSetChanged();
	}

	public void replace(@Nonnull List<Address> list)
	{
		this.list.clear();
		this.list.addAll(list);
		notifyDataSetChanged();
	}

	@Override
	public int getCount()
	{
		return list.size();
	}

	@Override
	public Address getItem(final int position)
	{
		return list.get(position);
	}

	@Override
	public long getItemId(final int position)
	{
		return list.get(position).hashCode();
	}

	@Override
	public boolean hasStableIds()
	{
		return true;
	}

	@Override
	public View getView(final int position, View row, final ViewGroup parent)
	{
		Address address = (Address) getItem(position);

		if (row == null){
			row = inflater.inflate(R.layout.address_book_row, null);
		}

		final TextView addressView = (TextView) row.findViewById(R.id.address_book_row_address);
		addressView.setText(WalletUtils.formatAddress(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));

		final TextView labelView = (TextView) row.findViewById(R.id.address_book_row_label);
		final String label = AddressBookProvider.resolveLabel(context, address.toString());
		if (label != null)
		{
			labelView.setText(label);
		}
		else
		{
			labelView.setText(R.string.address_unlabeled);
			labelView.setTextColor(colorInsignificant);
		}
		return row;
	}
}
