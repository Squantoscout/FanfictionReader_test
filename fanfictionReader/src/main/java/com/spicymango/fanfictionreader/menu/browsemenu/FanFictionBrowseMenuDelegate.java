package com.spicymango.fanfictionreader.menu.browsemenu;

import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.menu.browsemenu.BrowseMenuSetup.ToggleLabels;
import com.spicymango.fanfictionreader.menu.browsemenu.FanFictionBrowseLoaders.FanFictionCommunityBrowseLoader;
import com.spicymango.fanfictionreader.menu.browsemenu.FanFictionBrowseLoaders.FanFictionCrossOverBrowseLoader;
import com.spicymango.fanfictionreader.menu.browsemenu.FanFictionBrowseLoaders.FanFictionRegularBrowseLoader;
import com.spicymango.fanfictionreader.menu.categorymenu.CategoryMenuActivity;
import com.spicymango.fanfictionreader.menu.communitymenu.CommunityMenuActivity;
import com.spicymango.fanfictionreader.util.Sites;

import android.content.Context;
import android.content.Intent;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Builds the {@link BrowseMenuSetup} for both FanFiction.net browse menus - the regular genre
 * browse (which also offers a crossover toggle) and the community browse. Pulled out of
 * {@link BrowseMenuActivity.BrowseMenuFragment}'s switch statement (Phase 3 of the FanFiction.net
 * / AO3 separation) so FanFiction.net's choices live in one place instead of interleaved with
 * AO3's inside the router.
 *
 * @author Michael Chen
 */
final class FanFictionBrowseMenuDelegate implements BrowseMenuSiteType {

	private FanFictionBrowseMenuDelegate() {
		// Static-only utility class
	}

	/**
	 * @param siteId  One of {@link BrowseMenuSiteType#SITE_FANFICTION} or
	 *                {@link BrowseMenuSiteType#SITE_FANFICTION_COMMUNITY}.
	 * @param context The activity context, used to launch the next screen and to construct the
	 *                loader(s).
	 */
	static BrowseMenuSetup configure(int siteId, Context context) {
		switch (siteId) {
		case SITE_FANFICTION: {
			final OnItemClickListener listener = (parent, view, position, id) -> {
				Intent i = new Intent(context, CategoryMenuActivity.class);
				i.setData(((BrowseMenuItem) parent.getItemAtPosition(position)).uri);
				context.startActivity(i);
			};
			return new BrowseMenuSetup(
					R.string.menu_browse_title_stories,
					Sites.FANFICTION.TITLE,
					args -> new FanFictionRegularBrowseLoader(context, args),
					args -> new FanFictionCrossOverBrowseLoader(context, args),
					listener,
					new ToggleLabels(R.string.toggle_regular, R.string.toggle_crossover));
		}
		case SITE_FANFICTION_COMMUNITY: {
			final OnItemClickListener listener = (parent, view, position, id) -> {
				final Intent i;
				if (position == 0) {
					i = new Intent(context, CommunityMenuActivity.class);
				} else {
					i = new Intent(context, CategoryMenuActivity.class);
				}
				i.setData(((BrowseMenuItem) parent.getItemAtPosition(position)).uri);
				context.startActivity(i);
			};
			return new BrowseMenuSetup(
					R.string.menu_button_communities,
					Sites.FANFICTION.TITLE,
					args -> new FanFictionCommunityBrowseLoader(context, args),
					null,
					listener,
					null);
		}
		default:
			throw new IllegalArgumentException("FanFictionBrowseMenuDelegate cannot handle site id " + siteId);
		}
	}
}
