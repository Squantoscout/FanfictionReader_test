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
import android.content.Context;
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
 * A dialog that backs up all the application data files into a zip file.
 * <p>
 *     The destination is chosen by the user via the system file picker (Storage Access
 *     Framework), which works reliably under Android's Scoped Storage rules on every modern
 *     Android version - unlike writing directly to a fixed path on shared storage, which is
 *     blocked outright on Android 10+ regardless of permissions granted.
 * </p>
 */
public class BackUpDialog extends DialogFragment {
	private static final String ARG_DESTINATION_URI = "ARG_destination_uri";

	/** The default file name suggested to the user in the save-location picker.*/
	public static final String FILENAME = "FanFiction_backup.bak";

	/** The progress bar in the back up dialog*/
	private ProgressBar mBar;

	/**
	 * Creates a new instance of the dialog that will back up to the given destination.
	 * @param destinationUri The Uri of the destination file, as returned by the system file picker.
	 */
	public static BackUpDialog newInstance(Uri destinationUri) {
		final BackUpDialog dialog = new BackUpDialog();
		final Bundle args = new Bundle();
		args.putParcelable(ARG_DESTINATION_URI, destinationUri);
		dialog.setArguments(args);
		return dialog;
	}

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

		return builder.create();
	}

	@Override
	public void onResume() {
		super.onResume();

		final Uri destination = getArguments() != null ? (Uri) getArguments().getParcelable(ARG_DESTINATION_URI) : null;
		if (destination == null) {
			dismiss();
			return;
		}
		startBackUpTask(destination);
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

		/**
		 * The application context, captured up front in the constructor while the Activity is
		 * definitely still alive, rather than calling getActivity() again later from the
		 * background thread. See the equivalent field in RestoreDialog for why this matters.
		 */
		private final Context appContext;

		/**
		 * Holds a human-readable description of the most recent failure, so it can be shown to
		 * the user directly rather than only being logged remotely.
		 */
		private String lastErrorDetail;

		BackUpTask(FragmentActivity activity, Uri destination) {
			super(activity);
			this.destination = destination;
			this.appContext = activity.getApplicationContext();

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
				final OutputStream os = appContext.getContentResolver().openOutputStream(destination);
				if (os == null) throw new IOException("Unable to open the selected destination");
				zos = new ZipOutputStream(os);

				// Zip all files
				for (File f : app_internal) {
					zipDir(zos, f, buffer, f.getName());
				}

				for (File f : appFiles) {
					zipDir(zos, f, buffer, f.getName());
				}

				publishProgress(mZippedFiles);

			} catch (IOException e) {
				FirebaseCrashlytics.getInstance().recordException(e);
				result = R.string.error_unknown;
				lastErrorDetail = e.getClass().getSimpleName() + ": " + e.getMessage();
			} catch (SecurityException e) {
				// The app lost permission to write to the picked destination - most commonly
				// because the app was killed in the background while the file picker was open.
				result = R.string.error_permission_denied;
				lastErrorDetail = e.getMessage();
			} catch (Exception e) {
				// A safety net: any other unexpected error should show a message rather than
				// crash the whole app.
				FirebaseCrashlytics.getInstance().recordException(e);
				result = R.string.error_unknown;
				lastErrorDetail = e.getClass().getSimpleName() + ": " + e.getMessage();
			} finally {
				// Note that ZipOutputStream closes the underlying OutputStream
				try {
					if (zos != null)
						zos.close();
				} catch (IOException e) {
					FirebaseCrashlytics.getInstance().recordException(e);
					result = R.string.error_unknown;
					lastErrorDetail = e.getClass().getSimpleName() + ": " + e.getMessage();
				}
			}
			return result;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			if (getActivity() == null) return;
			final FragmentManager manager = getActivity().getSupportFragmentManager();
			BackUpDialog dialog = (BackUpDialog) manager.findFragmentByTag(BackUpDialog.class.getName());
			if (dialog == null) return;

			// On the first progress update, set the progress bar maximum
			if (values[0] == 0) {
				dialog.mBar.setMax(mTotalFiles);
			}

			dialog.mBar.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Integer result) {
			if (getActivity() == null) return;

			final String message = lastErrorDetail == null || lastErrorDetail.isEmpty()
					? getActivity().getString(result)
					: getActivity().getString(result) + "\n\n" + lastErrorDetail;

			new AlertDialog.Builder(getActivity())
					.setTitle(R.string.diag_back_up)
					.setMessage(message)
					.setPositiveButton(android.R.string.ok, null)
					.show();

			FragmentManager manager = getActivity().getSupportFragmentManager();

			DialogFragment dialog = (DialogFragment) manager
					.findFragmentByTag(BackUpDialog.class.getName());

			if (dialog != null) dialog.dismiss();

			final Fragment taskManagerFragment = manager.findFragmentByTag(TaskManagerFragment.DEFAULT_TAG);
			if (taskManagerFragment != null) {
				manager.beginTransaction().remove(taskManagerFragment).commit();
			}

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
				} else if (shouldIncludeFile(file)) {
					try (FileInputStream in = new FileInputStream(file)) {
						final ZipEntry entry = new ZipEntry(parent + '/' + file.getName());
						zos.putNextEntry(entry);

						// Zip the individual file
						int length;
						while ((length = in.read(buffer)) > 0) {
							zos.write(buffer, 0, length);
						}
						zos.closeEntry();

						// Update the progress bar (throttled to avoid flooding the main thread
						// with updates when backing up a library with many thousands of files)
						mZippedFiles++;
						if (mZippedFiles % 20 == 0) {
							publishProgress(mZippedFiles);
						}
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
				}else if (shouldIncludeFile(file)){
					count++;
				}
			}
			return count;
		}

		/**
		 * A simple file filter that only allows the app's databases and shared preferences
		 * folders through, since those are the only parts of the internal data directory that
		 * actually matter for restoring a library. Everything else under the app's internal
		 * directory (WebView cache, analytics databases, temporary files, etc.) is excluded, since
		 * including it made backups needlessly enormous (hundreds of MB, tens of thousands of
		 * files) without containing anything useful to restore.
		 *
		 * @author Michael Chen
		 */
		private final static class FilesDirFilter implements java.io.FilenameFilter{
			@Override
			public boolean accept(File dir, String filename) {
				return filename.equalsIgnoreCase("databases") || filename.equalsIgnoreCase("shared_prefs");
			}
		}

		/**
		 * Checks whether a given file should be included in the backup. Used to further trim the
		 * "databases" folder down to just the app's own library database, excluding unrelated
		 * databases (e.g. Firebase analytics) that add bulk without adding any value to a restore.
		 *
		 * @param file The file being considered
		 * @return True if the file should be included in the backup
		 */
		private static boolean shouldIncludeFile(File file) {
			final String path = file.getPath().replace('\\', '/');
			if (path.contains("/databases/")) {
				return file.getName().startsWith("library.db");
			}
			return true;
		}
	}
}
