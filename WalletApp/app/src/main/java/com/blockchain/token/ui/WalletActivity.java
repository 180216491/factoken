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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import javax.annotation.Nonnull;

import com.blockchain.token.error.ErrorListActivity;
import com.blockchain.token.error.Model.ErrorModel;
import com.blockchain.token.ui.scan.ScanActivity;
import com.matthewmitchell.peercoinj.core.AddressFormatException;
import com.matthewmitchell.peercoinj.core.Transaction;
import com.matthewmitchell.peercoinj.core.VerificationException;
import com.matthewmitchell.peercoinj.core.Wallet;
import com.matthewmitchell.peercoinj.core.Wallet.BalanceType;
import com.matthewmitchell.peercoinj.store.WalletProtobufSerializer;
import com.matthewmitchell.peercoinj.wallet.Protos;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.preference.Preference;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.common.base.Charsets;

import com.blockchain.token.Configuration;
import com.blockchain.token.Constants;
import com.blockchain.token.WalletApplication;
import com.blockchain.token.data.PaymentIntent;
import com.blockchain.token.ui.InputParser.BinaryInputParser;
import com.blockchain.token.ui.InputParser.StringInputParser;
import com.blockchain.token.ui.preference.PreferenceActivity;
import com.blockchain.token.ui.send.SendCoinsActivity;
import com.blockchain.token.util.Crypto;
import com.blockchain.token.util.HttpGetThread;
import com.blockchain.token.util.Iso8601Format;
import com.blockchain.token.util.Nfc;
import com.blockchain.token.util.WalletUtils;
import com.blockchain.token.util.WholeStringBuilder;
import com.blockchain.token.R;
import com.blockchain.token.ui.RestoreWalletTask.CloseAction;
import com.matthewmitchell.peercoinj.utils.MonetaryFormat;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.text.SimpleDateFormat;

/**
 * @author Andreas Schildbach
 */
public final class WalletActivity extends AbstractWalletActivity
{
	private static final int DIALOG_BACKUP_WALLET = 1;
	private static final int DIALOG_TIMESKEW_ALERT = 2;
	private static final int DIALOG_VERSION_ALERT = 3;
	private static final int DIALOG_LOW_STORAGE_ALERT = 4;

	private WalletApplication application;
	private Configuration config;

	private Handler handler = new Handler();

	private static final int REQUEST_CODE_SCAN = 0;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		MaybeMaintenanceFragment.add(getFragmentManager());

		application = getWalletApplication();


		runAfterLoad(new Runnable() {

			@Override
			public void run() {
				
				config = application.getConfiguration();

				config.touchLastUsed();

				handleIntent(getIntent());
				
				invalidateOptionsMenu(); // Load menu properly
				
			}
		});
		
		setContentView(R.layout.wallet_content);

	}

	@Override
	protected void onResume()
	{
		super.onResume();

		runAfterLoad(new Runnable() {
			@Override
			public void run() {
				application.startBlockchainService(true);
			}
		});

		checkLowStorageAlert();
	}

	@Override
	protected void onPause()
	{
		handler.removeCallbacksAndMessages(null);
		super.onPause();
	}

	@Override
	protected void onNewIntent(final Intent intent)
	{
		handleIntent(intent);
	}

	private void handleIntent(@Nonnull final Intent intent)
	{
		final String action = intent.getAction();

		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action))
		{
			final String inputType = intent.getType();
			final NdefMessage ndefMessage = (NdefMessage) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
			final byte[] input = Nfc.extractMimePayload(Constants.MIMETYPE_TRANSACTION, ndefMessage);

			new BinaryInputParser(inputType, input)
			{
				@Override
				protected void handlePaymentIntent(final PaymentIntent paymentIntent)
				{
					cannotClassify(inputType);
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(WalletActivity.this, null, 0, messageResId, messageArgs);
				}
			}.parse();
		}
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
					SendCoinsActivity.start(WalletActivity.this, paymentIntent);
				}

				@Override
				protected void handleDirectTransaction(final Transaction tx) throws VerificationException
				{
					application.processDirectTransaction(tx);
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(WalletActivity.this, null, R.string.button_scan, messageResId, messageArgs);
				}
			}.parse();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.wallet_options, menu);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {

		super.onPrepareOptionsMenu(menu);
		
		if (!application.isLoaded())
			return false; // Wallet not loaded just yet

		final String externalStorageState = Environment.getExternalStorageState();

        boolean writable = Environment.MEDIA_MOUNTED.equals(externalStorageState);
        boolean readOnly = Environment.MEDIA_MOUNTED_READ_ONLY.equals(externalStorageState);

        menu.findItem(R.id.wallet_options_restore_wallet).setEnabled(writable || readOnly);
        menu.findItem(R.id.wallet_options_backup_wallet).setEnabled(writable);
        menu.findItem(R.id.wallet_options_export).setEnabled(writable && txListAdapter != null && !txListAdapter.transactions.isEmpty());

		return true;
	}

    private String makeEmailText(String text) {
        return text + "\n\n" + String.format(Constants.WEBMARKET_APP_URL, getPackageName()) + "\n\n" + Constants.SOURCE_URL + '\n';
    }

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.wallet_options_address_book:
				AddressBookActivity.start(this);
				return true;

            case R.id.wallet_options_export:
                handleExportTransactions();
                return true;

			case R.id.wallet_options_network_monitor:
				startActivity(new Intent(this, NetworkMonitorActivity.class));
				return true;

			case R.id.wallet_options_restore_wallet: //还原钱包
				verifyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0x01, new PermissionCallBack() {
					@Override
					public void onGranted() {
						showDialog(DIALOG_RESTORE_WALLET);
					}
					@Override
					public void onDenied() {
					}
				});
				return true;
			case R.id.wallet_options_preferences: //设置
				startActivity(new Intent(this, PreferenceActivity.class));
				return true;
			case R.id.wallet_options_safety: //风险提示
				HelpDialogFragment.page(getFragmentManager(), R.string.wallet_options_safety, R.string.help_safety);
				return true;
			case R.id.wallet_options_backup_wallet: //备份钱包
				handleBackupWallet();
				return true;
			case R.id.wallet_options_encrypt_keys: //设置密码
				handleEncryptKeys();
				return true;
			case R.id.wallet_options_help: //帮助
				HelpDialogFragment.page(getFragmentManager(), R.string.button_help, R.string.help_wallet);
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	public void handleRequestCoins()
	{
		startActivity(new Intent(this, RequestCoinsActivity.class));
	}

	public void handleSendCoins()
	{
		startActivity(new Intent(this, SendCoinsActivity.class));
	}

	public void handleScan()
	{
		verifyPermissions(Manifest.permission.CAMERA, new String[]{Manifest.permission.CAMERA}, 1, new AbstractWalletActivity.PermissionCallBack() {
			@Override
			public void onGranted() {
				startActivityForResult(new Intent(WalletActivity.this, ScanActivity.class), REQUEST_CODE_SCAN);
			}

			@Override
			public void onDenied() {

			}
		});

	}

	public void handleBackupWallet()
	{
		verifyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0x01, new PermissionCallBack() {
			@Override
			public void onGranted() {
				showDialog(DIALOG_BACKUP_WALLET);
			}

			@Override
			public void onDenied() {

			}
		});
	}

    public void handleExportTransactions() {

        // Create CSV file from transactions

        final File file = new File(Constants.Files.EXTERNAL_WALLET_BACKUP_DIR, Constants.Files.TX_EXPORT_NAME + "-" + getFileDate() + ".csv");

        try {
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            writer.append("Date,Label,Amount (" + MonetaryFormat.CODE_PPC + "),Fee (" + MonetaryFormat.CODE_PPC + "),Address,Transaction Hash,Confirmations\n");
            if (txListAdapter == null || txListAdapter.transactions.isEmpty()) {
                longToast(R.string.export_transactions_mail_intent_failed);
                return;
            }
            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm z");
            dateFormat.setTimeZone(TimeZone.getDefault());

            for (Transaction tx: txListAdapter.transactions) {

                TransactionsListAdapter.TransactionCacheEntry txCache = txListAdapter.getTxCache(tx);
                String memo = tx.getMemo() == null ? "" : tx.getMemo();
                String fee = tx.getFee() == null ? "" : tx.getFee().toPlainString();
				String address = txCache.address == null ? getString(R.string.send_to_me) : txCache.address.toString();

                writer.append(dateFormat.format(tx.getUpdateTime()) + ",");
                writer.append(memo + ",");
                writer.append(txCache.value.toPlainString() + ",");
                writer.append(fee + ",");
                writer.append(address + ",");
                writer.append(tx.getHash().toString() + ",");
                writer.append(tx.getConfidence().getDepthInBlocks() + "\n");

            }

            writer.flush();
            writer.close();

        } catch (IOException x) {
            longToast(R.string.export_transactions_mail_intent_failed);
            log.error("exporting transactions failed", x);
            return;
        }

        final DialogBuilder dialog = new DialogBuilder(this);
        dialog.setMessage(Html.fromHtml(getString(R.string.export_transactions_dialog_success, file)));

        dialog.setPositiveButton(WholeStringBuilder.bold(getString(R.string.export_keys_dialog_button_archive)), new OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int which) {

                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_transactions_mail_subject));
                intent.putExtra(Intent.EXTRA_TEXT, makeEmailText(getString(R.string.export_transactions_mail_text)));
                intent.setType(Constants.MIMETYPE_TX_EXPORT);
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));

                try {
                    startActivity(Intent.createChooser(intent, getString(R.string.export_transactions_mail_intent_chooser)));
                    log.info("invoked chooser for exporting transactions");
                } catch (final Exception x) {
                    longToast(R.string.export_transactions_mail_intent_failed);
                    log.error("exporting transactions failed", x);
                }

            }

        });

        dialog.setNegativeButton(R.string.button_dismiss, null);
        dialog.show();

    }

	public void handleEncryptKeys()
	{
		EncryptKeysDialogFragment.show(getFragmentManager());
	}

	@Override
	protected Dialog onCreateDialog(final int id, final Bundle args)
	{
		try {
			if (id == DIALOG_RESTORE_WALLET)
				return createRestoreWalletDialog();
			else if (id == DIALOG_BACKUP_WALLET)
				return createBackupWalletDialog();
			else if (id == DIALOG_TIMESKEW_ALERT)
				return createTimeskewAlertDialog(args.getLong("diff_minutes"));
			else if (id == DIALOG_VERSION_ALERT)
				return createVersionAlertDialog();
			else if (id == DIALOG_LOW_STORAGE_ALERT)
				return createLowStorageAlertDialog();
			else
				throw new IllegalArgumentException();
		}catch (Exception e){
			e.printStackTrace();
			throw new IllegalArgumentException();
		}
	}

	@Override
	protected void onPrepareDialog(final int id, final Dialog dialog) {
		try{
			if (id == DIALOG_RESTORE_WALLET)
				prepareRestoreWalletDialog(dialog);
			else if (id == DIALOG_BACKUP_WALLET)
				prepareBackupWalletDialog(dialog);
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	private Dialog createRestoreWalletDialog()
	{
		final View view = getLayoutInflater().inflate(R.layout.restore_wallet_dialog, null);
		final Spinner fileView = (Spinner) view.findViewById(R.id.import_keys_from_storage_file);
		final EditText passwordView = (EditText) view.findViewById(R.id.import_keys_from_storage_password);

		final DialogBuilder dialog = new DialogBuilder(this);
		dialog.setTitle(R.string.import_keys_dialog_title);
		dialog.setView(view);
		dialog.setPositiveButton(R.string.import_keys_dialog_button_import, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				final File file = (File) fileView.getSelectedItem();
				final String password = passwordView.getText().toString().trim();
				passwordView.setText(null); // get rid of it asap

				if (WalletUtils.BACKUP_FILE_FILTER.accept(file))
					restoreWalletFromProtobuf(file, CloseAction.CLOSE_RECREATE);
				else if (WalletUtils.KEYS_FILE_FILTER.accept(file))
					restorePrivateKeysFromBase58(file, CloseAction.CLOSE_RECREATE);
				else if (Crypto.OPENSSL_FILE_FILTER.accept(file))
					restoreWalletFromEncrypted(file, password, CloseAction.CLOSE_RECREATE);
			}
		});
		dialog.setNegativeButton(R.string.button_cancel, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});
		dialog.setOnCancelListener(new OnCancelListener()
		{
			@Override
			public void onCancel(final DialogInterface dialog)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});

		final FileAdapter adapter = new FileAdapter(this)
		{
			@Override
			public View getDropDownView(final int position, View row, final ViewGroup parent)
			{
				final File file = getItem(position);
				final boolean isExternal = Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.equals(file.getParentFile());
				final boolean isEncrypted = Crypto.OPENSSL_FILE_FILTER.accept(file);

				if (row == null)
					row = inflater.inflate(R.layout.restore_wallet_file_row, null);

				final TextView filenameView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_filename);
				filenameView.setText(file.getName());

				final TextView securityView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_security);
				final String encryptedStr = context.getString(isEncrypted ? R.string.import_keys_dialog_file_security_encrypted
						: R.string.import_keys_dialog_file_security_unencrypted);
				final String storageStr = context.getString(isExternal ? R.string.import_keys_dialog_file_security_external
						: R.string.import_keys_dialog_file_security_internal);
				securityView.setText(encryptedStr + ", " + storageStr);

				final TextView createdView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_created);
				createdView
						.setText(context.getString(isExternal ? R.string.import_keys_dialog_file_created_manual
								: R.string.import_keys_dialog_file_created_automatic, DateUtils.getRelativeTimeSpanString(context,
								file.lastModified(), true)));

				return row;
			}
		};

		fileView.setAdapter(adapter);

		return dialog.create();
	}

	private void prepareRestoreWalletDialog(final Dialog dialog)
	{
		final AlertDialog alertDialog = (AlertDialog) dialog;

		final List<File> files = new LinkedList<File>();

		// external storage
		if (Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.exists() && Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.isDirectory())
			for (final File file : Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.listFiles()) {
				
				if (!file.getName().contains("factoken"))
					continue;
				
				if (WalletUtils.BACKUP_FILE_FILTER.accept(file) || WalletUtils.KEYS_FILE_FILTER.accept(file)
						|| Crypto.OPENSSL_FILE_FILTER.accept(file))
					files.add(file);
			}

		// internal storage
		for (final String filename : fileList())
			if (filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + '.'))
				files.add(new File(getFilesDir(), filename));

		// sort
		Collections.sort(files, new Comparator<File>()
		{
			@Override
			public int compare(final File lhs, final File rhs)
			{
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});

		final View replaceWarningView = alertDialog.findViewById(R.id.restore_wallet_from_storage_dialog_replace_warning);
		final boolean hasCoins = application.getWallet().getBalance(BalanceType.ESTIMATED).signum() > 0;
		replaceWarningView.setVisibility(hasCoins ? View.VISIBLE : View.GONE);

		final Spinner fileView = (Spinner) alertDialog.findViewById(R.id.import_keys_from_storage_file);
		final FileAdapter adapter = (FileAdapter) fileView.getAdapter();
		adapter.setFiles(files);
		fileView.setEnabled(!adapter.isEmpty());

		final EditText passwordView = (EditText) alertDialog.findViewById(R.id.import_keys_from_storage_password);
		passwordView.setText(null);

		final ImportDialogButtonEnablerListener dialogButtonEnabler = new ImportDialogButtonEnablerListener(passwordView, alertDialog)
		{
			@Override
			protected boolean hasFile()
			{
				return fileView.getSelectedItem() != null;
			}

			@Override
			protected boolean needsPassword()
			{
				final File selectedFile = (File) fileView.getSelectedItem();
				return selectedFile != null ? Crypto.OPENSSL_FILE_FILTER.accept(selectedFile) : false;
			}
		};
		passwordView.addTextChangedListener(dialogButtonEnabler);
		fileView.setOnItemSelectedListener(dialogButtonEnabler);

		final CheckBox showView = (CheckBox) alertDialog.findViewById(R.id.import_keys_from_storage_show);
		showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
	}

	private Dialog createBackupWalletDialog()
	{
		final View view = getLayoutInflater().inflate(R.layout.backup_wallet_dialog, null);
		final EditText passwordView = (EditText) view.findViewById(R.id.export_keys_dialog_password);

		final DialogBuilder dialog = new DialogBuilder(this);
		dialog.setTitle(R.string.export_keys_dialog_title);
		dialog.setView(view);
		dialog.setPositiveButton(R.string.export_keys_dialog_button_export, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				final String password = passwordView.getText().toString().trim();
				passwordView.setText(null); // get rid of it asap

				backupWallet(password);

				config.disarmBackupReminder();
			}
		});
		dialog.setNegativeButton(R.string.button_cancel, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});
		dialog.setOnCancelListener(new OnCancelListener()
		{
			@Override
			public void onCancel(final DialogInterface dialog)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});
		return dialog.create();
	}

	private void prepareBackupWalletDialog(final Dialog dialog)
	{
		final AlertDialog alertDialog = (AlertDialog) dialog;

		final EditText passwordView = (EditText) alertDialog.findViewById(R.id.export_keys_dialog_password);
		passwordView.setText(null);

		final ImportDialogButtonEnablerListener dialogButtonEnabler = new ImportDialogButtonEnablerListener(passwordView, alertDialog);
		passwordView.addTextChangedListener(dialogButtonEnabler);

		final CheckBox showView = (CheckBox) alertDialog.findViewById(R.id.export_keys_dialog_show);
		showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));

        final TextView warningView = (TextView) alertDialog.findViewById(R.id.backup_wallet_dialog_warning_encrypted);
		Wallet wallet = application.getWallet();

        if (wallet == null) {
			warningView.setVisibility(View.GONE);
			runAfterLoad(new Runnable() {

				@Override
				public void run() {
					warningView.setVisibility(application.getWallet().isEncrypted() ? View.VISIBLE : View.GONE);
				}
				
			});
		}else
			warningView.setVisibility(wallet.isEncrypted() ? View.VISIBLE : View.GONE);
	}

	private void checkLowStorageAlert()
	{
		final Intent stickyIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
		if (stickyIntent != null)
			showDialog(DIALOG_LOW_STORAGE_ALERT);
	}

	private Dialog createLowStorageAlertDialog()
	{
		final DialogBuilder dialog = DialogBuilder.warn(this, R.string.wallet_low_storage_dialog_title);
		dialog.setMessage(R.string.wallet_low_storage_dialog_msg);
		dialog.setPositiveButton(R.string.wallet_low_storage_dialog_button_apps, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int id)
			{
				startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
				finish();
			}
		});
		dialog.setNegativeButton(R.string.button_dismiss, null);
		return dialog.create();
	}

	private Dialog createTimeskewAlertDialog(final long diffMinutes)
	{
		final PackageManager pm = getPackageManager();
		final Intent settingsIntent = new Intent(android.provider.Settings.ACTION_DATE_SETTINGS);

		final DialogBuilder dialog = DialogBuilder.warn(this, R.string.wallet_timeskew_dialog_title);
		dialog.setMessage(getString(R.string.wallet_timeskew_dialog_msg, diffMinutes));

		if (pm.resolveActivity(settingsIntent, 0) != null)
		{
			dialog.setPositiveButton(R.string.button_settings, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(settingsIntent);
					finish();
				}
			});
		}

		dialog.setNegativeButton(R.string.button_dismiss, null);
		return dialog.create();
	}

	private Dialog createVersionAlertDialog()
	{
		final PackageManager pm = getPackageManager();
		final Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.MARKET_APP_URL, getPackageName())));
		final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.BINARY_URL));

		final DialogBuilder dialog = DialogBuilder.warn(this, R.string.wallet_version_dialog_title);
		final StringBuilder message = new StringBuilder(getString(R.string.wallet_version_dialog_msg));
		if (Build.VERSION.SDK_INT < Constants.SDK_DEPRECATED_BELOW)
			message.append("\n\n").append(getString(R.string.wallet_version_dialog_msg_deprecated));
		dialog.setMessage(message);

		if (pm.resolveActivity(marketIntent, 0) != null)
		{
			dialog.setPositiveButton(R.string.wallet_version_dialog_button_market, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(marketIntent);
					finish();
				}
			});
		}

		if (pm.resolveActivity(binaryIntent, 0) != null)
		{
			dialog.setNeutralButton(R.string.wallet_version_dialog_button_binary, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(binaryIntent);
					finish();
				}
			});
		}

		dialog.setNegativeButton(R.string.button_dismiss, null);
		return dialog.create();
	}

    private String getFileDate() {
        final DateFormat dateFormat = Iso8601Format.newDateFormat();
        dateFormat.setTimeZone(TimeZone.getDefault());
        return dateFormat.format(new Date());
    }

	private void backupWallet(@Nonnull final String password) {

		Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.mkdirs();

        final File file = new File(Constants.Files.EXTERNAL_WALLET_BACKUP_DIR, Constants.Files.EXTERNAL_WALLET_BACKUP + "-" + getFileDate());
		final Protos.Wallet walletProto = new WalletProtobufSerializer().walletToProto(application.getWallet());

		Writer cipherOut = null;

		try
		{
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			walletProto.writeTo(baos);
			baos.close();
			final byte[] plainBytes = baos.toByteArray();

			cipherOut = new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8);
			cipherOut.write(Crypto.encrypt(plainBytes, password.toCharArray()));
			cipherOut.flush();

			final DialogBuilder dialog = new DialogBuilder(this);
			dialog.setMessage(Html.fromHtml(getString(R.string.export_keys_dialog_success, file)));
			dialog.setPositiveButton(WholeStringBuilder.bold(getString(R.string.export_keys_dialog_button_archive)), new OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int which)
				{
					archiveWalletBackup(file);
				}
			});
			dialog.setNegativeButton(R.string.button_dismiss, null);
			dialog.show();

			log.info("backed up wallet to: '" + file + "'");
		}
		catch (final IOException x)
		{
			final DialogBuilder dialog = DialogBuilder.warn(this, R.string.import_export_keys_dialog_failure_title);
			dialog.setMessage(getString(R.string.export_keys_dialog_failure, x.getMessage()));
			dialog.singleDismissButton(null);
			dialog.show();

			log.error("problem backing up wallet", x);
		}
		finally
		{
			try
			{
				cipherOut.close();
			}
			catch (final IOException x)
			{
			}
		}
	}

	private void archiveWalletBackup(@Nonnull final File file)
	{
		final Intent intent = new Intent(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_keys_dialog_mail_subject));
        intent.putExtra(Intent.EXTRA_TEXT, makeEmailText(getString(R.string.export_keys_dialog_mail_text)));
		intent.setType(Constants.MIMETYPE_WALLET_BACKUP);
		intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
		try{
			startActivity(Intent.createChooser(intent, getString(R.string.export_keys_dialog_mail_intent_chooser)));
		} catch (final Exception x){
		}
	}
}
