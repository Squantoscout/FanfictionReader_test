package com.spicymango.fanfictionreader.menu.storymenu;

import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.activity.Site;
import com.spicymango.fanfictionreader.activity.reader.StoryDisplayActivity;
import com.spicymango.fanfictionreader.menu.storymenu.FanFictionStoryLoaders.FFCommunityStoryLoader;
import com.spicymango.fanfictionreader.menu.storymenu.FanFictionStoryLoaders.FFJustInStoryLoader;
import com.spicymango.fanfictionreader.menu.storymenu.FanFictionStoryLoaders.FFRegularStoryLoader;

import android.content.Context;
import android.net.Uri;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Builds the {@link StoryMenuSetup} for every FanFiction.net story-listing URI type. Pulled out
 * of {@link StoryMenuActivity.StoryMenuFragment}'s switch statement (Phase 3 of the
 * FanFiction.net / AO3 separation) so FanFiction.net's title/subtitle/loader/click-listener
 * choices live in one place instead of interleaved with AO3's inside the router.
 *
 * @author Michael Chen
 */
final class FanFictionStoryMenuDelegate implements StoryMenuUriType {

	private FanFictionStoryMenuDelegate() {
		// Static-only utility class
	}

	/**
	 * @param uriType One of the {@code FF_*} constants from {@link StoryMenuUriType}.
	 * @param context The activity context, used to open the story once tapped and to construct
	 *                the loader.
	 * @param uri     The content URI that was matched to {@code uriType}.
	 */
	static StoryMenuSetup configure(int uriType, Context context, Uri uri) {
		// Every FanFiction.net story listing opens the same reading screen.
		final OnItemClickListener openStory = (parent, view, position, id) ->
				StoryDisplayActivity.openStory(context, id, Site.FANFICTION, true);

		switch (uriType) {
		case FF_NORMAL_MENU:
			return new StoryMenuSetup(
					R.string.menu_navigation_title_regular,
					uri.getLastPathSegment(),
					args -> new FFRegularStoryLoader(context, args, uri),
					openStory);
		case FF_CROSSOVER_MENU:
			return new StoryMenuSetup(
					R.string.menu_navigation_title_crossover,
					uri.getPathSegments().get(0),
					args -> new FFRegularStoryLoader(context, args, uri),
					openStory);
		case FF_JUST_IN_MENU:
			return new StoryMenuSetup(
					R.string.menu_story_title_just_in,
					"",
					args -> new FFJustInStoryLoader(context, args, uri),
					openStory);
		case FF_COMMUNITY_MENU:
			return new StoryMenuSetup(
					R.string.menu_navigation_title_community,
					uri.getPathSegments().get(1).replace('-', ' '),
					args -> new FFCommunityStoryLoader(context, args, uri),
					openStory);
		default:
			throw new IllegalArgumentException("FanFictionStoryMenuDelegate cannot handle uri type " + uriType);
		}
	}
}
