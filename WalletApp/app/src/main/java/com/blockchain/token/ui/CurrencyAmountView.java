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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.matthewmitchell.peercoinj.core.Coin;
import com.matthewmitchell.peercoinj.core.Monetary;
import com.matthewmitchell.peercoinj.utils.MonetaryFormat;

import com.blockchain.token.util.GenericUtils;
import com.blockchain.token.util.MonetarySpannable;
import com.blockchain.token.R;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Andreas Schildbach
 */
public final class CurrencyAmountView extends FrameLayout
{
	public interface Listener
	{
		void changed();

		void focusChanged(final boolean hasFocus);
	}

    private enum CurrencyType {
        COIN, FIAT, SHAPESHIFT
    }

    private CurrencyType currencyType;
    private int smallestExponent;

	private int significantColor, lessSignificantColor, errorColor;
	private Drawable deleteButtonDrawable, contextButtonDrawable;
	private Drawable currencySymbolDrawable;
	private String localCurrencyCode = null;
	private MonetaryFormat inputFormat;
	private Monetary hint = null;
	private MonetaryFormat hintFormat = new MonetaryFormat().noCode();
	private boolean amountSigned = false;
	private boolean validateAmount = true;

    private long previousAmount = 0;

	private EditText textView;
	private View contextButton;
	private Listener listener;
	private OnClickListener contextButtonClickListener;

	public CurrencyAmountView(final Context context)
	{
		super(context);
		init(context);
	}

	public CurrencyAmountView(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
		init(context);
	}

	private void init(final Context context)
	{
		final Resources resources = context.getResources();
		significantColor = resources.getColor(R.color.fg_significant);
		lessSignificantColor = resources.getColor(R.color.fg_less_significant);
		errorColor = resources.getColor(R.color.fg_error);
		deleteButtonDrawable = resources.getDrawable(R.drawable.ic_input_delete);
	}

	public String builderStr = "";

	@Override
	protected void onFinishInflate()
	{
		super.onFinishInflate();

		final Context context = getContext();

		textView = (EditText) getChildAt(0);
		textView.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		textView.setHintTextColor(lessSignificantColor);
		textView.setHorizontalFadingEdgeEnabled(true);
		textView.setSingleLine();
		setValidateAmount(false);
		textView.addTextChangedListener(textViewListener);
		textView.setOnFocusChangeListener(textViewListener);
		// TODO: 2017/9/8   update
		textView.setFilters(new InputFilter[]{new InputFilter() {
			@Override
			public CharSequence filter(CharSequence source, int start, int end,
									   Spanned dest, int dstart, int dend) {
				SpannableStringBuilder builder = new SpannableStringBuilder(dest).replace(dstart, dend, source.subSequence(start, end));
				if (!TextUtils.isEmpty(builder.toString())) {
					Pattern p = Pattern
							.compile("^([0-9]|[1-9]\\d{0,10})([.]\\d{0,8})?$");
					Matcher matcher = p.matcher(builder.toString());
					if (matcher.matches()) {
						builderStr = builder.toString();
					} else {
						textView.setText(builderStr);
						textView.setSelection(builderStr.length());
					}
				}
				return null;
			}}
		});

		contextButton = new View(context)
		{
			@Override
			protected void onMeasure(final int wMeasureSpec, final int hMeasureSpec)
			{
				setMeasuredDimension(textView.getCompoundPaddingRight(), textView.getMeasuredHeight());
			}
		};
		final LayoutParams chooseViewParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		chooseViewParams.gravity = Gravity.RIGHT;
		contextButton.setLayoutParams(chooseViewParams);
		this.addView(contextButton);

		updateAppearance();
	}

    public void setCurrencySymbol(@Nullable final String currencyCode) {

        int symbol;

        localCurrencyCode = null;

        if (MonetaryFormat.CODE_PPC.equals(currencyCode)) {
			symbol = -1;
            currencyType = CurrencyType.COIN;
        }else if ("NBT".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_nbt;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 4;
        } else if ("BTC".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_btc;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if ("BLK".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_blk;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if ("XCP".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_xcp;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if ("DASH".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_dash;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if ("DGB".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_dgb;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if ("DOGE".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_doge;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if ("LTC".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_ltc;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if ("MINT".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_mint;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 6;
        } else if ("NMC".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_nmc;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if ("POT".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_pot;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if ("NVC".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_nvc;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if ("RDD".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_rdd;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if ("VRC".equals(currencyCode)) {
            symbol = R.drawable.currency_symbol_vrc;
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
        } else if (Arrays.asList("BTCD", "CLAM", "FTC", "GEMZ", "MSC", "SDC", "START", "SJCX", "SWARM", "USDT", "UNO").contains(currencyCode)) {
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 8;
            symbol = -1;
        } else if ("QRK".equals(currencyCode)) {
            currencyType = CurrencyType.SHAPESHIFT;
            smallestExponent = 5;
            symbol = -1;
        } else if (currencyCode != null) {
            final String currencySymbol = GenericUtils.currencySymbol(currencyCode);
            final float textSize = textView.getTextSize();
            final float smallerTextSize = textSize * (20f / 24f);
            currencySymbolDrawable = new CurrencySymbolDrawable(currencySymbol, smallerTextSize, lessSignificantColor, textSize * 0.37f);
            localCurrencyCode = currencyCode;
            currencyType = CurrencyType.FIAT;
            updateAppearance();
            return;	
        }else
            symbol = -1;

        currencySymbolDrawable = (symbol == -1) ? null : getResources().getDrawable(symbol);
        updateAppearance();

    }

	public void setInputFormat(final MonetaryFormat inputFormat)
	{
		this.inputFormat = inputFormat.noCode();
	}

    public void setHintAndFormat(final MonetaryFormat hintFormat, @Nullable final Monetary hint) {
        this.hintFormat = hintFormat.noCode();
        this.hint = hint;
        updateAppearance();
    }

	public void setHintFormat(final MonetaryFormat hintFormat)
	{
		this.hintFormat = hintFormat.noCode();
		updateAppearance();
	}

	public void setHint(@Nullable final Monetary hint)
	{
		this.hint = hint;
		updateAppearance();
	}

	public void setAmountSigned(final boolean amountSigned)
	{
		this.amountSigned = amountSigned;
	}

	public void setValidateAmount(final boolean validateAmount)
	{
		this.validateAmount = validateAmount;
	}

	public void setContextButton(final int contextButtonResId, @Nonnull final OnClickListener contextButtonClickListener)
	{
		this.contextButtonDrawable = getContext().getResources().getDrawable(contextButtonResId);
		this.contextButtonClickListener = contextButtonClickListener;

		updateAppearance();
	}

	public void setListener(@Nonnull final Listener listener)
	{
		this.listener = listener;
	}

    @CheckForNull
    public Monetary getAmount() {

        if (!isValidAmount(false))
            return null;

        final String amountStr = textView.getText().toString().trim();
        if (currencyType == CurrencyType.COIN)
            return inputFormat.parse(amountStr);
        else if (currencyType == CurrencyType.FIAT)
            return inputFormat.parseFiat(localCurrencyCode, amountStr);
        else
            return inputFormat.parseShapeShiftCoin(amountStr, smallestExponent);

    }

    private long monetaryToValue(Monetary monetary) {
        return monetary == null ? 0 : monetary.getValue();
    }

    public void setAmount(@Nullable final Monetary amount, final boolean fireListener) {

        if (!fireListener)
            textViewListener.setFire(false);

        if (amount != null)
            textView.setText(new MonetarySpannable(inputFormat, amountSigned, amount));
        else
            textView.setText(null);

        if (!fireListener)
            textViewListener.setFire(true);

        previousAmount = monetaryToValue(amount);

    }

	@Override
	public void setEnabled(final boolean enabled)
	{
		super.setEnabled(enabled);

		textView.setEnabled(enabled);

		updateAppearance();
	}

	public void setTextColor(final int color)
	{
		significantColor = color;

		updateAppearance();
	}

	public void setStrikeThru(final boolean strikeThru)
	{
		if (strikeThru)
			textView.setPaintFlags(textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
		else
			textView.setPaintFlags(textView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
	}

	public TextView getTextView()
	{
		return textView;
	}

	public void setNextFocusId(final int nextFocusId)
	{
		textView.setNextFocusDownId(nextFocusId);
		textView.setNextFocusForwardId(nextFocusId);
	}

	private boolean isValidAmount(final boolean zeroIsValid) {

        final String str = textView.getText().toString().trim();

        if (inputFormat == null)
            return false;

        try {
            if (!str.isEmpty()) {
                final Monetary amount;
                if (currencyType == CurrencyType.COIN)
                    amount = inputFormat.parse(str);
                else if (currencyType == CurrencyType.FIAT)
                    amount = inputFormat.parseFiat(localCurrencyCode, str);
                else
                    amount = inputFormat.parseShapeShiftCoin(str, smallestExponent);

                // exactly zero
                return zeroIsValid || amount.signum() > 0;
            }
        } catch (final NumberFormatException x) {
        }

        return false;

	}

	private final OnClickListener deleteClickListener = new OnClickListener()
	{
		@Override
		public void onClick(final View v)
		{
			setAmount(null, true);
			textView.requestFocus();
		}
	};

	private void updateAppearance()
	{
		final boolean enabled = textView.isEnabled();

		contextButton.setEnabled(enabled);

		final String amount = textView.getText().toString().trim();

		if (enabled && !amount.isEmpty())
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(currencySymbolDrawable, null, deleteButtonDrawable, null);
			contextButton.setOnClickListener(deleteClickListener);
		}
		else if (enabled && contextButtonDrawable != null)
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(currencySymbolDrawable, null, contextButtonDrawable, null);
			contextButton.setOnClickListener(contextButtonClickListener);
		}
		else
		{
			textView.setCompoundDrawablesWithIntrinsicBounds(currencySymbolDrawable, null, null, null);
			contextButton.setOnClickListener(null);
		}

		contextButton.requestLayout();

		textView.setTextColor(!validateAmount || isValidAmount(true) ? significantColor : errorColor);

		final Spannable hintSpannable = new MonetarySpannable(hintFormat, hint != null ? hint : Coin.ZERO).applyMarkup(null,
				MonetarySpannable.STANDARD_INSIGNIFICANT_SPANS);
		textView.setHint(hintSpannable);
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
		final Bundle state = new Bundle();
		state.putParcelable("super_state", super.onSaveInstanceState());
		state.putSerializable("amount", getAmount());
		return state;
	}

	@Override
	protected void onRestoreInstanceState(final Parcelable state) {

		if (state instanceof Bundle) {

			final Bundle bundle = (Bundle) state;

            Monetary amount = (Monetary) bundle.getSerializable("amount");
            previousAmount = monetaryToValue(amount);

			super.onRestoreInstanceState(bundle.getParcelable("super_state"));
            setAmount(amount, false);

		} else 
			super.onRestoreInstanceState(state);

	}

	private final TextViewListener textViewListener = new TextViewListener();

	private final class TextViewListener implements TextWatcher, OnFocusChangeListener
	{
		private boolean fire = true;

		public void setFire(final boolean fire)
		{
			this.fire = fire;
		}

		@Override
		public void afterTextChanged(final Editable s)
		{
			// workaround for German keyboards
			final String original = s.toString();
			final String replaced = original.replace(',', '.');
			if (!replaced.equals(original))
			{
				s.clear();
				s.append(replaced);
			}
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		@Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            updateAppearance();
            if(!TextUtils.isEmpty(s)){
            	if(s.toString().indexOf(".") == -1){
            		if(s.length() > 10){
						CharSequence maxStr = s.subSequence(0, 10);
            			textView.setText(maxStr);
						textView.setSelection(maxStr.length());
					}
				}
			}
            if (listener != null && fire && monetaryToValue(getAmount()) != previousAmount)
                listener.changed();
        }

		@Override
		public void onFocusChange(final View v, final boolean hasFocus)
		{
			if (!hasFocus)
			{
				final Monetary amount = getAmount();
				if (amount != null)
					setAmount(amount, false);
			}

			if (listener != null && fire)
				listener.focusChanged(hasFocus);
		}
	}
}
