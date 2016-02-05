package ohs.eden.keyphrase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ohs.io.FileUtils;
import ohs.io.TextFileReader;
import ohs.io.TextFileWriter;
import ohs.types.CounterMap;
import ohs.types.Indexer;
import ohs.types.ListMap;
import ohs.utils.Generics;
import ohs.utils.StrUtils;

public class DataHandler {

	public static void main(String[] args) throws Exception {
		System.out.println("process begins.");
		DataHandler dh = new DataHandler();
		// dh.extractKeywordData();
		dh.process();
		System.out.println("process ends.");
	}

	public void check() {
		TextFileReader reader = new TextFileReader(KPPath.EDS_DICT_FILE);
		TextFileWriter writer = new TextFileWriter(KPPath.KEYPHRASE_DIR + "eds_dict.txt");
		while (reader.hasNext()) {
			String line = reader.next();
			String[] parts = line.split(",");
			int num_commas = parts.length - 1;
			if (parts.length != 3 && num_commas > 1) {
				writer.write(line + "\t" + num_commas + "\n");
			}
		}
		reader.close();
		writer.close();
	}

	public void extractKeywordData() throws Exception {
		String[] inFileNames = { KPPath.PAPER_DUMP_FILE, KPPath.REPORT_DUMP_FILE };

		CounterMap<String, String> keywordDocs = Generics.newCounterMap();

		TextFileWriter writer = new TextFileWriter(KPPath.ABSTRACT_FILE);

		for (int i = 0; i < inFileNames.length; i++) {
			String inFileName = inFileNames[i];

			TextFileReader reader = new TextFileReader(inFileName);
			List<String> labels = Generics.newArrayList();

			while (reader.hasNext()) {
				// if (reader.getNumLines() > 100000) {
				// break;
				// }

				String line = reader.next();
				String[] parts = line.split("\t");

				for (int j = 0; j < parts.length; j++) {
					if (parts[j].length() > 1) {
						parts[j] = parts[j].substring(1, parts[j].length() - 1);
					}
				}

				if (reader.getNumLines() == 1) {
					for (String p : parts) {
						labels.add(p);
					}
				} else {
					if (parts.length != labels.size()) {
						continue;
					}

					String cn = parts[0];
					String korAbs = parts[parts.length - 2];
					String engAbs = parts[parts.length - 1];
					String korKeywordStr = null;
					String engKeywordStr = null;

					if (i == 0) {
						korKeywordStr = parts[8];
						engKeywordStr = parts[9];
					} else if (i == 1) {
						korKeywordStr = parts[5];
						engKeywordStr = parts[6];
					}

					List<String> kors = getKeywords(korKeywordStr);
					List<String> engs = getKeywords(engKeywordStr);

					if (kors.size() == 0 && engs.size() == 0) {

					} else if (kors.size() == engs.size()) {
						if (kors.size() > 0) {
							for (int j = 0; j < kors.size(); j++) {
								String kor = kors.get(j);
								String eng = engs.get(j);
								keywordDocs.incrementCount(kor + "\t" + eng, cn, 1);
							}
						}
					} else {
						for (String kor : kors) {
							keywordDocs.incrementCount(kor + "\t" + "<none>", cn, 1);
						}

						for (String eng : engs) {
							keywordDocs.incrementCount("<none>" + "\t" + eng, cn, 1);
						}
					}

					writer.write(String.format("\"%s\"\t\"%s\"\t\"%s\"\n", cn, korAbs, engAbs));
				}
			}
			reader.close();
			// writer.close();

			// Iterator<String> iter = cm.keySet().iterator();
			// while (iter.hasNext()) {
			// String inKey = iter.next();
			// Counter<String> c = cm.getCounter(inKey);
			// if (c.totalCount() < 5) {
			// iter.remove();
			// }
			// }
		}
		writer.close();

		FileUtils.writeStrCounterMap(KPPath.KEYWORD_FILE, keywordDocs, null, true);
		// FileUtils.write(KPPath.PAPER_KOREAN_CONTEXT_FILE, cm2, null, true);
		// FileUtils.write(KPPath.PAPER_ENGLISH_CONTEXT_FILE, cm3, null, true);

		// FileUtils.write(KWPath.PAPER_KEYWORD_FILE, kwCounts, true);
	}

	private List<String> getKeywords(String keywordStr) {
		List<String> ret = Generics.newArrayList();
		for (String kw : keywordStr.split(";")) {
			kw = kw.trim();
			if (kw.length() > 0) {
				ret.add(kw);
			}
		}
		return ret;
	}

	private String tagKeyword(String keyword, String text) throws Exception {
		StringBuffer sb = new StringBuffer();

		Pattern p = Pattern.compile(keyword, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(text);

		while (m.find()) {
			String g = m.group();
			m.appendReplacement(sb, String.format("<KWD>%s</KWD>", g));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	public void process() throws Exception {
		KeywordData kwdData = new KeywordData();
		kwdData.read(KPPath.KEYWORD_FILE.replace(".txt", ".ser"));

		Indexer<String> kwdIndexer = kwdData.getKeywordIndexer();
		ListMap<Integer, Integer> docKeywords = Generics.newListMap();
		for (int kwdid : kwdData.getKeywordDocs().keySet()) {
			List<Integer> docids = kwdData.getKeywordDocs().get(kwdid);

			for (int docid : docids) {
				docKeywords.put(docid, kwdid);
			}
		}

		TextFileReader reader = new TextFileReader(KPPath.ABSTRACT_FILE);
		TextFileWriter writer = new TextFileWriter(KPPath.KEYWORD_ABSTRACT_FILE);

		while (reader.hasNext()) {
			String[] parts = reader.next().split("\t");

			for (int i = 0; i < parts.length; i++) {
				parts[i] = parts[i].substring(1, parts[i].length() - 1);
			}

			int docid = kwdData.getDocIndexer().indexOf(parts[0]);
			String korAbs = parts[1];
			String engAbs = parts[2];

			List<Integer> kwdids = docKeywords.get(docid, false);

			if (kwdids == null) {
				continue;
			}

			String[] abss = { korAbs, engAbs };
			Set<String>[] kwdSets = new HashSet[2];

			for (int j = 0; j < kwdSets.length; j++) {
				kwdSets[j] = Generics.newHashSet();
			}

			for (int kwdid : kwdids) {
				String keyword = kwdIndexer.getObject(kwdid);
				String korKwd = keyword.split("\t")[0];
				String engKwd = keyword.split("\t")[1];
				kwdSets[0].add(korKwd);
				kwdSets[1].add(engKwd);
			}

			for (int j = 0; j < kwdSets.length; j++) {
				if (j != 0) {
					continue;
				}

				Set<String> founds = Generics.newHashSet();
				String abs = abss[j];

				try {
					abs = StrUtils.tag(abs, kwdSets[j], "KWD");
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}

				if (abs.length() > abss[j].length()) {

					// StrUtils.extract(abs);

					writer.write(abs.replace(". ", ".\n") + "\n\n");
				}

				// if (founds.size() > 0) {
				// writer.write(String.format("Keywords:\t%s\n", founds));
				// writer.write(String.format("Abstract:\t%s\n", abss[j]));
				// writer.write("\n");
				// }

			}

		}
		reader.close();
		writer.close();
	}

}
