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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Loads a single chapter of an Archive of Our Own work for the reading screen.
 *
 * This is the "AO3 story reading" milestone described in the project summary, on its third
 * revision after on-device testing:
 *
 * <ul>
 * <li>The first version assumed AO3 pages could be fetched with a plain background HTTP request
 * (wrong - every fetch in this app, AO3 included, actually needs a real WebView capture via
 * {@code CloudflareFragment}).</li>
 * <li>The second version resolved a chapter's real, opaque id via the work's own {@code /navigate}
 * page first, then fetched the chapter itself - two WebView capture round-trips per chapter load.
 * On-device testing showed this was unreliable: {@code StoryDisplayActivity} adds a new
 * {@code CloudflareFragment} under the same literal tag every time a capture is requested, with no
 * guarantee the previous one has finished being removed first - firing that twice in quick
 * succession for one chapter load was a genuine, timing-dependent race.</li>
 * <li>This version fetches the entire work - every chapter - in a single page, using AO3's
 * {@code ?view_full_work=true} parameter, and caches every chapter's text in memory after that one
 * capture. The first chapter viewed in a reading session still needs exactly one real WebView
 * capture (matching {@code FanFictionLoader}'s already-stable one-capture-per-load pattern - no
 * back-to-back captures, so no race); every later chapter turn within that same session is served
 * straight from the in-memory cache via {@link #requiresWebViewCapture()} returning false, with no
 * further network activity at all.</li>
 * </ul>
 *
 * <ul>
 * <li>Decision 1 - No AO3 content-provider table exists yet, so {@link #getFromDatabase(long)}
 * always returns an empty cursor. {@code StoryLoader} then always treats an AO3 read as "not in
 * library", so every AO3 chapter is a live fetch and nothing is ever persisted to disk (the
 * in-memory cache here only lives as long as this loader instance does).</li>
 * <li>Decision 4 - AO3 authors are usernames, not numeric ids, so {@code StoryChapter}'s
 * {@code authorId} is simply left at its default of 0 here - no fake value is synthesized.</li>
 * <li>Decision 6 - the selectors below were checked against a live AO3 chapter page while this was
 * written; if AO3 changes its markup, follow the same "confirm against the live page, don't guess"
 * discipline used for the browse/story-list loaders (see {@code ArchiveOfOurOwnStoryLoaders}) to
 * fix them.</li>
 * </ul>
 *
 * @author Michael Chen
 */
class ArchiveOfOurOwnLoader extends StoryLoader {

	/**
	 * Every chapter's body text, in chapter-number order (index 0 = chapter 1). Null until the
	 * single {@code ?view_full_work=true} capture has happened; populated all at once from it, and
	 * cached here for the lifetime of this loader (i.e. one reading session) so later chapter
	 * turns never need another capture.
	 */
	private List<String> mChapterHtml;

	/** The work's title, cached alongside {@link #mChapterHtml} from that same one capture. */
	private String mCachedTitle;

	public ArchiveOfOurOwnLoader(Context context, Bundle in, long storyId, int currentPage) {
		super(context, in, storyId, currentPage);
	}

	@Override
	protected boolean requiresWebViewCapture() {
		// True until the one-and-only capture of the whole work has happened and populated the
		// cache; false for every chapter turn after that, since the whole work - every chapter -
		// was already fetched in that first capture (see getUri()).
		return mChapterHtml == null;
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
		// Only ever consulted while requiresWebViewCapture() is still true - i.e. before the work
		// has been fetched and cached - so this always points at the whole work.
		final Uri.Builder builder = new Uri.Builder();
		builder.scheme(Sites.ARCHIVE_OF_OUR_OWN.BASE_URI.getScheme());
		builder.authority(Sites.ARCHIVE_OF_OUR_OWN.AUTHORITY);
		builder.appendEncodedPath("works");
		builder.appendEncodedPath(Long.toString(getStoryId()));

		// Fetches every chapter of the work as one page, so the whole work can be cached in one
		// capture (see the class javadoc) instead of needing a fresh capture per chapter turn.
		builder.appendQueryParameter("view_full_work", "true");

		// Without this, AO3 shows a "This work could have adult content" interstitial in place of
		// the actual page for anything rated above Teen - the captured page would just be that
		// warning screen (no "div#chapters" at all), silently producing a blank chapter rather
		// than a visible error.
		builder.appendQueryParameter("view_adult", "true");

		return builder.build();
	}

	@Override
	protected String getStoryFromSite(long storyId, int currentPage, StoryChapter data) throws IOException {
		// Called only once requiresWebViewCapture() has returned false, i.e. the whole work is
		// already cached from the one real capture - this serves a later chapter turn straight
		// from memory. Deliberately never attempts its own network fetch: that would reintroduce
		// exactly the unreliable plain-background-request problem this two-step design (one real
		// capture, then memory) exists to avoid.
		if (mChapterHtml == null || currentPage < 1 || currentPage > mChapterHtml.size()) {
			throw new IOException("Chapter " + currentPage + " is not cached for AO3 work " + storyId);
		}

		if (data.getStoryTitle() == null) {
			data.setStoryTitle(mCachedTitle);
		}
		data.setTotalChapters(mChapterHtml.size());

		return mChapterHtml.get(currentPage - 1);
	}

	@Override
	protected String parseHTML(String html, StoryChapter data) {
		// Only ever called once, for the single real WebView capture of the whole work.
		final Document document = Jsoup.parse(html, getUri().toString());

		// The work title is shown once, under "#workskin", as "h2.title.heading".
		final Element title = document.select("#workskin h2.title.heading").first();
		if (title == null) return null;
		mCachedTitle = title.text().trim();

		// A multi-chapter work (which is what "view_full_work" is for) nests each chapter in its
		// own "div.chapter" inside "div#chapters", in order. A one-shot has no such wrapper: its
		// single chapter's content sits directly inside "div#chapters".
		final Elements chapterBlocks = document.select("div#chapters div.chapter");
		final List<String> chapters = new ArrayList<>();

		if (chapterBlocks.isEmpty()) {
			final String body = extractStoryText(document.select("div#chapters").first());
			if (body == null) return null;
			chapters.add(body);
		} else {
			for (Element block : chapterBlocks) {
				final String body = extractStoryText(block);
				if (body == null) return null;
				chapters.add(body);
			}
		}

		mChapterHtml = chapters;

		// AO3 authors are usernames, not numeric ids (decision 4): authorId is left at its default
		// of 0 rather than synthesizing a fake value.
		data.setStoryTitle(mCachedTitle);
		data.setTotalChapters(chapters.size());

		final int page = getCurrentPage();
		if (page < 1 || page > chapters.size()) return null;
		return chapters.get(page - 1);
	}

	@Override
	protected void reDownload(long storyId, int currentPage) {
		// Nothing to re-download: AO3 downloading doesn't exist yet, and there's no library entry
		// for an AO3 story that could be missing a file in the first place.
	}

	/**
	 * Extracts one chapter's body text out of its containing element (either a "div.chapter"
	 * block, for a multi-chapter work, or the whole "div#chapters" element, for a one-shot).
	 *
	 * <p>The prose is nested in a "div.userstuff.module" inside that root. Matching on both
	 * classes together (rather than a bare "div.userstuff") matters: AO3 marks the work's own
	 * summary in the preface, and any chapter-level author's notes at the top or bottom of a
	 * chapter, as a "blockquote" carrying only the "userstuff" class - never "module" - so a bare
	 * "div.userstuff" selector would grab a chapter's own notes instead of its real text whenever
	 * that chapter happens to have one, exactly the kind of "works for some chapters, blank for
	 * others" inconsistency a first version of this method produced.
	 *
	 * @return The chapter's body html, or null if no "div.userstuff.module" was found (treated as
	 *         a parse failure, the same as any other selector mismatch)
	 */
	@Nullable
	private static String extractStoryText(@Nullable Element root) {
		if (root == null) return null;

		final Element storyText = root.select("div.userstuff.module").first();
		if (storyText == null) return null;

		// AO3 nests a "Chapter Text" landmark heading (meant only for screen readers jumping
		// straight to the prose) as the first element inside that same div - strip it out so it
		// doesn't show up as a literal line of visible text above the actual story.
		storyText.select("h3.landmark.heading").remove();

		return storyText.html();
	}
}
