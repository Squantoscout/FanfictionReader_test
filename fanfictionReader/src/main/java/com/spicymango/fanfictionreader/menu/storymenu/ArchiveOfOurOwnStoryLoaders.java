package com.spicymango.fanfictionreader.menu.storymenu;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.spicymango.fanfictionreader.menu.BaseLoader;
import com.spicymango.fanfictionreader.util.Parser;
import com.spicymango.fanfictionreader.util.Story;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

/**
 * Contains the loaders used to obtain Archive of Our Own stories. Split out of the former
 * {@code StoryMenuLoaders} (Phase 2 of the FanFiction.net / AO3 separation) so that AO3-only
 * changes no longer require re-touching the FanFiction.net loaders that live alongside them.
 *
 * @author Michael Chen
 */
final class ArchiveOfOurOwnStoryLoaders {

	/**
	 * The loader responsible for loading regular Archive of Our Own stories.
	 *
	 * @author Michael Chen
	 */
	public final static class AO3RegularStoryLoader extends BaseLoader<Story> {
		private final DateFormat mFormat;
		private final Uri mUri;

		public AO3RegularStoryLoader(Context context, Bundle savedInstanceState, Uri uri) {
			super(context, savedInstanceState);
			mUri = uri;
			mFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
		}

		@Override
		protected int getTotalPages(Document document) {
			Elements pageLists = document.select("ol.pagination");
			if (pageLists.isEmpty()) return 1;

			// Get the first page list set (Each web page has two)
			Element pageList = pageLists.first();
			Elements pageButtons = pageList.children();

			// Get the button corresponding to the last page
			Element pageButton = pageButtons.get(pageButtons.size() - 2);

			return Integer.parseInt(pageButton.text());
		}

		@Override
		protected Uri getUri(int currentPage) {
			Uri.Builder builder = mUri.buildUpon();
			builder.appendQueryParameter("page", String.valueOf(currentPage));
			return builder.build();
		}

		/**
		 * Records which specific field was missing when a work's blurb didn't match an expected
		 * selector, along with a snippet of that work's own HTML block - so a mismatch here shows
		 * exactly which selector needs fixing, rather than a bare "parsing error".
		 *
		 * @param fieldName    The name of the field that could not be found
		 * @param storyElement The HTML block for the specific work that failed to parse
		 * @return Always false, so this can be used directly as a return statement
		 */
		private boolean missingField(String fieldName, Element storyElement) {
			final String snippet = storyElement.outerHtml();
			setLastErrorDetail("Missing \"" + fieldName + "\" in a work's blurb. Blurb HTML: "
					+ (snippet.length() > 400 ? snippet.substring(0, 400) + "..." : snippet));
			return false;
		}

		@Override
		protected boolean load(Document document, List<Story> list) {

			Elements stories = document.select("li.work.blurb.group");

			if (stories.isEmpty()) {
				// Diagnostic: confirm whether we got the intended page at all, and whether a
				// similarly-named but different wrapper class might be the real one.
				final String title = document.title();
				final Elements similarElements = document.select("li[class*=work]");
				setLastErrorDetail("Page title: \"" + title + "\". Found 0 works via \"li.work.blurb.group\","
						+ " but " + similarElements.size() + " elements matching \"li[class*=work]\".");
				return false;
			}

			for (Element story : stories) {

				Story.Builder builder = new Story.Builder();

				// Fetch the title, the author, and the story id
				Elements header = story.select("h4.heading a");
				if (header.isEmpty()) return missingField("h4.heading a (title)", story);
				Element title = header.first();
				// Anonymous works only have one link in the heading (the title) - the author is
				// shown as plain "Anonymous" text rather than a linked username, so there's no
				// second link to grab in that case.
				final String authorName = header.size() >= 2 ? header.last().ownText() : "Anonymous";
				String id = title.attr("href").replaceAll("[\\D]", "");

				builder.setName(title.ownText());
				builder.setId(Integer.parseInt(id));
				builder.setAuthor(authorName);

				// Fetch the rating
				Element rating = story.select("span.rating").first();
				if (rating == null) return missingField("span.rating", story);
				builder.setRating(rating.text());

				// Fetch the summary
				Element summary = story.select("blockquote.summary").first();
				if (summary != null) {
					builder.setSummary(summary.text());
				}

				// Add characters if they exist
				Elements characters = story.select("li.characters > a");
				for (Element character : characters) {
					builder.addCharacter(character.ownText());
				}

				// Add relationships if they exist
				Elements relationships = story.select("li.relationships > a");
				for (Element relationship : relationships) {
					builder.addRelationship(relationship.ownText());
				}

				// Add additional (freeform) tags if they exist
				Elements additionalTags = story.select("li.freeforms > a");
				for (Element tag : additionalTags) {
					builder.addAdditionalTag(tag.ownText());
				}

				// Add archive warnings if they exist
				Elements warnings = story.select("li.warnings > a");
				for (Element warning : warnings) {
					builder.addWarning(warning.ownText());
				}

				// Fetch the language
				Element language = story.select("dd.language").first();
				if (language == null) return missingField("dd.language", story);
				builder.setLanguage(language.ownText());

				// Fetch the number of words
				Element words = story.select("dd.words").first();
				if (words == null) return missingField("dd.words", story);
				builder.setWordLength(Parser.parseInt(words.ownText()));

				// Fetch the number of chapters
				Element chapters = story.select("dd.chapters").first();
				if (chapters == null) return missingField("dd.chapters", story);
				String chapterNo = chapters.text();
				chapterNo = chapterNo.substring(0, chapterNo.indexOf('/'));
				builder.setChapterLength(Integer.parseInt(chapterNo));

				// Fetch the number of hits (follows)
				Element follows = story.select("dd.hits").first();
				if (follows != null) {
					builder.setFollows(Parser.parseInt(follows.ownText()));
				}

				// Fetch the number of kudos (favorites)
				Element favorites = story.select("dd.kudos").first();
				if (favorites != null) {
					builder.setFollows(Parser.parseInt(favorites.text()));
				}

				// Fetch the number of comments (reviews)
				Element comments = story.select("dd.comments").first();
				if (comments != null){
					builder.setReviews(Parser.parseInt(comments.text()));
				}

				// Fetch the update date
				Element updateText = story.select("p.datetime").first();
				if (updateText == null) return missingField("p.datetime", story);
				try {
					Date updateDate = mFormat.parse(updateText.text());
					builder.setUpdateDate(updateDate);
				} catch (ParseException e) {
					e.printStackTrace();
				}

				// Find if the work is complete
				Elements complete = story.select("span.complete-yes");
				builder.setCompleted(complete.size() > 0);

				list.add(builder.build());
			}

			return true;
		}

	}

}
