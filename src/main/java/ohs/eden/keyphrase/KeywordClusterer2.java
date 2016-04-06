package ohs.eden.keyphrase;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ohs.io.FileUtils;
import ohs.io.TextFileWriter;
import ohs.math.ArrayMath;
import ohs.math.ArrayUtils;
import ohs.math.VectorMath;
import ohs.math.VectorUtils;
import ohs.matrix.SparseVector;
import ohs.string.search.ppss.Gram;
import ohs.string.search.ppss.GramGenerator;
import ohs.types.Counter;
import ohs.types.CounterMap;
import ohs.types.Indexer;
import ohs.types.Pair;
import ohs.types.SetMap;
import ohs.utils.Generics;
import ohs.utils.StopWatch;
import ohs.utils.StrUtils;
import ohs.utils.TermWeighting;
import ohs.utils.UnicodeUtils;

public class KeywordClusterer2 {

	public static void main(String[] args) throws Exception {
		System.out.printf("[%s] begins.\n", KeywordClusterer2.class.getName());

		KeywordData data = new KeywordData();

		if (FileUtils.exists(KPPath.KEYWORD_DATA_FILE.replace("txt", "ser"))) {
			data.read(KPPath.KEYWORD_DATA_FILE.replace("txt", "ser"));
		} else {
			data.readText(KPPath.KEYWORD_DATA_FILE);
			data.write(KPPath.KEYWORD_DATA_FILE.replace("txt", "ser"));
		}

		KeywordClusterer2 kc = new KeywordClusterer2(data);
		kc.cluster();
		kc.writeClusters(KPPath.KEYWORD_CLUSTER_FILE);

		// data.write(KPPath.KEYWORD_DATA_FILE.replace("txt", "ser"));

		System.out.println("process ends.");
	}

	private static String normalize(String s) {
		return s.replaceAll("[\\p{Punct}\\s]+", "").toLowerCase();
	}

	private KeywordData kwdData;

	private Indexer<String> kwdIndexer;

	private CounterMap<Integer, Integer> clusterToKwds;

	private int[] kwdToCluster;

	private Map<Integer, String> clusterLabel;

	private GramGenerator gg = new GramGenerator(2);

	private int prefix_size = 6;

	public KeywordClusterer2(KeywordData kwdData) {
		this.kwdData = kwdData;

		kwdIndexer = kwdData.getKeywordIndexer();
	}

	private int[][] buildGramPostings(Map<Integer, SparseVector> cents, int prefix_size, int gram_size) {
		SetMap<Integer, Integer> gramPostings = Generics.newSetMap();
		for (int cid : cents.keySet()) {
			SparseVector cent = cents.get(cid);
			cent.sortByValue();
			for (int i = 0; i < cent.size() && i < prefix_size; i++) {
				gramPostings.put(cent.indexAtLoc(i), cid);
			}
			cent.sortByIndex();
		}
		System.out.printf("build [%d] gram postings\n", gramPostings.size());

		int[][] ret = new int[gram_size][];
		for (int i = 0; i < ret.length; i++) {
			int[] cids = new int[0];
			Set<Integer> set = gramPostings.get(i, false);

			if (set != null) {
				cids = new int[set.size()];
				int loc = 0;
				for (int cid : set) {
					cids[loc++] = cid;
				}
				set.clear();

				Arrays.sort(cids);
			}
			ret[i] = cids;
		}
		return ret;
	}

	public void cluster() throws Exception {
		kwdToCluster = new int[kwdIndexer.size()];

		clusterToKwds = Generics.newCounterMap(kwdIndexer.size());

		for (int i = 0; i < kwdIndexer.size(); i++) {
			clusterToKwds.setCount(i, i, 1);
			kwdToCluster[i] = i;
		}

		matchTwoLanguages();

		// selectClusterLabels();
		// writeClusters(KPPath.KEYWORD_CLUSTER_FILE.replace(".txt", "-01.txt"));

		matchLanguage(false);

		// selectClusterLabels();
		// writeClusters(KPPath.KEYWORD_CLUSTER_FILE.replace(".txt", "-02.txt"));

		matchKoreanCharacters();

		selectClusterLabels();
		writeClusters(KPPath.KEYWORD_CLUSTER_FILE.replace(".txt", "-03.txt"));

		// exactLanguageMatch(true);

		// selectClusterLabels();

		// writeClusterText(KPPath.KEYWORD_CLUSTER_FILE.replace(".txt", "-03.txt"));

		// filter(3);

		// hierarchicalAgglomerativeClustering();

		selectClusterLabels();

		SetMap<Integer, Integer> t = Generics.newSetMap(clusterToKwds.size());

		for (int cid : clusterToKwds.keySet()) {
			t.put(cid, clusterToKwds.keySetOfCounter(cid));
		}

		kwdData.setClusterLabel(clusterLabel);
		kwdData.setClusters(t);
	}

	private Counter<String>[] computeLabelScores(Set<Integer> kwdids) {
		GramGenerator gg = new GramGenerator(3);
		int num_langs = 2;

		Counter<String>[] ret = new Counter[num_langs];

		for (int i = 0; i < num_langs; i++) {
			CounterMap<String, Character> gramProbs = Generics.newCounterMap();

			Counter<Integer> c = Generics.newCounter();

			for (int kwdid : kwdids) {
				String kwdStr = kwdIndexer.getObject(kwdid);
				String kwd = kwdStr.split("\t")[i];

				if (kwd.length() == 2) {
					continue;
				}

				int kw_freq = kwdData.getKeywordFreqs()[kwdid];
				c.incrementCount(kwdid, kw_freq);
			}

			Counter<Integer> backup = Generics.newCounter(c);

			c.pruneKeysBelowThreshold(2);

			if (c.size() == 0) {
				c = backup;
			}

			for (int kwdid : c.keySet()) {
				String kwdStr = kwdIndexer.getObject(kwdid);
				String kwd = kwdStr.split("\t")[i];
				int kw_freq = (int) c.getCount(kwdid);

				for (Gram g : gg.generateQGrams(kwd.toLowerCase())) {
					gramProbs.incrementCount(g.getString().substring(0, 2), g.getString().charAt(2), kw_freq);
				}
			}

			gramProbs.normalize();

			Counter<String> kwdScores = Generics.newCounter();

			for (int kwid : c.keySet()) {
				String kwdStr = kwdIndexer.getObject(kwid);
				String kwd = kwdStr.split("\t")[i];
				double log_likelihood = computeLoglikelihood(gg.generateQGrams(normalize(kwd)), gramProbs);
				kwdScores.incrementCount(kwd, log_likelihood);
			}

			if (kwdScores.size() == 0) {
				kwdScores.setCount("\"\"", 0);
			}

			double max = kwdScores.max();
			double score_sum = 0;

			for (String lang : kwdScores.keySet()) {
				double score = kwdScores.getCount(lang);
				score = Math.exp(score - max);
				kwdScores.setCount(lang, score);
				score_sum += score;
			}
			kwdScores.scale(1f / score_sum);
			ret[i] = kwdScores;
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

	private void computeWeights(Map<Integer, SparseVector> cents, int[] gram_freqs) {
		double num_clusters = cents.size();

		for (int cid : cents.keySet()) {
			SparseVector cent = cents.get(cid);
			double norm = 0;
			for (int i = 0; i < cent.size(); i++) {
				int gid = cent.indexAtLoc(i);
				double cnt = cent.valueAtLoc(i);
				double tf = Math.log(cnt) + 1;
				double gram_freq = gram_freqs[gid];
				double idf = gram_freq == 0 ? 0 : Math.log((num_clusters + 1) / gram_freq);
				double tfidf = tf * idf;
				cent.setAtLoc(i, tfidf);
				norm += (tfidf * tfidf);
			}
			norm = Math.sqrt(norm);
			cent.scale(1f / norm);
		}
	}

	private void filter(int cutoff) {
		int old_size = clusterToKwds.size();

		Iterator<Integer> iter = clusterToKwds.keySet().iterator();
		while (iter.hasNext()) {
			int cid = iter.next();
			Counter<Integer> kwids = clusterToKwds.getCounter(cid);
			if (kwids.size() < cutoff) {
				kwids.clear();
				iter.remove();
			}
		}

		System.out.printf("filter using cutoff [%d]\n", cutoff);
		System.out.printf("clusterToKwds [%d -> %d]\n", old_size, clusterToKwds.size());
	}

	private void hierarchicalAgglomerativeClustering() {
		System.out.println("hierarchical agglomerative clustering");

		Indexer<String> gramIndexer = Generics.newIndexer();
		Map<Integer, SparseVector> cents1 = Generics.newHashMap();
		Map<Integer, SparseVector> cents2 = Generics.newHashMap();

		int[] gram_freqs = new int[0];
		int[] unigram_freqs = new int[0];

		{
			Counter<Integer> gramFreqs = Generics.newCounter();
			Counter<Integer> unigramFreqs = Generics.newCounter();

			for (int cid : clusterToKwds.keySet()) {
				Set<Integer> kwdids = clusterToKwds.keySetOfCounter(cid);

				if (kwdids.size() < 2) {
					continue;
				}

				Counter<Integer> gramCnts = Generics.newCounter();
				Counter<Integer> unigramCnts = Generics.newCounter();

				for (int kwdid : kwdids) {
					String kwdStr = kwdIndexer.getObject(kwdid);
					String[] two = kwdStr.split("\t");

					for (int i = 0; i < two.length; i++) {

						if (i != 0) {
							break;
						}

						String kwd = two[i].substring(1, two[i].length() - 1);
						kwd = normalize(kwd);

						if (kwd.length() == 0) {
							continue;
						}

						{
							Set<Integer> grams = Generics.newHashSet();

							for (Gram g : gg.generateQGrams(kwd)) {
								int gid = gramIndexer.getIndex(g.getString());
								gramCnts.incrementCount(gid, 1);
								grams.add(gid);
							}

							for (int gid : grams) {
								gramFreqs.incrementCount(gid, 1);
							}
						}

						{
							Set<Integer> unigrams = Generics.newHashSet();

							for (int j = 0; j < kwd.length(); j++) {
								int uid = kwd.charAt(j);
								unigramCnts.incrementCount(uid, 1);
								unigrams.add(uid);
							}

							for (int uid : unigrams) {
								unigramFreqs.incrementCount(uid, 1);
							}
						}
					}
				}

				if (gramCnts.size() > 0) {
					cents1.put(cid, VectorUtils.toSparseVector(gramCnts));
				}

				if (unigramCnts.size() > 0) {
					cents2.put(cid, VectorUtils.toSparseVector(unigramCnts));
				}
			}

			gram_freqs = new int[gramIndexer.size()];

			for (int gid : gramFreqs.keySet()) {
				gram_freqs[gid] = (int) gramFreqs.getCount(gid);
			}

			int max_id = 0;

			for (int id : unigramFreqs.keySet()) {
				max_id = Math.max(id, max_id);
			}

			unigram_freqs = new int[max_id + 1];

			for (int id : unigramFreqs.keySet()) {
				unigram_freqs[id] = (int) unigramFreqs.getCount(id);
			}
		}

		System.out.printf("[%d] q-grams\n", gramIndexer.size());
		System.out.printf("[%d] cluster centroids\n", cents1.size());

		int prefix_size = 6;

		int[][] gramPostings = buildGramPostings(cents1, prefix_size, gramIndexer.size());

		computeWeights(cents1, gram_freqs);

		computeWeights(cents2, unigram_freqs);

		double num_clusters = cents1.size();

		StopWatch stopWatch = StopWatch.newStopWatch();

		double cutoff_cosine = 0.7;

		int[] clusterToNewCluster = new int[kwdIndexer.size()];

		for (int new_cid : clusterToKwds.keySet()) {
			for (int cid : clusterToKwds.keySetOfCounter(new_cid)) {
				clusterToNewCluster[cid] = new_cid;
			}
		}

		for (int i = 0; i < 1; i++) {
			List<Integer> qids = Generics.newArrayList(cents1.keySet());

			int print_chunk_size_ = qids.size() / 100;

			Counter<Pair<Integer, Integer>> qcPairs = Generics.newCounter();

			for (int j = 0; j < qids.size(); j++) {
				if ((j + 1) % print_chunk_size_ == 0) {
					int progess = (int) ((j + 1f) / qids.size() * 100);
					System.out.printf("\r[%dth, %d percent - %d/%d, %s]", i + 1, progess, j + 1, qids.size(), stopWatch.stop());
				}

				int qid = qids.get(j);

				SparseVector qCent = cents1.get(qid);
				Counter<Integer> cands = Generics.newCounter();

				qCent.sortByValue();

				for (int k = 0; k < qCent.size() && k < prefix_size; k++) {
					int gid = qCent.indexAtLoc(k);
					double idf = TermWeighting.idf(num_clusters, gram_freqs[gid]);

					for (int cid : gramPostings[gid]) {
						int new_cid = clusterToNewCluster[cid];
						if (cents1.containsKey(new_cid)) {
							cands.incrementCount(new_cid, idf);
						}
					}
				}

				qCent.sortByIndex();

				cands.removeKey(qid);

				double mixture = 0.5;

				for (int cid : cands.getSortedKeys()) {
					String kwdStr1 = kwdIndexer.getObject(qid);
					String kwdStr2 = kwdIndexer.getObject(cid);

					double cosine1 = VectorMath.dotProduct(qCent, cents1.get(cid));
					double cosine2 = VectorMath.dotProduct(cents2.get(qid), cents2.get(cid));
					double cosine3 = ArrayMath.addAfterScale(cosine1, mixture, cosine2);

					if (cosine3 < cutoff_cosine) {
						break;
					}

					Pair<Integer, Integer> p1 = Generics.newPair(qid, cid);
					qcPairs.incrementCount(p1, cosine1);
				}
			}

			System.out.printf("\r[%dth, %d percent - %d/%d, %s]\n", i + 1, 100, qids.size(), qids.size(), stopWatch.stop());

			CounterMap<Integer, Integer> queryToClusters = Generics.newCounterMap(cents1.size());
			Set<Integer> used = Generics.newHashSet();

			for (Pair<Integer, Integer> p : qcPairs.getSortedKeys()) {
				int qid = p.getFirst();
				int cid = p.getSecond();

				// String[] s1 = kwdIndexer.getObject(qid).split("\t");
				// String[] s2 = kwdIndexer.getObject(cid).split("\t");
				//
				// if (s1[0].equals("데이터베이스") && s2[0].equals("데이타베이스")) {
				// System.out.printf("QID:\t%s\n", kwdIndexer.getObject(qid));
				// System.out.printf("CID:\t%s\n", kwdIndexer.getObject(cid));
				// System.out.println(VectorUtils.toCounter(cents.get(qid), gramIndexer));
				// System.out.println(VectorUtils.toCounter(cents.get(cid), gramIndexer));
				// System.out.printf("Cosine:\t%s\n", qcPairs.getCount(p));
				// System.out.printf("Cosine:\t%s\n", VectorMath.dotProduct(cents.get(qid), cents.get(cid)));
				// System.out.println();
				// }

				if (used.contains(cid)) {
					continue;
				}
				used.add(cid);

				queryToClusters.setCount(qid, cid, qcPairs.getCount(p));
			}

			Iterator<Integer> iter = queryToClusters.keySet().iterator();

			while (iter.hasNext()) {
				int qid = iter.next();
				if (used.contains(qid)) {
					iter.remove();
				}
			}

			if (queryToClusters.size() == 0) {
				break;
			}

			for (int qid : queryToClusters.keySet()) {
				Set<Integer> cids = Generics.newHashSet();
				cids.add(qid);
				cids.addAll(queryToClusters.keySetOfCounter(qid));

				int new_cid = min(cids);

				// if (cidSet.size() > 1) {
				// System.out.println("###########################");
				// for (int cid2 : cidSet) {
				// String label = clusterLabel.get(cid2);
				// System.out.printf("Label:\t%s\n", label);
				//
				// for (int kwid : clusterToKwds.getCounter(cid2).keySet()) {
				// String kwd = kwdIndexer.getObject(kwid);
				// System.out.printf("Keyword:\t%d\t%s\n", kwid, kwd);
				// }
				//
				// System.out.println("-------------------------");
				// }
				// System.out.println("");
				// }
				//
				// System.out.printf("%d -> %d, %s\n", new_cid, qid,
				// queryOutputs.getCounter(qid).keySet());

				Counter<Integer> newCent1 = Generics.newCounter();
				Counter<Integer> newCent2 = Generics.newCounter();
				Counter<Integer> kwds = Generics.newCounter();

				for (int cid : cids) {
					VectorMath.add(cents1.remove(cid), newCent1);
					VectorMath.add(cents2.remove(cid), newCent2);

					kwds.incrementAll(clusterToKwds.removeKey(cid));
				}

				newCent1.scale(1f / cids.size());
				newCent2.scale(1f / cids.size());

				cents1.put(new_cid, VectorUtils.toSparseVector(newCent1));
				cents2.put(new_cid, VectorUtils.toSparseVector(newCent2));

				clusterToKwds.setCounter(new_cid, kwds);

				for (int cid : cids) {
					clusterToNewCluster[cid] = new_cid;
				}
			}
		}

		for (int cid : clusterToKwds.keySet()) {
			for (int kwdid : clusterToKwds.keySetOfCounter(cid)) {
				kwdToCluster[kwdid] = cid;
			}
		}
	}

	private String getKoreanKeyword(int kwdid) {
		return kwdIndexer.getObject(kwdid).split("\t")[0];
	}

	private double getsyllableCosine(int cid1, int cid2) {
		int[] cids = new int[] { cid1, cid2 };
		SparseVector[] vs = new SparseVector[2];

		for (int i = 0; i < cids.length; i++) {
			Counter<Integer> c = new Counter<Integer>();
			for (int kwdid : clusterToKwds.keySetOfCounter(cids[i])) {
				String kwd = getKoreanKeyword(kwdid);
				String key = normalize(kwd);
				key = UnicodeUtils.decomposeToJamo(key);

				for (int j = 0; j < key.length(); j++) {
					c.incrementCount((int) key.charAt(j), 1);
				}
			}
			vs[i] = new SparseVector(c);
			VectorMath.unitVector(vs[i]);
		}
		return VectorMath.dotProduct(vs[0], vs[1], false);

	}

	private void matchKoreanCharacters() {
		System.out.println("match korean characters");

		int old_size = clusterToKwds.size();

		Map<Integer, SparseVector> clusterToChars = Generics.newHashMap();
		SetMap<Integer, Integer> gramToClusters = Generics.newSetMap();
		Indexer<String> gramIndexer = Generics.newIndexer();
		Counter<Integer> gramFreqs = Generics.newCounter();

		for (Entry<Integer, Counter<Integer>> e : clusterToKwds.getEntrySet()) {
			int cid = e.getKey();
			Counter<Integer> kwdids = e.getValue();
			Counter<Integer> chCnts = Generics.newCounter();

			for (int kwdid : kwdids.keySet()) {
				String kwdStr = kwdIndexer.getObject(kwdid);
				String[] two = kwdStr.split("\t");
				String kwd = two[0];
				String key = normalize(kwd);

				if (key.length() < 2) {
					continue;
				}

				// String s = UnicodeUtils.decomposeToJamo(korKwd);
				for (char c : key.toCharArray()) {
					chCnts.incrementCount((int) c, 1);
				}

				Counter<Integer> gramCnts = gg.generateQGrams(key, gramIndexer, true);

				for (int gid : gramCnts.keySet()) {
					gramToClusters.put(gid, cid);
					gramFreqs.incrementCount(gid, 1);
				}
			}

			if (chCnts.size() > 0) {
				SparseVector sv = VectorUtils.toSparseVector(chCnts);
				clusterToChars.put(cid, sv);
			}
		}

		SparseVector chFreqs = null;

		{
			Counter<Integer> freqs = Generics.newCounter();
			for (int cid : clusterToChars.keySet()) {
				SparseVector sv = clusterToChars.get(cid);
				for (int ch : sv.indexes()) {
					freqs.incrementCount(ch, 1);
				}
			}
			chFreqs = VectorUtils.toSparseVector(freqs);
		}

		double num_clusters = clusterToKwds.keySet().size();

		for (SparseVector sv : clusterToChars.values()) {
			for (int i = 0; i < sv.size(); i++) {
				int ch = sv.indexAtLoc(i);
				double ch_cnt = sv.valueAtLoc(i);
				double cluster_freq = chFreqs.valueAlways(ch);
				double weight = TermWeighting.tfidf(ch_cnt, num_clusters, cluster_freq);
				sv.setAtLoc(i, weight);
			}
			VectorMath.unitVector(sv);
		}

		List<Integer> cids = Generics.newArrayList(clusterToChars.keySet());

		CounterMap<Integer, Integer> clusterCosines = Generics.newCounterMap();

		for (int i = 0; i < cids.size(); i++) {
			if ((i + 1) % 1000 == 0) {
				System.out.printf("\r[%d/%d]", i + 1, cids.size());
			}
			int cid1 = cids.get(i);
			String kwdStr1 = kwdIndexer.getObject(cid1);
			String key = normalize(kwdStr1.split("\t")[0]);

			String kwd1 = kwdStr1.split("\t")[0];

			if (cid1 != 1072666) {
				continue;
			}

			Counter<Integer> gramIDFs = gg.generateQGrams(key, gramIndexer, false);

			for (int gid : gramIDFs.keySet()) {
				double idf = TermWeighting.idf(num_clusters, gramFreqs.getCount(gid));
				gramIDFs.setCount(gid, idf);
			}

			Counter<Integer> toCompare = Generics.newCounter();

			List<Integer> gids = gramIDFs.getSortedKeys();

			for (int j = 0; j < gids.size() && j < prefix_size; j++) {
				int gid = gids.get(j);
				toCompare.incrementAll(gramToClusters.get(gid, false), gramIDFs.getCount(gid));
			}

			if (toCompare.size() == 0) {
				continue;
			}

			SparseVector sv1 = clusterToChars.get(cid1);

			List<Integer> keys = toCompare.getSortedKeys();

			for (int j = 0; j < keys.size(); j++) {
				int cid2 = keys.get(j);
				String kwdStr2 = kwdIndexer.getObject(cid2);
				String kwd2 = kwdStr2.split("\t")[0];

				if (cid1 == cid2) {
					continue;
				}

				if (cid2 != 1072717) {
					continue;
				}

				if (clusterCosines.containKey(cid1, cid2) || clusterCosines.containKey(cid2, cid1)) {
					continue;
				}

				SparseVector sv2 = clusterToChars.get(cid2);
				double cosine = VectorMath.dotProduct(sv1, sv2);

				if (cosine >= 0.9) {
					clusterCosines.incrementCount(cid1, cid2, cosine);
					clusterCosines.incrementCount(cid2, cid1, cosine);
				} else if (cosine >= 0.75) {
					double cosine2 = getsyllableCosine(cid1, cid2);

					if (cosine2 >= 0.9) {
						clusterCosines.incrementCount(cid1, cid2, cosine);
						clusterCosines.incrementCount(cid2, cid1, cosine);
					}
				}
			}

			// System.out.println(kwdIndexer.getObject(cid1));
			// System.out.println(VectorUtils.toCounter(c, kwdIndexer).toStringSortedByValues(true, true, 10, "\t"));
			// System.out.println();
		}

		System.out.printf("\r[%d/%d]\n", cids.size(), cids.size());

		Set<Integer> used = Generics.newHashSet();

		for (int cid1 : clusterCosines.keySet()) {
			Counter<Integer> c = clusterCosines.getCounter(cid1);
			List<Integer> keys = c.getSortedKeys();

			if (used.contains(cid1)) {
				continue;
			}

			for (int i = 0; i < keys.size(); i++) {
				int cid2 = keys.get(i);
				double cosine = c.getCount(cid2);

				if (used.contains(cid2)) {
					continue;
				}

				used.add(cid1);
				used.add(cid2);

				// System.out.printf("[%s] + [%s]\n", kwdIndexer.getObject(cid1), kwdIndexer.getObject(cid2));

				Counter<Integer> newCluster = Generics.newCounter();
				newCluster.incrementAll(clusterToKwds.removeKey(cid1));
				newCluster.incrementAll(clusterToKwds.removeKey(cid2));

				int new_cid = Math.min(cid1, cid2);

				clusterToKwds.setCounter(new_cid, newCluster);

				break;
			}
		}

		for (int cid : clusterToKwds.keySet()) {
			for (int kwdid : clusterToKwds.keySetOfCounter(cid)) {
				kwdToCluster[kwdid] = cid;
			}
		}

		int new_size = clusterToKwds.size();

		System.out.printf("[%d -> %d clusters]\n", old_size, new_size);

	}

	private void matchLanguage(boolean isEnglish) {
		System.out.println("match language (English: " + isEnglish + ")");

		int old_size = clusterToKwds.size();

		CounterMap<String, Integer> keyToClusters = Generics.newCounterMap();

		for (Entry<Integer, Counter<Integer>> e : clusterToKwds.getEntrySet()) {
			int cid = e.getKey();
			Counter<Integer> kwdids = e.getValue();

			for (int kwdid : kwdids.keySet()) {
				String kwdStr = kwdIndexer.getObject(kwdid);
				String[] two = kwdStr.split("\t");
				String key = StrUtils.value(isEnglish, two[1], two[0]);
				key = normalize(key);
				// if (key.length() < 4) {
				// continue;
				// }
				keyToClusters.incrementCount(key, cid, 1);
			}
		}

		for (String key : keyToClusters.keySet()) {
			Set<Integer> cids = keyToClusters.keySetOfCounter(key);

			Counter<Integer> kwds = Generics.newCounter();

			for (int cid : cids) {
				Counter<Integer> tmp = clusterToKwds.removeKey(cid);
				if (tmp != null) {
					kwds.incrementAll(tmp);
				}
			}

			int new_cid = min(cids);

			clusterToKwds.setCounter(new_cid, kwds);

			for (int kwdid : kwds.keySet()) {
				kwdToCluster[kwdid] = new_cid;
			}
		}

		int new_size = clusterToKwds.size();

		System.out.printf("[%d -> %d clusters]\n", old_size, new_size);
	}

	private void matchTwoLanguages() {
		System.out.println("match two languages");

		int old_size = clusterToKwds.size();

		SetMap<String, Integer> keyToKwds = Generics.newSetMap();

		for (int i = 0; i < kwdIndexer.size(); i++) {
			String kwdStr = kwdIndexer.getObject(i);
			kwdStr = kwdStr.replace("\t", "tab").replaceAll("[\\p{Punct}]+", "").toLowerCase();
			keyToKwds.put(kwdStr, i);
		}

		for (String kwd : keyToKwds.keySet()) {
			Set<Integer> kwdids = keyToKwds.get(kwd);

			if (kwdids.size() > 1) {
				Set<Integer> cids = Generics.newHashSet();
				Counter<Integer> newCluster = Generics.newCounter();

				for (int kwid : kwdids) {
					int cid = kwdToCluster[kwid];
					cids.add(cid);
					newCluster.incrementAll(clusterToKwds.removeKey(cid));
				}

				int new_cid = min(cids);

				clusterToKwds.setCounter(new_cid, newCluster);

				for (int kwdid : kwdids) {
					kwdToCluster[kwdid] = new_cid;
				}
			}
		}

		int new_size = clusterToKwds.size();

		System.out.printf("[%d -> %d clusters]\n", old_size, new_size);

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

	public void printClusters() {
		StringBuffer sb = new StringBuffer();
		sb.append(String.format("Clusters:\t%d", clusterToKwds.size()));
		sb.append(String.format("\nKeywords:\t%d", (int) clusterToKwds.totalCount()));

		List<Integer> cids = clusterToKwds.getRowCountSums().getSortedKeys();
		// List<Integer> cids = Generics.newArrayList();
		//
		// {
		// List<String> keys = Generics.newArrayList();
		//
		// Map<Integer, Integer> map = Generics.newHashMap();
		//
		// for (int cid : clusterToKwds.keySet()) {
		// int kwdid = clusterToKwds.getCounter(cid).argMax();
		// map.put(kwdid, cid);
		// String kwd = kwdIndexer.getObject(kwdid);
		// keys.add(kwd);
		// }
		//
		// Collections.sort(keys);
		//
		// cids = Generics.newArrayList();
		// for (int i = 0; i < keys.size(); i++) {
		// int kwid = kwdIndexer.indexOf(keys.get(i));
		// int cid = map.get(kwid);
		// cids.add(cid);
		// }
		// }

		for (int i = 0, n = 1; i < cids.size() && i < 10; i++) {
			int cid = cids.get(i);
			sb.append(String.format("No:\t%d", n));
			sb.append(String.format("\nID:\t%d", cid));
			sb.append(String.format("\nLabel:\t%s", clusterLabel.get(cid)));
			sb.append(String.format("\nKeywords:\t%d", clusterToKwds.getCounter(cid).size()));

			Counter<Integer> c = Generics.newCounter();

			for (int kwdid : clusterToKwds.getCounter(cid).keySet()) {
				c.setCount(kwdid, kwdData.getKeywordFreqs()[kwdid]);
			}

			n++;

			List<Integer> kwdids = c.getSortedKeys();
			for (int j = 0; j < kwdids.size() && j < 5; j++) {
				int kwid = kwdids.get(j);
				int kw_freq = kwdData.getKeywordFreqs()[kwid];
				sb.append(String.format("\n%d:\t%d\t%s\t%d", j + 1, kwid, kwdIndexer.getObject(kwid), kw_freq));
			}
			sb.append("\n\n");
		}

		System.out.println(sb.toString());
	}

	private void selectClusterLabels() {
		System.out.println("select cluster labels");

		clusterLabel = Generics.newHashMap();

		for (int cid : clusterToKwds.keySet()) {
			Set<Integer> kwdids = clusterToKwds.getCounter(cid).keySet();
			Counter<String>[] scoreData = computeLabelScores(kwdids);
			String korLabel = scoreData[0].argMax();
			String engLabel = scoreData[1].argMax();

			clusterLabel.put(cid, korLabel + "\t" + engLabel);
		}
	}

	public void writeClusters(String fileName) {
		TextFileWriter writer = new TextFileWriter(fileName);

		writer.write(String.format("Clusters:\t%d", clusterToKwds.size()));
		writer.write(String.format("\nKeywords:\t%d", (int) clusterToKwds.totalCount()));

		List<Integer> cids = Generics.newArrayList();

		boolean sortAphabetically = false;

		if (sortAphabetically) {
			List<String> keys = Generics.newArrayList();

			Map<Integer, Integer> map = Generics.newHashMap();

			for (int cid : clusterToKwds.keySet()) {
				int kwdid = clusterToKwds.getCounter(cid).argMax();
				map.put(kwdid, cid);
				String kwd = kwdIndexer.getObject(kwdid);
				keys.add(kwd);
			}

			Collections.sort(keys);

			cids = Generics.newArrayList();
			for (int i = 0; i < keys.size(); i++) {
				int kwdid = kwdIndexer.indexOf(keys.get(i));
				int cid = map.get(kwdid);
				cids.add(cid);
			}
		} else {
			cids = clusterToKwds.getRowCountSums().getSortedKeys();
		}

		for (int i = 0, n = 1; i < cids.size(); i++) {
			int cid = cids.get(i);

			StringBuffer sb = new StringBuffer();
			sb.append(String.format("No:\t%d", n));
			sb.append(String.format("\nID:\t%d", cid));
			sb.append(String.format("\nLabel:\t%s", clusterLabel.get(cid)));
			sb.append(String.format("\nKeywords:\t%d", clusterToKwds.getCounter(cid).size()));

			Counter<Integer> c = Generics.newCounter();

			for (int kwdid : clusterToKwds.getCounter(cid).keySet()) {
				c.setCount(kwdid, kwdData.getKeywordFreqs()[kwdid]);
			}

			n++;

			List<Integer> kwids = c.getSortedKeys();
			for (int j = 0; j < kwids.size(); j++) {
				int kwid = kwids.get(j);
				int kw_freq = kwdData.getKeywordFreqs()[kwid];
				sb.append(String.format("\n%d:\t%d\t%s\t%d", j + 1, kwid, kwdIndexer.getObject(kwid), kw_freq));
			}
			writer.write("\n\n" + sb.toString());
		}
		writer.close();

		System.out.printf("write [%d] clusterToKwds at [%s]\n", clusterToKwds.size(), fileName);
	}

}