package com.spicymango.fanfictionreader.menu.storymenu;

import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.activity.reader.StoryDisplayActivity;
import com.spicymango.fanfictionreader.menu.storymenu.ArchiveOfOurOwnStoryLoaders.AO3RegularStoryLoader;
import com.spicymango.fanfictionreader.util.Sites;

import android.content.Context;
import android.net.Uri;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Builds the {@link StoryMenuSetup} for every Archive of Our Own story-listing URI type. Pulled
 * out of {@link StoryMenuActivity.StoryMenuFragment}'s switch statement (Phase 3 of the
 * FanFiction.net / AO3 separation) so AO3's choices live in one place instead of interleaved with
 * FanFiction.net's inside the router.
 *
 * <p>AO3 collections ({@code AO3_COLLECTION_MENU}) still have no loader implemented - that part of
 * the pre-split behavior is left alone, since building one would be a separate change - but both
 * URI types now wire up the "open a story" click listener, now that {@code StoryDisplayActivity}
 * has real AO3 support (see the AO3 story-reading plan). The collection menu's list will simply
 * stay empty until it gets a real loader.
 *
 * @author Michael Chen
 */
final class ArchiveOfOurOwnStoryMenuDelegate implements StoryMenuUriType {

	private ArchiveOfOurOwnStoryMenuDelegate() {
		// Static-only utility class
	}

	/**
	 * @param uriType One of the {@code AO3_*} constants from {@link StoryMenuUriType}.
	 * @param context The activity context, used to open the story once tapped and to construct
	 *                the loader.
	 * @param uri     The content URI that was matched to {@code uriType}.
	 */
	static StoryMenuSetup configure(int uriType, Context context, Uri uri) {
		// Every AO3 story listing opens the same reading screen. No AO3 content-provider table
		// exists yet, so there's no library entry to auto-update - every AO3 read is a live fetch
		// (decision 1).
		final OnItemClickListener openStory = (parent, view, position, id) ->
				StoryDisplayActivity.openStory(context, id, Sites.ARCHIVE_OF_OUR_OWN, false);

		switch (uriType) {
		case AO3_NORMAL_MENU:
			return new StoryMenuSetup(
					R.string.menu_navigation_title_regular,
					uri.getPathSegments().get(1),
					args -> new AO3RegularStoryLoader(context, args, uri),
					openStory);
		case AO3_COLLECTION_MENU:
			return new StoryMenuSetup(
					R.string.menu_navigation_title_community,
					uri.getPathSegments().get(1),
					null,
					openStory);
		default:
			throw new IllegalArgumentException("ArchiveOfOurOwnStoryMenuDelegate cannot handle uri type " + uriType);
		}
	}
}
