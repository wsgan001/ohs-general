package ohs.entity;

import java.io.BufferedWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ohs.io.IOUtils;
import ohs.io.TextFileReader;
import ohs.io.TextFileWriter;
import ohs.string.search.ppss.GramOrderer;
import ohs.string.search.ppss.StringRecord;
import ohs.types.Counter;
import ohs.types.CounterMap;

/**
 * @author Heung-Seon Oh
 * 
 * 
 * 
 */
public class EntityLinker implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7199650129494305577L;

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		System.out.println("process begins.");
		EntityLinker el = new EntityLinker();
		el.createSearchers(ENTPath.NAME_PERSON_FILE);

		Counter<Entity> scores = el.link("Mattingly");

		// System.out.println(scores.toStringSortedByValues(true, true, scores.size()));

		TextFileWriter writer = new TextFileWriter(ENTPath.EX_FILE);
		for (Entity e : scores.getSortedKeys()) {
			writer.write(e.toString() + "\t" + scores.getCount(e) + "\n");
		}
		writer.close();

		// TextFileReader reader = new TextFileReader("../../data/news_ir/ners.txt");
		// while (reader.hasNext()) {
		// String line = reader.next();
		//
		// }
		System.out.println("process ends.");
	}

	private SimplePivotalPrefixStringSearcher searcher;

	private TextFileWriter logWriter = new TextFileWriter(ENTPath.ODK_LOG_FILE);

	private List<StringRecord> srs;

	private Map<Integer, Entity> ents;

	private Map<Integer, Integer> recToEntIdMap;

	public EntityLinker() {

	}

	/**
	 * Create two pivotal prefix searchers for English and Korean. If extOrgFileName is given, global gram orders are determined based on
	 * external organization names.
	 * 
	 * @param dataFileName
	 * 
	 *            Contains external organization names. They are used to compute global gram orders employed in Searchers.
	 */
	public void createSearchers(String dataFileName) {
		srs = new ArrayList<StringRecord>();
		recToEntIdMap = new HashMap<Integer, Integer>();
		ents = new HashMap<Integer, Entity>();

		int q = 2;
		int tau = 3;

		TextFileReader reader = new TextFileReader(dataFileName);
		while (reader.hasNext()) {
			String line = reader.next();

			if (reader.getNumLines() == 1) {
				continue;
			}

			String[] parts = line.split("\t");
			String name = parts[0];
			String topic = parts[1];
			String catStr = parts[2];
			String variantStr = parts[3];

			Entity ent = new Entity(ents.size(), name, topic);
			ents.put(ent.getId(), ent);

			StringRecord sr = new StringRecord(srs.size(), name);
			srs.add(sr);

			recToEntIdMap.put(sr.getId(), ent.getId());

			if (!variantStr.equals("none")) {
				String[] variants = variantStr.split("\\|");
				for (int i = 0; i < variants.length; i++) {
					sr = new StringRecord(srs.size(), variants[i]);
					srs.add(sr);
					recToEntIdMap.put(sr.getId(), ent.getId());
				}
			}
		}

		System.out.printf("read [%d] records from [%d] entities at [%s].\n", srs.size(), ents.size(), dataFileName);

		GramOrderer gramOrderer = new GramOrderer();

		searcher = new SimplePivotalPrefixStringSearcher(q, tau, true);
		searcher.setGramSorter(gramOrderer);
		searcher.index(srs);
	}

	public Counter<Entity> link(String name) {
		Counter<StringRecord> searchScore = searcher.search(name);

		CounterMap<Integer, Integer> cm = new CounterMap<Integer, Integer>();
		Counter<Entity> ret = new Counter<Entity>();

		for (StringRecord sr : searchScore.keySet()) {
			double score = searchScore.getCount(sr);
			int rid = sr.getId();
			StringRecord temp = srs.get(rid);
			int eid = recToEntIdMap.get(rid);
			cm.incrementCount(eid, rid, score);
		}

		for (int eid : cm.keySet()) {
			Counter<Integer> c = cm.getCounter(eid);
			ret.setCount(ents.get(eid), c.max());
		}

		logWriter.write(name + "\n");
		logWriter.write(searchScore.toString());
		logWriter.write("\n\n");

		// logWriter.write(orgScores2.toString() + "\n\n");

		return ret;
	}

	public void write(String fileName) throws Exception {
		BufferedWriter writer = IOUtils.openBufferedWriter(fileName);
		searcher.writeObject(fileName);
		writer.close();
	}

}
