package com.spicymango.fanfictionreader.menu.categorymenu;

import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.menu.BaseLoader;
import com.spicymango.fanfictionreader.menu.categorymenu.CategoryMenuActivity.CategoryMenuFragment.Filterable;
import com.spicymango.fanfictionreader.util.Sites;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

final class CategoryMenuLoaders {

	public final static class FanFictionRegularCategoryLoader extends BaseLoader<CategoryMenuItem>
			implements Filterable {
		private static final String STATE_FILTER = "STATE_FILTER";

		private final String mFormatString;
		private final Uri mUri;
		private int mCurrentFilter;

		public FanFictionRegularCategoryLoader(Context context, Bundle savedInstanceState, Uri uri) {
			super(context, savedInstanceState);

			Uri.Builder builder = uri.buildUpon();
			builder.authority(Sites.FANFICTION.BASE_URI.getAuthority());
			mUri = builder.build();

			mFormatString = context.getString(R.string.menu_navigation_count_story);

			if (savedInstanceState != null) {
				mCurrentFilter = savedInstanceState.getInt(STATE_FILTER);
			}
		}

		@Override
		protected void onSaveInstanceState(Bundle savedInstanceState) {
			savedInstanceState.putInt(STATE_FILTER, mCurrentFilter);
		}

		@Override
		protected int getTotalPages(Document document) {
			return 0;
		}

		@Override
		protected Uri getUri(int currentPage) {
			char query = ' ';
			if (mCurrentFilter == 0) {
				query = ' ';
			} else if (mCurrentFilter == 1) {
				query = '1';
			} else {
				query = (char) ('a' + mCurrentFilter - 2);
			}

			Uri.Builder builder = mUri.buildUpon();
			builder.appendQueryParameter("l", String.valueOf(query));
			return builder.build();
		}

		@Override
		protected boolean load(Document document, List<CategoryMenuItem> list) {
			Elements categories = document.select("div#content > div.bs > a");

			for (Element category : categories) {
				Elements children = category.children();
				if (children.size() == 0) { return false; }

				String title = category.ownText();
				String views = children.get(0).wholeText();
				views = String.format(mFormatString, views);
				Uri url = Uri.parse(category.absUrl("href"));
				CategoryMenuItem item = new CategoryMenuItem(title, views, url);
				list.add(item);
			}
			return true;
		}

		@Override
		public String[] getFilterEntries() {
			String[] filterList;
			filterList = new String[28];
			filterList[0] = getContext().getString(R.string.menu_navigation_filter_top_200);
			filterList[1] = "#";
			for (int i = 0; i < 26; i++) {
				filterList[2 + i] = "" + (char) (('A') + i);
			}
			return filterList;
		}

		@Override
		public void onFilterSelected(int position) {
			if (mCurrentFilter != position) {
				mCurrentFilter = position;
				resetState();
				startLoading();
			}
		}
	}

	public final static class FanFictionSubCategoryLoader extends BaseLoader<CategoryMenuItem>implements Filterable {
		private static final String STATE_FILTER = "STATE_FILTER";

		private final String mFormatString;
		private final Uri mUri;
		private int mCurrentFilter;

		public FanFictionSubCategoryLoader(Context context, Bundle savedInstanceState, Uri uri) {
			super(context, savedInstanceState);

			Uri.Builder builder = uri.buildUpon();
			builder.authority(Sites.FANFICTION.BASE_URI.getAuthority());
			mUri = builder.build();

			mFormatString = context.getString(R.string.menu_navigation_count_story);

			if (savedInstanceState != null) {
				mCurrentFilter = savedInstanceState.getInt(STATE_FILTER);
			}
		}

		@Override
		protected void onSaveInstanceState(Bundle savedInstanceState) {
			savedInstanceState.putInt(STATE_FILTER, mCurrentFilter);
		}

		@Override
		protected int getTotalPages(Document document) {
			return 0;
		}

		@Override
		protected Uri getUri(int currentPage) {
			int query;
			if (mCurrentFilter == 0) {
				query = 0;
			} else if (mCurrentFilter < 5) {
				query = 200 + mCurrentFilter;
			} else if (mCurrentFilter == 5) {
				query = 209;
			} else if (mCurrentFilter == 6) {
				query = 211;
			} else if (mCurrentFilter == 7) {
				query = 205;
			} else if (mCurrentFilter == 8) {
				query = 207;
			} else {
				query = 208;
			}

			Uri.Builder builder = mUri.buildUpon();
			builder.appendQueryParameter("pcategoryid", String.valueOf(query));
			return builder.build();
		}

		@Override
		protected boolean load(Document document, List<CategoryMenuItem> list) {
			Elements categories = document.select("div#content > div.bs > a");

			String allCrossover = getCrossoverName(document);
			Uri allUri = getCrossoverUri(document);
			if (allCrossover == null || allUri == null) { return false; }
			CategoryMenuItem allCrossovers = new CategoryMenuItem(allCrossover, "", allUri);
			list.add(allCrossovers);

			for (Element category : categories) {
				Elements children = category.children();
				if (children.size() == 0) { return false; }

				String title = category.ownText();
				String views = children.get(0).wholeText();
				views = String.format(mFormatString, views);
				Uri url = Uri.parse(category.absUrl("href"));
				CategoryMenuItem item = new CategoryMenuItem(title, views, url);
				list.add(item);
			}
			return true;
		}

		/**
		 * Gets the "all crossover" text for the current document
		 * 
		 * @param document The document to fetch the information
		 * @return The requested text, or null if the link does not exist
		 */
		private String getCrossoverName(Document document) {
			Elements url = document.select("div#content > center > a");
			if (url == null || url.first() == null) { return null; }
			return url.first().ownText();
		}

		/**
		 * Gets the "all crossover" url for the current document
		 * 
		 * @param document The document to fetch the information
		 * @return The requested url, or null if the link does not exist
		 */
		private Uri getCrossoverUri(Document document) {
			Elements url = document.select("div#content > center > a");
			if (url == null || url.first() == null) { return null; }
			return Uri.parse(url.first().absUrl("href"));
		}

		@Override
		public String[] getFilterEntries() {
			return getContext().getResources().getStringArray(R.array.menu_navigation_filter_crossover);
		}

		@Override
		public void onFilterSelected(int position) {
			if (mCurrentFilter != position) {
				mCurrentFilter = position;
				resetState();
				startLoading();
			}
		}

	}

	public final static class FanFictionCommunityCategoryLoader extends BaseLoader<CategoryMenuItem>
			implements Filterable {
		private static final String STATE_FILTER = "STATE_FILTER";

		private final String mFormatString;
		private final Uri mUri;
		private int mCurrentFilter;

		public FanFictionCommunityCategoryLoader(Context context, Bundle savedInstanceState, Uri uri) {
			super(context, savedInstanceState);

			Uri.Builder builder = uri.buildUpon();
			builder.authority(Sites.FANFICTION.BASE_URI.getAuthority());
			mUri = builder.build();

			mFormatString = context.getString(R.string.menu_navigation_count_community);

			if (savedInstanceState != null) {
				mCurrentFilter = savedInstanceState.getInt(STATE_FILTER);
			}
		}

		@Override
		protected void onSaveInstanceState(Bundle savedInstanceState) {
			savedInstanceState.putInt(STATE_FILTER, mCurrentFilter);
		}

		@Override
		protected int getTotalPages(Document document) {
			return 0;
		}

		@Override
		protected Uri getUri(int currentPage) {
			char query = ' ';
			if (mCurrentFilter == 0) {
				query = ' ';
			} else if (mCurrentFilter == 1) {
				query = '1';
			} else {
				query = (char) ('a' + mCurrentFilter - 2);
			}

			Uri.Builder builder = mUri.buildUpon();
			builder.appendQueryParameter("l", String.valueOf(query));
			return builder.build();
		}

		@Override
		protected boolean load(Document document, List<CategoryMenuItem> list) {
			Elements categories = document.select("div#content > div.bs > a");

			for (Element category : categories) {
				Elements children = category.children();
				if (children.size() == 0) { return false; }

				String title = category.ownText();
				String views = children.get(0).wholeText();
				views = String.format(mFormatString, views);
				Uri url = Uri.parse(category.absUrl("href"));
				CategoryMenuItem item = new CategoryMenuItem(title, views, url);
				list.add(item);
			}
			return true;
		}

		@Override
		public String[] getFilterEntries() {
			String[] filterList;
			filterList = new String[28];
			filterList[0] = getContext().getString(R.string.menu_navigation_filter_top_200);
			filterList[1] = "#";
			for (int i = 0; i < 26; i++) {
				filterList[2 + i] = "" + (char) (('A') + i);
			}
			return filterList;
		}

		@Override
		public void onFilterSelected(int position) {
			if (mCurrentFilter != position) {
				mCurrentFilter = position;
				resetState();
				startLoading();
			}
		}
	}
}
