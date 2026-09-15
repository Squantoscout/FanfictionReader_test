package com.spicymango.fanfictionreader.dialogs.backup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.slezica.tools.async.ManagedAsyncTask;
import com.slezica.tools.async.TaskManagerFragment;
import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.Settings;
import com.spicymango.fanfictionreader.util.FileHandler;

import android.app.AlertDialog;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Restores a previous backup created by {@link BackUpDialog}. The source file is chosen by the
 * caller (see {@link #newInstance(Uri)}) via the system file picker (Storage Access Framework),
 * which works reliably under Android's Scoped Storage rules on every modern Android version -
 * unlike scanning a fixed path on shared storage, which is blocked outright on Android 10+
 * regardless of permissions granted.
 */
public class RestoreDialog extends DialogFragment {
	private static final String ARG_SOURCE_URI = "ARG_source_uri";

	private ProgressBar bar;

	/**
	 * Creates a new instance of the dialog that will restore from the given backup file.
	 * @param sourceUri The Uri of the backup file, as returned by the system file picker.
	 */
	public static RestoreDialog newInstance(Uri sourceUri) {
		final RestoreDialog dialog = new RestoreDialog();
		final Bundle args = new Bundle();
		args.putParcelable(ARG_SOURCE_URI, sourceUri);
		dialog.setArguments(args);
		return dialog;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		setCancelable(false);

		bar = new ProgressBar(getActivity(), null,
				android.R.attr.progressBarStyleHorizontal);
		bar.setId(android.R.id.progress);
		bar.setIndeterminate(true);

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.diag_restoring);
		builder.setMessage(getString(R.string.diag_restore_progress, 0));
		builder.setView(bar);

		return builder.create();
	}

	@Override
	public void onResume() {
		super.onResume();
		startRestore();
	}

	private void startRestore() {
		// Check if the restore task has already been started
		// The ManagedAsyncTask creates the following fragment when started
		Fragment manager = getFragmentManager().findFragmentByTag(TaskManagerFragment.DEFAULT_TAG);

		if (manager == null){
			final Uri sourceUri = getArguments() != null ? (Uri) getArguments().getParcelable(ARG_SOURCE_URI) : null;
			if (sourceUri == null) {
				dismiss();
				return;
			}
			new RestoreTask(getActivity(), sourceUri).execute((Void) null);
		}
	}

	private static class RestoreTask extends
			ManagedAsyncTask<Void, Integer, Integer> {
		private int filesRestored = 0;

		private final File intFilesDir, emuFilesDir, extFilesDir;

		private final Uri sourceUri;
		private final File dataFile;

		private final boolean saveOnInternal;

		RestoreTask(FragmentActivity activity, Uri sourceUri) {
			super(activity);
			this.sourceUri = sourceUri;

			// Set up directories
			intFilesDir = activity.getFilesDir();
			emuFilesDir = FileHandler.getEmulatedFilesDir(activity);
			extFilesDir = FileHandler.getExternalFilesDir(activity);

			saveOnInternal = !Settings.shouldWriteToSD(activity) || (extFilesDir == null);

			String s = activity.getApplicationInfo().dataDir;
			dataFile = new File(s);
		}

		@Override
		protected Integer doInBackground(Void... params) {
			int result = R.string.toast_restore_successful;

			try (InputStream is = getActivity().getContentResolver().openInputStream(sourceUri)) {
				if (is == null) throw new FileNotFoundException("Unable to open the selected backup file");

				// Delete any pre-existing files, as they will be inaccessible after the restore
				deleteDir(intFilesDir);

				if (emuFilesDir != null) {
					deleteDir(emuFilesDir);
				}

				// Do not delete those on the SD card, as it may be shared across several devices
				// deleteDir(extFilesDir);

				publishProgress(filesRestored);

				// Unzip all entries. A ZipInputStream is used instead of ZipFile since the source
				// is a content Uri, not a plain file path with random access support.
				try (ZipInputStream zis = new ZipInputStream(is)) {
					ZipEntry entry;
					while ((entry = zis.getNextEntry()) != null) {
						extractEntry(entry, zis);
						zis.closeEntry();
					}
				}
				publishProgress(filesRestored);

			} catch (ZipException e) {
				result = R.string.error_corrupted;
			} catch (FileNotFoundException e) {
				result = R.string.error_backup_not_found;
			} catch (SecurityException e) {
				// The app lost permission to read the picked file - most commonly because the
				// app was killed in the background while the file picker was open (e.g. if the
				// user browsed around for a while) and the permission grant didn't survive.
				result = R.string.error_permission_denied;
			} catch (IOException e) {
				result = R.string.error_unknown;
			} catch (Exception e) {
				// A safety net: any other unexpected error should show a message rather than
				// crash the whole app.
				FirebaseCrashlytics.getInstance().recordException(e);
				result = R.string.error_unknown;
			}
			return result;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			RestoreDialog diag = (RestoreDialog) getActivity()
					.getSupportFragmentManager().findFragmentByTag(
							RestoreDialog.class.getName());
			if (diag != null) {
				diag.bar.setProgress(values[0]);
				final Dialog dialog = diag.getDialog();
				if (dialog instanceof AlertDialog) {
					((AlertDialog) dialog).setMessage(diag.getString(R.string.diag_restore_progress, values[0]));
				}
			}
		}

		@Override
		protected void onPostExecute(Integer result) {

			Toast toast = Toast.makeText(getActivity(), result,
					Toast.LENGTH_SHORT);
			toast.show();

			FragmentManager manager = getActivity().getSupportFragmentManager();

			DialogFragment diag = (DialogFragment) manager
					.findFragmentByTag(RestoreDialog.class.getName());

			diag.dismiss();

			manager.beginTransaction()
					.remove(manager
							.findFragmentByTag(TaskManagerFragment.DEFAULT_TAG))
					.commit();

		}

		@Override
		protected void onCancelled() {
			Toast toast = Toast.makeText(getActivity(),
					R.string.error_backup_not_found, Toast.LENGTH_SHORT);
			toast.show();

			FragmentManager manager = getActivity().getSupportFragmentManager();

			DialogFragment diag = (DialogFragment) manager
					.findFragmentByTag(RestoreDialog.class.getName());

			diag.dismiss();

			manager.beginTransaction()
					.remove(manager
							.findFragmentByTag(TaskManagerFragment.DEFAULT_TAG))
					.commit();
		}

		private void extractEntry(final ZipEntry entry, InputStream is)
				throws IOException {

			File output;
			String name = entry.getName();

			filesRestored++;
			if (filesRestored % 20 == 0) {
				publishProgress(filesRestored);
			}

			if (!name.contains("files")) {
				output = new File(dataFile, name);
			} else {
				if (saveOnInternal)
					output = new File(intFilesDir, name.replaceFirst("files/",
							""));
				else
					output = new File(extFilesDir, name.replaceFirst("files/",
							""));
			}
			output.getParentFile().mkdirs();

			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(output);
				final byte[] buf = new byte[1024];
				int length;
				while ((length = is.read(buf, 0, buf.length)) >= 0) {
					fos.write(buf, 0, length);
				}
			} catch (FileNotFoundException ignored) {

			} catch (IOException IOException) {
				IOException.printStackTrace();
			} finally {
				if (fos != null) {
					fos.close();
				}
			}
		}

		/**
		 * Deletes a directory and all of its contents
		 * 
		 * @param dir
		 *            The directory to delete
		 * @return True if all the files are successfully deleted, false
		 *         otherwise
		 */
		private static boolean deleteDir(File dir) {
			boolean success = true;
			if (dir.isDirectory()) {
				String[] children = dir.list();
				for (String aChildren : children) {
					success &= deleteDir(new File(dir, aChildren));
				}
			}
			return success & dir.delete();
		}

	}
}
