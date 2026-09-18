package com.spicymango.fanfictionreader.menu.browsemenu;

import java.util.List;

import org.jsoup.nodes.Document;

import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.menu.BaseLoader;
import com.spicymango.fanfictionreader.util.Sites;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

/**
 * Contains the loaders used to obtain the FanFiction.net browse menu entries. Split out of the
 * former {@code BrowseMenuLoaders} (Phase 2 of the FanFiction.net / AO3 separation) so that
 * FanFiction.net-only changes no longer require re-touching the unrelated AO3 browse loader
 * that used to live alongside them.
 *
 * @author Michael Chen
 */
final class FanFictionBrowseLoaders {

	final static class FanFictionRegularBrowseLoader extends BaseLoader<BrowseMenuItem> {
		private static final String[] FANFIC_URLS = { "anime", "book", "cartoon", "comic", "game", "misc", "movie",
				"play", "tv" };
		private final String[] categories;

		public FanFictionRegularBrowseLoader(Context context, Bundle savedInstanceState) {
			super(context, savedInstanceState);
			disableProgressBar();
			categories = context.getResources().getStringArray(R.array.browse_menu_ff);
		}

		@Override
		protected int getTotalPages(Document document) {
			return 0; // There are no additional pages
		}

		@Override
		protected Uri getUri(int currentPage) {
			return null;
		}

		@Override
		protected boolean load(Document document, List<BrowseMenuItem> list) {
			for (int i = 0; i < categories.length; i++) {
				Uri.Builder builder = Sites.FANFICTION.BASE_URI.buildUpon();
				builder.appendPath(FANFIC_URLS[i]);
				builder.appendPath("");
				list.add(new BrowseMenuItem(categories[i], builder.build()));
			}
			return true;
		}
	}

	final static class FanFictionCrossOverBrowseLoader extends BaseLoader<BrowseMenuItem> {
		private static final String[] FANFIC_URLS = { "anime", "book", "cartoon", "comic", "game", "misc", "movie",
				"play", "tv" };
		private final String[] categories;

		public FanFictionCrossOverBrowseLoader(Context context, Bundle savedInstanceState) {
			super(context, savedInstanceState);
			disableProgressBar();
			categories = context.getResources().getStringArray(R.array.browse_menu_ff);
		}

		@Override
		protected int getTotalPages(Document document) {
			return 0; // There are no additional pages
		}

		@Override
		protected Uri getUri(int currentPage) {
			return null;
		}

		@Override
		protected boolean load(Document document, List<BrowseMenuItem> list) {
			for (int i = 0; i < categories.length; i++) {
				Uri.Builder builder = Sites.FANFICTION.BASE_URI.buildUpon();
				builder.appendPath("crossovers");
				builder.appendPath(FANFIC_URLS[i]);
				builder.appendPath("");
				list.add(new BrowseMenuItem(categories[i], builder.build()));
			}
			return true;
		}

	}

	final static class FanFictionCommunityBrowseLoader extends BaseLoader<BrowseMenuItem> {
		private static final String[] FANFIC_URLS = { "general/0", "anime", "book", "cartoon", "comic", "game", "misc",
				"movie", "play", "tv" };
		private final String[] categories;

		public FanFictionCommunityBrowseLoader(Context context, Bundle savedInstanceState) {
			super(context, savedInstanceState);
			disableProgressBar();
			categories = context.getResources().getStringArray(R.array.browse_menu_ff_community);
		}

		@Override
		protected int getTotalPages(Document document) {
			return 0; // There are no additional pages
		}

		@Override
		protected Uri getUri(int currentPage) {
			return null;
		}

		@Override
		protected boolean load(Document document, List<BrowseMenuItem> list) {
			for (int i = 0; i < categories.length; i++) {
				Uri.Builder builder = Sites.FANFICTION.BASE_URI.buildUpon();
				builder.appendPath("communities");
				builder.appendEncodedPath(FANFIC_URLS[i]);
				builder.appendPath("");
				list.add(new BrowseMenuItem(categories[i], builder.build()));
			}
			return true;
		}
	}
}
