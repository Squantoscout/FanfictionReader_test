package com.spicymango.fanfictionreader.menu.storymenu;

/**
 * The URI-match codes {@link StoryMenuActivity.StoryMenuFragment}'s {@link android.content.UriMatcher}
 * assigns to each kind of story-listing URI. Kept as a shared, package-private set of constants
 * (Phase 3 of the FanFiction.net / AO3 separation) so the router and its per-site delegates -
 * {@link FanFictionStoryMenuDelegate} and {@link ArchiveOfOurOwnStoryMenuDelegate} - agree on the
 * same codes without the delegates needing to know about the router's private fields.
 *
 * @author Michael Chen
 */
interface StoryMenuUriType {
	// ArchiveOfOurOwn
	int AO3_NORMAL_MENU = 7;
	int AO3_COLLECTION_MENU = 8;
	// FanFiction
	int FF_NORMAL_MENU = 0;
	int FF_CROSSOVER_MENU = 1;
	int FF_JUST_IN_MENU = 2;
	int FF_COMMUNITY_MENU = 3;
}
