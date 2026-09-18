package com.spicymango.fanfictionreader.menu.browsemenu;

import android.content.UriMatcher;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentTransaction;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ToggleButton;

import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.Settings;
import com.spicymango.fanfictionreader.menu.BaseFragment;
import com.spicymango.fanfictionreader.util.Sites;

import java.util.List;
import java.util.Objects;

public class BrowseMenuActivity extends AppCompatActivity {

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home){
			onBackPressed();
			return true;
		} else{
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Settings.setOrientationAndThemeNoActionBar(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_frame_layout);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

		if (savedInstanceState == null) {
			FragmentTransaction fr = getSupportFragmentManager().beginTransaction();
			fr.replace(R.id.content_frame, new BrowseMenuFragment());
			fr.commit();
		}
	}

	public final static class BrowseMenuFragment extends BaseFragment<BrowseMenuItem>
			implements OnCheckedChangeListener, BrowseMenuSiteType {

		private final static UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

		private final static String STATE_TOGGLE_BUTTON = "STATE_TOGGLE";

		static {
			//Archive of Our Own
			URI_MATCHER.addURI(Sites.ARCHIVE_OF_OUR_OWN.AUTHORITY, null, SITE_ARCHIVE_OF_OUR_OWN);
			
			//FanFiction Mobile
			URI_MATCHER.addURI(Sites.FANFICTION.AUTHORITY, null, SITE_FANFICTION);
			URI_MATCHER.addURI(Sites.FANFICTION.AUTHORITY, "communities/", SITE_FANFICTION_COMMUNITY);
			//FanFiction Desktop
			URI_MATCHER.addURI(Sites.FANFICTION.AUTHORITY_DESKTOP, null, SITE_FANFICTION);
			URI_MATCHER.addURI(Sites.FANFICTION.AUTHORITY_DESKTOP, "communities/", SITE_FANFICTION_COMMUNITY);
		}

		private int mActiveLoaderId;
		private LoaderAdapter<BrowseMenuItem> mLoaderOff, mLoaderOn;
		private ToggleButton mToggle;

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);

			Uri uri = requireActivity().getIntent().getData();
			int siteId = URI_MATCHER.match(uri);

			// Each site is handled entirely by whichever site's delegate owns it, so this switch
			// only needs to know which delegate to ask - not what that site actually does with
			// the URI.
			final BrowseMenuSetup setup;
			switch (siteId) {
			case SITE_ARCHIVE_OF_OUR_OWN:
				setup = ArchiveOfOurOwnBrowseMenuDelegate.configure(siteId, getActivity());
				break;
			case SITE_FANFICTION:
			case SITE_FANFICTION_COMMUNITY:
				setup = FanFictionBrowseMenuDelegate.configure(siteId, getActivity());
				break;
			default:
				throw new IllegalArgumentException();
			}

			setTitle(setup.titleRes);
			setSubTitle(setup.subtitle);
			mLoaderOff = args -> setup.loaderOffFactory.apply(args);
			if (setup.loaderOnFactory != null) {
				mLoaderOn = args -> setup.loaderOnFactory.apply(args);
			}
			mListView.setOnItemClickListener(setup.itemClickListener);
			if (setup.toggleLabels != null) {
				enableToggleButton(setup.toggleLabels.textOff, setup.toggleLabels.textOn, savedInstanceState);
			}

			if (mToggle == null) {
				mActiveLoaderId = 0;
				LoaderManager.getInstance(this).initLoader(0, mLoaderArgs, this);
			} else {
				mActiveLoaderId = mToggle.isChecked() ? 1 : 0;
				LoaderManager.getInstance(this).initLoader(mActiveLoaderId, mLoaderArgs, this);
			}
		}

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			mActiveLoaderId = isChecked ? 1 : 0;
			LoaderManager.getInstance(this).initLoader(mActiveLoaderId, null, this);
		}

		@NonNull
		@Override
		public Loader<List<BrowseMenuItem>> onCreateLoader(int id, Bundle args) {
			switch (id) {
			case 0:
				return mLoaderOff.getNewLoader(args);
			case 1:
			default:
				return mLoaderOn.getNewLoader(args);
			}
		}

		@Override
		public void onCreateOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
			MenuItem item = menu.add("");
			item.setActionView(mToggle);
			item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			super.onCreateOptionsMenu(menu, inflater);
		}

		@Override
		public void onLoadFinished(@NonNull Loader<List<BrowseMenuItem>> loader, List<BrowseMenuItem> data) {
			if (loader.getId() == mActiveLoaderId) {
				super.onLoadFinished(loader, data);
			}
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			if (mToggle != null) {
				outState.putBoolean(STATE_TOGGLE_BUTTON, mToggle.isChecked());
			}
		}

		private void enableToggleButton(@StringRes int textOff, @StringRes int textOn, Bundle savedInstanceState) {
			mToggle = new ToggleButton(getActivity());
			mToggle.setTextOff(requireActivity().getString(textOff));
			mToggle.setTextOn(requireActivity().getString(textOn));
			mToggle.setOnCheckedChangeListener(this);
			if (savedInstanceState != null) mToggle.setChecked(savedInstanceState.getBoolean(STATE_TOGGLE_BUTTON));
			else mToggle.setChecked(false);
			setHasOptionsMenu(true);
		}

		@Override
		protected BaseAdapter adapter(List<BrowseMenuItem> dataSet) {
			return new BrowseMenuAdapter(getActivity(), dataSet);
		}
	}
}
