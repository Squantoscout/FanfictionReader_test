package com.spicymango.fanfictionreader.dialogs.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.slezica.tools.async.ManagedAsyncTask;
import com.slezica.tools.async.TaskManagerFragment;
import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.util.FileHandler;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * A dialog that backs up all the application data files into a zip file.
 * <p>
 *     The destination is chosen by the user via the system file picker (Storage Access
 *     Framework), which works reliably under Android's Scoped Storage rules on every modern
 *     Android version - unlike writing directly to a fixed path on shared storage, which is
 *     blocked outright on Android 10+ regardless of permissions granted.
 * </p>
 */
public class BackUpDialog extends DialogFragment {
	private static final int REQUEST_CREATE_DOCUMENT = 100;
	private static final String STATE_AWAITING_PICKER = "STATE_Awaiting_picker";

	/** The default file name suggested to the user in the save-location picker.*/
	public static final String FILENAME = "FanFiction_backup.bak";

	/** The progress bar in the back up dialog*/
	private ProgressBar mBar;

	/**
	 * True if the file picker has been launched but has not yet returned a result. Used to avoid
	 * re-launching the picker every time the dialog is resumed (e.g. after a configuration
	 * change).
	 */
	private boolean awaitingPicker;

	@Override
	@NonNull
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		// Once the backup process starts, it cannot be interrupted.
		setCancelable(false);

		// Add a progress bar to the dialog
		mBar = new ProgressBar(getActivity(), null, android.R.attr.progressBarStyleHorizontal);
		mBar.setId(android.R.id.progress);

		// Create the dialog
		final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.diag_back_up);
		builder.setMessage(R.string.diag_back_up_message);
		builder.setView(mBar);

		awaitingPicker = savedInstanceState != null && savedInstanceState.getBoolean(STATE_AWAITING_PICKER, false);

		return builder.create();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putBoolean(STATE_AWAITING_PICKER, awaitingPicker);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onResume() {
		super.onResume();

		// Only launch the picker once; onActivityResult takes over from there.
		if (!awaitingPicker) {
			awaitingPicker = true;

			final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType("application/octet-stream");
			intent.putExtra(Intent.EXTRA_TITLE, FILENAME);
			startActivityForResult(intent, REQUEST_CREATE_DOCUMENT);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		if (requestCode == REQUEST_CREATE_DOCUMENT) {
			awaitingPicker = false;

			if (resultCode == FragmentActivity.RESULT_OK && data != null && data.getData() != null) {
				startBackUpTask(data.getData());
			} else {
				// The user backed out of the picker without choosing a location.
				dismiss();
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	private void startBackUpTask(Uri destination){
		// Start the managed async task if it has not been started already.
		Fragment manager = getFragmentManager().findFragmentByTag(TaskManagerFragment.DEFAULT_TAG);
		if (manager == null) {
			new BackUpTask(getActivity(), destination).execute((Void)null);
		}
	}

	/**
	 * A ManagedAsyncTask that will perform a backup.
	 */
	private final static class BackUpTask extends ManagedAsyncTask<Void, Integer, Integer>{
		private int mTotalFiles = 0;
		private int mZippedFiles = 0;

		private final File app_internal[];
		private final Uri destination;

		private final ArrayList<File> appFiles;

		BackUpTask(FragmentActivity activity, Uri destination) {
			super(activity);
			this.destination = destination;

			String s = activity.getApplicationInfo().dataDir;
			app_internal = new File(s).listFiles(new FilesDirFilter());

			appFiles = new ArrayList<>(3);

			// Get the path of all the app files in both the sd card, the emulated memory, and the
			// internal memory. These are the app's own directories, which remain fully accessible
			// regardless of Scoped Storage restrictions on shared storage.
			appFiles.add(activity.getFilesDir());

			if (FileHandler.isExternalStorageWritable(activity)) {
				appFiles.add(FileHandler.getExternalFilesDir(activity));
			}

			if (FileHandler.isEmulatedFilesDirWritable()) {
				final File emulatedDir = FileHandler.getEmulatedFilesDir(activity);
				if (emulatedDir != null)
					appFiles.add(emulatedDir);
			}
		}

		@Override
		protected Integer doInBackground(Void... params) {
			int result = R.string.toast_back_up;

			//Count all non-story files
			for (File f : app_internal) {
				mTotalFiles += countFiles(f);
			}

			//Count all story files
			for (File f : appFiles) {
				mTotalFiles += countFiles(f);
			}

			// Set the maximum possible progress in the progress bar
			publishProgress(mZippedFiles);

			ZipOutputStream zos = null;

			byte[] buffer = new byte[1024];

			try {
				final OutputStream os = getActivity().getContentResolver().openOutputStream(destination);
				if (os == null) throw new IOException("Unable to open the selected destination");
				zos = new ZipOutputStream(os);

				// Zip all files
				for (File f : app_internal) {
					zipDir(zos, f, buffer, f.getName());
				}

				for (File f : appFiles) {
					zipDir(zos, f, buffer, f.getName());
				}

			} catch (IOException e) {
				FirebaseCrashlytics.getInstance().recordException(e);
				result = R.string.error_unknown;
			} finally {
				// Note that ZipOutputStream closes the underlying OutputStream
				try {
					if (zos != null)
						zos.close();
				} catch (IOException e) {
					FirebaseCrashlytics.getInstance().recordException(e);
					result = R.string.error_unknown;
				}
			}
			return result;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			final FragmentManager manager = getActivity().getSupportFragmentManager();
			BackUpDialog dialog = (BackUpDialog) manager.findFragmentByTag(BackUpDialog.class.getName());

			// On the first progress update, set the progress bar maximum
			if (values[0] == 0) {
				dialog.mBar.setMax(mTotalFiles);
			}

			dialog.mBar.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Integer result) {
			Toast toast = Toast.makeText(getActivity(), result, Toast.LENGTH_SHORT);
			toast.show();

			FragmentManager manager = getActivity().getSupportFragmentManager();

			DialogFragment dialog = (DialogFragment) manager
					.findFragmentByTag(BackUpDialog.class.getName());

			dialog.dismiss();

			manager.beginTransaction()
					.remove(manager
							.findFragmentByTag(TaskManagerFragment.DEFAULT_TAG))
					.commit();

		}

		/**
		 * Zips all the files and folders present in the supplied directory
		 * @param zos The zipOutputStream
		 * @param dir The parent directory
		 * @param buffer A buffer for the zipping process
		 * @param parent The name of the parent path
		 * @throws IOException If an error occurs while writing the backup file
		 */
		private void zipDir(ZipOutputStream zos, File dir, byte[] buffer, String parent) throws IOException{

			// If the file is not a directory, do not try to zip it.
			if (!dir.isDirectory()) {
				return;
			}

			// In theory, files[] shouldn't be null since the check above should show that dir is a
			// directory. However, some phones (MYPHONE, MID, and ZTE) will return null on listFiles,
			// hence the check.
			final File[] files = dir.listFiles();
			if (files == null) return;

			for (File file : files) {
				if (file.isDirectory()) {
					// Recursively zip directories
					zipDir(zos, file, buffer, parent + '/' + file.getName());
				} else {
					try (FileInputStream in = new FileInputStream(file)) {
						final ZipEntry entry = new ZipEntry(parent + '/' + file.getName());
						zos.putNextEntry(entry);

						// Zip the individual file
						int length;
						while ((length = in.read(buffer)) > 0) {
							zos.write(buffer, 0, length);
						}
						zos.closeEntry();

						// Update the progress bar
						mZippedFiles++;
						publishProgress(mZippedFiles);
					} catch (IOException e) {
						throw new IOException(e.getMessage());
					}
				}
			}
		}

		/**
		 * Counts how many files are contained in a folder
		 * @param folder The parent folder
		 * @return The total number of files inside the folder
		 */
		private static int countFiles(File folder){
			int count = 0;
			File[] files = folder.listFiles();

			if (files == null) return 0;

			for (File file : files) {
				if (file.isDirectory()) {
					count += countFiles(file);
				}else{
					count++;
				}
			}
			return count;
		}

		/**
		 * A simple file filter that separates saved files from the database and
		 * the settings.
		 *
		 * @author Michael Chen
		 */
		private final static class FilesDirFilter implements java.io.FilenameFilter{
			@Override
			public boolean accept(File dir, String filename) {
				return !filename.equalsIgnoreCase("Files");
			}
		}
	}
}
