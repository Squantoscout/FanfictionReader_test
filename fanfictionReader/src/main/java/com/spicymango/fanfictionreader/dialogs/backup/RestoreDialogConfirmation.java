package com.spicymango.fanfictionreader.dialogs.backup;

import com.spicymango.fanfictionreader.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;


public class RestoreDialogConfirmation extends DialogFragment implements OnClickListener {
	private static final int REQUEST_OPEN_DOCUMENT = 101;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.diag_restoring);
		builder.setMessage(R.string.diag_restore_warning);
		builder.setPositiveButton(android.R.string.ok, this);
		builder.setNegativeButton(android.R.string.cancel, this);
		return builder.create();
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		switch (which) {
		case DialogInterface.BUTTON_POSITIVE:
			// Let the user pick the backup file to restore from via the system file picker,
			// rather than assuming a fixed location on shared storage (which is blocked outright
			// by Scoped Storage on Android 10+).
			final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType("*/*");
			startActivityForResult(intent, REQUEST_OPEN_DOCUMENT);
			break;
		case DialogInterface.BUTTON_NEGATIVE:
			dismiss();
		default:
			break;
		}
		
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		if (requestCode == REQUEST_OPEN_DOCUMENT) {
			dismiss();

			if (resultCode == FragmentActivity.RESULT_OK && data != null && data.getData() != null) {
				DialogFragment diag = RestoreDialog.newInstance(data.getData());
				diag.show(getFragmentManager(), diag.getClass().getName());
			}
			// If the user cancelled the picker, do nothing further.
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

}
