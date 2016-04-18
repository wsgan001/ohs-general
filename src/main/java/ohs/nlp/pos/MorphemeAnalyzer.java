package ohs.nlp.pos;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.math.stat.descriptive.SynchronizedMultivariateSummaryStatistics;

import com.mysql.fabric.xmlrpc.base.Struct;

import ohs.io.FileUtils;
import ohs.math.ArrayUtils;
import ohs.nlp.ling.types.KDocument;
import ohs.nlp.ling.types.KSentence;
import ohs.nlp.ling.types.MultiToken;
import ohs.nlp.ling.types.TokenAttr;
import ohs.tree.trie.hash.Node;
import ohs.tree.trie.hash.Trie;
import ohs.tree.trie.hash.Trie.SearchResult;
import ohs.tree.trie.hash.Trie.SearchResult.MatchType;
import ohs.types.Indexer;
import ohs.types.SetMap;
import ohs.utils.Generics;
import ohs.utils.StrUtils;
import ohs.utils.UnicodeUtils;

public class MorphemeAnalyzer {

	public static void main(String[] args) throws Exception {
		System.out.println("proces begins.");

		TextTokenizer t = new TextTokenizer();
		MorphemeAnalyzer a = new MorphemeAnalyzer();

		{
			String document = "프랑스의 세계적인 의상 디자이너 엠마누엘 웅가로가 실내 장식용 직물 디자이너로 나섰다.\n";

			KDocument doc = t.tokenize(document);
			a.analyze(doc);
		}

		// SejongReader r = new SejongReader(NLPPath.POS_DATA_FILE, NLPPath.POS_TAG_SET_FILE);
		// while (r.hasNext()) {
		// KDocument doc = r.next();
		//
		// StringBuffer sb = new StringBuffer();
		//
		// for (int i = 0; i < doc.size(); i++) {
		// KSentence sent = doc.getSentence(i);
		// MultiToken[] mts = MultiToken.toMultiTokens(sent.getTokens());
		//
		// for (int j = 0; j < mts.length; j++) {
		// sb.append(mts[j].getValue(TokenAttr.WORD));
		// if (j != mts.length - 1) {
		// sb.append(" ");
		// }
		// }
		// if (i != doc.size() - 1) {
		// sb.append("\n");
		// }
		// }
		//
		// KDocument newDoc = t.tokenize(sb.toString());
		// a.analyze(newDoc);
		//
		// }
		// r.close();

		System.out.println("proces ends.");
	}

	private SetMap<String, String> analDict;

	private Trie<Character> userDict;

	private Trie<Character> trie;

	public MorphemeAnalyzer() throws Exception {
		readAnalyzedDict(NLPPath.DICT_ANALYZED_FILE);
		readSystemDict(NLPPath.DICT_SYSTEM_FILE);
	}

	public void analyze(KDocument doc) {
		for (int i = 0; i < doc.size(); i++) {
			KSentence sent = doc.getSentence(i);
			MultiToken[] mts = sent.toMultiTokens();

			for (int j = 0; j < mts.length; j++) {
				MultiToken mt = mts[j];
				String eojeol = mt.getValue(TokenAttr.WORD);
				// Set<String> morphemes = analDict.get(eojeol, false);

				// if (morphemes == null) {
				analyze(eojeol);
				// } else {
				// String s = StrUtils.join(" # ", morphemes);
				// mt.setValue(TokenAttr.POS, s);
				// }
			}
		}

		System.out.println();
	}

	public Set<String> analyze(String word) {
		Set<String> ret = Generics.newHashSet();

		int L = word.length();

		Character[] chs = StrUtils.asCharacters(word);
		char[][] jasos = UnicodeUtils.decomposeToJamo(word);
		List<Integer> tmpStarts = getJosaStartLocs(word);
		List<Integer> tmpStarts2 = getEoMalEoMiStartLocs(word);

		System.out.println(word);
		System.out.println(tmpStarts);
		System.out.println(tmpStarts2);

		return ret;
	}

	private List<Integer> getJosaStartLocs(String word) {
		int L = word.length();
		List<Integer> ret = Generics.newArrayList();
		Character[] cs = StrUtils.asCharacters(word);

		for (int i = L - 1; i >= 0; i--) {
			SearchResult<Character> sr = trie.search(cs, i, L);

			if (sr.getMatchType() == MatchType.EXACT) {
				Set<SJTag> tags = (Set<SJTag>) sr.getNode().getData();

				if (tags != null) {
					for (SJTag tag : SJTag.JO_SA) {
						if (tags.contains(tag)) {
							ret.add(i);
							break;
						}
					}
				}
			} else {
				break;
			}

		}
		return ret;
	}

	private List<Integer> getEoMalEoMiStartLocs(String word) {
		int L = word.length();
		List<Integer> ret = Generics.newArrayList();
		Character[] cs = StrUtils.asCharacters(word);

		for (int i = L - 1; i >= 0; i--) {
			SearchResult<Character> sr = trie.search(cs, i, L);

			if (sr.getMatchType() == MatchType.EXACT) {
				Set<SJTag> tags = (Set<SJTag>) sr.getNode().getData();

				if (tags != null) {
					for (SJTag tag : SJTag.EO_MAL_EO_MI) {
						if (tags.contains(tag)) {
							ret.add(i);
							break;
						}
					}
				}
			} else {
				break;
			}

		}
		return ret;
	}

	private void search(Character[] cs, int start, int end) {
		if (start >= 0 && end <= cs.length) {
			SearchResult<Character> sr = trie.search(cs, start, end);

			if (sr.getMatchType() == MatchType.EXACT) {
				search(cs, start, end + 1);
			} else {
				search(cs, end - 1, end);
				System.out.println();
			}
		}
	}

	public void analyze(String[] words) {
		List<String>[] ret = new List[words.length];

		for (int i = 0; i < ret.length; i++) {
			ret[i] = Generics.newArrayList();
		}
	}

	private Indexer<String> posIndexer;

	// private Trie<Character> trie;

	private void readAnalyzedDict(String fileName) throws Exception {

		analDict = Generics.newSetMap();

		// trie = Trie.newTrie();
		//
		// TextFileReader reader = new TextFileReader(fileName);
		//
		// while (reader.hasNext()) {
		// String line = reader.next();
		//
		// if (reader.getNumLines() == 1) {
		// continue;
		// }
		//
		// String[] parts = line.split("\t");
		// for (int i = 1; i < parts.length; i++) {
		// analDict.put(parts[0], parts[i]);
		//
		// String[] subParts = parts[i].split(MultiToken.DELIM_MULTI_TOKEN.replace("+", "\\+"));
		//
		// for (int j = 0; j < subParts.length; j++) {
		// String[] two = subParts[j].split(Token.DELIM_TOKEN);
		// String word = two[0];
		// SJTag pos = SJTag.valueOf(two[1]);
		//
		// StringBuffer sb = new StringBuffer();
		// for (int k = 0; k < word.length(); k++) {
		// // sb.append(UnicodeUtils.decomposeToJamo(word.charAt(k)));
		// sb.append(word.charAt(k));
		// }
		//
		// Node<Character> node = trie.insert(StrUtils.asCharacters(sb.toString().toCharArray()));
		//
		// Counter<SJTag> c = (Counter<SJTag>) node.getData();
		//
		// if (c == null) {
		// c = Generics.newCounter();
		// node.setData(c);
		// }
		//
		// c.incrementCount(pos, 1);
		// }
		// }
		// }
		// reader.close();
		//
		// trie.trimToSize();
	}

	private void readSystemDict(String fileName) throws Exception {
		List<String> lines = FileUtils.readLines(fileName);

		trie = Trie.newTrie();

		for (String line : lines) {
			String[] parts = line.split("\t");
			String word = parts[0];
			SJTag pos = SJTag.valueOf(parts[1]);

			if (word.equals("의")) {
				System.out.println();
			}

			Node<Character> node = trie.insert(StrUtils.asCharacters(word));

			Set<SJTag> tags = (Set<SJTag>) node.getData();

			if (tags == null) {
				tags = Generics.newHashSet();
				node.setData(tags);
			}

			tags.add(pos);

		}

		trie.trimToSize();

	}

}
