package com.spicymango.fanfictionreader.menu.browsemenu;

import java.util.function.Function;

import com.spicymango.fanfictionreader.menu.BaseLoader;

import android.os.Bundle;
import android.widget.AdapterView.OnItemClickListener;

import androidx.annotation.StringRes;

/**
 * Everything {@link BrowseMenuActivity.BrowseMenuFragment} needs to configure itself for one
 * matched site, gathered from whichever per-site delegate handled it
 * ({@link FanFictionBrowseMenuDelegate} or {@link ArchiveOfOurOwnBrowseMenuDelegate}). Introduced
 * in Phase 3 of the FanFiction.net / AO3 separation: keeping this as a plain data holder is what
 * lets the fragment's switch statement dispatch to a delegate instead of embedding each site's
 * title/subtitle/loader/click-listener/toggle choices inline.
 *
 * @author Michael Chen
 */
final class BrowseMenuSetup {

	@StringRes
	final int titleRes;
	final String subtitle;
	final Function<Bundle, BaseLoader<BrowseMenuItem>> loaderOffFactory;

	/** Null for sites with no secondary (toggle-on) loader, e.g. AO3 and FanFiction communities. */
	final Function<Bundle, BaseLoader<BrowseMenuItem>> loaderOnFactory;

	final OnItemClickListener itemClickListener;

	/** Null for sites with no regular/crossover-style toggle button. */
	final ToggleLabels toggleLabels;

	BrowseMenuSetup(@StringRes int titleRes, String subtitle,
			Function<Bundle, BaseLoader<BrowseMenuItem>> loaderOffFactory,
			Function<Bundle, BaseLoader<BrowseMenuItem>> loaderOnFactory, OnItemClickListener itemClickListener,
			ToggleLabels toggleLabels) {
		this.titleRes = titleRes;
		this.subtitle = subtitle;
		this.loaderOffFactory = loaderOffFactory;
		this.loaderOnFactory = loaderOnFactory;
		this.itemClickListener = itemClickListener;
		this.toggleLabels = toggleLabels;
	}

	/** The pair of labels shown on a browse menu's regular/crossover toggle button. */
	static final class ToggleLabels {
		@StringRes
		final int textOff;
		@StringRes
		final int textOn;

		ToggleLabels(@StringRes int textOff, @StringRes int textOn) {
			this.textOff = textOff;
			this.textOn = textOn;
		}
	}
}
