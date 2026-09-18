package com.spicymango.fanfictionreader.menu.browsemenu;

/**
 * The site-match codes {@link BrowseMenuActivity.BrowseMenuFragment}'s
 * {@link android.content.UriMatcher} assigns to each browse-menu screen. Kept as a shared,
 * package-private set of constants (Phase 3 of the FanFiction.net / AO3 separation) so the router
 * and its per-site delegates - {@link FanFictionBrowseMenuDelegate} and
 * {@link ArchiveOfOurOwnBrowseMenuDelegate} - agree on the same codes without the delegates
 * needing to know about the router's private fields.
 *
 * @author Michael Chen
 */
interface BrowseMenuSiteType {
	int SITE_ARCHIVE_OF_OUR_OWN = 0;
	int SITE_FANFICTION = 1;
	int SITE_FANFICTION_COMMUNITY = 2;
}
