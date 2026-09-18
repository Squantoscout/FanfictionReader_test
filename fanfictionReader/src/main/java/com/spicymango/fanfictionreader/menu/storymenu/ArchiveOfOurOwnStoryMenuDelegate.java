package com.spicymango.fanfictionreader.menu.storymenu;

import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.menu.storymenu.ArchiveOfOurOwnStoryLoaders.AO3RegularStoryLoader;

import android.content.Context;
import android.net.Uri;

/**
 * Builds the {@link StoryMenuSetup} for every Archive of Our Own story-listing URI type. Pulled
 * out of {@link StoryMenuActivity.StoryMenuFragment}'s switch statement (Phase 3 of the
 * FanFiction.net / AO3 separation) so AO3's choices live in one place instead of interleaved with
 * FanFiction.net's inside the router.
 *
 * <p>AO3 collections ({@code AO3_COLLECTION_MENU}) have no loader implemented yet, and no AO3 URI
 * type has a working "open a story" screen yet ({@code StoryDisplayActivity} still has a literal
 * TODO for AO3) - both are left {@code null} here, matching the pre-split behavior exactly rather
 * than fixing either as a side effect of this move.
 *
 * @author Michael Chen
 */
final class ArchiveOfOurOwnStoryMenuDelegate implements StoryMenuUriType {

	private ArchiveOfOurOwnStoryMenuDelegate() {
		// Static-only utility class
	}

	/**
	 * @param uriType One of the {@code AO3_*} constants from {@link StoryMenuUriType}.
	 * @param context The activity context, used to construct the loader.
	 * @param uri     The content URI that was matched to {@code uriType}.
	 */
	static StoryMenuSetup configure(int uriType, Context context, Uri uri) {
		switch (uriType) {
		case AO3_NORMAL_MENU:
			return new StoryMenuSetup(
					R.string.menu_navigation_title_regular,
					uri.getPathSegments().get(1),
					args -> new AO3RegularStoryLoader(context, args, uri),
					null);
		case AO3_COLLECTION_MENU:
			return new StoryMenuSetup(
					R.string.menu_navigation_title_community,
					uri.getPathSegments().get(1),
					null,
					null);
		default:
			throw new IllegalArgumentException("ArchiveOfOurOwnStoryMenuDelegate cannot handle uri type " + uriType);
		}
	}
}
