package com.spicymango.fanfictionreader.menu.browsemenu;

import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.menu.BaseLoader;
import com.spicymango.fanfictionreader.util.Sites;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

/**
 * Contains the loaders used to obtain the browse menu entries
 * 
 * @author Michael Chen
 *
 */
final class BrowseMenuLoaders {
	/* Archive of Our Own Loaders */
	protected final static class ArchiveOfOurOwnBrowseLoader extends BaseLoader<BrowseMenuItem> {

		/**
		 * The real, current fandom directory page. AO3's plain homepage (previously used here)
		 * was redesigned at some point and no longer contains a fandom-browsing widget at all -
		 * this is the actual page that lists fandoms by media category.
		 */
		private static final Uri FANDOM_DIRECTORY_URI = Uri.parse("https://archiveofourown.org/media");

		public ArchiveOfOurOwnBrowseLoader(Context context, Bundle savedInstanceState) {
			super(context, savedInstanceState);
		}

		@Override
		protected int getTotalPages(Document document) {
			return 0; // There are no additional pages
		}

		@Override
		protected Uri getUri(int currentPage) {
			return FANDOM_DIRECTORY_URI;
		}

		@Override
		protected boolean load(Document document, List<BrowseMenuItem> list) {
			// Each fandom category (Anime & Manga, Books & Literature, etc.) lists its top
			// fandoms as links to that fandom's tag page. Restricting to hrefs containing
			// "/tags/" excludes the "All <Category>..." links and any navigation/footer links
			// that might otherwise match a broader selector.
			Elements fandoms = document.select("ol.fandom.index.group a[href*=/tags/]");

			if (fandoms.isEmpty()) {
				// The previous diagnostic just printed the first 200 characters of the page,
				// which is useless here since AO3 puts the same generic privacy notice at the
				// top of nearly every page regardless of what's actually wrong. Instead: report
				// the page title (confirms whether we even got the intended page at all) and a
				// broader, unscoped link search (confirms whether the real content is present
				// under a different wrapper than assumed, or is genuinely missing/blocked).
				final String title = document.title();
				final Elements anyTagLinks = document.select("a[href*=/tags/]");
				setLastErrorDetail("Page title: \"" + title + "\". Found 0 fandom links via the expected"
						+ " selector, but " + anyTagLinks.size() + " links containing /tags/ anywhere on"
						+ " the page.");
				return false;
			}

			for (Element element : fandoms) {
				final Uri url = Uri.parse(element.attr("abs:href"));
				final String title = element.ownText();
				if (title.isEmpty()) continue;

				list.add(new BrowseMenuItem(title, url));
			}
			return true;
		}

	}

	/* FanFiction Loaders */
	protected final static class FanFictionRegularBrowseLoader extends BaseLoader<BrowseMenuItem> {
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

	protected final static class FanFictionCrossOverBrowseLoader extends BaseLoader<BrowseMenuItem> {
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

	protected final static class FanFictionCommunityBrowseLoader extends BaseLoader<BrowseMenuItem> {
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

	/* FictionPress Loaders */
	protected final static class FictionpressFictionPressBrowseLoader extends BaseLoader<BrowseMenuItem> {

		public FictionpressFictionPressBrowseLoader(Context context, Bundle savedInstanceState) {
			super(context, savedInstanceState);
		}

		@Override
		protected int getTotalPages(Document document) {
			return 0; // There are no additional pages
		}

		@Override
		protected Uri getUri(int currentPage) {
			return Sites.FICTIONPRESS.BASE_URI;
		}

		@Override
		protected boolean load(Document document, List<BrowseMenuItem> list) {
			Elements fiction = document.select("#gui_table1i a");

			if (fiction.isEmpty()) { return false; }

			for (Element element : fiction) {
				final Uri url = Uri.parse(element.attr("abs:href"));
				final String title = element.ownText();
				list.add(new BrowseMenuItem(title, url));
			}

			return true;
		}

	}

	protected final static class FictionPressPoetryPressBrowseLoader extends BaseLoader<BrowseMenuItem> {

		public FictionPressPoetryPressBrowseLoader(Context context, Bundle savedInstanceState) {
			super(context, savedInstanceState);
		}

		@Override
		protected int getTotalPages(Document document) {
			return 0; // There are no additional pages
		}

		@Override
		protected Uri getUri(int currentPage) {
			return Sites.FICTIONPRESS.BASE_URI;
		}

		@Override
		protected boolean load(Document document, List<BrowseMenuItem> list) {
			Elements fiction = document.select("#gui_table2i a");

			if (fiction.isEmpty()) return false;

			for (Element element : fiction) {
				final Uri url = Uri.parse(element.attr("abs:href"));
				final String title = element.ownText();
				list.add(new BrowseMenuItem(title, url));
			}

			return true;
		}

	}

	protected final static class FictionPressCommunityBrowseLoader extends BaseLoader<BrowseMenuItem> {
		private static final String[] FANFIC_URLS = { "general/0", "fiction", "poetry" };
		private final String[] categories;

		public FictionPressCommunityBrowseLoader(Context context, Bundle savedInstanceState) {
			super(context, savedInstanceState);
			disableProgressBar();
			categories = context.getResources().getStringArray(R.array.browse_menu_fp_community);
		}

		@Override
		protected int getTotalPages(Document document) {
			return 0;
		}

		@Override
		protected Uri getUri(int currentPage) {
			return null;
		}

		@Override
		protected boolean load(Document document, List<BrowseMenuItem> list) {
			for (int i = 0; i < categories.length; i++) {
				Uri.Builder builder = Sites.FICTIONPRESS.BASE_URI.buildUpon();
				builder.appendPath("communities");
				builder.appendPath(FANFIC_URLS[i]);
				builder.appendPath("");
				list.add(new BrowseMenuItem(categories[i], builder.build()));
			}
			return true;
		}

	}
}
