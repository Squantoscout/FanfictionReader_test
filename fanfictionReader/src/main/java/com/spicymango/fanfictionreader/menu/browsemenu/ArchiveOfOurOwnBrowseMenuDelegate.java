package com.spicymango.fanfictionreader.menu.browsemenu;

import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.menu.storymenu.StoryMenuActivity;
import com.spicymango.fanfictionreader.util.Sites;

import android.content.Context;
import android.content.Intent;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Builds the {@link BrowseMenuSetup} for the Archive of Our Own browse menu (the fandom
 * directory). Pulled out of {@link BrowseMenuActivity.BrowseMenuFragment}'s switch statement
 * (Phase 3 of the FanFiction.net / AO3 separation) so AO3's choices live in one place instead of
 * interleaved with FanFiction.net's inside the router.
 *
 * @author Michael Chen
 */
final class ArchiveOfOurOwnBrowseMenuDelegate implements BrowseMenuSiteType {

	private ArchiveOfOurOwnBrowseMenuDelegate() {
		// Static-only utility class
	}

	/**
	 * @param siteId  Must be {@link BrowseMenuSiteType#SITE_ARCHIVE_OF_OUR_OWN}.
	 * @param context The activity context, used to launch the fandom's story list and to
	 *                construct the loader.
	 */
	static BrowseMenuSetup configure(int siteId, Context context) {
		if (siteId != SITE_ARCHIVE_OF_OUR_OWN) {
			throw new IllegalArgumentException("ArchiveOfOurOwnBrowseMenuDelegate cannot handle site id " + siteId);
		}

		// AO3 has no FanFiction.net-style genre/subgenre hierarchy - tapping a fandom goes
		// straight to that fandom's story list, rather than through CategoryMenuActivity (which
		// expects FanFiction.net's category page structure and doesn't know how to handle an AO3
		// tag URL, silently falling through and finishing).
		final OnItemClickListener listener = (parent, view, position, id) -> {
			Intent i = new Intent(context, StoryMenuActivity.class);
			i.setData(((BrowseMenuItem) parent.getItemAtPosition(position)).uri);
			context.startActivity(i);
		};

		return new BrowseMenuSetup(
				R.string.menu_browse_title_stories,
				Sites.ARCHIVE_OF_OUR_OWN.TITLE,
				args -> new ArchiveOfOurOwnBrowseLoader(context, args),
				null,
				listener,
				null);
	}
}
