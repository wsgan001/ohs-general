package ohs.eden.keyphrase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ohs.eden.linker.StringSearcher;
import ohs.io.FileUtils;
import ohs.io.TextFileWriter;
import ohs.string.search.ppss.Gram;
import ohs.string.search.ppss.GramGenerator;
import ohs.types.Counter;
import ohs.types.CounterMap;
import ohs.types.Indexer;
import ohs.types.SetMap;
import ohs.utils.Generics;

public class KeywordClusterer {

	public static final String NONE = "<none>";

	public static void main(String[] args) throws Exception {
		System.out.println("process begins.");

		KeywordData rkd = new KeywordData();

		if (FileUtils.exists(KPPath.KEYWORD_FILE.replace("txt", "ser"))) {
			rkd.read(KPPath.KEYWORD_FILE.replace("txt", "ser"));
		} else {
			rkd.readFromText(KPPath.KEYWORD_FILE);
			rkd.write(KPPath.KEYWORD_FILE.replace("txt", "ser"));
		}

		KeywordClusterer kc = new KeywordClusterer(rkd);
		kc.cluster();
		kc.write(KPPath.KEYWORD_CLUSTER_FILE);

		System.out.println("process ends.");
	}

	private static String normalize(String s) {
		return s.replaceAll("[\\s\\p{Punct}&&[^<>]]+", "").toLowerCase();
	}

	private KeywordData kwData;

	private Indexer<String> keywordIndexer;

	private CounterMap<Integer, Integer> clusterKeywordMap;

	private Map<Integer, Integer> keywordClusterMap;

	private Map<Integer, String> clusterLabelMap;

	public KeywordClusterer(KeywordData kwData) {
		this.kwData = kwData;

		keywordIndexer = kwData.getKeywordIndexer();
	}

	public void cluster() {
		clusterKeywordMap = Generics.newCounterMap();

		keywordClusterMap = Generics.newHashMap();

		for (int i = 0; i < keywordIndexer.size(); i++) {
			clusterKeywordMap.setCount(i, i, 1);
			keywordClusterMap.put(i, i);
		}

		clusterUsingExactMatch();

		clusterUsingExactLanguageMatch(false);

		selectClusterLabels();
	}

	private void clusterUsingExactLanguageMatch(boolean isEnglish) {
		System.out.println("cluster using exact language match");

		SetMap<String, Integer> keyKeywordMap = Generics.newSetMap();

		for (Entry<Integer, Counter<Integer>> e : clusterKeywordMap.getEntrySet()) {

			Counter<Integer> kwids = e.getValue();

			for (int kwid : kwids.keySet()) {
				String keyword = keywordIndexer.getObject(kwid);
				String key = isEnglish ? keyword.split("\t")[1] : keyword.split("\t")[0];
				key = normalize(key);

				if (key.equals(NONE) || key.length() < 4) {
					continue;
				}
				// key = key.replaceAll("[\\s\\p{Punct}]+", "").toLowerCase();
				keyKeywordMap.put(key, kwid);
			}
		}

		Counter<Integer> kwClusterFreqs = Generics.newCounter();

		for (String key : keyKeywordMap.keySet()) {
			for (int kwid : keyKeywordMap.get(key)) {
				int cid = keywordClusterMap.get(kwid);
				kwClusterFreqs.incrementCount(cid, 1);
			}
		}

		CounterMap<String, Integer> keyClusterMap = Generics.newCounterMap();

		for (String key : keyKeywordMap.keySet()) {
			for (int kwid : keyKeywordMap.get(key)) {
				int cid = keywordClusterMap.get(kwid);
				// double kw_cluster_freq = kwClusterFreqs.getCount(cid);
				// if (kw_cluster_freq > 1) {
				// System.out.println(key + "\t" + kw_cluster_freq);
				// continue;
				// }

				// if (visited.contains(cid)) {
				// continue;
				// }
				// visited.add(cid);
				keyClusterMap.incrementCount(key, cid, 1);
			}
		}

		// System.out.println(keyClusterMap.invert());
		// System.out.println();

		for (String key : keyClusterMap.keySet()) {
			Set<Integer> cids = keyClusterMap.getCounter(key).keySet();

			if (cids.size() > 1) {
				Counter<Integer> newCluster = Generics.newCounter();
				int new_cid = min(cids);

				for (int cid : cids) {
					Counter<Integer> c = clusterKeywordMap.removeKey(cid);
					if (c != null) {
						newCluster.incrementAll(c);
					}
				}

				for (int kwid : newCluster.keySet()) {
					clusterKeywordMap.setCount(new_cid, kwid, 1);
					keywordClusterMap.put(kwid, new_cid);
				}
			}
		}
	}

	private void clusterUsingExactMatch() {
		System.out.println("cluster using exact match");
		SetMap<String, Integer> tm = Generics.newSetMap();

		for (int i = 0; i < keywordIndexer.size(); i++) {
			String keyword = keywordIndexer.getObject(i);
			keyword = keyword.replace("\t", "tab").replaceAll("[\\s\\p{Punct}]+", "").toLowerCase();
			tm.put(keyword, i);
		}

		for (String keyword : tm.keySet()) {
			List<Integer> kwids = Generics.newArrayList(tm.get(keyword));

			if (kwids.size() > 1) {
				Set<Integer> cids = Generics.newHashSet();
				Counter<Integer> newCluster = Generics.newCounter();

				for (int i = 0; i < kwids.size(); i++) {
					int kwid = kwids.get(i);
					int cid = keywordClusterMap.get(kwid);
					cids.add(cid);
					newCluster.incrementAll(clusterKeywordMap.removeKey(cid));
				}

				int new_cid = min(cids);

				for (int kwid : kwids) {
					clusterKeywordMap.setCount(new_cid, kwid, 1);
					keywordClusterMap.put(kwid, new_cid);
				}
			}
		}

		// printClusters();
	}

	private void clusterUsingNGrams() {
		Indexer<String> ngramIndexer = Generics.newIndexer();
	}

	private Counter<String>[] computeLabelScores(Set<Integer> kwids) {
		GramGenerator gg = new GramGenerator(3);
		int num_langs = 2;

		Counter<String>[] ret = new Counter[num_langs];

		for (int i = 0; i < num_langs; i++) {
			CounterMap<String, Character> ngramProbs = Generics.newCounterMap();

			Counter<Integer> c = Generics.newCounter();

			for (int kwid : kwids) {
				String keyword = keywordIndexer.getObject(kwid);
				String lang = keyword.split("\t")[i];
				if (lang.equals(NONE)) {
					continue;
				}
				int kw_freq = kwData.getKeywordFreqs()[kwid];
				c.incrementCount(kwid, kw_freq);
			}

			Counter<Integer> backup = Generics.newCounter(c);

			c.pruneKeysBelowThreshold(2);

			if (c.size() == 0) {
				c = backup;
			}

			for (int kwid : c.keySet()) {
				String keyword = keywordIndexer.getObject(kwid);
				String lang = keyword.split("\t")[i];
				int kw_freq = (int) c.getCount(kwid);

				for (Gram g : gg.generate(lang.toLowerCase())) {
					ngramProbs.incrementCount(g.getString().substring(0, 2), g.getString().charAt(2), kw_freq);
				}
			}

			ngramProbs.normalize();

			Counter<String> kwScores = Generics.newCounter();

			for (int kwid : c.keySet()) {
				String keyword = keywordIndexer.getObject(kwid);
				String lang = keyword.split("\t")[i];
				double log_likelihood = computeLoglikelihood(gg.generate(lang.toLowerCase()), ngramProbs);
				kwScores.incrementCount(lang, log_likelihood);
			}

			if (kwScores.size() == 0) {
				kwScores.setCount(NONE, 0);
			}

			double max = kwScores.max();
			double score_sum = 0;

			for (String lang : kwScores.keySet()) {
				double score = kwScores.getCount(lang);
				score = Math.exp(score - max);
				kwScores.setCount(lang, score);
				score_sum += score;
			}
			kwScores.scale(1f / score_sum);
			ret[i] = kwScores;
		}
		return ret;
	}

	private double computeLoglikelihood(Gram[] gs, CounterMap<String, Character> bigramProbs) {
		double ret = 0;
		for (Gram g : gs) {
			double prob = bigramProbs.getCount(g.getString().substring(0, 2), g.getString().charAt(2));
			if (prob > 0) {
				ret += Math.log(prob);
			}
		}
		return ret;
	}

	private void filter() {
		for (int cid : clusterKeywordMap.keySet()) {
			Counter<Integer> kwids = clusterKeywordMap.getCounter(cid);
		}
	}

	private int min(Set<Integer> set) {
		int ret = Integer.MAX_VALUE;
		for (int i : set) {
			if (i < ret) {
				ret = i;
			}
		}
		return ret;
	}

	private void printClusters() {
		System.out.printf("Clusters:\t%d\n", clusterKeywordMap.size());

		List<Integer> cids = Generics.newArrayList();
		for (int cid : clusterKeywordMap.keySet()) {
			Counter<Integer> kwids = clusterKeywordMap.getCounter(cid);

			if (kwids.size() > 1) {
				cids.add(cid);
			}
		}

		for (int i = 0; i < 10 && i < cids.size(); i++) {
			int cid = cids.get(i);

			Counter<Integer> kwids = clusterKeywordMap.getCounter(cid);

			List<Integer> temp = kwids.getSortedKeys();

			StringBuffer sb = new StringBuffer();
			sb.append(String.format("Cluster ID:\t%d", cid));

			for (int j = 0; j < temp.size(); j++) {
				int kwid = temp.get(j);
				sb.append(String.format("\n%d:\t%s", j + 1, keywordIndexer.getObject(kwid)));
			}

			System.out.println(sb.toString() + "\n");
		}
		System.out.println();
	}

	private void selectClusterLabels() {
		System.out.println("select cluster labels");

		clusterLabelMap = Generics.newHashMap();

		for (int cid : clusterKeywordMap.keySet()) {
			Set<Integer> kwids = clusterKeywordMap.getCounter(cid).keySet();
			Counter<String>[] scoreData = computeLabelScores(kwids);
			String korLabel = scoreData[0].argMax();
			String engLabel = scoreData[1].argMax();

			clusterLabelMap.put(cid, korLabel + "\t" + engLabel);
		}
	}

	public void write(String fileName) {
		TextFileWriter writer = new TextFileWriter(fileName);

		writer.write(String.format("Clusters:\t%d", clusterKeywordMap.size()));
		writer.write(String.format("\nKeywords:\t%d", (int) clusterKeywordMap.totalCount()));

		// List<Integer> cids = clusterKeywordMap.getInnerCountSums().getSortedKeys();
		List<Integer> cids = Generics.newArrayList();

		{
			List<String> keys = Generics.newArrayList();

			Map<Integer, Integer> map = Generics.newHashMap();

			for (int cid : clusterKeywordMap.keySet()) {
				int kwid = clusterKeywordMap.getCounter(cid).argMax();
				map.put(kwid, cid);
				String keyword = keywordIndexer.getObject(kwid);
				keys.add(keyword);
			}

			Collections.sort(keys);

			cids = Generics.newArrayList();
			for (int i = 0; i < keys.size(); i++) {
				int kwid = keywordIndexer.indexOf(keys.get(i));
				int cid = map.get(kwid);
				cids.add(cid);
			}
		}

		int cutoff = 10;

		for (int i = 0, n = 1; i < cids.size(); i++) {
			int cid = cids.get(i);

			StringBuffer sb = new StringBuffer();
			sb.append(String.format("Cluster Number:\t%d", n));
			sb.append(String.format("\nCluster ID:\t%d", cid));
			sb.append(String.format("\nCluater Label:\t%s", clusterLabelMap.get(cid)));
			sb.append(String.format("\nKeywords:\t%d", clusterKeywordMap.getCounter(cid).size()));

			Counter<Integer> c = Generics.newCounter();

			for (int kwid : clusterKeywordMap.getCounter(cid).keySet()) {
				c.setCount(kwid, kwData.getKeywordFreqs()[kwid]);
			}

			if (c.size() < cutoff) {
				continue;
			}

			n++;

			List<Integer> kwids = c.getSortedKeys();
			for (int j = 0; j < kwids.size(); j++) {
				int kwid = kwids.get(j);
				int kw_freq = kwData.getKeywordFreqs()[kwid];
				sb.append(String.format("\n%d:\t%d\t%s\t%d", j + 1, kwid, keywordIndexer.getObject(kwid), kw_freq));
			}
			writer.write("\n\n" + sb.toString());
		}
		writer.close();

		System.out.printf("write [%d] clusters at [%s]\n", clusterKeywordMap.size(), fileName);
	}

}