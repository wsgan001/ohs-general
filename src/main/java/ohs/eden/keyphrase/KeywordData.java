package ohs.eden.keyphrase;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import com.google.common.base.Stopwatch;

import ohs.io.FileUtils;
import ohs.io.TextFileReader;
import ohs.types.Counter;
import ohs.types.Indexer;
import ohs.types.ListMap;
import ohs.types.SetMap;
import ohs.utils.Generics;
import ohs.utils.StopWatch;
import ohs.utils.StrUtils;

public class KeywordData {

	private Indexer<String> kwdIndexer;

	private Indexer<String> docIndxer;

	private ListMap<Integer, Integer> keywordToDocs;

	private List<Integer> kwdids;

	private int[] kwd_freqs;

	private SetMap<Integer, Integer> clusters;

	private Map<Integer, String> clusterToLabel;

	public ListMap<Integer, Integer> getDocIdsList() {
		return keywordToDocs;
	}

	public Indexer<String> getDocIndexer() {
		return docIndxer;
	}

	public Indexer<String> getDocumentIndxer() {
		return docIndxer;
	}

	public ListMap<Integer, Integer> getKeywordDocs() {
		return keywordToDocs;
	}

	public int[] getKeywordFreqs() {
		return kwd_freqs;
	}

	public Indexer<String> getKeywordIndexer() {
		return kwdIndexer;
	}

	public List<Integer> getKeywords() {
		return kwdids;
	}

	public void read(String fileName) throws Exception {
		StopWatch stopWatch = StopWatch.newStopWatch();

		ObjectInputStream ois = FileUtils.openObjectInputStream(fileName);
		kwdIndexer = FileUtils.readStrIndexer(ois);
		kwdids = FileUtils.readIntList(ois);
		kwd_freqs = FileUtils.readIntArray(ois);

		docIndxer = FileUtils.readStrIndexer(ois);
		keywordToDocs = FileUtils.readIntListMap(ois);

		clusters = FileUtils.readIntSetMap(ois);
		clusterToLabel = FileUtils.readIntStrMap(ois);

		ois.close();

		System.out.printf("read [%s] at [%s] - [%s]\n", getClass().getName(), fileName, stopWatch.stop());
	}

	public void readText(String fileName) {
		StopWatch stopWatch = StopWatch.newStopWatch();

		kwdIndexer = Generics.newIndexer();
		docIndxer = Generics.newIndexer();

		kwdids = Generics.newArrayList();
		keywordToDocs = Generics.newListMap();

		clusters = Generics.newSetMap();
		clusterToLabel = Generics.newHashMap();

		Counter<Integer> kwdFreqs = Generics.newCounter();

		TextFileReader reader = new TextFileReader(fileName);
		while (reader.hasNext()) {
			String line = reader.next();

			if (line.startsWith(FileUtils.LINE_SIZE)) {
				int size = Integer.parseInt(line.split("\t")[1]);
				kwdids = Generics.newArrayList(size);
				keywordToDocs = Generics.newListMap(size);
				continue;
			}

			String[] parts = line.split("\t");

			String kor = parts[0];
			String eng = parts[1];
			double kwd_freq = Double.parseDouble(parts[2]);

			// kor = kor.substring(1, kor.length() - 2);
			// eng = eng.substring(1, eng.length() - 2);
			String kwdStr = kor + "\t" + eng;
			int kwdid = kwdIndexer.getIndex(kwdStr);
			kwdids.add(kwdid);
			kwdFreqs.setCount(kwdid, kwd_freq);

			List<Integer> docids = Generics.newArrayList();

			for (int i = 3; i < parts.length; i++) {
				String cn = parts[i];
				docids.add(docIndxer.getIndex(cn));
			}
			keywordToDocs.put(kwdid, docids);
		}
		reader.close();

		kwd_freqs = new int[kwdFreqs.size()];

		for (int i = 0; i < kwdIndexer.size(); i++) {
			kwd_freqs[i] = (int) kwdFreqs.getCount(i);
		}

		System.out.printf("read [%s] at [%s] - [%s]\n", getClass().getName(), fileName, stopWatch.stop());
	}

	public void setClusterLabel(Map<Integer, String> clusterToLabel) {
		this.clusterToLabel = clusterToLabel;
	}

	public void setClusters(SetMap<Integer, Integer> clusters) {
		this.clusters = clusters;
	}

	public void write(String fileName) throws Exception {
		System.out.printf("write [%s] at [%s]\n", getClass().getName(), fileName);

		ObjectOutputStream oos = FileUtils.openObjectOutputStream(fileName);

		FileUtils.writeStrIndexer(oos, kwdIndexer);
		FileUtils.writeIntCollection(oos, kwdids);
		FileUtils.writeIntArray(oos, kwd_freqs);

		FileUtils.writeStrIndexer(oos, docIndxer);
		FileUtils.writeIntListMap(oos, keywordToDocs);

		FileUtils.writeIntSetMap(oos, clusters);
		FileUtils.writeIntStrMap(oos, clusterToLabel);

		oos.close();
	}
}
