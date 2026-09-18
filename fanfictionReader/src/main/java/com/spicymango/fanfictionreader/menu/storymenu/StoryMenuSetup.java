package com.spicymango.fanfictionreader.menu.storymenu;

import java.util.function.Function;

import com.spicymango.fanfictionreader.menu.BaseLoader;
import com.spicymango.fanfictionreader.util.Story;

import android.os.Bundle;
import android.widget.AdapterView.OnItemClickListener;

import androidx.annotation.StringRes;

/**
 * Everything {@link StoryMenuActivity.StoryMenuFragment} needs to configure itself for one
 * matched URI type, gathered from whichever per-site delegate handled it
 * ({@link FanFictionStoryMenuDelegate} or {@link ArchiveOfOurOwnStoryMenuDelegate}). Introduced in
 * Phase 3 of the FanFiction.net / AO3 separation: keeping this as a plain data holder is what lets
 * the fragment's switch statement dispatch to a delegate instead of embedding each site's
 * title/subtitle/loader/click-listener choices inline.
 *
 * @author Michael Chen
 */
final class StoryMenuSetup {

	@StringRes
	final int titleRes;
	final String subtitle;

	/** Null for URI types with no loader implemented yet (currently AO3 collections). */
	final Function<Bundle, BaseLoader<Story>> loaderFactory;

	/**
	 * Null for URI types with no working "open a story" screen yet. Currently every AO3 URI type
	 * has a click listener (see {@link ArchiveOfOurOwnStoryMenuDelegate}, now that
	 * {@code StoryDisplayActivity} has real AO3 reading support); this stays available for any
	 * future URI type that doesn't have one yet.
	 */
	final OnItemClickListener itemClickListener;

	StoryMenuSetup(@StringRes int titleRes, String subtitle, Function<Bundle, BaseLoader<Story>> loaderFactory,
			OnItemClickListener itemClickListener) {
		this.titleRes = titleRes;
		this.subtitle = subtitle;
		this.loaderFactory = loaderFactory;
		this.itemClickListener = itemClickListener;
	}
}
