package ohs.entity;

import java.io.BufferedWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ohs.io.FileUtils;
import ohs.string.search.ppss.Gram;
import ohs.string.search.ppss.GramGenerator;
import ohs.string.search.ppss.StringRecord;
import ohs.string.sim.CharacterSequence;
import ohs.string.sim.EditDistance;
import ohs.string.sim.Sequence;
import ohs.string.sim.SmithWaterman;
import ohs.types.Counter;
import ohs.types.DeepMap;
import ohs.types.Indexer;
import ohs.types.ListMap;
import ohs.utils.Generics;
import ohs.utils.Generics.ListType;
import ohs.utils.Generics.MapType;
import ohs.utils.StopWatch;

/**
 * 
 * @author Heung-Seon Oh
 */
public class StringSearcher implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8740333778747553831L;

	private Map<Integer, StringRecord> srs;

	private ListMap<Integer, Integer> index;

	private Indexer<String> gramIndexer;

	private GramGenerator gramGenerator;

	private DeepMap<String, Integer, Double> cache;

	private int top_k = 100;

	public StringSearcher() {
		this(3);
	}

	public StringSearcher(int q) {
		gramGenerator = new GramGenerator(q);
		cache = new DeepMap<String, Integer, Double>(1000, MapType.WEAK_HASH_MAP, MapType.WEAK_HASH_MAP);
	}

	public void filter() {
		System.out.println("filter index.");
		double filter_ratio = 0.1;
		double max = -Double.MAX_VALUE;
		int num_filtered = 0;

		Iterator<Integer> iter = index.keySet().iterator();

		while (iter.hasNext()) {
			int qid = iter.next();
			List<Integer> rids = index.get(qid, false);
			double ratio = 1f * rids.size() / srs.size();
			if (ratio < filter_ratio) {
				rids.clear();
				iter.remove();
				num_filtered++;
			}
			max = Math.max(ratio, max);
		}
	}

	public GramGenerator getGramGenerator() {
		return gramGenerator;
	}

	public Map<Integer, StringRecord> getStringRecords() {
		return srs;
	}

	public int getTopK() {
		return top_k;
	}

	public void index(List<StringRecord> input, boolean append) {
		System.out.printf("index [%s] records.\n", input.size());

		if (index == null && !append) {
			gramIndexer = new Indexer<String>();
			index = new ListMap<Integer, Integer>(1000, MapType.HASH_MAP, ListType.ARRAY_LIST);
			srs = Generics.newHashMap();
		}

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		int num_chunks = input.size() / 100;

		for (int i = 0; i < input.size(); i++) {

			if ((i + 1) % num_chunks == 0) {
				int progess = (int) ((i + 1f) / input.size() * 100);
				System.out.printf("\r[%d percent, %s]", progess, stopWatch.stop());
			}

			StringRecord sr = input.get(i);
			Gram[] grams = gramGenerator.generate(String.format("<%s>", sr.getString()));
			if (grams.length == 0) {
				continue;
			}

			Set<Integer> gids = Generics.newHashSet();

			for (int j = 0; j < grams.length; j++) {
				gids.add(gramIndexer.getIndex(grams[j].getString()));
			}

			for (int gid : gids) {
				index.put(gid, sr.getId());
			}

			srs.put(sr.getId(), sr);
		}

		System.out.printf("\r[%d percent, %s]\n", 100, stopWatch.stop());

		// for (int gid : index.keySet()) {
		// Collections.sort(index.get(gid));
		// }
	}

	public String info() {
		StringBuffer sb = new StringBuffer();
		sb.append(String.format("string records:\t%d\n", srs.size()));

		{
			Counter<Integer> c = new Counter<Integer>();

			int max = -Integer.MAX_VALUE;
			int min = Integer.MAX_VALUE;
			double num_chars = 0;

			for (StringRecord sr : srs.values()) {
				c.incrementCount(sr.getString().length(), 1);
				max = Math.max(max, sr.getString().length());
				min = Math.min(min, sr.getString().length());
				num_chars += sr.getString().length();
			}
			double avg_chars = num_chars / srs.size();
			sb.append(String.format("max record length:\t%d\n", max));
			sb.append(String.format("min record length:\t%d\n", min));
			sb.append(String.format("avg record length:\t%f\n", avg_chars));
		}

		{
			int max = -Integer.MAX_VALUE;
			int min = Integer.MAX_VALUE;
			int num_records = 0;

			for (int qid : index.keySet()) {
				List<Integer> rids = index.get(qid, false);
				max = Math.max(max, rids.size());
				min = Math.min(min, rids.size());
				num_records += rids.size();
			}
			double avg_records = 1f * num_records / index.size();
			sb.append(String.format("q-grams:\t%d\n", index.size()));
			sb.append(String.format("max postings:\t%d\n", max));
			sb.append(String.format("min Postings:\t%d\n", min));
			sb.append(String.format("avg Postings:\t%f", avg_records));
		}
		return sb.toString();
	}

	public void read(ObjectInputStream ois) throws Exception {
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		int size = ois.readInt();
		srs = Generics.newHashMap(size);
		for (int i = 0; i < size; i++) {
			StringRecord sr = new StringRecord();
			sr.read(ois);
			srs.put(sr.getId(), sr);
		}

		top_k = ois.readInt();
		gramGenerator = new GramGenerator(ois.readInt());
		gramIndexer = FileUtils.readIndexer(ois);

		int size1 = ois.readInt();
		index = new ListMap<Integer, Integer>(size1, MapType.HASH_MAP, ListType.ARRAY_LIST);

		for (int i = 0; i < size1; i++) {
			int gid = ois.readInt();
			List<Integer> rids = FileUtils.readIntegers(ois);
			index.set(gid, rids);
		}

		System.out.printf("read [%s] - [%s]\n", this.getClass().getName(), stopWatch.stop());
	}

	public Counter<StringRecord> search(String s) {
		Gram[] grams = gramGenerator.generate(String.format("<%s>", s));

		if (grams.length == 0) {
			return new Counter<StringRecord>();
		}

		Counter<Integer> candidates = new Counter<Integer>();
		for (int i = 0; i < grams.length; i++) {
			int gid = gramIndexer.indexOf(grams[i].getString());
			if (gid < 0) {
				continue;
			}
			List<Integer> rids = index.get(gid, false);
			if (rids != null) {
				double idf = Math.log((srs.size() + 1f) / rids.size());
				for (int rid : rids) {
					candidates.incrementCount(rid, idf);
				}
			}
		}

		SmithWaterman sw = new SmithWaterman();
		EditDistance ed = new EditDistance();
		Counter<StringRecord> ret = new Counter<StringRecord>();
		List<Integer> rids = candidates.getSortedKeys();

		for (int i = 0; i < rids.size() && i < top_k; i++) {
			StringRecord sr = srs.get(rids.get(i));
			double score = 0;

			if (cache.containsKeys(s, sr.getId())) {
				score = cache.get(s, sr.getId(), false);
			} else {
				Sequence ss = new CharacterSequence(s);
				Sequence tt = new CharacterSequence(sr.getString());
				double score1 = sw.getSimilarity(ss, tt);
				double score2 = ed.getSimilarity(ss, tt);
				score = score1 * score2;
				cache.put(s, sr.getId(), score);
			}
			ret.setCount(sr, score);
		}
		return ret;
	}

	public void setTopK(int top_k) {
		this.top_k = top_k;
	}

	public void write(ObjectOutputStream oos) throws Exception {
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		oos.writeInt(srs.size());
		for (StringRecord sr : srs.values()) {
			sr.write(oos);
		}

		oos.writeInt(top_k);
		oos.writeInt(gramGenerator.getQ());
		FileUtils.writeStrings(oos, gramIndexer.getObjects());

		oos.writeInt(index.size());
		Iterator<Integer> iter = index.keySet().iterator();

		while (iter.hasNext()) {
			int gid = iter.next();
			oos.writeInt(gid);
			FileUtils.writeIntegers(oos, index.get(gid));
		}
		oos.flush();

		System.out.printf("write [%s] - [%s]\n", this.getClass().getName(), stopWatch.stop());
	}

	public void write(String fileName) throws Exception {
		BufferedWriter writer = FileUtils.openBufferedWriter(fileName);
		writer.close();
	}
}