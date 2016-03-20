package ohs.nlp.ling.types;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.tools.ant.taskdefs.SendEmail;

import ohs.utils.Generics;

public class KSentence {

	public static final String START_SYMBOL = "<s>";

	public static final String END_SYMBOL = "</s>";

	private MultiToken[] toks;

	public KSentence() {

	}

	public KSentence(List<MultiToken> toks) {
		this(toks.toArray(new MultiToken[toks.size()]));
	}

	public static KSentence parse(String s) {
		String[] lines = s.split("\n");
		MultiToken[] mts = new MultiToken[lines.length];
		for (int i = 0; i < lines.length; i++) {
			mts[i] = MultiToken.parse(lines[i]);
		}
		return new KSentence(mts);
	}

	public KSentence(MultiToken[] toks) {
		this.toks = toks;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		KSentence other = (KSentence) obj;
		if (!Arrays.equals(toks, other.toks))
			return false;
		return true;
	}

	public MultiToken getFirst() {
		return toks[0];
	}

	public MultiToken getLast() {
		return toks[toks.length - 1];
	}

	public KSentence getSentence(int start, int end) {
		return new KSentence(getTokens(start, end));
	}

	public MultiToken getToken(int i) {
		return toks[i];
	}

	public MultiToken[] getTokens() {
		return toks;
	}

	public MultiToken[] getTokens(int start, int end) {
		MultiToken[] ret = new MultiToken[end - start];
		for (int i = start, loc = 0; i < end; i++, loc++) {
			ret[loc] = toks[i];
		}
		return ret;
	}

	public String[] getValues() {
		return getValues(Token.DELIM_VALUE, MultiToken.DELIM_TOKEN, TokenAttr.values(), 0, toks.length);
	}

	public String[] getValues(String delimValue, String delimTok, TokenAttr[] attrs, int start, int end) {
		String[] ret = new String[end - start];
		for (int i = start, loc = 0; i < end; i++, loc++) {
			MultiToken tok = toks[i];
			ret[loc] = tok.joinValues(delimValue, delimTok, attrs);
		}
		return ret;
	}

	public String[] getValues(TokenAttr attr) {
		return getValues(Token.DELIM_VALUE, MultiToken.DELIM_TOKEN, new TokenAttr[] { attr }, 0, toks.length);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(toks);
		return result;
	}

	public String joinValues() {
		return joinValues(Token.DELIM_VALUE, MultiToken.DELIM_TOKEN, TokenAttr.values(), 0, toks.length);
	}

	public String joinValues(String delimValue, String delimTok, TokenAttr[] attrs, int start, int end) {
		return String.join(" ", getValues(delimValue, delimTok, attrs, start, end));
	}

	public int length() {
		int ret = 0;
		for (MultiToken t : toks) {
			ret += t.length();
		}
		return ret;
	}

	public void read(ObjectInputStream ois) throws Exception {
		toks = new MultiToken[ois.readInt()];
		for (int i = 0; i < toks.length; i++) {
			MultiToken mt = new MultiToken();
			mt.read(ois);
			toks[i] = mt;
		}
	}

	public int size() {
		return toks.length;
	}

	public KDocument toDocument() {
		return new KDocument(new KSentence[] { this });
	}

	@Override
	public String toString() {
		return toString(true);
	}

	public String toString(boolean printAttrNames) {
		StringBuffer sb = new StringBuffer();

		// if (printAttrNames) {
		// sb.append("Loc");
		// for (int i = 0; i < TokenAttr.values().length; i++) {
		// sb.append(String.format("\t%s", TokenAttr.values()[i]));
		// }
		// sb.append("\n");
		// }
		//
		// for (int i = 0; i < toks.length; i++) {
		// MultiToken tok = toks[i];
		// sb.append(String.format("%d\t%s", i, tok.joinValues()));
		// if (i != toks.length - 1) {
		// sb.append("\n");
		// }
		// }

		for (int i = 0; i < toks.length; i++) {
			sb.append(String.format("%d\t%s\t%s", i, toks[i].getText(), toks[i].joinValues()));
			if (i != toks.length - 1) {
				sb.append("\n");
			}
		}
		return sb.toString();
	}

	public KSentence toSymbolTaggedSentence() {
		List<MultiToken> ret = Generics.newArrayList();

		Token t = new Token();
		for (TokenAttr attr : TokenAttr.values()) {
			t.setValue(attr, START_SYMBOL);
		}
		ret.add(new MultiToken(-1, t));

		for (MultiToken mt : toks) {
			ret.add(mt);
		}

		t = new Token();
		for (TokenAttr attr : TokenAttr.values()) {
			t.setValue(attr, END_SYMBOL);
		}
		ret.add(new MultiToken(-1, t));

		return new KSentence(ret);
	}

	public Token[] toTokens() {
		List<Token> ret = Generics.newArrayList();
		for (MultiToken mt : toks) {
			for (Token t : mt.getTokens()) {
				ret.add(t);
			}
		}
		return ret.toArray(new Token[ret.size()]);
	}

	public void write(ObjectOutputStream oos) throws Exception {
		oos.writeInt(toks.length);
		for (int i = 0; i < toks.length; i++) {
			toks[i].write(oos);
		}
	}

}
