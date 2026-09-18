package com.spicymango.fanfictionreader.activity.reader;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

import com.spicymango.fanfictionreader.util.Sites;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Loads a single chapter of an Archive of Our Own work for the reading screen.
 *
 * This is the "AO3 story reading" milestone described in the project summary. It follows the six
 * locked decisions there, with one correction made after an on-device test: decision 2 assumed
 * AO3 pages could be fetched with a plain background HTTP request, on the theory that the AO3
 * browse/story-list loaders already did so successfully. That theory turned out to be wrong -
 * every existing fetch in this app, AO3 included, actually goes through {@code CloudflareFragment}
 * (a real WebView with a spoofed desktop Chrome user agent and JS execution), never a raw request.
 * A raw request from this loader is what produced the "Failed to connect to server" error seen on
 * a real device. This version goes through the same WebView capture path FanFictionLoader uses.
 *
 * <p>That capture path only supports one page fetch per read attempt (see
 * {@code StoryLoader.loadInBackground()}: {@link #getUri()} is asked for one page, that page is
 * captured, and {@link #parseHTML} is handed its content). AO3 chapter reading genuinely needs two
 * fetches - the work's {@code /navigate} page to resolve a chapter's real id, then the chapter
 * page itself (decision 3) - so this loader uses the new {@link #needsAnotherFetch} hook to make
 * {@code StoryLoader} loop back through a second capture round-trip: {@link #getUri()} returns the
 * {@code /navigate} page first, and once {@link #parseHTML} has consumed it and resolved the
 * chapter id list, {@link #getUri()} returns the real chapter page for the second round-trip.
 *
 * <ul>
 * <li>Decision 1 - No AO3 content-provider table exists yet, so {@link #getFromDatabase(long)}
 * always returns an empty cursor. {@code StoryLoader} then always treats an AO3 read as "not in
 * library", so every AO3 chapter is a live fetch and nothing is ever persisted.</li>
 * <li>Decision 3 - AO3 chapters are addressed by an opaque per-chapter id, not a simple 1-based
 * number the way FanFiction.net's URLs are. This loader resolves "chapter N of work X" to its real
 * chapter id via the work's {@code /navigate} page (captured through the WebView flow above), and
 * caches the resulting number-to-id list in memory for the lifetime of this loader so normal
 * next/prev navigation only fetches {@code /navigate} once per story, not once per chapter
 * turned.</li>
 * <li>Decision 4 - AO3 authors are usernames, not numeric ids, so {@code StoryChapter}'s
 * {@code authorId} is simply left at its default of 0 here - no fake value is synthesized.</li>
 * <li>Decision 6 - the selectors below were checked against a live AO3 chapter page and a live
 * {@code /navigate} page while this was written; if AO3 changes its markup, follow the same
 * "confirm against the live page, don't guess" discipline used for the browse/story-list loaders
 * (see {@code ArchiveOfOurOwnStoryLoaders}) to fix them.</li>
 * </ul>
 *
 * @author Michael Chen
 */
class ArchiveOfOurOwnLoader extends StoryLoader {

	/**
	 * Chapter ids for this work, in chapter-number order (index 0 = chapter 1). Null until the
	 * work's {@code /navigate} page has been captured and parsed once; cached for the lifetime of
	 * this loader afterward (decision 3) - a new {@code StoryDisplayActivity}/loader instance
	 * re-resolves it.
	 */
	private List<Long> mChapterIds;

	/**
	 * True right after {@link #parseHTML} has just consumed the {@code /navigate} page and
	 * populated {@link #mChapterIds}, cleared once the real chapter page has also been captured
	 * and parsed. Read by {@link #needsAnotherFetch(StoryChapter)} to tell {@code StoryLoader} a
	 * second capture round-trip is still needed before this chapter is actually ready.
	 */
	private boolean mAwaitingChapterCapture;

	public ArchiveOfOurOwnLoader(Context context, Bundle in, long storyId, int currentPage) {
		super(context, in, storyId, currentPage);
	}

	@Override
	protected Cursor getFromDatabase(long storyId) {
		// No AO3 content-provider table exists yet (decision 1): report "not in library" via an
		// empty, no-op cursor rather than a real query, so every AO3 read is a live fetch.
		return new MatrixCursor(new String[0]);
	}

	@Override
	protected String getStoryFromFile(long storyId, int currentPage) throws FileNotFoundException {
		// Unreachable: getFromDatabase() always reports "not in library", so
		// StoryLoader.loadInBackground() never takes the "read from file" path for AO3. Throw
		// defensively instead of silently fabricating a result if that ever changes.
		throw new FileNotFoundException(
				"Archive of Our Own stories are not stored locally; getStoryFromFile() should never be called.");
	}

	@NonNull
	@Override
	protected Uri getUri() {
		final Uri.Builder builder = new Uri.Builder();
		builder.scheme(Sites.ARCHIVE_OF_OUR_OWN.BASE_URI.getScheme());
		builder.authority(Sites.ARCHIVE_OF_OUR_OWN.AUTHORITY);
		builder.appendEncodedPath("works");
		builder.appendEncodedPath(Long.toString(getStoryId()));

		if (mChapterIds == null) {
			// The chapter's real, opaque id isn't known yet - fetch the work's /navigate page
			// first to resolve it (decision 3).
			builder.appendEncodedPath("navigate");
		} else {
			final int page = getCurrentPage();
			if (page >= 1 && page <= mChapterIds.size()) {
				builder.appendEncodedPath("chapters");
				builder.appendEncodedPath(Long.toString(mChapterIds.get(page - 1)));
			}
			// If the requested page is out of range, fall through with just the work's own url;
			// parseHTML() will fail to find "div#chapters div.userstuff" on that page and report
			// a parse failure the same way any other selector mismatch does.
		}

		return builder.build();
	}

	@Override
	protected String getStoryFromSite(long storyId, int currentPage, StoryChapter data) throws IOException {
		// Unreachable: this loader always goes through the Cloudflare/WebView capture path (like
		// FanFictionLoader, and like this app's AO3 browse/story-list loaders), never a direct
		// background fetch - a background fetch is what originally failed against a real AO3
		// server (see the class javadoc). StoryLoader.loadInBackground() never calls this method;
		// see getUri() and needsAnotherFetch() instead.
		throw new IOException(
				"Archive of Our Own chapters are fetched via the WebView capture flow; getStoryFromSite() should never be called.");
	}

	@Override
	protected String parseHTML(String html, StoryChapter data) {
		final Document document = Jsoup.parse(html, getUri().toString());

		if (mChapterIds == null) {
			// This capture was the /navigate page: resolve the chapter-number -> chapter-id list
			// from it (decision 3), then signal that a second capture (of the real chapter page)
			// is still needed before this chapter is actually ready.
			final List<Long> ids = parseChapterIds(document, getStoryId());
			if (ids == null) return null;

			mChapterIds = ids;
			mAwaitingChapterCapture = true;
			data.setTotalChapters(ids.size());

			// Placeholder only - needsAnotherFetch() stops StoryLoader from treating this as the
			// final chapter text.
			return null;
		}

		// This capture was the real chapter page.
		mAwaitingChapterCapture = false;

		if (data.getStoryTitle() == null) {
			// The work title is shown once, under "#workskin", as "h2.title.heading". Gated on
			// the title rather than on total-chapter-count, since total chapters is already set
			// from the /navigate resolution above by the time this first runs.
			final Element title = document.select("#workskin h2.title.heading").first();
			if (title == null) return null;
			data.setStoryTitle(title.text().trim());

			// AO3 authors are usernames, not numeric ids (decision 4): authorId is left at its
			// default of 0 rather than synthesizing a fake value.
		}

		// The chapter's prose is always nested in a "div.userstuff" somewhere inside
		// "div#chapters" - directly, for a one-shot, or one level deeper inside a "div.chapter"
		// wrapper for a multi-chapter work. Scoping to "#chapters" specifically (rather than a
		// bare "div.userstuff") matters: AO3 also uses the "userstuff" class for the work's
		// summary/notes in the preface, above the actual chapter text.
		final Elements storyText = document.select("div#chapters div.userstuff");
		if (storyText.isEmpty()) return null;
		return storyText.first().html();
	}

	@Override
	protected boolean needsAnotherFetch(StoryChapter data) {
		return mAwaitingChapterCapture;
	}

	@Override
	protected void reDownload(long storyId, int currentPage) {
		// Nothing to re-download: AO3 downloading doesn't exist yet, and there's no library entry
		// for an AO3 story that could be missing a file in the first place.
	}

	/**
	 * Parses the work's {@code /navigate} page into an ordered list of chapter ids (index 0 =
	 * chapter 1). Every chapter on that page is a link shaped
	 * {@code .../works/{storyId}/chapters/{id}}, listed in chapter order - matching on that link
	 * shape (rather than a guessed wrapper class name for the surrounding list) keeps this working
	 * even if AO3's list markup changes, as long as the link shape itself doesn't.
	 *
	 * @return The resolved chapter ids, in chapter-number order, or null if none were found
	 *         (treated as a parse failure, the same as any other selector mismatch)
	 */
	@Nullable
	private static List<Long> parseChapterIds(Document document, long storyId) {
		final Pattern chapterLinkPattern = Pattern.compile("/works/" + storyId + "/chapters/(\\d++)");
		final Elements links = document.select("a[href*=/chapters/]");

		final List<Long> ids = new ArrayList<>();
		for (Element link : links) {
			final Matcher matcher = chapterLinkPattern.matcher(link.attr("href"));
			if (matcher.find()) {
				ids.add(Long.parseLong(matcher.group(1)));
			}
		}

		return ids.isEmpty() ? null : ids;
	}
}
