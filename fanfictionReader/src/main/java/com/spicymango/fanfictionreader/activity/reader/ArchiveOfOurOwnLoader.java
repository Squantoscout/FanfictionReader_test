package com.spicymango.fanfictionreader.activity.reader;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

import com.spicymango.fanfictionreader.util.JsoupUtil;
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

/**
 * Loads a single chapter of an Archive of Our Own work for the reading screen.
 *
 * This is the "AO3 story reading" milestone described in the project summary. It follows the six
 * locked decisions there rather than re-deriving them:
 *
 * <ul>
 * <li>Decision 1 - No AO3 content-provider table exists yet, so {@link #getFromDatabase(long)}
 * always returns an empty cursor. {@code StoryLoader} then always treats an AO3 read as "not in
 * library", so every AO3 chapter is a live fetch and nothing is ever persisted.</li>
 * <li>Decision 2 - AO3 does not need the Cloudflare/WebView capture detour that FanFiction.net
 * requires (already proven by the AO3 browse/story-list loaders, which fetch AO3 pages directly).
 * {@link #requiresWebViewCapture()} returns false so {@code StoryLoader.loadInBackground()} calls
 * {@link #getStoryFromSite(long, int, StoryChapter)} directly instead of raising
 * {@code ERROR_CLOUDFLARE_CAPTCHA}.</li>
 * <li>Decision 3 - AO3 chapters are addressed by an opaque per-chapter id, not a simple 1-based
 * number the way FanFiction.net's URLs are. This loader resolves "chapter N of work X" to its real
 * chapter id via the work's {@code /navigate} page, and caches the resulting number-to-id list in
 * memory for the lifetime of this loader so normal next/prev navigation only fetches
 * {@code /navigate} once per story, not once per chapter turned.</li>
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
	 * Chapter ids for this work, in chapter-number order (index 0 = chapter 1). Resolved lazily
	 * from the work's {@code /navigate} page on first access and cached for the lifetime of this
	 * loader (decision 3) - a new {@code StoryDisplayActivity}/loader instance re-resolves it.
	 */
	private List<Long> mChapterIds;

	public ArchiveOfOurOwnLoader(Context context, Bundle in, long storyId, int currentPage) {
		super(context, in, storyId, currentPage);
	}

	@Override
	protected boolean requiresWebViewCapture() {
		// AO3 pages can be fetched directly with a plain HTTP request; no anti-bot detour needed.
		return false;
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
		// A display/debug URI pointing at the work itself. The real per-chapter URI can't be
		// built from the story id and chapter number alone (decision 3), so this intentionally
		// does not include a chapter id - see getStoryFromSite() and resolveChapterId().
		Uri.Builder builder = new Uri.Builder();
		builder.scheme(Sites.ARCHIVE_OF_OUR_OWN.BASE_URI.getScheme());
		builder.authority(Sites.ARCHIVE_OF_OUR_OWN.AUTHORITY);
		builder.appendEncodedPath("works");
		builder.appendEncodedPath(Long.toString(getStoryId()));
		builder.appendEncodedPath("");
		return builder.build();
	}

	@Override
	protected String getStoryFromSite(long storyId, int currentPage, StoryChapter data) throws IOException {
		final long chapterId = resolveChapterId(storyId, currentPage);

		final Uri.Builder builder = new Uri.Builder();
		builder.scheme(Sites.ARCHIVE_OF_OUR_OWN.BASE_URI.getScheme());
		builder.authority(Sites.ARCHIVE_OF_OUR_OWN.AUTHORITY);
		builder.appendEncodedPath("works");
		builder.appendEncodedPath(Long.toString(storyId));
		builder.appendEncodedPath("chapters");
		builder.appendEncodedPath(Long.toString(chapterId));

		final Document document = JsoupUtil.safeGet(builder.build().toString());
		return parseHTML(document.outerHtml(), data);
	}

	@Override
	protected String parseHTML(String html, StoryChapter data) {
		final Document document = Jsoup.parse(html, getUri().toString());

		if (data.getTotalChapters() == 0) {
			// The work title is shown once, under "#workskin", as "h2.title.heading".
			final Element title = document.select("#workskin h2.title.heading").first();
			if (title == null) return null;
			data.setStoryTitle(title.text().trim());

			// The total chapter count comes from the /navigate resolution (decision 3), not from
			// anything on this page - by the time parseHTML() runs, resolveChapterId() has
			// already populated mChapterIds.
			data.setTotalChapters(mChapterIds == null ? getCurrentPage() : mChapterIds.size());

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
	protected void reDownload(long storyId, int currentPage) {
		// Nothing to re-download: AO3 downloading doesn't exist yet, and there's no library entry
		// for an AO3 story that could be missing a file in the first place.
	}

	/**
	 * Resolves a 1-based AO3 chapter number to its real, opaque chapter id via the work's
	 * {@code /navigate} page, fetching and caching the full list on first call (decision 3).
	 *
	 * @param storyId       The AO3 work id
	 * @param chapterNumber The 1-based chapter number requested
	 * @return The real chapter id AO3 uses in its own chapter URLs
	 * @throws IOException If the chapter list could not be fetched, or the requested chapter
	 *                      number does not exist in it
	 */
	private long resolveChapterId(long storyId, int chapterNumber) throws IOException {
		if (mChapterIds == null) {
			mChapterIds = fetchChapterIds(storyId);
		}

		if (chapterNumber < 1 || chapterNumber > mChapterIds.size()) {
			throw new IOException("Chapter " + chapterNumber + " does not exist for AO3 work " + storyId
					+ " (work has " + mChapterIds.size() + " chapter(s)).");
		}

		return mChapterIds.get(chapterNumber - 1);
	}

	/**
	 * Fetches and parses the work's {@code /navigate} page: a small, independently-fetchable page
	 * that lists every chapter of a work, in order, along with its real chapter id.
	 */
	private List<Long> fetchChapterIds(long storyId) throws IOException {
		final Uri.Builder builder = new Uri.Builder();
		builder.scheme(Sites.ARCHIVE_OF_OUR_OWN.BASE_URI.getScheme());
		builder.authority(Sites.ARCHIVE_OF_OUR_OWN.AUTHORITY);
		builder.appendEncodedPath("works");
		builder.appendEncodedPath(Long.toString(storyId));
		builder.appendEncodedPath("navigate");

		final Document document = JsoupUtil.safeGet(builder.build().toString());

		// Every chapter on the /navigate page is a link shaped .../works/{storyId}/chapters/{id},
		// listed in chapter order. Matching on that shape (rather than a guessed wrapper class
		// name for the surrounding list) keeps this working even if AO3's list markup changes,
		// as long as the link shape itself doesn't.
		final Pattern chapterLinkPattern = Pattern.compile("/works/" + storyId + "/chapters/(\\d++)");
		final Elements links = document.select("a[href*=/chapters/]");

		final List<Long> ids = new ArrayList<>();
		for (Element link : links) {
			final Matcher matcher = chapterLinkPattern.matcher(link.attr("href"));
			if (matcher.find()) {
				ids.add(Long.parseLong(matcher.group(1)));
			}
		}

		if (ids.isEmpty()) {
			throw new IOException("Could not find any chapters on the /navigate page for AO3 work " + storyId);
		}

		return ids;
	}
}
