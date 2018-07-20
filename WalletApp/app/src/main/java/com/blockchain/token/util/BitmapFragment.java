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

package com.blockchain.token.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.blockchain.token.R;
import com.blockchain.token.ui.AbstractWalletActivity;

/**
 * @author Andreas Schildbach
 */
public class BitmapFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = BitmapFragment.class.getName();

	private static final String KEY_BITMAP = "bitmap";
	private static final String KEY_LABEL = "label";
	private static final String KEY_ADDR = "addr";
	private ClipboardManager clipboardManager;
	private Spanned label;

	public static void show(final FragmentManager fm, @Nonnull final Bitmap bitmap)
	{
		instance(bitmap, null, null).show(fm, FRAGMENT_TAG);
	}

	public static void show(final FragmentManager fm, @Nonnull final Bitmap bitmap, @Nonnull final Spanned label, String currentAddressStr)
	{
		instance(bitmap, label, currentAddressStr).show(fm, FRAGMENT_TAG);
	}

	private static BitmapFragment instance(@Nonnull final Bitmap bitmap, @Nullable final Spanned label, String currentAddressStr)
	{
		final BitmapFragment fragment = new BitmapFragment();

		final Bundle args = new Bundle();
		args.putParcelable(KEY_BITMAP, bitmap);
		if (label != null)
			args.putString(KEY_LABEL, Html.toHtml(label));
		if (currentAddressStr != null)
			args.putString(KEY_ADDR, currentAddressStr);
		fragment.setArguments(args);

		return fragment;
	}

	private Activity activity;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;

		this.clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final Bundle args = getArguments();

		final Dialog dialog = new Dialog(activity);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.bitmap_dialog);
		dialog.setCanceledOnTouchOutside(true);

		final ImageView imageView = (ImageView) dialog.findViewById(R.id.bitmap_dialog_image);
		final Bitmap bitmap = (Bitmap) args.getParcelable(KEY_BITMAP);
		imageView.setImageBitmap(bitmap);

		final TextView labelView = (TextView) dialog.findViewById(R.id.bitmap_dialog_label);
		if (args.containsKey(KEY_LABEL))
		{
			final String maybeRemoveOuterHtmlParagraph = Formats.maybeRemoveOuterHtmlParagraph(args.getString(KEY_LABEL));
			final Spanned label = Html.fromHtml(maybeRemoveOuterHtmlParagraph);
			labelView.setText(label);
			labelView.setVisibility(View.VISIBLE);
		}
		else
		{
			labelView.setVisibility(View.GONE);
		}

		final View dialogView = dialog.findViewById(R.id.bitmap_dialog_group);
		dialogView.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				String addr = args.getString(KEY_ADDR);
				if(!TextUtils.isEmpty(addr)){
					addr = addr.replaceFirst("factoken:", "");
					clipboardManager.setPrimaryClip(ClipData.newPlainText("factoken payment request", addr));
					if(getActivity() instanceof AbstractWalletActivity){
						AbstractWalletActivity abstractActivity = (AbstractWalletActivity)getActivity();
						abstractActivity.toast("已拷贝到剪切板", "");
					}
				}
				dismiss();
			}
		});

		return dialog;
	}
}
