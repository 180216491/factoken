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

import com.matthewmitchell.peercoinj.core.Monetary;
import com.matthewmitchell.peercoinj.utils.FormatUtils;
import com.matthewmitchell.peercoinj.utils.MonetaryFormat;

import android.content.Context;
import android.graphics.Paint;
import android.support.v7.widget.AppCompatTextView;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.widget.TextView;
import com.blockchain.token.Constants;
import com.blockchain.token.util.MonetarySpannable;
import com.blockchain.token.R;

/**
 * @author Andreas Schildbach
 */
public final class CurrencyTextView extends AppCompatTextView
{
	private Monetary amount = null;

	public CurrencyTextView(final Context context)
	{
		super(context);
	}

	public CurrencyTextView(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
	}

	public void setAmount(@Nonnull final Monetary amount)
	{
		this.amount = amount;
		updateView();
	}

	@Override
	protected void onFinishInflate()
	{
		super.onFinishInflate();
		setSingleLine();
	}

	private void updateView()
	{
		setText(FormatUtils.formatStr(amount.getValue()));
	}
}
