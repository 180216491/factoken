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

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import com.blockchain.token.ui.TransactionsListFragment.Direction;
import com.blockchain.token.util.ViewPagerTabs;
import com.blockchain.token.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletTransactionsFragment extends Fragment
{
	private static final int INITIAL_PAGE = 1;

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.wallet_transactions_fragment, container, false);
		final ViewPager pager = (ViewPager) view.findViewById(R.id.transactions_pager);
		final RadioGroup group = (RadioGroup) view.findViewById(R.id.transactions_group);
		group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if(checkedId==R.id.received){
					pager.setCurrentItem(0);
				}else if(checkedId==R.id.all){
					pager.setCurrentItem(1);
				}else{
					pager.setCurrentItem(2);
				}
			}
		});
		final PagerAdapter pagerAdapter = new PagerAdapter(getFragmentManager());
		pager.setAdapter(pagerAdapter);
		pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

			}
			@Override
			public void onPageSelected(int position) {
				if(position == 0){
					group.check(R.id.received);
				}else if(position == 1){
					group.check(R.id.all);
				}else {
					group.check(R.id.sent);
				}
			}
			@Override
			public void onPageScrollStateChanged(int state) {

			}
		});
		pager.setCurrentItem(INITIAL_PAGE);
		pager.setPageMargin(2);
		pager.setPageMarginDrawable(R.color.bg_less_bright);
		return view;
	}

	private static class PagerAdapter extends FragmentStatePagerAdapter
	{
		public PagerAdapter(final FragmentManager fm)
		{
			super(fm);
		}

		@Override
		public int getCount()
		{
			return 3;
		}

		@Override
		public Fragment getItem(final int position)
		{
			Direction direction = null;
			if (position == 0)
				direction = Direction.RECEIVED;
			else if (position == 1)
				direction = null;
			else if (position == 2)
				direction = Direction.SENT;

			return TransactionsListFragment.instance(direction);
		}
	}

}
