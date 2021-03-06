package ohs.ir.medical.clef.ehealth_2015;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

import de.tudarmstadt.ukp.wikipedia.api.WikiConstants.Language;
import de.tudarmstadt.ukp.wikipedia.parser.Link;
import de.tudarmstadt.ukp.wikipedia.parser.NestedList;
import de.tudarmstadt.ukp.wikipedia.parser.NestedListContainer;
import de.tudarmstadt.ukp.wikipedia.parser.NestedListElement;
import de.tudarmstadt.ukp.wikipedia.parser.ParsedPage;
import de.tudarmstadt.ukp.wikipedia.parser.Section;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParser;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParserFactory;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import ohs.io.FileUtils;
import ohs.io.TextFileReader;
import ohs.io.TextFileWriter;
import ohs.ir.lucene.common.CommonFieldNames;
import ohs.ir.lucene.common.MedicalEnglishAnalyzer;
import ohs.ir.medical.general.MIRPath;
import ohs.ir.medical.general.SearcherUtils;
import ohs.types.DeepMap;
import ohs.types.ListMap;
import ohs.utils.Generics;
import ohs.utils.StrUtils;

/**
 * 
 * @author Heung-Seon Oh
 * 
 */
public class ICDHandler {

	private static String getName(String text) {
		String regex = "\\([^\\(\\)]+\\)";
		Pattern p = Pattern.compile(regex);

		Matcher m = p.matcher(text);

		StringBuffer sb = new StringBuffer();

		while (m.find()) {
			m.appendReplacement(sb, "");
		}
		m.appendTail(sb);

		text = sb.toString().trim();
		return text;
	}

	public static Set<String> getStopSectionTitleSet() {
		String[] stopSectionTitles = { "See also", "Further reading", "References", "External links" };
		Set<String> ret = new TreeSet<String>();
		for (String sectionTitle : stopSectionTitles) {
			ret.add(sectionTitle.toLowerCase());
		}
		return ret;
	}

	public static void main(String[] args) throws Exception {
		System.out.println("process begins.");
		ICDHandler ext = new ICDHandler();
		// ext.extractTopLevelChapters();
		// ext.extractStructure();
		// ext.refineStructure();
		ext.attachWikiPages();
		System.out.println("process ends.");
	}

	private static void recursive(NestedListContainer node, List<String> items) {
		for (int i = 0; i < node.getNestedLists().size(); i++) {
			NestedList nl = node.getNestedList(i);

			if (nl instanceof NestedListContainer) {
				recursive((NestedListContainer) nl, items);
			} else if (nl instanceof NestedListElement) {
				recursive((NestedListElement) nl, items);
			}
		}
	}

	private static void recursive(NestedListElement node, List<String> items) {
		String title = getName(node.getText());

		if (title.length() > 0) {
			//
			// if (title.equals("Latent syphilis, unspecified as early or
			// late")) {
			// System.out.println();
			// }

			StringBuffer sb = new StringBuffer();
			if (node.getTemplates().size() > 0) {
				sb.append(String.format("\nCode:\t%s", StrUtils.join("-", node.getTemplates().get(0).getParameters())));
			} else {
				// sb.append("\n");
			}

			sb.append(String.format("\nTitle:\t%s", title));

			for (int i = 0; i < node.getLinks().size(); i++) {
				Link link = node.getLinks().get(i);
				sb.append(String.format("\n%s:\t%d\t%s\t%s", link.getType().toString(), i + 1, link.getText(),
						link.getTarget()));
			}
			items.add(sb.toString().trim());
		}
	}

	public static String removePrefix(String s) {
		int idx = s.indexOf(":");
		s = s.substring(idx + 1).trim();
		return s;
	}

	private Map<String, Integer> cacheMap;

	private IndexSearcher indexSearcher;

	private MedicalEnglishAnalyzer analyzer;

	public ICDHandler() throws Exception {
		analyzer = MedicalEnglishAnalyzer.newAnalyzer();
		indexSearcher = SearcherUtils.getIndexSearcher(MIRPath.WIKI_INDEX_DIR);
		cacheMap = new HashMap<String, Integer>();
	}

	public void attachWikiPages() throws Exception {
		int numRedirects = 0;

		Set<String> stopSecTitleSet = getStopSectionTitleSet();

		String regex = "#REDIRECT \\[\\[([^\\[\\]]+)\\]\\]";
		Pattern p = Pattern.compile(regex);

		MediaWikiParser parser = new MediaWikiParserFactory(Language.english).createParser();

		TextFileReader reader = new TextFileReader(MIRPath.ICD10_HIERARCHY_REFINED_FILE);
		TextFileWriter writer = new TextFileWriter(MIRPath.ICD10_HIERARCHY_PAGE_FILE);
		TextFileWriter writer2 = new TextFileWriter(MIRPath.ICD10_LOG_FILE);

		while (reader.hasNext()) {
			List<String> lines = reader.getNextLines();

			String chapter = lines.get(1);
			String section = lines.get(2).split("\t")[1];
			String subSection = lines.get(3).replace("\t", " - ");

			chapter = removePrefix(chapter);

			for (int i = 4; i < lines.size(); i++) {
				String line = lines.get(i);
				String[] parts = line.split("\t");
				String wikiTitle = parts[1].replace("_", " ");
				String wikiKey = parts[2].replace("_", " ").toLowerCase();

				String searchKey = wikiKey;

				boolean isSearching = true;

				ParsedPage page = null;
				Document doc = null;

				while (isSearching) {
					doc = searchDocument(searchKey);

					if (doc == null) {
						break;
					}

					String title = doc.getField(CommonFieldNames.TITLE).stringValue();
					String wikiText = doc.getField(CommonFieldNames.CONTENT).stringValue();

					page = parser.parse(wikiText);

					String ss = page.getText();

					if (ss.startsWith("REDIRECT")) {
						Matcher m = p.matcher(wikiText);
						if (m.find()) {
							String redirectTo = m.group(1).trim().toLowerCase().replace("_", " ");
							if (searchKey.equals(redirectTo) || redirectTo.length() == 0) {
								isSearching = false;
							} else {
								searchKey = redirectTo;
								numRedirects++;
							}
						} else {
							isSearching = false;
						}
					} else {
						isSearching = false;
					}
				}

				if (doc == null) {
					writer2.write(String.format("Fail to find [%s]\n", wikiKey));
					continue;
				}

				String title = doc.getField(CommonFieldNames.TITLE).stringValue();
				String wikiText = doc.getField(CommonFieldNames.CONTENT).stringValue();
				String newLine = StrUtils.join("\t",
						new String[] { chapter, section, subSection, title, wikiText.replace("\n", "<NL>") });

				writer.write(newLine + "\n");
			}
		}

		reader.close();
		writer.close();
		writer2.close();
	}

	public void extractStructure() throws Exception {
		Set<String> stopSecTitleSet = getStopSectionTitleSet();

		String icdText = FileUtils.readText(MIRPath.ICD10_TOP_LEVEL_CHAPTER_FILE);

		String[] lines = icdText.split("\n");

		MediaWikiParser parser = new MediaWikiParserFactory().createParser();

		List<String> chapterItems = Generics.newArrayList();

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			String[] parts = line.split("\t");

			String chapter = parts[0];
			String url = parts[1];

			Document doc = searchDocument(chapter);

			if (doc == null) {
				continue;
			}

			String title = doc.getField(CommonFieldNames.TITLE).stringValue();
			String wikiText = doc.getField(CommonFieldNames.CONTENT).stringValue();

			ParsedPage page = parser.parse(wikiText);

			if (page == null) {
				continue;
			}

			page.setName(title);

			List<String> pageItems = Generics.newArrayList();
			pageItems.add(chapter);

			for (int k = 0; k < page.getSections().size(); k++) {
				Section section = page.getSection(k);

				String secTitle = section.getTitle();

				if (secTitle != null && stopSecTitleSet.contains(secTitle.toLowerCase())) {
					continue;
				}

				System.out.println(secTitle);

				List<String> secItems = Generics.newArrayList();
				secItems.add(String.format("Section Name:\t%s", secTitle));

				List<NestedListContainer> containers = section.getNestedLists();

				for (int m = 0; m < containers.size(); m++) {
					NestedListContainer container = containers.get(m);
					recursive(container, secItems);
				}

				if (secItems.size() == 1) {
					continue;
				}

				String secOutput = StrUtils.join("\n\n", secItems);

				pageItems.add(secOutput);
			}

			if (pageItems.size() > 1) {
				String pageOutput = StrUtils.join("\n\n", pageItems);
				chapterItems.add(pageOutput);
			}
		}

		String output = StrUtils.join("\n\n", chapterItems);

		FileUtils.write(MIRPath.ICD10_HIERARCHY_FILE, output);
	}

	public void extractTopLevelChapters() throws Exception {
		String text = FileUtils.readText(MIRPath.ICD10_HTML_FILE);

		String prefix = "<td><a href=\"http://en.wikipedia.org/wiki/";
		String regex = "\"([^\"]+)\"";
		Pattern p = Pattern.compile(regex);

		StringBuffer sb = new StringBuffer();

		for (String line : text.split("\n")) {
			int start = line.indexOf(prefix);

			if (line.contains("<td><a href=\"http://en.wikipedia.org/wiki/ICD-10_Chapter")) {
				Matcher m = p.matcher(line);

				List<String> list = new ArrayList<String>();

				while (m.find()) {
					list.add(m.group(1));
				}

				sb.append(list.get(1) + "\t" + list.get(0) + "\n");
			}
		}

		FileUtils.write(MIRPath.ICD10_TOP_LEVEL_CHAPTER_FILE, sb.toString().trim());
	}

	public void refineStructure() throws Exception {
		List<String> items = new ArrayList<String>();

		TextFileReader reader = new TextFileReader(new File(MIRPath.ICD10_HIERARCHY_FILE));
		while (reader.hasNext()) {
			List<String> lines = reader.getNextLines();
			items.add(StrUtils.join("\n", lines));
		}
		reader.close();

		IntArrayList chapterLocs = new IntArrayList();
		IntArrayList sectionLocs = new IntArrayList();
		IntArrayList codeLocs = new IntArrayList();

		DeepMap<Integer, Integer, Integer> iMap = new DeepMap<Integer, Integer, Integer>();

		{
			int chapLoc = -1;
			int secLoc = -1;

			for (int i = 0; i < items.size(); i++) {
				String item = items.get(i);

				if (item.startsWith("ICD-10 Chapter")) {
					chapLoc = i;
				} else if (item.startsWith("Section Name:")) {
					secLoc = i;
				} else if (item.startsWith("Code:")) {
					chapterLocs.add(chapLoc);
					sectionLocs.add(secLoc);
					codeLocs.add(i);
				}
			}
		}

		System.out.println(codeLocs);

		ListMap<String, String> map = new ListMap<String, String>();

		for (int i = 0; i < codeLocs.size() - 1; i++) {
			String chater = items.get(chapterLocs.get(i));
			String section = items.get(sectionLocs.get(i));

			int start = codeLocs.get(i);
			int end = codeLocs.get(i + 1);

			String code = null;
			String title = null;
			List<String> wikiTitles = new ArrayList<String>();
			List<String> wikiKeys = new ArrayList<String>();

			for (int j = start; j < end; j++) {
				String item = items.get(j);
				wikiKeys.clear();
				wikiTitles.clear();

				String[] lines = item.split("\n");

				for (int k = 0; k < lines.length; k++) {
					String line = lines[k];
					if (line.startsWith("Code:")) {
						code = line.split("\t")[1];
					} else if (line.startsWith("Title:")) {
						title = line.split("\t")[1];
					} else if (line.startsWith("INTERNAL")) {
						wikiTitles.add(line.split("\t")[2]);
						wikiKeys.add(line.split("\t")[3]);
					}
				}

				String regex = "^[A-Z]\\-[0-9][0-9]";
				Pattern p = Pattern.compile(regex);

				if (!p.matcher(code).find()) {
					continue;
				}

				String tempKey = String.format("%s\n%s\n%s\t%s", chater, section, code, title);

				for (int k = 0; k < wikiKeys.size(); k++) {
					String tempValue = wikiTitles.get(k) + "\t" + wikiKeys.get(k);
					map.put(tempKey, tempValue);
				}
			}
		}

		System.out.println(map);

		StringBuffer sb = new StringBuffer();

		List<String> keys = new ArrayList<String>(map.keySet());

		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			List<String> internals = map.get(key);

			sb.append(i + 1);
			sb.append("\n" + key);

			for (int j = 0; j < internals.size(); j++) {
				sb.append(String.format("\n%d\t%s", j + 1, internals.get(j)));
			}

			if (i != keys.size()) {
				sb.append("\n\n");
			}
		}

		FileUtils.write(MIRPath.ICD10_HIERARCHY_REFINED_FILE, sb.toString());
	}

	private Document searchDocument(String wikiTitle) throws Exception {
		System.out.println(wikiTitle);

		Document ret = null;

		Integer docId = cacheMap.get(wikiTitle.toLowerCase());

		if (docId == null) {
			BooleanQuery searchQuery = new BooleanQuery();
			searchQuery.add(new BooleanClause(
					new TermQuery(new Term(CommonFieldNames.REDIRECT_TITLE, wikiTitle.toLowerCase())), Occur.SHOULD));
			TopDocs topDocs = indexSearcher.search(searchQuery, 1);
			if (topDocs.scoreDocs.length > 0) {
				ret = indexSearcher.getIndexReader().document(topDocs.scoreDocs[0].doc);
			}

			if (ret == null) {
				cacheMap.put(wikiTitle.toLowerCase(), -1);
			} else {
				cacheMap.put(wikiTitle.toLowerCase(), docId);
			}

		} else if (docId == -1) {

		} else {
			indexSearcher.getIndexReader().document(docId.intValue());
		}

		return ret;
	}
}
